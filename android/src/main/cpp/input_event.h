#pragma once

#include <cstddef>
#include <cstdint>
#include <cstdio>

namespace robowindows {
enum class EventType : uint8_t { Key, Mouse, Touch, Cancel };

struct InputEvent {
    EventType type{};
    int32_t values[7]{};
    float axes[6]{};
    int64_t time_nanos{};
    bool captured{};
};

inline bool ShouldForwardGuestMouse(const InputEvent& event) {
    return event.type == EventType::Mouse && event.captured;
}

inline void FormatInputEvent(char* result, size_t size, const InputEvent& event) {
    switch (event.type) {
        case EventType::Key:
            std::snprintf(result, size,
                    "{\"type\":\"key\",\"action\":%d,\"keyCode\":%d,\"scanCode\":%d,"
                    "\"repeat\":%d,\"meta\":%d,\"source\":%d,\"device\":%d,\"timeNanos\":%lld}",
                    event.values[0], event.values[1], event.values[2], event.values[3],
                    event.values[4], event.values[5], event.values[6],
                    static_cast<long long>(event.time_nanos));
            break;
        case EventType::Mouse:
            std::snprintf(result, size,
                    "{\"type\":\"mouse\",\"action\":%d,\"buttons\":%d,\"actionButton\":%d,"
                    "\"source\":%d,\"device\":%d,\"relativeX\":%.3f,\"relativeY\":%.3f,"
                    "\"absoluteX\":%.3f,\"absoluteY\":%.3f,\"verticalScroll\":%.3f,"
                    "\"horizontalScroll\":%.3f,\"timeNanos\":%lld,\"captured\":%s}",
                    event.values[0], event.values[1], event.values[2], event.values[3],
                    event.values[4], event.axes[0], event.axes[1], event.axes[2], event.axes[3],
                    event.axes[4], event.axes[5], static_cast<long long>(event.time_nanos),
                    event.captured ? "true" : "false");
            break;
        case EventType::Touch:
            std::snprintf(result, size,
                    "{\"type\":\"touch\",\"action\":%d,\"pointerCount\":%d,\"source\":%d,"
                    "\"device\":%d,\"x\":%.3f,\"y\":%.3f,\"pressure\":%.3f,\"timeNanos\":%lld}",
                    event.values[0], event.values[1], event.values[2], event.values[3],
                    event.axes[0], event.axes[1], event.axes[2],
                    static_cast<long long>(event.time_nanos));
            break;
        case EventType::Cancel:
            std::snprintf(result, size, "{\"type\":\"cancel\"}");
            break;
    }
}
}
