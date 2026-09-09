#include "run_diagnostics.h"
#include <cassert>

int main() {
    constexpr int64_t ms = 1000000;
    RunDiagnostics timing;
    timing.begin(10 * ms); // No invented gap before the first call.
    timing.complete(10 * ms, 20 * ms, 0, 15 * ms, 25 * ms);
    timing.callback(true, 80);
    timing.callback(true, 20);
    timing.callback(false, 7);
    timing.begin(30 * ms); // 10-ms gap, 5-ms late vs fixed deadline.
    timing.complete(30 * ms, 32 * ms, 15 * ms, 16 * ms, 35 * ms);
    auto first = timing.take_snapshot();
    assert(first.calls == 2 && first.wall_total_us == 12000);
    assert(first.process_cpu_total_us == 16000 && first.process_cpu_max_us == 15000);
    assert(first.cpu_clock_errors == 0); // Parallel CPU > wall is valid.
    assert(first.host_gap_max_us == 10000 && first.wake_late_max_us == 5000);
    assert(first.video_total_us == 100 && first.video_max_us == 80);
    assert(first.audio_total_us == 7 && first.audio_max_us == 7);
    auto empty = timing.take_snapshot();
    assert(empty.calls == 0 && empty.wall_total_us == 0 && empty.process_cpu_total_us == 0);
    assert(empty.video_total_us == 0 && empty.audio_total_us == 0);
    timing.begin(40 * ms); // Interval boundaries preserve gap continuity.
    timing.complete(40 * ms, 50 * ms, -1, -1, 45 * ms);
    auto failed = timing.take_snapshot();
    assert(failed.cpu_clock_errors == 1 && failed.process_cpu_total_us == 0);
    assert(failed.host_gap_max_us == 8000 && failed.wake_late_max_us == 5000);
    timing.complete(50 * ms, 51 * ms, 20 * ms, 19 * ms, 55 * ms);
    assert(timing.take_snapshot().cpu_clock_errors == 1);
    timing.reset(); // Pause/restart must not invent a long stall on resume.
    timing.begin(10000 * ms);
    timing.complete(10000 * ms, 10001 * ms, 0, 0, 10010 * ms);
    auto resumed = timing.take_snapshot();
    assert(resumed.host_gap_max_us == 0 && resumed.wake_late_max_us == 0);
    assert(resumed.process_cpu_max_us == 0 && resumed.cpu_clock_errors == 0);
    assert(RunDiagnostics::process_cpu_ns() >= 0);
    CallbackTimingScope disabled(nullptr, true);
}
