#pragma once

#include <atomic>
#include <cstdint>
#include <limits>

enum class RuntimeState : uint8_t {
    Stopped,
    Starting,
    Foreground,
    Paused,
    Background,
    SurfaceLost,
    Stopping,
};

struct RuntimeTelemetrySnapshot {
    uint64_t interval_ms = 0;
    RuntimeState runtime_state = RuntimeState::Stopped;
    uint64_t emulator_run_calls = 0;
    uint64_t retro_run_max_us = 0;
    uint64_t retro_run_over_budget_calls = 0;
    uint64_t audio_producer_gap_max_us = 0;
    uint64_t scheduler_lateness_max_us = 0;
    uint64_t scheduler_catchup_calls = 0;
    uint64_t scheduler_deadline_resyncs = 0;
    uint64_t audio_produced_frames = 0;
    uint64_t audio_consumed_frames = 0;
    uint64_t audio_underrun_callbacks = 0;
    uint64_t audio_missing_frames = 0;
    uint64_t audio_dropped_frames = 0;
    uint64_t audio_saturated_samples = 0;
    uint64_t audio_stream_errors = 0;
    uint64_t guest_frames_submitted = 0;
    uint64_t frames_published = 0;
    uint64_t frames_presented = 0;
    uint64_t frames_coalesced = 0;
    uint64_t surface_post_failures = 0;
    uint64_t audio_queue_frames_min = 0;
    uint64_t audio_queue_frames_max = 0;
    uint64_t audio_queue_frames_current = 0;
};

class RuntimeTelemetry {
public:
    void reset(RuntimeState initial_state = RuntimeState::Stopped);
    void set_state(RuntimeState state);
    RuntimeState state() const;

    void add_emulator_run_calls(uint64_t count = 1);
    void observe_retro_run(uint64_t duration_us, uint64_t budget_us);
    void observe_audio_producer_gap(uint64_t gap_us);
    void observe_scheduler_lateness(uint64_t lateness_us);
    void add_scheduler_catchup_calls(uint64_t count = 1);
    void add_scheduler_deadline_resyncs(uint64_t count = 1);
    void add_audio_produced_frames(uint64_t count);
    void add_audio_consumed_frames(uint64_t count);
    void add_audio_underrun(uint64_t missing_frames);
    void add_audio_dropped_frames(uint64_t count);
    void add_audio_saturated_samples(uint64_t count);
    void add_audio_stream_errors(uint64_t count = 1);
    void add_guest_frames_submitted(uint64_t count = 1);
    void add_frames_published(uint64_t count = 1);
    void add_frames_presented(uint64_t count = 1);
    void add_frames_coalesced(uint64_t count = 1);
    void add_surface_post_failures(uint64_t count = 1);
    void observe_audio_queue_frames(uint64_t frames);

    RuntimeTelemetrySnapshot take_snapshot(uint64_t interval_ms);

private:
    static void update_min(std::atomic<uint64_t>& target, uint64_t value);
    static void update_max(std::atomic<uint64_t>& target, uint64_t value);

    std::atomic<RuntimeState> state_{RuntimeState::Stopped};
    std::atomic<uint64_t> emulator_run_calls_{0};
    std::atomic<uint64_t> retro_run_max_us_{0};
    std::atomic<uint64_t> retro_run_over_budget_calls_{0};
    std::atomic<uint64_t> audio_producer_gap_max_us_{0};
    std::atomic<uint64_t> scheduler_lateness_max_us_{0};
    std::atomic<uint64_t> scheduler_catchup_calls_{0};
    std::atomic<uint64_t> scheduler_deadline_resyncs_{0};
    std::atomic<uint64_t> audio_produced_frames_{0};
    std::atomic<uint64_t> audio_consumed_frames_{0};
    std::atomic<uint64_t> audio_underrun_callbacks_{0};
    std::atomic<uint64_t> audio_missing_frames_{0};
    std::atomic<uint64_t> audio_dropped_frames_{0};
    std::atomic<uint64_t> audio_saturated_samples_{0};
    std::atomic<uint64_t> audio_stream_errors_{0};
    std::atomic<uint64_t> guest_frames_submitted_{0};
    std::atomic<uint64_t> frames_published_{0};
    std::atomic<uint64_t> frames_presented_{0};
    std::atomic<uint64_t> frames_coalesced_{0};
    std::atomic<uint64_t> surface_post_failures_{0};
    std::atomic<uint64_t> audio_queue_frames_min_{std::numeric_limits<uint64_t>::max()};
    std::atomic<uint64_t> audio_queue_frames_max_{0};
    std::atomic<uint64_t> audio_queue_frames_current_{0};
};

const char* runtime_state_name(RuntimeState state);
