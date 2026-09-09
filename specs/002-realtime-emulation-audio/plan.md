# Implementation Plan: Real-Time Emulation and Clean Audio

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

- Preserve the current conservative profile as the last-known-safe fallback.
- Test CPU/core changes only with a cloned image. Require identical checksums only when the
  test terminates before guest execution; otherwise verify mountability/filesystem health
  and expected guest persistence while retaining pre/post checksums as evidence.
- Each phase is separately reversible: telemetry, presenter, audio queue, profile tuning,
  then optional dynrec patch.
- A startup marker is cleared only after the session reaches healthy running state; an
  uncleared marker prevents automatic reuse of an experimental profile.
- Never delete, overwrite, or restore the user's guest image as part of crash recovery.
