#include "frame_presenter.h"

#include <algorithm>
#include <chrono>
#include <ctime>

namespace {
uint64_t thread_cpu_us() {
    timespec value{};
    if (clock_gettime(CLOCK_THREAD_CPUTIME_ID, &value) != 0) return 0;
    return uint64_t(value.tv_sec) * 1000000 + value.tv_nsec / 1000;
}
}

FramePresenter::FramePresenter(FrameMailbox& mailbox, RuntimeTelemetry& telemetry)
        : mailbox_(mailbox), telemetry_(telemetry) {}

FramePresenter::~FramePresenter() {
    stop();
    set_window(nullptr);
}

void FramePresenter::start(int policy) {
    if (running_.exchange(true)) return;
    requested_ = policy;
    active_ = policy;
    fallback_ = false;
    inject_failure_ = false;
    { std::lock_guard<std::mutex> lock(metrics_mutex_); metrics_ = {}; }
    thread_ = std::thread(&FramePresenter::run, this);
}

void FramePresenter::stop() {
    if (!running_.exchange(false)) return;
    mailbox_.stop();
    wake_.notify_all();
    if (thread_.joinable()) thread_.join();
}

void FramePresenter::set_window(ANativeWindow* replacement) {
    std::lock_guard<std::mutex> lock(window_mutex_);
    if (pending_window_) ANativeWindow_release(pending_window_);
    pending_window_ = replacement;
    ++window_generation_;
    wake_.notify_all();
}

PresentationSnapshot FramePresenter::take_snapshot() {
    std::lock_guard<std::mutex> lock(metrics_mutex_);
    auto result = metrics_; metrics_ = {}; return result;
}

void FramePresenter::synchronize_window() {
    ANativeWindow* replacement;
    {
        std::lock_guard<std::mutex> lock(window_mutex_);
        if (active_generation_ == window_generation_) return;
        active_generation_ = window_generation_;
        replacement = pending_window_;
        if (replacement) ANativeWindow_acquire(replacement);
    }
    gpu_.reset(); gpu_ready_ = false;
    if (window_) ANativeWindow_release(window_);
    window_ = replacement;
    configured_window_ = nullptr;
}

void FramePresenter::run() {
    using Clock = std::chrono::steady_clock;
    auto next_present = std::chrono::steady_clock::now();
    auto last_present = Clock::time_point{};
    RuntimeState previous_state = RuntimeState::Stopped;
    while (running_) {
        const auto previous_generation = active_generation_;
        synchronize_window();
        const auto state = telemetry_.state();
        if (previous_generation != active_generation_ || previous_state != state) {
            last_present = {}; next_present = Clock::now(); previous_state = state;
        }
        PublishedFrame frame{};
        if (!mailbox_.wait_acquire_latest_for(frame, std::chrono::milliseconds(20))) continue;
        {
            std::unique_lock<std::mutex> lock(window_mutex_);
            wake_.wait_until(lock, next_present, [this] {
                return !running_ || active_generation_ != window_generation_;
            });
        }
        // Software keeps legacy acquisition behavior; GPU chooses newest after pacing.
        if (active_ == presentation::Gpu) {
            PublishedFrame latest{};
            if (mailbox_.acquire_latest(latest)) {
                latest.coalesced += frame.coalesced + 1;
                mailbox_.release(frame); frame = latest;
            }
        }
        synchronize_window();
        const auto now = Clock::now();
        if (!running_) {
            mailbox_.release(frame);
            break;
        }
        telemetry_.add_frames_coalesced(frame.coalesced);
        if (!window_) { mailbox_.release(frame); last_present = {}; continue; }
        const auto cpu_start = thread_cpu_us();
        uint64_t draw_us = 0, swap_us = 0;
        bool ok = false, failed_gpu = false;
        if (active_ == presentation::Gpu) {
            if (!gpu_ready_) gpu_ready_ = gpu_.initialize(window_);
            ok = !inject_failure_.exchange(false) && gpu_ready_ && gpu_.present(frame, draw_us, swap_us);
            if (!ok) {
                // A concurrent surface replacement is normal lifecycle, not a failure.
                bool changed;
                { std::lock_guard<std::mutex> lock(window_mutex_); changed = window_generation_ != active_generation_; }
                if (changed) { mailbox_.release(frame); continue; }
                gpu_.reset(); gpu_ready_ = false; configured_window_ = nullptr;
                active_ = presentation::Software; fallback_ = true; failed_gpu = true;
            }
        }
        if (active_ == presentation::Software) ok = present(frame);
        const auto cpu_end = thread_cpu_us();
        const auto posted_at = Clock::now();
        {
            std::lock_guard<std::mutex> lock(metrics_mutex_);
            metrics_.upload_draw_us += draw_us; metrics_.swap_us += swap_us;
            if (cpu_start && cpu_end >= cpu_start) metrics_.thread_cpu_us += cpu_end - cpu_start;
            else ++metrics_.cpu_clock_errors;
            if (failed_gpu) { ++metrics_.graphics_errors; ++metrics_.fallbacks; }
            if (ok && last_present != Clock::time_point{}) {
                metrics_.interval_max_us = std::max(metrics_.interval_max_us, uint64_t(
                        std::chrono::duration_cast<std::chrono::microseconds>(posted_at - last_present).count()));
            }
        }
        if (ok) {
            telemetry_.add_frames_presented();
            last_present = posted_at;
        } else {
            telemetry_.add_surface_post_failures();
        }
        mailbox_.release(frame);
        next_present = now + std::chrono::nanoseconds(presentation::interval_ns(active_));
    }
    gpu_.reset(); gpu_ready_ = false;
    if (window_) ANativeWindow_release(window_);
    window_ = configured_window_ = nullptr;
    active_generation_ = 0;
}

