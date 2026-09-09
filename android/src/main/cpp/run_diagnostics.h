#pragma once

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <ctime>

struct RunTimingSnapshot {
    uint64_t calls = 0;
    uint64_t wall_total_us = 0;
    uint64_t process_cpu_total_us = 0;
    uint64_t process_cpu_max_us = 0;
    uint64_t cpu_clock_errors = 0;
    uint64_t host_gap_max_us = 0;
    uint64_t wake_late_max_us = 0;
    uint64_t video_total_us = 0;
    uint64_t video_max_us = 0;
    uint64_t audio_total_us = 0;
    uint64_t audio_max_us = 0;
};

// Owned by the frontend core thread, including its synchronous libretro callbacks.
// Process CPU includes the emulator worker, presenter and audio threads; it is
// intentionally not attributed to any one CPU decoder or derived as wall-minus-CPU.
class RunDiagnostics {
public:
    void reset() { snapshot_ = {}; has_previous_ = false; }
    void begin(int64_t now_ns) {
        if (!has_previous_) return;
        snapshot_.host_gap_max_us = std::max(snapshot_.host_gap_max_us,
                delta_us(previous_end_ns_, now_ns));
        snapshot_.wake_late_max_us = std::max(snapshot_.wake_late_max_us,
                delta_us(previous_deadline_ns_, now_ns));
    }
    void complete(int64_t start_ns, int64_t end_ns, int64_t cpu_start_ns,
            int64_t cpu_end_ns, int64_t deadline_ns) {
        ++snapshot_.calls;
        snapshot_.wall_total_us += delta_us(start_ns, end_ns);
        if (cpu_start_ns < 0 || cpu_end_ns < cpu_start_ns) {
            ++snapshot_.cpu_clock_errors;
        } else {
            const uint64_t cpu_us = delta_us(cpu_start_ns, cpu_end_ns);
            snapshot_.process_cpu_total_us += cpu_us;
            snapshot_.process_cpu_max_us = std::max(snapshot_.process_cpu_max_us, cpu_us);
        }
        previous_end_ns_ = end_ns;
        previous_deadline_ns_ = deadline_ns;
        has_previous_ = true;
    }
    void callback(bool video, uint64_t duration_us) {
        auto& total = video ? snapshot_.video_total_us : snapshot_.audio_total_us;
        auto& maximum = video ? snapshot_.video_max_us : snapshot_.audio_max_us;
        total += duration_us;
        maximum = std::max(maximum, duration_us);
    }
    RunTimingSnapshot take_snapshot() {
        const auto result = snapshot_;
        snapshot_ = {};
        return result;
    }
    static uint64_t delta_us(int64_t start, int64_t end) {
        return end > start ? static_cast<uint64_t>(end - start) / 1000 : 0;
    }
    static int64_t process_cpu_ns() {
        timespec value{};
        if (clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &value) != 0) return -1;
        return static_cast<int64_t>(value.tv_sec) * 1000000000LL + value.tv_nsec;
    }
private:
    RunTimingSnapshot snapshot_{};
    bool has_previous_ = false;
    int64_t previous_end_ns_ = 0;
    int64_t previous_deadline_ns_ = 0;
};

class CallbackTimingScope {
public:
    CallbackTimingScope(RunDiagnostics* diagnostics, bool video)
            : diagnostics_(diagnostics), video_(video) {
        if (diagnostics_) start_ = std::chrono::steady_clock::now();
    }
    ~CallbackTimingScope() {
        if (!diagnostics_) return;
        const auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now() - start_).count();
        diagnostics_->callback(video_, static_cast<uint64_t>(std::max<int64_t>(0, elapsed)));
    }
private:
    RunDiagnostics* diagnostics_;
    bool video_;
    std::chrono::steady_clock::time_point start_{};
};
