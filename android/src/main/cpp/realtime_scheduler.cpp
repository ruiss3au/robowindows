#include "realtime_scheduler.h"

#include <algorithm>
#include <cmath>

namespace {
constexpr int64_t kNanosecondsPerSecond = 1000000000LL;
constexpr int64_t kMaximumDebtNs = 250000000LL;
constexpr unsigned kMaximumCatchUpCalls = 20;
constexpr uint64_t kLowQueueMs = 75;
constexpr uint64_t kTargetQueueMs = 100;
constexpr uint64_t kHighQueueMs = 125;
}

RealTimeScheduler::RealTimeScheduler(RuntimeTimingPolicy policy) : policy_(policy) {}

void RealTimeScheduler::configure(double frames_per_second, int sample_rate, int64_t now_ns) {
    if (std::isfinite(frames_per_second) && frames_per_second > 1.0) {
        base_interval_ns_ = std::max<int64_t>(1,
                static_cast<int64_t>(kNanosecondsPerSecond / frames_per_second));
    }
    if (sample_rate > 0) sample_rate_ = sample_rate;
    reset(now_ns);
}

void RealTimeScheduler::reset(int64_t now_ns) {
    deadline_ns_ = now_ns;
    correction_ = PacingCorrection::Nominal;
    consecutive_catch_up_calls_ = 0;
}

void RealTimeScheduler::observe_audio_queue(size_t frames) {
    if (policy_ != RuntimeTimingPolicy::Balanced100Ms || sample_rate_ <= 0) return;
    const uint64_t scaled_depth = static_cast<uint64_t>(frames) * 1000;
    const uint64_t low = static_cast<uint64_t>(sample_rate_) * kLowQueueMs;
    const uint64_t target = static_cast<uint64_t>(sample_rate_) * kTargetQueueMs;
    const uint64_t high = static_cast<uint64_t>(sample_rate_) * kHighQueueMs;
    if (scaled_depth < low) {
        correction_ = PacingCorrection::Faster;
    } else if (scaled_depth > high) {
        correction_ = PacingCorrection::Slower;
    } else if ((correction_ == PacingCorrection::Faster && scaled_depth >= target) ||
            (correction_ == PacingCorrection::Slower && scaled_depth <= target)) {
        correction_ = PacingCorrection::Nominal;
    }
}

SchedulerDecision RealTimeScheduler::complete_run(int64_t now_ns) {
    deadline_ns_ += paced_interval_ns();
    SchedulerDecision decision{};
    decision.deadline_ns = deadline_ns_;
    if (now_ns > deadline_ns_) {
        decision.lateness_us = static_cast<uint64_t>((now_ns - deadline_ns_) / 1000);
    }

    if (policy_ == RuntimeTimingPolicy::Legacy) {
        if (deadline_ns_ + base_interval_ns_ < now_ns) {
            deadline_ns_ = now_ns;
            decision.deadline_ns = deadline_ns_;
        }
        return decision;
    }

    const int64_t debt_ns = now_ns > deadline_ns_ ? now_ns - deadline_ns_ : 0;
    if (debt_ns > kMaximumDebtNs) {
        deadline_ns_ = now_ns;
        consecutive_catch_up_calls_ = 0;
        decision.deadline_ns = deadline_ns_;
        decision.resynchronized = true;
    } else if (debt_ns > 0) {
        ++consecutive_catch_up_calls_;
        decision.catch_up = true;
        if (consecutive_catch_up_calls_ >= kMaximumCatchUpCalls) {
            consecutive_catch_up_calls_ = 0;
            decision.yield_thread = true;
        }
    } else {
        consecutive_catch_up_calls_ = 0;
    }
    return decision;
}

RuntimeTimingPolicy RealTimeScheduler::policy() const {
    return policy_;
}

PacingCorrection RealTimeScheduler::correction() const {
    return correction_;
}

size_t RealTimeScheduler::prebuffer_frames() const {
    return static_cast<size_t>(sample_rate_) /
            (policy_ == RuntimeTimingPolicy::Balanced100Ms ? 10 : 5);
}

uint64_t RealTimeScheduler::run_budget_us() const {
    return static_cast<uint64_t>(base_interval_ns_ / 1000);
}

unsigned RealTimeScheduler::consecutive_catch_up_calls() const {
    return consecutive_catch_up_calls_;
}

int64_t RealTimeScheduler::paced_interval_ns() const {
    if (policy_ != RuntimeTimingPolicy::Balanced100Ms) return base_interval_ns_;
    if (correction_ == PacingCorrection::Faster) return base_interval_ns_ * 99 / 100;
    if (correction_ == PacingCorrection::Slower) return base_interval_ns_ * 101 / 100;
    return base_interval_ns_;
}

bool valid_runtime_timing_policy(int policy_id) {
    return policy_id == static_cast<int>(RuntimeTimingPolicy::Legacy) ||
            policy_id == static_cast<int>(RuntimeTimingPolicy::Balanced100Ms);
}

const char* runtime_timing_policy_name(RuntimeTimingPolicy policy) {
    switch (policy) {
        case RuntimeTimingPolicy::Legacy: return "legacy";
        case RuntimeTimingPolicy::Balanced100Ms: return "balanced_100ms";
    }
    return "unknown";
}

const char* pacing_correction_name(PacingCorrection correction) {
    switch (correction) {
        case PacingCorrection::Faster: return "faster_1pct";
        case PacingCorrection::Nominal: return "nominal";
        case PacingCorrection::Slower: return "slower_1pct";
    }
    return "unknown";
}
