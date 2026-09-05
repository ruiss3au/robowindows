LOCAL_PATH := $(call my-dir)
ROBOWINDOWS_LOCAL_PATH := $(LOCAL_PATH)

# Build the pinned DOSBox Pure source as a headless core. RoboWindows owns the
# frontend callbacks and never exposes an upstream menu or activity.
include $(LOCAL_PATH)/../../../../third_party/dosbox-pure/jni/Android.mk

LOCAL_PATH := $(ROBOWINDOWS_LOCAL_PATH)

include $(CLEAR_VARS)
LOCAL_MODULE := robowindows_host
LOCAL_SRC_FILES := native_host.cpp core_host.cpp runtime_telemetry.cpp frame_mailbox.cpp \
    frame_presenter.cpp audio_ring.cpp audio_output.cpp
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../../../third_party/dosbox-pure/libretro-common/include
LOCAL_CPPFLAGS := -std=c++17 -Wall -Wextra -Werror
LOCAL_LDLIBS := -llog -landroid -laaudio
LOCAL_SHARED_LIBRARIES := retro
include $(BUILD_SHARED_LIBRARY)
