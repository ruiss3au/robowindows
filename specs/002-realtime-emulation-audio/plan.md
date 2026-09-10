# Implementation Plan: Real-Time Emulation and Clean Audio

The 2026-09-10 opt-in promotion (Feature 008 T047) extends the existing balanced
policy to ordinary-machine DynRec through the explicit launch selector. No
scheduler/audio algorithm changes; ordinary Normal and experimental timing stay
unchanged. Host policy tests cover every combination. Rollback uses Normal,
without silently changing existing selections or reopening paused investigation.

**Feature**: `002-realtime-emulation-audio` | **Date**: 2026-08-10  
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Summary

Make DOS and Windows guests maintain wall-clock time and clean playback at the actual host
output rate on the SM-T500. Move scaling and surface posting off the emulator thread, replace the
mutex-based audio path with a bounded SPSC queue and lifecycle state machine, calibrate a
conservative CPU/cycle profile, and keep the crashing ARM64 dynrec path experimental until
it independently passes correctness and disk-safety gates.

## Technical Context

**Language/Version**: C++17, Java 17, Gradle Kotlin DSL, POSIX shell  
**Primary Dependencies**: pinned DOSBox Pure/libretro core, Android NDK r28c, AAudio,
ANativeWindow, Android lifecycle APIs  
**Storage**: existing app-private machine images/configuration; small app-private runtime
health records and debug evidence  
**Testing**: Java unit tests, native host tests where practical, ADB-driven SM-T500
integration and soak scripts, checksummed cloned guest disks  
**Target Platform**: Samsung SM-T500, Android 16/API 36, `arm64-v8a`; minimum API 26  
**Project Type**: standalone Android mobile application with native emulator host  
**Performance Goals**: audio production matching the actual opened output rate within ±2%
(48,000 frames/s on the SM-T500); guest time within ±5%; at least 15 presented frames/s;
no steady-state underruns; 30-minute thermal stability  
**Constraints**: no upstream emulator UI, no root, no proprietary media in source or
logs, no tuning against the sole writable disk, no claim that broken dynrec is supported  
**Scale/Scope**: one primary tablet, DOS and Windows-family profiles, one emulator
session, stereo PCM output, changing guest resolutions

## Constitution Check

| Gate | Pre-design | Post-design evidence |
|---|---|---|
| Specification is source of truth | Pass | Design maps to FR-001–FR-020 and SC-001–SC-009 |
| Reproducible work | Pass | Pinned core/toolchain retained; evidence identifies build and profile |
| Legal media boundary | Pass | Tests consume user-owned media outside Git; logs prohibit guest content and keys |
| SM-T500 primary, portable boundary | Pass | Acceptance runs on the tablet; implementation uses standard API 26+ primitives |
| Safe guest state | Pass | Crash experiments use clones and pre/post checksums; recovery never replaces media |
| Correctness before optimization | Pass | Conservative profile is delivered first; dynrec is a separate experimental track |
| Incremental delivery | Pass | Instrument → decouple video → stabilize audio → calibrate → investigate dynrec |
| Upstream-friendly changes | Pass | Host fixes remain in the adapter; any core patch is isolated and documented |
| Product-owned UI | Pass | No upstream menu, OSD, configuration, or recovery screen is exposed |

No constitutional exceptions are required.

## Architecture and Delivery

### Phase 0: Establish a repeatable baseline

1. Add a bounded telemetry snapshot matching the runtime contract; treat its audio rate as
   supporting evidence rather than an independent guest clock.
2. Add an ADB test driver that verifies foreground/unlocked state and records device,
   build, profile, lifecycle, audio, video, and a separately observed guest timer workload.
3. Run only against a checksummed clone of the installed Windows image.

**Exit**: one reproducible trace distinguishes emulation deficit, render cost, pause,
surface loss, audio starvation, and crash.

### Phase 1: Remove presentation from the emulator clock

1. Benchmark callback-only frame copying at real guest resolutions to ensure the handoff
   itself is affordable.
