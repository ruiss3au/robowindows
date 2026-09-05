#include "audio_output.h"

#include <cassert>
#include <cstring>

int main() {
    AudioOutputState state;
    assert(state.phase() == AudioPhase::Stopped);
    const uint64_t initial_generation = state.generation();
    state.initialize();
    assert(state.phase() == AudioPhase::Prebuffering);
    assert(state.generation() == initial_generation + 1);
    assert(state.mark_playing());
    assert(!state.mark_playing());
    assert(state.phase() == AudioPhase::Playing);

    const uint64_t playing_generation = state.generation();
    state.suspend();
    assert(state.phase() == AudioPhase::Suspended);
    assert(state.generation() == playing_generation + 1);
    state.suspend();
    assert(state.generation() == playing_generation + 1);
    state.resume();
    assert(state.phase() == AudioPhase::Prebuffering);
    assert(state.generation() == playing_generation + 2);

    state.begin_recovery();
    assert(state.phase() == AudioPhase::Recovering);
    state.recovered();
    assert(state.phase() == AudioPhase::Prebuffering);
    assert(std::strcmp(audio_phase_name(state.phase()), "prebuffering") == 0);

    state.stop();
    assert(state.phase() == AudioPhase::Stopped);
    state.resume();
    assert(state.phase() == AudioPhase::Stopped);
    state.begin_recovery();
    assert(state.phase() == AudioPhase::Stopped);
    return 0;
}
