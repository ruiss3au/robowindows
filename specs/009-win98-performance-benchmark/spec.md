# Feature Specification: Windows 98 Performance Benchmark

**Feature**: `009-win98-performance-benchmark`
**Status**: In progress

## User Story

As a RoboWindows tester, I can run one repeatable Windows 98 benchmark and see
which part of the VM is fast, slow, unstable, or thermally limited before I
change a working machine profile.

## Requirements

- **FR-001**: The guest benchmark MUST be built entirely from repository source,
  run on Windows 98 without installing software, and contain no proprietary
  operating-system or application material.
- **FR-002**: One versioned run MUST measure separate CPU, memory, and Windows
  GDI 2D workloads using the guest monotonic millisecond timer. GDI throughput
  MUST be measured on a bounded off-screen 320×200 surface; a small progress
  preview MAY update at no more than ten times per guest second. It MUST
  report elapsed time, work count, throughput, and an integrity value for every
  completed phase; it MUST NOT replace these with only one composite score.
- **FR-003**: A run MUST produce a bounded machine-readable text result naming
  the benchmark version and phase results. Partial, duplicate, malformed, or
  unknown-version results MUST fail validation.
- **FR-004**: The matched host capture MUST record build identity, declared and
  observed core, cycle policy/value, emulator-call rate, submitted/published/
  presented/coalesced frames and presented FPS, audio production/consumption/
  underruns/drops/saturation/errors, lifecycle validity, decoder residency,
  Android thermal status, and available temperature sensors.
- **FR-005**: Reports MUST show raw subsystem metrics and explicit quality-gate
  results. Any optional summary MUST identify the reference baseline and MUST
  not allow a high CPU or GDI result to hide bad guest-time accuracy, audio,
  stability, input, disk health, or thermal behavior.
- **FR-006**: Profile comparisons MUST use three completed runs per profile,
  equivalent cloned starting state, the same benchmark version and presentation
  conditions, and the median of each throughput metric. A profile is better only
  if Feature 008 SC-005 and all correctness/quality gates pass.
- **FR-007**: Benchmark media and results MUST remain separate from the stable
  machine. Device execution targets only a cleanly stopped experimental copy;
  abnormal termination follows Feature 008 quarantine and recovery rules.
- **FR-008**: The collector MUST reject background, paused, surface-lost,
  incomplete, crashed, or wrong-duration captures and redact device identifiers,
  app-private paths, guest content, framebuffer data, and PCM.
- **FR-009**: A short benchmark capture MUST NOT be presented as the required
  30-minute thermal soak, ten-cycle stability gate, AoE2 scenario, physical-input
  check, or human audio-quality judgment.
- **FR-010**: The benchmark MUST present a calm Windows 98 utility window with
  its current phase and a final result. It MUST NOT alternate full-window colors,
  rapidly flash, play a benchmark tone, obscure the desktop with an oversized
  surface, or make its Close action ineffective during a phase.

## Acceptance Scenarios

1. Given Windows 98 and the benchmark media, when the tester runs the program,
   then a compact utility identifies the CPU, memory, and off-screen GDI phases,
   shows at most a small rate-limited graphics preview, finishes without flashing
   or playing a tone, and writes a versioned result.
2. Given a foreground capture, when the result and host telemetry are combined,
   then the report shows configured cycles/core, guest throughput, presented FPS,
   audio counters, decoder evidence and temperatures as separate metrics.
3. Given a partial guest result or invalid lifecycle interval, when reporting is
   attempted, then the report fails instead of scoring or comparing that run.
4. Given three valid fixed-20k DynRec runs and three valid Normal runs, when they
   are compared, then medians and quality gates are shown per subsystem and the
   stable machine remains untouched.
5. Given a benchmark phase is running, when the tester closes its window, then
   the program exits promptly without requiring RoboWindows to stop the guest.

## Out of Scope

- Bundling Windows, AoE2, drivers, media, screenshots, or reference PCM.
- Treating one synthetic score as proof of game performance or audio quality.
- Promoting DynRec or changing the stable machine automatically.