2. Make `video_refresh` copy or exchange only the newest valid guest frame into bounded
   storage and return promptly.
3. Run scaling and `ANativeWindow_lock`/post on a dedicated presenter thread; if copying is
   still material, use a bounded multi-slot ownership exchange rather than an extra copy.
4. Coalesce superseded frames, react to resolution/surface changes, and cap presentation
   independently while preserving the newest frame.
5. Stop and join the presenter safely during session/surface teardown.

**Exit**: surface blocking does not delay emulation; foreground presentation is at least
15 fps; guest/audio production is measurably higher than the current every-fourth-frame
baseline.

### Phase 2: Make audio bounded and lifecycle-correct

1. Replace callback mutex acquisition with a fixed-capacity SPSC PCM ring.
2. Model audio as stopped, prebuffering, playing, suspended, or recovering.
3. Start after the configured bounded prebuffer, suspend consumption during guest pause or
   lifecycle interruption, discard stale samples on resume, and prebuffer again.
4. Track produced, consumed, dropped, silence-filled, queue-depth, and stream-error values.
5. Recover from AAudio disconnect/error without restarting or modifying the guest disk.

**Exit**: no callback locks or allocation; ten pause/resume cycles pass; when production
is real-time, ten reference sounds have zero post-start underruns.

### Phase 2.5: Stabilize experimental real-time scheduling

The first matched fixed-20k benchmark pair showed equivalent CPU throughput and
real-time video, but 121 DynRec and 19 Normal underruns. Before repeating the
three-run comparison, add one allowlisted `balanced-100ms` timing policy to
experimental machines under either CPU core. Stable machines remain on the
existing frontend cadence and 200 ms prebuffer.

1. Carry the internal timing-policy ID through the ordinary JNI start and the
   isolated DynRec handoff; reject unknown IDs at both boundaries and never infer
   policy from a path or config file.
2. Use fixed deadlines with at most 250 ms retained debt and bursts of no more
   than 20 consecutive catch-up calls. Cooperatively yield between bursts while
   preserving debt. Clamp only larger debt, record a resynchronization, and make
   the capture fail quality validation.
3. Start playback at 100 ms. Pace guest calls at 99%, 100%, or 101% of their
   nominal interval using a 75–125 ms queue band and hysteresis to the 100 ms
   target. Do not transform PCM or fabricate samples.
4. Reset deadlines, correction state, gap measurement, queue contents, and
   prebuffering on pause/focus loss, recovery, guest restart, and session restart.
5. Extend bounded schema-3 telemetry and its strict capture summarizer before a
   new device comparison.

**Rollback**: select the legacy timing-policy ID for all launches and remove the
experimental call-site selection; no guest configuration or media migration is
required. A schema-3 capture remains diagnostic evidence but cannot promote a
profile that resynchronized or underrran.

**Exit**: deterministic host tests cover on-time cadence, short and long stalls,
bounded catch-up, debt clamp, queue hysteresis, lifecycle reset, telemetry reset,
and unchanged legacy scheduling. Repository, pinned-source, host, Android, and
disposable device gates pass before a new matched pair.

### Phase 3: Calibrate a conservative real-time profile

The 2026-09-10 Normal Windows GPU and Software captures both reproduced
starvation. Their longest sustained bursts spend nearly the entire interval
inside emulator calls, not host sleeps or synchronous AV callbacks; the current
aggregates cannot isolate worker CPU work from scheduling delays. Do not retune
cycles, buffers or the instruction engine from these measurements alone.

Fix the independently proven refresh-change reset defect first (FR-028): add a
cadence-update operation separate from lifecycle `configure`/`reset`, preserving
experimental deadlines/correction/burst state while retaining legacy behavior.
Do not reset call diagnostics or producer-gap history on live refresh changes.
Host regressions must exercise positive debt, repeated small refresh changes,
clamping after a change, queue hysteresis, burst limits and lifecycle reset.
Run host/pinned/build checks and disposable tests without opening Windows disks.
Rollback reverts this call-site/helper change only. This is a targeted debt and
measurement correction, not proof that every observed underrun is cured.

