#pragma once
#include <cstdint>

// Owned by the frontend thread after reset before thread launch. The worker
// remains opaque: only its existing shutdown handshake authorizes final drain.
class TerminalReportGate {
    bool enabled_ = false, outputs_ = false, worker_ = false, claimed_ = false;
    uint64_t periodic_ = 0;
public:
    void reset(bool enabled) {
        enabled_ = enabled; outputs_ = worker_ = claimed_ = false; periodic_ = 0;
    }
    void periodic_reported() { if (enabled_ && !claimed_) ++periodic_; }
    void outputs_stopped() { if (enabled_) outputs_ = true; }
    void worker_stopped() { if (enabled_) worker_ = true; }
    bool claim() {
        if (!enabled_ || !outputs_ || !worker_ || claimed_) return false;
        claimed_ = true; return true;
    }
    uint64_t periodic_records() const { return periodic_; }
    static uint64_t elapsed_us(int64_t begin_ns, int64_t end_ns, uint64_t& errors) {
        if (begin_ns < 0 || end_ns < begin_ns) { ++errors; return 0; }
        return static_cast<uint64_t>(end_ns - begin_ns) / 1000;
    }
};
