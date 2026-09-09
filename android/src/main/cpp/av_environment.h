#pragma once

#include <cmath>
#include "libretro.h"

struct AvEnvironmentResult {
    bool handled = false;
    bool accepted = false;
    double fps = 0;
    double sample_rate = 0;
};

// Payloads for these commands are unrelated types. Only AV_INFO may read timing.
inline AvEnvironmentResult handle_av_environment(unsigned command, const void* data) {
    switch (command) {
        case RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_V2:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS:
        case RETRO_ENVIRONMENT_SET_VARIABLES:
        case RETRO_ENVIRONMENT_SET_CORE_OPTIONS_DISPLAY:
        case RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS:
        case RETRO_ENVIRONMENT_SET_SUPPORT_ACHIEVEMENTS:
        case RETRO_ENVIRONMENT_SET_MEMORY_MAPS:
        case RETRO_ENVIRONMENT_SET_GEOMETRY:
            return {true, true, 0, 0};
        case RETRO_ENVIRONMENT_SET_SYSTEM_AV_INFO: {
            if (!data) return {true, false, 0, 0};
            const auto& timing = static_cast<const retro_system_av_info*>(data)->timing;
            if (!std::isfinite(timing.fps) || timing.fps <= 1 || timing.fps > 1000 ||
                    !std::isfinite(timing.sample_rate) || timing.sample_rate <= 0 ||
                    timing.sample_rate > 384000) return {true, false, 0, 0};
            return {true, true, timing.fps, timing.sample_rate};
        }
        default:
            return {};
    }
}