The user clarified that the failing captures included opening windows and then
Age of Empires II, not idle desktop only. Add an explicit `stress` variant to
the disposable timer/tone harness: repeating eight-guest-second idle, integer,
RAM-copy and VGA-write phases at the existing fixed 20k. Use real-mode BIOS tick
timing, 32-bit arithmetic operands and bounded memory regions; do not claim
protected-mode, Windows-driver or game equivalence. Preserve the default light
fixture byte-for-byte. A distinct result magic and four-bit executed-phase mask
must prevent accepting the wrong fixture or a partial run. Validate the actual
production image in pinned QEMU and the parser on the host before a stopped
installation and one disposable Normal/GPU then DynRec/GPU comparison. No real
guest launch or runtime retuning is authorized by this diagnostic slice.
Rollback removes the extra fixture/selector only; settings and engine remain
unchanged. A non-reproduction narrows only this synthetic workload, not Windows.

Before the next benchmark, isolate informational libretro callbacks from AV
timing dispatch in a host-testable helper (FR-026). A recorded startup advertised
an implausibly large FPS with zero sample rate; the existing grouped switch
incorrectly reads option and memory-map payloads as AV timing. This is a proven
host bug, not yet the cause of the post-slice underrun burst.

Add a separate bounded `RoboWindowsTiming` schema-1 line alongside schema 3 for
experimental sessions (FR-027). Measure process CPU because the pinned emulator
has its own worker thread; frontend-thread CPU alone would misclassify guest
work as a wait. Keep process CPU and elapsed time separate, since parallel
threads can consume more CPU time than elapsed time. Extend capture/report
tooling to retain this optional diagnostic record, rejecting partial or malformed
extensions while continuing to read historical captures without it.

Rollback of the diagnostic slice removes its collection/reporting only. The
type-safe callback correction is independently testable and should be retained.
Require host tests, pinned-source verification, Android build and disposable
Normal/DynRec gate before another guest run. Feature 011 subsequently authorizes
GPU presentation as an independent experimental-copy-only feature; this does not
complete the outstanding real-time or DynRec quality gates.

1. Benchmark `normal`/`auto` with explicit fixed cycle candidates on boot, idle, window
   movement, and sound workloads.
2. Select the highest cycle target that maintains independent guest time and production at
   the actual opened host rate during the thermal soak; do not resample a slow guest to
   disguise timing failure.
3. Persist the validated target as a named recoverable profile and retain a last-known-safe
   profile after abnormal termination.
4. Repeat the timer-and-tone criteria with a redistributable DOS workload before making the
   family-wide support claim.

**Exit**: SC-001–SC-006, SC-008, and SC-009 pass on the SM-T500, or the evidence identifies a
remaining core-execution deficit before acceleration work begins.

### Phase 4: Investigate ARM64 dynrec independently

1. Reproduce the crash on a cloned disk with symbols and capture the native tombstone.
2. Verify ARMV8LE configuration, executable cache allocation/protection, instruction-cache
   invalidation, block linking, and Android W^X transitions against the pinned core.
3. Put any minimal core patch behind an experimental profile and document upstream origin
   and divergence.
4. Promote it only after conservative-profile parity for CPU correctness, guest time,
   lifecycle, thermal behavior, and disk checksums.

**Exit**: dynrec is either validated with evidence or remains hidden and automatically
recoverable; it never blocks the conservative release.

## Project Structure

```text
android/src/main/cpp/
├── core_host.cpp                 # callbacks, session/lifecycle coordination
├── frame_mailbox.{h,cpp}         # latest-frame bounded handoff
├── frame_presenter.{h,cpp}       # presentation thread and scaling/posting
├── audio_ring.{h,cpp}            # fixed SPSC PCM queue
└── runtime_telemetry.{h,cpp}     # bounded counters and snapshots
android/src/main/java/org/robowindows/app/
├── LaunchConfig.java             # validated execution/cycle profile output
└── ...                           # existing RoboWindows UI and lifecycle bridge
android/src/test/                 # configuration/profile unit tests
tests/native/                     # queue, mailbox, and state-machine tests
scripts/test-sm-t500-realtime.sh  # ADB acceptance and evidence capture
config/                           # versioned performance-profile definitions
specs/002-realtime-emulation-audio/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/runtime-telemetry.md
```

