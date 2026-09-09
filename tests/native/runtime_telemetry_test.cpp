#include "runtime_telemetry.h"

#include <cassert>
#include <cstring>
#include <thread>

int main() {
    RuntimeTelemetry telemetry;
    telemetry.reset(RuntimeState::Starting);
    assert(telemetry.state() == RuntimeState::Starting);
    assert(std::strcmp(runtime_state_name(RuntimeState::SurfaceLost), "surface_lost") == 0);

    telemetry.set_state(RuntimeState::Foreground);
    telemetry.add_emulator_run_calls(7);
    telemetry.observe_retro_run(12000, 10000);
    telemetry.observe_retro_run(8000, 10000);
    telemetry.observe_audio_producer_gap(25000);
    telemetry.observe_scheduler_lateness(9000);
    telemetry.add_scheduler_catchup_calls(2);
    telemetry.add_scheduler_deadline_resyncs();
    telemetry.add_audio_produced_frames(800);
    telemetry.add_audio_consumed_frames(192);
    telemetry.add_audio_underrun(4);
    telemetry.add_audio_dropped_frames(3);
    telemetry.add_audio_saturated_samples(2);
    telemetry.add_audio_stream_errors(1);
    telemetry.add_guest_frames_submitted(5);
    telemetry.add_frames_published(4);
    telemetry.add_frames_presented(3);
    telemetry.add_frames_coalesced(1);
    telemetry.add_surface_post_failures(2);
    telemetry.observe_audio_queue_frames(100);
    telemetry.observe_audio_queue_frames(25);
    telemetry.observe_audio_queue_frames(150);

    RuntimeTelemetrySnapshot first = telemetry.take_snapshot(1000);
    assert(first.interval_ms == 1000);
    assert(first.runtime_state == RuntimeState::Foreground);
    assert(first.emulator_run_calls == 7);
    assert(first.retro_run_max_us == 12000);
    assert(first.retro_run_over_budget_calls == 1);
    assert(first.audio_producer_gap_max_us == 25000);
    assert(first.scheduler_lateness_max_us == 9000);
    assert(first.scheduler_catchup_calls == 2);
    assert(first.scheduler_deadline_resyncs == 1);
    assert(first.audio_produced_frames == 800);
    assert(first.audio_consumed_frames == 192);
    assert(first.audio_underrun_callbacks == 1);
    assert(first.audio_missing_frames == 4);
    assert(first.audio_dropped_frames == 3);
    assert(first.audio_saturated_samples == 2);
    assert(first.audio_stream_errors == 1);
    assert(first.guest_frames_submitted == 5);
    assert(first.frames_published == 4);
    assert(first.frames_presented == 3);
    assert(first.frames_coalesced == 1);
    assert(first.surface_post_failures == 2);
    assert(first.audio_queue_frames_min == 25);
    assert(first.audio_queue_frames_max == 150);
    assert(first.audio_queue_frames_current == 150);

    RuntimeTelemetrySnapshot reset_interval = telemetry.take_snapshot(500);
    assert(reset_interval.emulator_run_calls == 0);
    assert(reset_interval.audio_produced_frames == 0);
    assert(reset_interval.audio_queue_frames_min == 0);
    assert(reset_interval.audio_queue_frames_max == 0);
    assert(reset_interval.audio_queue_frames_current == 150);
    assert(reset_interval.retro_run_max_us == 0);
    assert(reset_interval.scheduler_deadline_resyncs == 0);
    assert(reset_interval.runtime_state == RuntimeState::Foreground);

    constexpr uint64_t kPerThread = 10000;
    std::thread producer_a([&] {
        for (uint64_t i = 0; i < kPerThread; ++i) telemetry.add_audio_produced_frames(1);
    });
    std::thread producer_b([&] {
        for (uint64_t i = 0; i < kPerThread; ++i) telemetry.add_audio_produced_frames(1);
    });
    producer_a.join();
    producer_b.join();
    assert(telemetry.take_snapshot(1000).audio_produced_frames == 2 * kPerThread);

    telemetry.reset(RuntimeState::Stopped);
    RuntimeTelemetrySnapshot stopped = telemetry.take_snapshot(1000);
    assert(stopped.runtime_state == RuntimeState::Stopped);
    assert(stopped.audio_queue_frames_current == 0);
    return 0;
}
