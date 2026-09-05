#pragma once

#include <atomic>
#include <cstdint>

enum class AudioPhase : uint8_t {
    Stopped,
    Prebuffering,
    Playing,
    Suspended,
    Recovering,
};

class AudioOutputState {
public:
    void initialize();
    bool mark_playing();
    void suspend();
    void resume();
    void begin_recovery();
    void recovered();
    void stop();

    AudioPhase phase() const;
    uint64_t generation() const;

private:
    std::atomic<AudioPhase> phase_{AudioPhase::Stopped};
    std::atomic<uint64_t> generation_{0};
};

const char* audio_phase_name(AudioPhase phase);
