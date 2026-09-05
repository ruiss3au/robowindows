#include "audio_output.h"

void AudioOutputState::initialize() {
    generation_.fetch_add(1, std::memory_order_relaxed);
    phase_.store(AudioPhase::Prebuffering, std::memory_order_release);
}

bool AudioOutputState::mark_playing() {
    AudioPhase expected = AudioPhase::Prebuffering;
    return phase_.compare_exchange_strong(expected, AudioPhase::Playing,
            std::memory_order_acq_rel, std::memory_order_acquire);
}

void AudioOutputState::suspend() {
    const AudioPhase current = phase();
    if (current == AudioPhase::Stopped || current == AudioPhase::Suspended) return;
    generation_.fetch_add(1, std::memory_order_relaxed);
    phase_.store(AudioPhase::Suspended, std::memory_order_release);
}

void AudioOutputState::resume() {
    if (phase() != AudioPhase::Suspended) return;
    generation_.fetch_add(1, std::memory_order_relaxed);
    phase_.store(AudioPhase::Prebuffering, std::memory_order_release);
}

void AudioOutputState::begin_recovery() {
    if (phase() == AudioPhase::Stopped) return;
    phase_.store(AudioPhase::Recovering, std::memory_order_release);
}

void AudioOutputState::recovered() {
    if (phase() != AudioPhase::Recovering) return;
    generation_.fetch_add(1, std::memory_order_relaxed);
    phase_.store(AudioPhase::Prebuffering, std::memory_order_release);
}

void AudioOutputState::stop() {
    generation_.fetch_add(1, std::memory_order_relaxed);
    phase_.store(AudioPhase::Stopped, std::memory_order_release);
}

AudioPhase AudioOutputState::phase() const {
    return phase_.load(std::memory_order_acquire);
}

uint64_t AudioOutputState::generation() const {
    return generation_.load(std::memory_order_relaxed);
}

const char* audio_phase_name(AudioPhase phase) {
    switch (phase) {
        case AudioPhase::Stopped: return "stopped";
        case AudioPhase::Prebuffering: return "prebuffering";
        case AudioPhase::Playing: return "playing";
        case AudioPhase::Suspended: return "suspended";
        case AudioPhase::Recovering: return "recovering";
    }
    return "unknown";
}
