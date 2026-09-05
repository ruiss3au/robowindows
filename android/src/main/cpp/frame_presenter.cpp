#include "frame_presenter.h"

#include <algorithm>
#include <chrono>

FramePresenter::FramePresenter(FrameMailbox& mailbox, RuntimeTelemetry& telemetry)
        : mailbox_(mailbox), telemetry_(telemetry) {}

FramePresenter::~FramePresenter() {
    stop();
    set_window(nullptr);
}

void FramePresenter::start() {
    if (running_.exchange(true)) return;
    thread_ = std::thread(&FramePresenter::run, this);
}

void FramePresenter::stop() {
    if (!running_.exchange(false)) return;
    mailbox_.stop();
    if (thread_.joinable()) thread_.join();
}

void FramePresenter::set_window(ANativeWindow* replacement) {
    std::lock_guard<std::mutex> lock(window_mutex_);
    if (window_) ANativeWindow_release(window_);
    window_ = replacement;
    configured_window_ = nullptr;
}

void FramePresenter::run() {
    // The SM-T500 can present 30 fps initially, but a sustained 30-fps software scale
    // causes enough thermal contention to starve the 70 Hz guest after several minutes.
    // Fifteen fps remains usable for the desktop while preserving more sustained CPU
    // headroom for guest time and audio on this tablet.
    constexpr auto kMinimumFrameInterval = std::chrono::milliseconds(66);
    auto next_present = std::chrono::steady_clock::now();
    while (running_) {
        PublishedFrame frame{};
        if (!mailbox_.wait_acquire_latest(frame)) break;
        auto now = std::chrono::steady_clock::now();
        if (now < next_present) std::this_thread::sleep_until(next_present);
        now = std::chrono::steady_clock::now();
        if (!running_) {
            mailbox_.release(frame);
            break;
        }
        telemetry_.add_frames_coalesced(frame.coalesced);
        if (present(frame)) {
            telemetry_.add_frames_presented();
        } else {
            telemetry_.add_surface_post_failures();
        }
        mailbox_.release(frame);
        next_present = now + kMinimumFrameInterval;
    }
}

bool FramePresenter::present(const PublishedFrame& frame) {
    std::lock_guard<std::mutex> lock(window_mutex_);
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
