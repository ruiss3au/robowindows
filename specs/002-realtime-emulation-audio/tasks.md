# Tasks: Real-Time Emulation and Clean Audio

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md),
[data-model.md](data-model.md), and [runtime telemetry contract](contracts/runtime-telemetry.md)  
**Strategy**: Establish trustworthy evidence first, then deliver independently testable
audio and real-time execution improvements. ARM64 dynrec remains optional and quarantined.

## Phase 1: Setup and protected baseline

**Purpose**: Make performance work reproducible without risking the installed guest.

- [x] T001 Record the current APK checksum, pinned core revision, SM-T500 fingerprint,
  Android build, audio output rate/burst, and conservative profile in
  `docs/realtime-baseline.md`
- [x] T002 Verify the installed Windows backup and create/checksum a writable test clone
  using a non-destructive mode added to `scripts/test-sm-t500-realtime.sh`
- [ ] T003 [P] Define the versioned Windows and redistributable DOS timer-and-tone workloads
  in `tests/realtime/workloads.md`, including a small Windows 98 benchmark program for future
  fixed-cycle trials.
- [ ] T004 [P] Add repository redaction assertions for disk images, ISO media, registration
  data, PCM dumps, and private device paths to `scripts/verify-assets.sh`

**Checkpoint**: Every device experiment identifies exact inputs and uses recoverable media.

---

## Phase 2: Foundational measurement contract

**Purpose**: Produce evidence that cannot confuse pause, lifecycle, rendering, and audio
effects. This phase blocks performance claims and tuning.

### Tests first

- [x] T005 [P] Add counter/snapshot and reset-interval tests for the telemetry contract in
  `tests/native/runtime_telemetry_test.cpp`
- [ ] T006 [P] Add parser/redaction/environment-validity tests for SM-T500 reports in
  `tests/realtime/test_report_parser.sh`
- [ ] T007 Add a failing ADB smoke test for foreground, unlocked, full-screen, audio-focus,
  pause, and surface-state attribution in `scripts/test-sm-t500-realtime.sh`

### Implementation

- [x] T008 Implement bounded atomic counters and one-second snapshots in
  `android/src/main/cpp/runtime_telemetry.h` and
  `android/src/main/cpp/runtime_telemetry.cpp`
- [x] T009 Integrate telemetry with session run, video, audio, pause, surface, and teardown
  paths in `android/src/main/cpp/core_host.cpp`
- [ ] T010 Expose sanitized lifecycle and selected-profile state to native telemetry through
  `android/src/main/java/org/robowindows/app/NativeHost.java` and
  `android/src/main/java/org/robowindows/app/MainActivity.java`
- [ ] T011 Complete the ADB report collector and invalid-environment rejection in
  `scripts/test-sm-t500-realtime.sh`
- [ ] T012 Build the redistributable timer-and-tone utility from versioned source via
  `scripts/build-realtime-guest-tools.sh`, with any new Debian prerequisite emitted only by
  a user-run update to `scripts/bootstrap-debian.sh`

**Checkpoint**: A controlled run attributes deficits using host metrics plus an independent
guest result and excludes invalid lifecycle intervals.

---

## Phase 3: User Story 2 — Sustainable guest speed (Priority: P1)

**Goal**: Video presentation no longer governs emulator/audio cadence while the latest guest
frame remains responsive.

**Independent Test**: Run the five-minute Windows workload with sound measurement disabled;
prove at least 15 presented fps, independent guest time within ±5%, and no emulator-thread
surface posting.

### Tests first

- [x] T013 [P] [US2] Add bounded ownership, newest-frame selection, resolution-change, and
  shutdown-race tests in `tests/native/frame_mailbox_test.cpp`
- [ ] T014 [P] [US2] Add presenter cadence, frame coalescing, and surface-loss state tests in
  `tests/native/frame_presenter_test.cpp`
- [x] T015 [US2] Add callback-copy and ownership-exchange microbenchmarks at observed guest
  resolutions to `tests/native/frame_handoff_benchmark.cpp`

### Implementation

- [x] T016 [US2] Implement a bounded latest-frame mailbox in
  `android/src/main/cpp/frame_mailbox.h` and `android/src/main/cpp/frame_mailbox.cpp`, choosing
  copy or slot ownership from T015 evidence
- [x] T017 [US2] Implement scaling, letterboxing, channel conversion, cadence limiting,
  coalescing, and surface posting in `android/src/main/cpp/frame_presenter.h` and
  `android/src/main/cpp/frame_presenter.cpp`
