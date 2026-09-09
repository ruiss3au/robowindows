#include "graphics_probe.h"
#include "frame_presenter.h"
#include <chrono>
#include <sstream>

std::string run_graphics_probe(ANativeWindow* window) {
    // No emulator, files or guest state. This is graphics evidence, not a clock/audio gate.
    GpuPresenter gpu;
    if (!gpu.initialize(window)) return "FAIL initialize";
    for (unsigned width : {320u, 640u, 801u}) {
        const unsigned height = width == 801 ? 601 : 200;
        const size_t stride = width + 4;
        std::vector<uint32_t> pixels(stride * height, 0x12345678);
        const uint32_t colors[] = {0xff0000, 0x00ff00, 0x0000ff, 0xffffff, 0x000000, 0xffff00,
                0x00ffff, 0xff00ff, 0x778899};
        for (unsigned y = 0; y < height; ++y) for (unsigned x = 0; x < width; ++x) {
            pixels[y * stride + x] = colors[(y * 3 / height) * 3 + x * 3 / width];
        }
        PublishedFrame frame{0, 0, width, height, stride * 4,
                reinterpret_cast<const uint8_t*>(pixels.data()), 0};
        uint64_t draw = 0, swap = 0;
        if (!gpu.present(frame, draw, swap, true)) return "FAIL pattern/readback";
    }
    gpu.reset();
    if (!gpu.initialize(window)) return "FAIL context recreation";
    gpu.reset();
    std::ostringstream report;
    report << "PASS colors orientation padding resolution context";
    // Once Android's CPU API connects a Surface, do not reconnect EGL to that
    // same producer. Real mode changes create a new session SurfaceView.
    for (int policy : {presentation::Gpu, presentation::Software}) {
        FrameMailbox mailbox;
        RuntimeTelemetry telemetry;
        telemetry.reset(RuntimeState::Foreground);
        FramePresenter presenter(mailbox, telemetry);
        ANativeWindow_acquire(window); presenter.set_window(window);
        presenter.start(policy);
        std::vector<uint32_t> pixels(640 * 400, 0x2255aa);
        const auto start = std::chrono::steady_clock::now();
        for (unsigned i = 0; i < 240; ++i) {
            pixels[i % pixels.size()] ^= 0xffffff;
            mailbox.publish(pixels.data(), 640, 400, 640 * 4);
            std::this_thread::sleep_until(start + std::chrono::microseconds((i + 1) * 16667));
        }
        auto snapshot = telemetry.take_snapshot(4000);
        auto graphics = presenter.take_snapshot();
        report << " policy=" << policy << " posts=" << snapshot.frames_presented
               << " cpu_us=" << graphics.thread_cpu_us;
        if (presenter.status() != policy || snapshot.surface_post_failures || graphics.graphics_errors ||
                graphics.cpu_clock_errors || snapshot.frames_presented < (policy == 1 ? 108 : 54)) {
            presenter.stop(); return "FAIL pacing " + report.str();
        }
        // Detach/reattach while publishing: absence must not trigger fallback.
        presenter.set_window(nullptr);
        mailbox.publish(pixels.data(), 640, 400, 640 * 4);
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        ANativeWindow_acquire(window); presenter.set_window(window);
        mailbox.publish(pixels.data(), 640, 400, 640 * 4);
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
        if (presenter.status() != policy) { presenter.stop(); return "FAIL surface recreation"; }
        if (policy == presentation::Gpu) {
            presenter.inject_gpu_failure_for_test();
            for (int i = 0; i < 20; ++i) {
                mailbox.publish(pixels.data(), 640, 400, 640 * 4);
                std::this_thread::sleep_for(std::chrono::milliseconds(20));
            }
            auto recovered = telemetry.take_snapshot(600);
            auto failure = presenter.take_snapshot();
            if (presenter.status() != -1 || failure.fallbacks != 1 || failure.graphics_errors != 1 ||
                    recovered.frames_presented < 3 || recovered.surface_post_failures) {
                presenter.stop(); return "FAIL software fallback";
            }
        }
        presenter.stop();
    }
    return report.str() + " surface fallback";
}