**Structure Decision**: Extend the existing single Android application and native adapter.
Concurrency helpers remain project-owned and independent of DOSBox Pure so they can be
unit-tested and upstream core changes remain a small, reviewable patch set.

## Rollback and Safety

### Bounded internal measurement (FR-030 / T072)

Use standalone patch 0009 against the pinned core. A source-owned C++11 helper
keeps worker-local slice counters; only completed slices are merged under a
short snapshot mutex. Frontend reset advances an atomic generation, discarding
in-flight pre-reset contributions without touching worker-local state. Start/end
slices around worker execution and exclude frame/pause semaphore waits. Count
translation and direct Normal fallback reasons without clocks, locks or atomics
on each event; do not time individual instructions or linked blocks. Frontend
frame-wait and mixer scopes retain their own generation and wall-time totals.
Export through the narrow native boundary and a separate bounded schema-1 record;
keep schema-4 runtime and existing timing records compatible. Disabled legacy
sessions perform no diagnostic clock reads or counter updates.

Test fake-clock accounting, failed clocks, nested work, resets during active
slices/scopes, disabled behavior and concurrent snapshots. Characterize empty
scope/event overhead with a bounded calibration before loading disposable media,
then require full x86 parity and the unchanged stress pair with no quality errors.
The source-owned CPU fixture uses balanced timing so the full fault/SMC gate
actually exercises enabled instrumentation; the disposable core/lifecycle probe
continues to cover disabled legacy behavior. No machine profile changes.
Record calibration limits and device variability rather than claiming a noisy
cross-build comparison isolates total overhead. Preserve the prior APK; rollback
removes only patch 0009 and its host/report integration, invalidates capability
and never changes guest disks. No Windows start is authorized by this slice.

### Offline follow-up (T073)

After the authorized instrumented desktop capture, review pinned worker paths
and failing/non-failing interval groups without modifying runtime behavior.
The [worker-path review](worker-path-review.md) records the shared BR_Opcode
meaning and selects a separately specified source-owned cache-reuse/retranslation
control before more Windows trials. Sampling design is a fallback, not permission
to add per-instruction clocks or alter cache publication. Existing APK/settings
are retained; this step requires no installation or device session.

### Translation fixture (FR-031 / T074–T075)

Use five build-time cases in a new source-owned protected-mode stage, reusing the
existing eight-sector boot loader. Generate 4,096 identical 64-byte routine slots
in bounded RAM, each with a 512-iteration deterministic integer loop. Warm uses
one primed slot; cold calls each slot once; reuse primes the entire footprint
then repeats it. Data-write and rewrite cases each prime one slot and perform
the same alternating write/call counts, targeting separate data or an immediate
inside the called routine. Check each return and the final 4,096-call checksum.
The 256-KiB footprint does not prove host cache residency; counters must confirm
reuse/invalidation on the eventual device runs.

Use a dedicated approximately 1-kHz PIT IRQ counter in protected mode, retaining
setup, warm-up, measured and total ticks separately. Restore BIOS interrupt/timer
state, write a checksummed 64-byte result to sector 18, then request APM shutdown.
Host/QEMU tests use a source-fixture-only debug-exit device after the result write.
Protocol or arithmetic failures remain failures even if teardown succeeds.

Package independent image identities and permit only literal case names through
the existing non-exported presentation fixture activity. Poll guest shutdown and
fail after 60 host seconds; stop/unload only that generated fixture before reading
its result and cleaning its private directory. Use balanced-100-ms/fixed-20k with
unchanged presentation selection. Log case/checksum/ticks and whole-session host
duration, not guest data or measured-loop host time. A correctness PASS does not
assert zero underruns, decoder residency, cache reuse or timer-speed acceptance;
report aggregate timing separately. No machine setting or core patch changes.