- [x] T018 [US2] Reduce `video_refresh` to bounded frame publication and remove the temporary
  modulo-four surface-rendering path from `android/src/main/cpp/core_host.cpp`
- [x] T019 [US2] Integrate presenter startup, surface replacement, stop, and join without
  holding the emulator callback in `android/src/main/cpp/core_host.cpp`
- [x] T020 [US2] Register new native sources and host-test targets in
  `android/src/main/cpp/Android.mk` and `scripts/test-host.sh`
- [ ] T021 [US2] Run the independent SM-T500 speed/display test and record before/after
  callback cost, guest-time rate, submitted/presented/coalesced frames, and thermal state in
  `docs/realtime-baseline.md`

**Checkpoint**: US2 independently meets SC-004 and SC-005; rendering is no longer the
emulator clock.

---

## Phase 4: User Story 1 — Clean guest audio (Priority: P1) 🎯 MVP

**Goal**: Audio playback is continuous at the correct tempo with real-time-safe callbacks and
correct pause/recovery behavior.

**Independent Test**: On the conservative Windows profile, play the same reference sound ten
times and perform ten pause/resume cycles; after prebuffering, observe no underruns during
sounds, no stale replay, correct duration, and no unexplained boundary saturation.

### Tests first

- [x] T022 [P] [US1] Add SPSC wraparound, full/empty, overflow, generation-reset, and producer/
  consumer stress tests in `tests/native/audio_ring_test.cpp`
- [x] T023 [P] [US1] Add stopped/prebuffering/playing/suspended/recovering transition tests in
  `tests/native/audio_state_test.cpp`
- [ ] T024 [US1] Add scripted foreground sound, silence, pause/resume, and AAudio-reopen
  acceptance cases to `scripts/test-sm-t500-realtime.sh`

### Implementation

- [x] T025 [US1] Implement the fixed-capacity interleaved-frame SPSC queue with documented
  overflow policy in `android/src/main/cpp/audio_ring.h` and
  `android/src/main/cpp/audio_ring.cpp`
- [x] T026 [US1] Implement the audio lifecycle controller and generation-based stale-sample
  invalidation in `android/src/main/cpp/audio_output.h` and
  `android/src/main/cpp/audio_output.cpp`
- [x] T027 [US1] Replace mutex-based `audio_batch`/AAudio callback buffering, allocation, and
  callback logging with the new queue and aggregate saturation/underrun counters in
  `android/src/main/cpp/core_host.cpp`
- [x] T028 [US1] Wire explicit pause, background/audio-focus interruption, resume prebuffer,
  stream error, and session teardown transitions through `MainActivity.java`,
  `NativeHost.java`, and `core_host.cpp`
- [x] T029 [US1] Register new audio sources/tests in `android/src/main/cpp/Android.mk` and
  `scripts/test-host.sh`
- [ ] T030 [US1] Run ten Windows reference sounds, five minutes of guest silence, and ten
  pause/resume cycles on the SM-T500; record SC-001–SC-003 and SC-006 evidence in
  `docs/realtime-validation.md`

**Checkpoint**: US1 independently provides clean audio whenever the conservative execution
profile can sustain the actual opened output rate.

---

## Phase 5: Conservative profile convergence (User Stories 1 and 2)

**Purpose**: Find a usable CPU budget the SM-T500 can sustain without disguising slow guest
time.

- [ ] T031 [P] [US1] [US2] Add cycle-policy/value validation and serialization tests in
  `android/src/test/java/org/robowindows/app/LaunchConfigTest.java`
- [ ] T032 [US1] [US2] Add explicit `auto`, `normal fixed`, and bounded candidate cycle
  settings while preserving SB16 I/O 220/388, IRQ 7, DMA 1/5 in
  `android/src/main/java/org/robowindows/app/LaunchConfig.java`
- [ ] T033 [US1] [US2] Define named candidate/validated/rejected profile state without
  exposing upstream controls in `MachineProfile.java` and `MachineStore.java`
- [ ] T034 [US1] [US2] Automate the Windows boot/idle/window/sound cycle-candidate matrix in
  `scripts/test-sm-t500-realtime.sh`
- [ ] T035 [US1] [US2] Select the highest conservative candidate meeting independent guest
  time, audio-rate, interaction, and disk-health criteria and record it in
  `docs/realtime-validation.md`