bool FramePresenter::present(const PublishedFrame& frame) {
    if (!window_) return false;
    if (configured_window_ != window_) {
        const int surface_width = ANativeWindow_getWidth(window_);
        const int surface_height = ANativeWindow_getHeight(window_);
        if (surface_width <= 0 || surface_height <= 0) return false;
        constexpr int kMaximumSoftwareWidth = 960;
        const int buffer_width = std::min(surface_width, kMaximumSoftwareWidth);
        const int buffer_height = std::max(1, static_cast<int>(
                static_cast<int64_t>(surface_height) * buffer_width / surface_width));
        if (ANativeWindow_setBuffersGeometry(window_, buffer_width, buffer_height,
                WINDOW_FORMAT_RGBA_8888) != 0) {
            return false;
        }
        configured_window_ = window_;
    }
    ANativeWindow_Buffer output{};
    if (ANativeWindow_lock(window_, &output, nullptr) != 0) return false;

    const auto* source = frame.pixels;
    auto* output_pixels = static_cast<uint32_t*>(output.bits);
    for (int y = 0; y < output.height; ++y) {
        std::fill(output_pixels + y * output.stride,
                output_pixels + y * output.stride + output.width, 0xff000000u);
    }
    const double scale = std::min(static_cast<double>(output.width) / frame.width,
            static_cast<double>(output.height) / frame.height);
    const int target_width = std::max(1, static_cast<int>(frame.width * scale));
    const int target_height = std::max(1, static_cast<int>(frame.height * scale));
    const int offset_x = (output.width - target_width) / 2;
    const int offset_y = (output.height - target_height) / 2;
    for (int y = 0; y < target_height; ++y) {
        const unsigned source_y = static_cast<unsigned>(y) * frame.height / target_height;
        const auto* src = reinterpret_cast<const uint32_t*>(
                source + static_cast<size_t>(source_y) * frame.pitch);
        auto* dst = output_pixels + (y + offset_y) * output.stride + offset_x;
        for (int x = 0; x < target_width; ++x) {
            const unsigned source_x = static_cast<unsigned>(x) * frame.width / target_width;
            const uint32_t pixel = src[source_x];
            const uint32_t red = (pixel >> 16) & 0xff;
            const uint32_t green = (pixel >> 8) & 0xff;
            const uint32_t blue = pixel & 0xff;
            dst[x] = red | (green << 8) | (blue << 16) | 0xff000000u;
        }
    }
    return ANativeWindow_unlockAndPost(window_) == 0;
}
