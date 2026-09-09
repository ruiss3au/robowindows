#pragma once

#include <android/native_window.h>

#include <atomic>
#include <mutex>
#include <condition_variable>
#include <thread>

#include "frame_mailbox.h"
#include "runtime_telemetry.h"
#include "gpu_presenter.h"
#include "presentation_policy.h"

struct PresentationSnapshot {
    uint64_t interval_max_us = 0, upload_draw_us = 0, swap_us = 0, thread_cpu_us = 0;
    uint64_t graphics_errors = 0, fallbacks = 0, cpu_clock_errors = 0;
};

class FramePresenter {
public:
    FramePresenter(FrameMailbox& mailbox, RuntimeTelemetry& telemetry);
    ~FramePresenter();

    void start(int policy = presentation::Software);
    void stop();
    void set_window(ANativeWindow* replacement);
    int status() const { return fallback_ ? -1 : active_.load(); }
    int requested() const { return requested_; }
    PresentationSnapshot take_snapshot();
    // Only the disposable graphics probe calls this; no guest-facing control.
    void inject_gpu_failure_for_test() { inject_failure_ = true; }

private:
    void run();
    bool present(const PublishedFrame& frame);
    void synchronize_window();

    FrameMailbox& mailbox_;
    RuntimeTelemetry& telemetry_;
    std::atomic<bool> running_{false};
    std::thread thread_;
    std::mutex window_mutex_;
    std::condition_variable wake_;
    ANativeWindow* pending_window_ = nullptr;
    uint64_t window_generation_ = 0, active_generation_ = 0;
    int requested_ = presentation::Software;
    std::atomic<int> active_{presentation::Software};
    std::atomic<bool> fallback_{false};
    std::atomic<bool> inject_failure_{false};
    GpuPresenter gpu_;
    bool gpu_ready_ = false;
    std::mutex metrics_mutex_;
    PresentationSnapshot metrics_;
    ANativeWindow* window_ = nullptr;
    ANativeWindow* configured_window_ = nullptr;
};