Builders stage outputs and publish without overwriting an existing differing
file. Byte-identical generated outputs are reusable; existing mutated run images
must be rejected. Rollback removes new packaged workload selection only. Require
host/QEMU protocol/negative tests, unchanged old hashes, pins and Android build;
installation and device characterization remain a separate coordinated step.

### Terminal diagnostic coverage (FR-032 / T077)

Add a host-testable once-only shutdown gate to the project-owned frontend, armed
only for balanced experimental sessions. Keep periodic reports byte-schema
compatible. After presenter/audio stop and `retro_unload_game()` completion,
take the residual snapshots before disabling worker diagnostics. Pinned Pure's
TCM_ON_SHUTDOWN publishes its final worker slice before signaling shutdown;
the frontend must never access an in-flight slice directly. No upstream change.

Emit a schema-1 terminal header and separately tagged schema-4 telemetry / schema-1
worker / schema-1 frontend records. Terminal elapsed includes unload/output
teardown, queue depth can reflect clearing, and state/audio are stopping/stopped.
Do not mix it into existing settled-window consumers. A strict normalizer checks
group order, header/interval agreement and periodic-group count before opted-in
cache reports combine residual and regular totals. Preserve historical captures
without a terminal group as incomplete coverage, not fabricated zero observations.
No load-failure or crash completeness claim; no interval synthesis on lifecycle
reset. Record existing generation-reset limitations explicitly.

Test disabled/early/duplicate shutdown, fresh-session reset, sub-ms/zero timing,
bad clocks, delayed final worker completion, and drain-before-disable. Parser
tests cover terminal-only and mixed histories, missing/reordered/duplicated
records, zero tails, clock errors, and unchanged ordinary consumers. Require host,
pins and Android build; retain all five cache image hashes. Rollback removes only
terminal reporting and consumer opt-in. No install/device run in this slice.

### Targeted translation/cache attribution (FR-033 / T079–T082)

The [selected design](translation-attribution.md) adds sampled outer translation
wall/thread-CPU timing and a bounded publication subset, plus clock-free lookup
and clear-operation reason counters. Select outer attempts 1/65/129/193 per
worker slice, cap publication scopes at four per selected translation, and bound
new duration clocks to 80 reads per slice. Explicitly report deterministic
selection bias; do not extrapolate exact total translation CPU or miss causes.
Worker-owned scope guards handle nesting, exceptions, pause boundaries and stale
generations; atomically publish the old worker and new cache accumulators together.

Use one new isolated diagnostic patch and the existing experimental internal
policy boundary, no UI or profile changes. Add a separate schema-1 cache record
and schema-2 terminal group without changing the existing telemetry/worker/timing
schemas. Version-aware consumers retain historical absence and settled-window
behavior. No guest markers, address maps, allocator/link changes or ARM barrier
changes. Validate host accounting, pinned hook sites, bounded overhead, parser
compatibility and Android build before a separately coordinated stopped install
and disposable Normal-first tests. Retain the current APK; rollback removes only
the new instrumentation and consumers, never machine settings or media.

T079 is documentation/source review only; implementation and device validation
are distinct T080–T082 milestones, not claimed completed work.

- Preserve the current conservative profile as the last-known-safe fallback.
- Test CPU/core changes only with a cloned image. Require identical checksums only when the
  test terminates before guest execution; otherwise verify mountability/filesystem health
  and expected guest persistence while retaining pre/post checksums as evidence.
- Each phase is separately reversible: telemetry, presenter, audio queue, profile tuning,
  then optional dynrec patch.
- A startup marker is cleared only after the session reaches healthy running state; an
  uncleared marker prevents automatic reuse of an experimental profile.
- Never delete, overwrite, or restore the user's guest image as part of crash recovery.
