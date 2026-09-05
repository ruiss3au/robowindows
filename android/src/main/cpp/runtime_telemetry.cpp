#include "runtime_telemetry.h"

namespace {
constexpr uint64_t kUnobservedMinimum = std::numeric_limits<uint64_t>::max();
}

void RuntimeTelemetry::reset(RuntimeState initial_state) {
    state_.store(initial_state, std::memory_order_relaxed);
    emulator_run_calls_.store(0, std::memory_order_relaxed);
    audio_produced_frames_.store(0, std::memory_order_relaxed);
    audio_consumed_frames_.store(0, std::memory_order_relaxed);
    audio_underrun_callbacks_.store(0, std::memory_order_relaxed);
    audio_missing_frames_.store(0, std::memory_order_relaxed);
    audio_dropped_frames_.store(0, std::memory_order_relaxed);
    audio_saturated_samples_.store(0, std::memory_order_relaxed);
    audio_stream_errors_.store(0, std::memory_order_relaxed);
    guest_frames_submitted_.store(0, std::memory_order_relaxed);
    frames_published_.store(0, std::memory_order_relaxed);
    frames_presented_.store(0, std::memory_order_relaxed);
    frames_coalesced_.store(0, std::memory_order_relaxed);
    surface_post_failures_.store(0, std::memory_order_relaxed);
    audio_queue_frames_min_.store(kUnobservedMinimum, std::memory_order_relaxed);
    audio_queue_frames_max_.store(0, std::memory_order_relaxed);
}

void RuntimeTelemetry::set_state(RuntimeState state) {
    state_.store(state, std::memory_order_relaxed);
}

RuntimeState RuntimeTelemetry::state() const {
    return state_.load(std::memory_order_relaxed);
}

void RuntimeTelemetry::add_emulator_run_calls(uint64_t count) {
    emulator_run_calls_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_audio_produced_frames(uint64_t count) {
    audio_produced_frames_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_audio_consumed_frames(uint64_t count) {
    audio_consumed_frames_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_audio_underrun(uint64_t missing_frames) {
    audio_underrun_callbacks_.fetch_add(1, std::memory_order_relaxed);
    audio_missing_frames_.fetch_add(missing_frames, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_audio_dropped_frames(uint64_t count) {
    audio_dropped_frames_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_audio_saturated_samples(uint64_t count) {
    audio_saturated_samples_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_audio_stream_errors(uint64_t count) {
    audio_stream_errors_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_guest_frames_submitted(uint64_t count) {
    guest_frames_submitted_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_frames_published(uint64_t count) {
    frames_published_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_frames_presented(uint64_t count) {
    frames_presented_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_frames_coalesced(uint64_t count) {
    frames_coalesced_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::add_surface_post_failures(uint64_t count) {
    surface_post_failures_.fetch_add(count, std::memory_order_relaxed);
}

void RuntimeTelemetry::update_min(std::atomic<uint64_t>& target, uint64_t value) {
    uint64_t current = target.load(std::memory_order_relaxed);
    while (value < current && !target.compare_exchange_weak(current, value,
            std::memory_order_relaxed, std::memory_order_relaxed)) {}
}

void RuntimeTelemetry::update_max(std::atomic<uint64_t>& target, uint64_t value) {
    uint64_t current = target.load(std::memory_order_relaxed);
    while (value > current && !target.compare_exchange_weak(current, value,
            std::memory_order_relaxed, std::memory_order_relaxed)) {}
}

void RuntimeTelemetry::observe_audio_queue_frames(uint64_t frames) {
    update_min(audio_queue_frames_min_, frames);
    update_max(audio_queue_frames_max_, frames);
}

RuntimeTelemetrySnapshot RuntimeTelemetry::take_snapshot(uint64_t interval_ms) {
    RuntimeTelemetrySnapshot snapshot{};
    snapshot.interval_ms = interval_ms;
    snapshot.runtime_state = state();
    snapshot.emulator_run_calls = emulator_run_calls_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_produced_frames =
            audio_produced_frames_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_consumed_frames =
            audio_consumed_frames_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_underrun_callbacks =
            audio_underrun_callbacks_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_missing_frames =
            audio_missing_frames_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_dropped_frames =
            audio_dropped_frames_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_saturated_samples =
            audio_saturated_samples_.exchange(0, std::memory_order_relaxed);
    snapshot.audio_stream_errors =
            audio_stream_errors_.exchange(0, std::memory_order_relaxed);
    snapshot.guest_frames_submitted =
            guest_frames_submitted_.exchange(0, std::memory_order_relaxed);
    snapshot.frames_published = frames_published_.exchange(0, std::memory_order_relaxed);
    snapshot.frames_presented = frames_presented_.exchange(0, std::memory_order_relaxed);
    snapshot.frames_coalesced = frames_coalesced_.exchange(0, std::memory_order_relaxed);
    snapshot.surface_post_failures =
            surface_post_failures_.exchange(0, std::memory_order_relaxed);
    const uint64_t minimum =
            audio_queue_frames_min_.exchange(kUnobservedMinimum, std::memory_order_relaxed);
    snapshot.audio_queue_frames_min = minimum == kUnobservedMinimum ? 0 : minimum;
    snapshot.audio_queue_frames_max =
            audio_queue_frames_max_.exchange(0, std::memory_order_relaxed);
    return snapshot;
}

const char* runtime_state_name(RuntimeState state) {
    switch (state) {
        case RuntimeState::Stopped: return "stopped";
        case RuntimeState::Starting: return "starting";
        case RuntimeState::Foreground: return "foreground";
        case RuntimeState::Paused: return "paused";
        case RuntimeState::Background: return "background";
        case RuntimeState::SurfaceLost: return "surface_lost";
        case RuntimeState::Stopping: return "stopping";
    }
    return "unknown";
}
