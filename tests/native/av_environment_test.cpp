#include "av_environment.h"

#include <cassert>
#include <limits>
#include <initializer_list>
#include <sys/mman.h>
#include <unistd.h>

int main() {
    const unsigned ignored[] = {
        RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME, RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2,
        RETRO_ENVIRONMENT_SET_CORE_OPTIONS, RETRO_ENVIRONMENT_SET_VARIABLES,
        RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY, RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS,
        RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS, RETRO_ENVIRONMENT_SET_MEMORY_MAPS,
        RETRO_ENVIRONMENT_SET_GEOMETRY,
    };
    // An unreadable payload proves ignored commands do not reinterpret or inspect it.
    const size_t page_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    void* guard = mmap(nullptr, page_size, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(guard != MAP_FAILED);
    for (unsigned command : ignored) {
        for (const void* payload : {static_cast<const void*>(guard), static_cast<const void*>(nullptr)}) {
            const auto result = handle_av_environment(command, payload);
            assert(result.handled && result.accepted && result.fps == 0);
        }
    }
    assert(!handle_av_environment(RETRO_ENVIRONMENT_GET_LOG_INTERFACE, guard).handled);
    munmap(guard, page_size);
    assert(!handle_av_environment(RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO, nullptr).accepted);
    retro_system_av_info av{};
    for (double fps : {60.0, 70.086304, 70.007141, 1000.0}) {
        av.timing = {fps, 48000.0};
        const auto result = handle_av_environment(RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO, &av);
        assert(result.handled && result.accepted && result.fps == fps);
    }
    for (double fps : {0.0, 1.0, -70.0, 1000.1, 2.3667844159188239e63,
            std::numeric_limits<double>::infinity(), std::numeric_limits<double>::quiet_NaN()}) {
        av.timing = {fps, 48000.0};
        assert(!handle_av_environment(RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO, &av).accepted);
    }
    for (double rate : {0.0, -1.0, 384001.0, std::numeric_limits<double>::infinity(),
            std::numeric_limits<double>::quiet_NaN()}) {
        av.timing = {70.0, rate};
        assert(!handle_av_environment(RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO, &av).accepted);
    }
}