- [ ] T036 [US1] [US2] Run the 30-minute soak and require every settled five-minute window to
  meet SC-008; reject rather than silently resample or mute an unsustainable profile
- [ ] T037 [US1] [US2] Run the redistributable DOS timer-and-tone workload and record SC-009
  evidence before publishing DOS-and-Windows-family support language

**Checkpoint**: The stable profile passes SC-001–SC-006, SC-008, and SC-009 on the SM-T500.

---

## Phase 6: User Story 4 — Actionable evidence (Priority: P2)

**Goal**: Developers can reproduce and correctly classify performance failures without
leaking guest content or overwhelming public logs.

**Independent Test**: Feed the collector foreground, paused, background, surface-loss,
starvation, saturation, and native-failure sessions and verify the report classification and
redaction decisions.

- [ ] T038 [P] [US4] Add golden sanitized telemetry records for every runtime/audio state and
  malformed schema cases under `tests/realtime/fixtures/`
- [ ] T039 [US4] Complete classification and bounded summary generation in
  `scripts/test-sm-t500-realtime.sh`
- [ ] T040 [US4] Disable interval telemetry in release builds while retaining minimal
  app-private recovery state in `runtime_telemetry.cpp` and `android/build.gradle.kts`
- [ ] T041 [US4] Verify reports contain no PCM, framebuffer content, guest text, media paths,
  registration data, or unbounded log streams using `scripts/verify-assets.sh`
- [ ] T042 [US4] Document metric definitions, invalid intervals, and the limitation that
  software counters cannot prove speaker-path acoustic quality in
  `docs/realtime-diagnostics.md`

**Checkpoint**: US4 independently satisfies FR-013–FR-015, FR-017, and FR-020.

---

## Phase 7: User Story 3 — Safe acceleration recovery (Priority: P2, optional optimization)

Delivery of T043–T050 is superseded by
[Feature 008](../008-arm64-dynamic-recompilation/tasks.md). The tasks below remain
historical scope; use Feature 008's execution contract and task list for new work.

**Goal**: An experimental execution mode cannot trap the user in a crash loop or endanger
the guest image.

**Independent Test**: Trigger the known dynamic-core failure before guest execution on a
checksummed clone; next launch uses the safe profile without file editing and the clone
checksum is unchanged.

### Tests first

- [ ] T043 [P] [US3] Add pending/healthy/abnormal/start-safe state-transition tests in
  `android/src/test/java/org/robowindows/app/RuntimeHealthRecordTest.java`
- [ ] T044 [US3] Add an opt-in cloned-media crash/recovery case with pre-execution checksum
  proof to `scripts/test-sm-t500-realtime.sh`

### Implementation and investigation

- [ ] T045 [US3] Implement atomic app-private runtime health persistence and last-known-safe
  selection in `RuntimeHealthRecord.java` and `MachineStore.java`
- [ ] T046 [US3] Integrate pending/healthy/orderly/abnormal markers and RoboWindows-owned
  recovery messaging in `MainActivity.java` without exposing dynamic mode as validated
- [ ] T047 [US3] Produce a symbolized debug build and capture/symbolicate the ARM64 dynrec
  tombstone in `docs/dynrec-sm-t500.md`
- [ ] T048 [US3] Audit ARMV8LE cache allocation/protection, W^X transitions, instruction-cache
  invalidation, generated branches, and block linking in the pinned core; document the first
  proven fault in `docs/dynrec-sm-t500.md`
- [ ] T049 [US3] If the fault is proven and a minimal fix exists, isolate it as a reviewable
  pinned-core patch under `patches/` and retain the execution profile as experimental
- [ ] T050 [US3] Run CPU correctness, Windows/DOS timer-and-tone, pause/resume, disk-health,
  and thermal parity; promote only if all conservative gates pass, otherwise record rejection

**Checkpoint**: SC-007 passes. Dynrec is either evidence-backed or remains hidden and safely
recoverable; it is not required for completion of the conservative MVP.

---

## Phase 8: Final convergence

### Experimental balanced-100ms corrective slice

- [x] T056 [US1] Add deterministic host tests for legacy cadence, on-time
  balanced pacing, short/long stalls, bounded catch-up, 250 ms debt clamping,
  queue hysteresis, and lifecycle reset (FR-021–024).
- [x] T057 [US4] Extend telemetry and strict benchmark fixtures to schema 3 with
  call duration, producer gap, scheduler, and current/min/max queue evidence;
  make any deadline resynchronization fail quality validation (FR-025).
