#pragma once

#include <cstddef>
#include <cstdint>

enum class RuntimeTimingPolicy : int {
    Legacy = 0,
    Balanced100Ms = 1,
};

enum class PacingCorrection : uint8_t {
    Faster,
    Nominal,
    Slower,
};

struct SchedulerDecision {
    int64_t deadline_ns = 0;
    uint64_t lateness_us = 0;
    bool catch_up = false;
    bool yield_thread = false;
    bool resynchronized = false;
};

class RealTimeScheduler {
public:
    explicit RealTimeScheduler(RuntimeTimingPolicy policy = RuntimeTimingPolicy::Legacy);

    void configure(double frames_per_second, int sample_rate, int64_t now_ns);
    void reset(int64_t now_ns);
    void observe_audio_queue(size_t frames);
    SchedulerDecision complete_run(int64_t now_ns);

    RuntimeTimingPolicy policy() const;
    PacingCorrection correction() const;
    size_t prebuffer_frames() const;
    uint64_t run_budget_us() const;
    unsigned consecutive_catch_up_calls() const;

private:
    int64_t paced_interval_ns() const;

    RuntimeTimingPolicy policy_;
    PacingCorrection correction_ = PacingCorrection::Nominal;
    int64_t base_interval_ns_ = 16666667;
    int64_t deadline_ns_ = 0;
    int sample_rate_ = 48000;
    unsigned consecutive_catch_up_calls_ = 0;
};

bool valid_runtime_timing_policy(int policy_id);
const char* runtime_timing_policy_name(RuntimeTimingPolicy policy);
const char* pacing_correction_name(PacingCorrection correction);
