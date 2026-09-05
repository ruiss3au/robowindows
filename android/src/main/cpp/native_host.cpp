#include <jni.h>
#include <android/log.h>
#include <array>
#include <cstdint>
#include <cstdio>
#include <mutex>
#include "core_host.h"
#include "input_event.h"

namespace {
using robowindows::EventType;
using robowindows::InputEvent;

constexpr size_t kQueueCapacity = 4096;
std::array<InputEvent, kQueueCapacity> queue{};
size_t queue_head = 0;
size_t queue_size = 0;
uint64_t key_events = 0;
uint64_t mouse_events = 0;
uint64_t touch_events = 0;
uint64_t cancellations = 0;
uint64_t dropped_events = 0;
std::mutex queue_mutex;

void push_event(const InputEvent& event) {
    std::lock_guard<std::mutex> lock(queue_mutex);
    if (queue_size == kQueueCapacity) {
        queue_head = (queue_head + 1) % kQueueCapacity;
        --queue_size;
        ++dropped_events;
        if (dropped_events == 1 || (dropped_events & (dropped_events - 1)) == 0) {
            __android_log_print(ANDROID_LOG_WARN, "RoboWindowsInput",
                    "bounded input history overflow dropped=%llu",
                    static_cast<unsigned long long>(dropped_events));
        }
    }
    queue[(queue_head + queue_size) % kQueueCapacity] = event;
    ++queue_size;
}
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_pushKey(JNIEnv*, jclass, jint action, jint key_code,
        jint scan_code, jint repeat_count, jint meta_state, jint source, jint device_handle,
        jlong event_time_nanos) {
    InputEvent event{};
    event.type = EventType::Key;
    event.values[0] = action;
    event.values[1] = key_code;
    event.values[2] = scan_code;
    event.values[3] = repeat_count;
    event.values[4] = meta_state;
    event.values[5] = source;
    event.values[6] = device_handle;
    event.time_nanos = event_time_nanos;
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        ++key_events;
    }
    push_event(event);
    CoreInputKey(action, key_code, meta_state);
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_pushMouse(JNIEnv*, jclass, jint action,
        jfloat relative_x, jfloat relative_y, jfloat absolute_x, jfloat absolute_y,
        jint button_state, jint action_button, jfloat vertical_scroll,
        jfloat horizontal_scroll, jint source, jint device_handle, jlong event_time_nanos,
        jboolean captured) {
    InputEvent event{};
    event.type = EventType::Mouse;
    event.values[0] = action;
    event.values[1] = button_state;
    event.values[2] = action_button;
    event.values[3] = source;
    event.values[4] = device_handle;
    event.axes[0] = relative_x;
    event.axes[1] = relative_y;
    event.axes[2] = absolute_x;
    event.axes[3] = absolute_y;
    event.axes[4] = vertical_scroll;
    event.axes[5] = horizontal_scroll;
    event.time_nanos = event_time_nanos;
    event.captured = captured;
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        ++mouse_events;
    }
    push_event(event);
    if (ShouldForwardGuestMouse(event)) {
        CoreInputMouse(relative_x, relative_y, button_state, vertical_scroll, horizontal_scroll);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_pushTouch(JNIEnv*, jclass, jint action,
        jint pointer_count, jfloat x, jfloat y, jfloat pressure, jint source,
        jint device_handle, jlong event_time_nanos) {
    InputEvent event{};
    event.type = EventType::Touch;
    event.values[0] = action;
    event.values[1] = pointer_count;
    event.values[2] = source;
    event.values[3] = device_handle;
    event.axes[0] = x;
    event.axes[1] = y;
    event.axes[2] = pressure;
    event.time_nanos = event_time_nanos;
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        ++touch_events;
    }
    push_event(event);
}

extern "C" JNIEXPORT void JNICALL
Java_org_robowindows_app_NativeHost_cancelInput(JNIEnv*, jclass) {
    InputEvent event{};
    event.type = EventType::Cancel;
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        ++cancellations;
    }
    push_event(event);
    CoreInputCancel();
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_robowindows_app_NativeHost_inputStats(JNIEnv* env, jclass) {
    char result[192];
    {
        std::lock_guard<std::mutex> lock(queue_mutex);
        std::snprintf(result, sizeof(result),
                "{\"keys\":%llu,\"mouse\":%llu,\"touch\":%llu,\"cancels\":%llu,\"queued\":%zu,\"dropped\":%llu}",
                static_cast<unsigned long long>(key_events),
                static_cast<unsigned long long>(mouse_events),
                static_cast<unsigned long long>(touch_events),
                static_cast<unsigned long long>(cancellations), queue_size,
                static_cast<unsigned long long>(dropped_events));
    }
    return env->NewStringUTF(result);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_robowindows_app_NativeHost_lastInputEvent(JNIEnv* env, jclass) {
    char result[512];
    std::lock_guard<std::mutex> lock(queue_mutex);
    if (queue_size == 0) return env->NewStringUTF("{}");
    const InputEvent& event = queue[(queue_head + queue_size - 1) % kQueueCapacity];
    robowindows::FormatInputEvent(result, sizeof(result), event);
    return env->NewStringUTF(result);
}
