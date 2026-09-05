#pragma once

#include <android/native_window.h>

#include <atomic>
#include <mutex>
#include <thread>

#include "frame_mailbox.h"
#include "runtime_telemetry.h"

class FramePresenter {
public:
    FramePresenter(FrameMailbox& mailbox, RuntimeTelemetry& telemetry);
    ~FramePresenter();

    void start();
    void stop();
    void set_window(ANativeWindow* replacement);

private:
    void run();
    bool present(const PublishedFrame& frame);

    FrameMailbox& mailbox_;
    RuntimeTelemetry& telemetry_;
    std::atomic<bool> running_{false};
    std::thread thread_;
    std::mutex window_mutex_;
    ANativeWindow* window_ = nullptr;
    ANativeWindow* configured_window_ = nullptr;
};
