#include "terminal_report.h"
#include "robowindows_core_timing.h"
#include <cassert>
#include <iostream>

struct Clock {
    static int64_t wall_ns, cpu_ns;
    static int64_t wall() { return wall_ns; }
    static int64_t cpu() { return cpu_ns; }
};
int64_t Clock::wall_ns = 0, Clock::cpu_ns = 0;

int main() {
    TerminalReportGate gate;
    gate.reset(false);
    gate.periodic_reported(); gate.outputs_stopped(); gate.worker_stopped();
    assert(!gate.claim() && gate.periodic_records() == 0);
    gate.reset(true);
    assert(!gate.claim());
    gate.outputs_stopped(); assert(!gate.claim());
    gate.periodic_reported(); gate.periodic_reported();
    gate.worker_stopped(); assert(gate.claim()); assert(!gate.claim());
    gate.periodic_reported(); assert(gate.periodic_records() == 2);
    gate.reset(true); assert(gate.periodic_records() == 0 && !gate.claim());
    gate.worker_stopped(); assert(!gate.claim());
    gate.outputs_stopped(); assert(gate.claim());

    uint64_t errors = 0;
    assert(TerminalReportGate::elapsed_us(1000, 1000, errors) == 0);
    assert(TerminalReportGate::elapsed_us(1000, 1001, errors) == 0);
    assert(TerminalReportGate::elapsed_us(1000, 750999, errors) == 749);
    assert(TerminalReportGate::elapsed_us(1000, 1002001000, errors) == 1002000);
    assert(errors == 0);
    assert(TerminalReportGate::elapsed_us(-1, 0, errors) == 0);
    assert(TerminalReportGate::elapsed_us(1000, 999, errors) == 0 && errors == 2);

    // A last worker slice can complete after the last ordinary report. Drain
    // it after shutdown publication, before configure(false) erases pending_.
    RWTiming<Clock> worker; worker.configure(true);
    gate.reset(true);
    worker.worker_begin(); worker.event(RW_TRANSLATE);
    assert(worker.take().worker_slices == 0); gate.periodic_reported();
    gate.outputs_stopped(); assert(!gate.claim());
    Clock::wall_ns = 950000; Clock::cpu_ns = 420000;
    worker.worker_end(); gate.worker_stopped();
    assert(gate.claim());
    auto tail = worker.take();
    assert(tail.worker_slices == 1 && tail.translations == 1);
    assert(tail.worker_wall_total_us == 950 && tail.worker_cpu_total_us == 420);
    worker.configure(false);
    assert(worker.take().worker_slices == 0 && !gate.claim());

    // Existing lifecycle generation resets still discard stale work, not
    // resurrect it at terminal publication or carry it into a new session.
    worker.configure(true); gate.reset(true);
    worker.worker_begin(); worker.event(RW_OPCODE); worker.reset();
    Clock::wall_ns += 1000; Clock::cpu_ns += 1000;
    worker.worker_end(); gate.outputs_stopped(); gate.worker_stopped();
    assert(gate.claim()); tail = worker.take();
    assert(tail.discarded_slices == 1 && tail.fallback_opcode == 0);
    worker.configure(false); gate.reset(false);
    gate.outputs_stopped(); gate.worker_stopped(); assert(!gate.claim());
    std::cout << "Terminal gate, clock, completed-worker drain and reset checks passed\n";
}
