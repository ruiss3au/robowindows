#include "frame_mailbox.h"

#include <chrono>
#include <cstring>
#include <cstdint>
#include <cstdio>
#include <vector>

int main() {
    constexpr unsigned kWidth = 640;
    constexpr unsigned kHeight = 400;
    constexpr size_t kPitch = kWidth * sizeof(uint32_t);
    constexpr int kIterations = 1000;
    std::vector<uint32_t> pixels(static_cast<size_t>(kWidth) * kHeight, 0xff123456u);
    std::vector<uint32_t> copy_target(pixels.size());
    FrameMailbox mailbox;

    const auto copy_start = std::chrono::steady_clock::now();
    for (int i = 0; i < kIterations; ++i) {
        std::memcpy(copy_target.data(), pixels.data(), kPitch * kHeight);
    }
    const auto copy_elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - copy_start).count();
    const volatile uint32_t copied_sample = copy_target[0];
    const auto start = std::chrono::steady_clock::now();
    for (int i = 0; i < kIterations; ++i) {
        pixels[0] = static_cast<uint32_t>(i);
        if (!mailbox.publish(pixels.data(), kWidth, kHeight, kPitch).published) return 1;
        PublishedFrame frame{};
        if (!mailbox.acquire_latest(frame)) return 2;
        mailbox.release(frame);
    }
    const auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
            std::chrono::steady_clock::now() - start).count();
    const double copy_per_frame_us = static_cast<double>(copy_elapsed) / kIterations;
    const double per_frame_us = static_cast<double>(elapsed) / kIterations;
    std::printf("frame_handoff width=%u height=%u bytes=%zu iterations=%d "
                "copy_per_frame_us=%.2f mailbox_per_frame_us=%.2f sample=%u\n",
            kWidth, kHeight, kPitch * kHeight, kIterations,
            copy_per_frame_us, per_frame_us, copied_sample);
    return per_frame_us <= 2000.0 ? 0 : 3;
}