- [x] T058 [US1] Pass the allowlisted timing-policy ID through ordinary JNI and
  the isolated DynRec Binder/JNI handoff; select balanced timing only for
  experimental machines under Normal or DynRec (FR-021).
- [x] T059 [US1] Implement the balanced 100 ms prebuffer, one-percent queue-band
  hysteresis, fixed deadlines, bounded catch-up, and lifecycle resets without
  PCM transformation or DynRec engine changes (FR-022–024).
- [x] T060 Run host tests, repository hygiene, pinned-source verification, and
  the ARM64 debug build.
- [x] T061 With both real machines stopped, install and run the disposable
  Normal/DynRec x86 gate; do not open either machine disk.
- [ ] T062 Run one matched Normal-20k/DynRec-20k pair on `incoming - copy` and
  require valid guest results, correct decoder residency, at least 15 FPS,
  guest timing within 5%, zero settled underruns/missing frames, zero deadline
  resynchronizations/lifecycle errors, and clean shutdown.
- [ ] T063 If T062 passes, resume the three-run medians and existing long gates.
  If long calls are DynRec-only, stop trials and investigate its measured hot
  path; if both cores stall similarly, continue in the shared host path.

T062 remains open. The corrected scheduler passed the complete Normal workload,
but the matched DynRec start failed before the benchmark when Windows Explorer
reported an illegal operation. Telemetry isolated repeated over-250 ms deadline
debt to DynRec page-fault activity. The trial stopped, the copy completed its
required Normal recovery shutdown, and further repetitions are blocked on the
DynRec hot-path investigation in T063.

The instrumented follow-up reproduced the DynRec-only startup failure and
measured PageFaultCore directly: roughly 9.8 million completed one-cycle calls,
a 491.819 ms maximum call and seven nested faults still active at the last
complete status sample. The disposable expanded x86 gate still passed, and its
DynRec fault/retry control completed 86/86 PageFaultCore calls with a 31 us
maximum. T062 and three-run collection remain blocked; the next work is a
source-owned reproduction of the Windows-only call amplification, not another
benchmark repetition or shared-scheduler change.

- [ ] T051 Re-run host tests and `git diff --check`, then build/install the debug APK using
  `scripts/build-android.sh`
- [ ] T052 Run the complete [quickstart](quickstart.md) on the SM-T500 and archive only
  sanitized evidence
- [ ] T053 Re-check FR-001–FR-020 and SC-001–SC-009 against evidence in
  `docs/realtime-validation.md`
- [ ] T054 Re-check constitutional gates, public-UI ownership, source pins, GPL notices, and
  prohibited-asset exclusions before any GitHub publication
- [ ] T055 Restore or retain the validated safe profile, verify an orderly guest shutdown,
  and document recovery/rollback in `docs/realtime-validation.md`

## Dependencies and safe execution order

- T001–T004 establish safety; T002 blocks every writable-device experiment.
- T005–T012 are foundational and block performance claims.
- US2 video decoupling precedes US1 device acceptance because it is a measured source of
  sustained audio starvation; its native unit work does not depend on audio work.
- T015 determines the handoff selected in T016; T016–T018 precede presenter integration T019.
- T022–T024 precede T025–T028; callback integration follows queue/state tests.
- Conservative convergence T031–T037 requires both US1 and US2 checkpoints.
- US4 fixture/document work can proceed after the telemetry contract, but final classification
  uses completed audio/video counters.
- US3 recovery-state work can proceed after the foundation. Dynrec diagnosis starts only on
  a verified clone and does not block the conservative MVP.
- T056–T059 precede another benchmark repetition. T060 precedes any installation,
  and T061 requires both user machines stopped. T062 alone decides whether T063
  may resume three-run collection.
- Final convergence requires all non-optional tasks and either a validated or explicitly
  rejected/hidden result for T047–T050.

## Implementation strategy

1. Establish protected evidence and the independent guest clock.
2. Remove video work from the emulation callback and validate guest speed.
3. Stabilize audio callbacks and lifecycle behavior.
4. Calibrate and soak a conservative Windows profile; repeat with DOS.
5. Harden diagnostic privacy and crash recovery.
6. Investigate dynrec only as an isolated optional optimization.

Stop after any checkpoint if its acceptance test fails; use the recorded subsystem evidence
to revise the next task rather than stacking more tuning on an unproven result.
