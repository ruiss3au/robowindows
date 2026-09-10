#include "realtime_scheduler.h"
#include "run_diagnostics.h"

#include <cassert>
#include <cstring>
#include <limits>

namespace {
constexpr int64_t kMs = 1000000;
}

int main() {
    assert(valid_runtime_timing_policy(0));
    assert(valid_runtime_timing_policy(1));
    assert(!valid_runtime_timing_policy(-1));
    assert(!valid_runtime_timing_policy(2));
    assert(std::strcmp(runtime_timing_policy_name(RuntimeTimingPolicy::Balanced100Ms),
            "balanced_100ms") == 0);

    RealTimeScheduler legacy(RuntimeTimingPolicy::Legacy);
    legacy.configure(100.0, 48000, 0);
    assert(legacy.prebuffer_frames() == 9600);
    SchedulerDecision legacy_on_time = legacy.complete_run(2 * kMs);
    assert(legacy_on_time.deadline_ns == 10 * kMs);
    assert(!legacy_on_time.catch_up && !legacy_on_time.resynchronized);
    SchedulerDecision legacy_short_stall = legacy.complete_run(25 * kMs);
    assert(legacy_short_stall.deadline_ns == 20 * kMs);
    SchedulerDecision legacy_discard = legacy.complete_run(45 * kMs);
    assert(legacy_discard.deadline_ns == 45 * kMs);
    assert(!legacy_discard.resynchronized);

    RealTimeScheduler balanced(RuntimeTimingPolicy::Balanced100Ms);
    balanced.configure(100.0, 48000, 0);
    assert(balanced.prebuffer_frames() == 4800);
    assert(balanced.run_budget_us() == 10000);
    SchedulerDecision on_time = balanced.complete_run(2 * kMs);
    assert(on_time.deadline_ns == 10 * kMs);
    assert(!on_time.catch_up && !on_time.resynchronized);

    SchedulerDecision short_stall = balanced.complete_run(35 * kMs);
    assert(short_stall.deadline_ns == 20 * kMs);
    assert(short_stall.lateness_us == 15000);
    assert(short_stall.catch_up && !short_stall.resynchronized);
    SchedulerDecision recovered = balanced.complete_run(36 * kMs);
    assert(recovered.deadline_ns == 30 * kMs);
    assert(recovered.catch_up);
    SchedulerDecision settled = balanced.complete_run(39 * kMs);
    assert(settled.deadline_ns == 40 * kMs);
    assert(!settled.catch_up);
    assert(balanced.consecutive_catch_up_calls() == 0);

    balanced.reset(0);
    SchedulerDecision long_stall = balanced.complete_run(300 * kMs);
    assert(long_stall.resynchronized && !long_stall.catch_up);
    assert(long_stall.deadline_ns == 300 * kMs);

    RealTimeScheduler debt_boundary(RuntimeTimingPolicy::Balanced100Ms);
    debt_boundary.configure(100.0, 48000, 0);
    SchedulerDecision retained_boundary = debt_boundary.complete_run(260 * kMs);
    assert(retained_boundary.catch_up && !retained_boundary.resynchronized);
    assert(retained_boundary.lateness_us == 250000);
    SchedulerDecision clamped_boundary = debt_boundary.complete_run(271 * kMs);
    assert(clamped_boundary.resynchronized && !clamped_boundary.catch_up);

    balanced.reset(0);
    for (unsigned i = 0; i < 19; ++i) {
        SchedulerDecision catch_up = balanced.complete_run((20 + i * 10) * kMs);
        assert(catch_up.catch_up && !catch_up.resynchronized);
        assert(!catch_up.yield_thread);
    }
    SchedulerDecision bounded = balanced.complete_run(210 * kMs);
    assert(bounded.catch_up && bounded.yield_thread && !bounded.resynchronized);
    assert(balanced.consecutive_catch_up_calls() == 0);
    SchedulerDecision retained = balanced.complete_run(220 * kMs);
    assert(retained.catch_up && !retained.yield_thread && !retained.resynchronized);
    assert(retained.deadline_ns == 210 * kMs);

    assert(balanced.correction() == PacingCorrection::Nominal);
    balanced.observe_audio_queue(3500);
    assert(balanced.correction() == PacingCorrection::Faster);
    balanced.observe_audio_queue(4000);
    assert(balanced.correction() == PacingCorrection::Faster);
    balanced.observe_audio_queue(4800);
    assert(balanced.correction() == PacingCorrection::Nominal);

    RealTimeScheduler paced(RuntimeTimingPolicy::Balanced100Ms);
    paced.configure(100.0, 48000, 0);
    paced.observe_audio_queue(3500);
    assert(paced.complete_run(0).deadline_ns == 9900000);
    paced.observe_audio_queue(4800);
    assert(paced.complete_run(9900000).deadline_ns == 19900000);
    paced.observe_audio_queue(6100);
    assert(paced.complete_run(19900000).deadline_ns == 30000000);
    balanced.observe_audio_queue(6100);
    assert(balanced.correction() == PacingCorrection::Slower);
    balanced.observe_audio_queue(5500);
    assert(balanced.correction() == PacingCorrection::Slower);
    balanced.observe_audio_queue(4800);
    assert(balanced.correction() == PacingCorrection::Nominal);

    balanced.observe_audio_queue(0);
    assert(balanced.correction() == PacingCorrection::Faster);
    balanced.reset(900 * kMs);
    assert(balanced.correction() == PacingCorrection::Nominal);
    assert(balanced.consecutive_catch_up_calls() == 0);
    assert(balanced.complete_run(901 * kMs).deadline_ns == 910 * kMs);

    // A live refresh update is not a pause/resume: outstanding debt, queue
    // correction and the bounded catch-up burst must survive it.
    RealTimeScheduler changing(RuntimeTimingPolicy::Balanced100Ms);
    changing.configure(100.0, 48000, 0);
    changing.observe_audio_queue(0);
    for (unsigned i = 0; i < 19; ++i) {
        const auto d = changing.complete_run((20 + i * 10) * kMs);
        assert(d.catch_up && !d.yield_thread);
        changing.update_frame_rate(100.0, (20 + i * 10) * kMs);
    }
    changing.update_frame_rate(50.0, 210 * kMs);
    assert(changing.run_budget_us() == 20000);
    assert(changing.prebuffer_frames() == 4800);
    assert(changing.correction() == PacingCorrection::Faster);
    const auto change_debt = changing.complete_run(220 * kMs);
    assert(change_debt.deadline_ns == 207900000);
    assert(change_debt.lateness_us == 12100);
    assert(change_debt.catch_up && change_debt.yield_thread);
    changing.observe_audio_queue(4800);
    assert(changing.correction() == PacingCorrection::Nominal);

    RealTimeScheduler small_changes(RuntimeTimingPolicy::Balanced100Ms);
    small_changes.configure(100.0, 48000, 0);
    for (unsigned i = 0; i < 20; ++i) {
        const int64_t now = (150 + i * 10) * kMs;
        small_changes.update_frame_rate(100.0 + i * 0.01, now);
        const auto d = small_changes.complete_run(now);
        assert(d.catch_up && !d.resynchronized);
        assert(d.lateness_us >= 140000 && d.lateness_us < 141000);
        assert(d.yield_thread == (i == 19));
    }

    changing.configure(100.0, 48000, 0);
    changing.complete_run(240 * kMs);
    changing.update_frame_rate(50.0, 240 * kMs);
    const auto changed_clamp = changing.complete_run(281 * kMs);
    assert(changed_clamp.resynchronized && changed_clamp.lateness_us == 251000);
    assert(changed_clamp.deadline_ns == 281 * kMs);

    // Reproduce the previous call-site bug: configure treats refresh as a new
    // session and silently loses 230 ms of debt, hiding the later clamp.
    RealTimeScheduler discarded(RuntimeTimingPolicy::Balanced100Ms);
    discarded.configure(100.0, 48000, 0);
    discarded.complete_run(240 * kMs);
    discarded.configure(50.0, 48000, 240 * kMs);
    assert(!discarded.complete_run(281 * kMs).resynchronized);

    // Timing records remain a complete interval across live AV changes, while
    // lifecycle resets remain explicit and independently tested.
    RealTimeScheduler measured(RuntimeTimingPolicy::Balanced100Ms);
    measured.configure(100.0, 48000, 0);
    RunDiagnostics diagnostics;
    diagnostics.begin(10 * kMs);
    diagnostics.complete(10 * kMs, 20 * kMs, 0, 5 * kMs,
            measured.complete_run(20 * kMs).deadline_ns);
    diagnostics.callback(true, 200);
    measured.update_frame_rate(50.0, 25 * kMs);
    diagnostics.begin(40 * kMs);
    diagnostics.complete(40 * kMs, 42 * kMs, 5 * kMs, 6 * kMs,
            measured.complete_run(42 * kMs).deadline_ns);
    const auto across_refresh = diagnostics.take_snapshot();
    assert(across_refresh.calls == 2 && across_refresh.wall_total_us == 12000);
    assert(across_refresh.host_gap_max_us == 20000);
    assert(across_refresh.wake_late_max_us == 30000);
    assert(across_refresh.video_total_us == 200);

    changing.update_frame_rate(std::numeric_limits<double>::quiet_NaN(), 300 * kMs);
    changing.update_frame_rate(0.0, 300 * kMs);
    assert(changing.run_budget_us() == 20000);
    assert(changing.complete_run(282 * kMs).deadline_ns == 301 * kMs);
    changing.observe_audio_queue(7000);
    changing.update_frame_rate(100.0, 302 * kMs);
    assert(changing.correction() == PacingCorrection::Slower);
    changing.reset(400 * kMs);
    assert(changing.correction() == PacingCorrection::Nominal);
    assert(changing.complete_run(401 * kMs).deadline_ns == 410 * kMs);

    legacy.update_frame_rate(50.0, 100 * kMs);
    assert(legacy.complete_run(105 * kMs).deadline_ns == 120 * kMs);
    assert(legacy.prebuffer_frames() == 9600);
    return 0;
}
