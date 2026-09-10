# Feature Specification: Real-Time Emulation and Clean Audio

**Feature**: `002-realtime-emulation-audio`

**Created**: 2026-08-10

**Status**: Draft

**Input**: Make DOS and Windows guests run at sustainable real-time speed with clean,
continuous audio on the Samsung SM-T500, without exposing upstream emulator UI or
compromising guest correctness and disk safety.

## User Scenarios & Testing

### User Story 1 - Hear clean guest audio (Priority: P1)

As a user running a supported DOS or Windows guest, I can hear music, speech, and
system sounds continuously at the expected pitch and tempo without clipping,
repetition, or gaps.

**Why this priority**: Current playback is unusable because the guest produces fewer
audio frames than the tablet consumes. Audio quality is the immediate blocking defect.

**Independent Test**: On the SM-T500, boot the validated Windows 98 profile, play the
same system sound ten times, and compare guest production, host consumption, underruns,
audible output, and guest elapsed time.

**Acceptance Scenarios**:

1. **Given** a supported guest with sound enabled, **When** it plays a reference sound,
   **Then** playback is continuous and retains the expected pitch, tempo, and duration.
2. **Given** a guest that is silent, **When** it remains idle for five minutes, **Then**
   the output remains silent without periodic clicks or noise bursts.
3. **Given** the guest is paused, **When** the user resumes it, **Then** playback resumes
   without draining stale samples, a loud discontinuity, or persistent underruns.
4. **Given** a validated DOS profile, **When** it plays the redistributable reference tone
   workload, **Then** it meets the same continuity, pitch, tempo, and duration requirements.

---

### User Story 2 - Run guests at sustainable speed (Priority: P1)

As a user, I can run a validated guest at a stable speed the tablet can sustain, with
video presentation consuming only the resources needed for a responsive display.

**Why this priority**: Measured guest audio production is approximately 14,000–18,000
frames per second with full rendering and 28,000–32,000 with reduced rendering, while
real-time playback requires 48,000. Buffering alone cannot correct a slow guest clock.

**Independent Test**: Run a five-minute workload containing boot, desktop idle, window
movement, and audio playback while recording guest elapsed time, presented frames,
produced audio frames, and underruns.

**Acceptance Scenarios**:

1. **Given** a validated guest profile, **When** the five-minute workload runs, **Then**
   an independent guest-visible timer workload stays within 5% of monotonic host time.
2. **Given** a 60–70 Hz guest display, **When** the guest is CPU-bound, **Then** video
   presentation may reduce independently without slowing guest time or audio cadence.
3. **Given** the target cannot sustain a selected performance profile, **When** the
   deficit persists, **Then** the app selects or recommends a safe sustainable profile
   instead of emitting broken audio.

---

### User Story 3 - Recover safely from unsupported acceleration (Priority: P2)

As a user, I can try an optimized execution mode without risking my guest disk or being
left with a machine that crashes every time it starts.

**Why this priority**: The current dynamic execution mode crashes immediately on the
SM-T500 ARM64 build. Experimental acceleration must fail safely.

**Independent Test**: Attempt an intentionally unsupported execution mode on a cloned
test machine, verify automatic recovery, then boot the conservative mode and compare the
clone checksum and filesystem state.

**Acceptance Scenarios**:

1. **Given** an execution mode that fails during startup, **When** the app detects the
   abnormal termination, **Then** it offers a known-safe profile before the next start.
2. **Given** startup fails before the guest performs writes, **When** recovery completes,
   **Then** the guest disk remains unchanged.
3. **Given** an optimized mode is advertised as validated, **When** the published target
   test is repeated, **Then** it passes the same correctness and disk-safety checks as the
   conservative mode.

---

### User Story 4 - Produce actionable performance evidence (Priority: P2)

As a developer or tester, I can distinguish guest CPU limits, video cost, audio starvation,
pause state, and host lifecycle effects using concise diagnostics that can be removed or
disabled in public builds.

**Why this priority**: Previous observations conflated driver resources, background
restriction, paused sessions, video cost, and execution-mode crashes.

**Independent Test**: Run one controlled session and produce a report that attributes any
failure to a measured subsystem without relying only on subjective listening.

**Acceptance Scenarios**:

1. **Given** a test session, **When** diagnostics are collected, **Then** they report guest
   time rate, produced and consumed audio frames, queue depth, underruns, presented video
   rate, pause state, execution mode, and abnormal termination.
2. **Given** the app moves to background or enters pause, **When** diagnostics are read,
   **Then** those intervals are identified and excluded from foreground performance claims.
3. **Given** a public release build, **When** ordinary users run it, **Then** verbose
   diagnostics do not expose proprietary guest content or create excessive log traffic.

## Edge Cases

- The guest changes display resolution, refresh rate, or audio behavior during boot.
- The guest emits no audio batches while silent rather than explicit zero samples.
- The core emits the nominal number of audio frames even though guest-visible time or
  audio content is wrong; frame count alone must not produce a passing result.
- Audio production arrives in bursts larger than a host playback callback.
- Android grants a different output rate or burst size than requested.
- The app is paused, backgrounded, split-screen, locked, or interrupted by audio focus.
- A surface is destroyed or recreated while the guest and audio stream remain active.
- Thermal throttling lowers sustainable performance after several minutes.
- An optimized execution mode initializes successfully but fails after guest code changes mode.
- Guest CPU cycles are too high for the target even when rendering is minimal.
- Reducing presented frames makes pointer movement or setup dialogs difficult to use.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST keep guest time, audio production, audio consumption, and
  video presentation coordinated without making video presentation the master clock.
- **FR-002**: The system MUST prevent host playback from repeatedly consuming an empty
  queue during normal foreground execution.
- **FR-003**: The system MUST start playback only after a bounded initial queue is ready.
- **FR-004**: The system MUST stop or suspend host playback consumption whenever guest
  execution is paused or unable to produce samples for a lifecycle reason.
- **FR-005**: The system MUST resume from pause without replaying stale samples or creating
  a permanent production/consumption phase error.
- **FR-006**: The system MUST permit video presentation rate to be reduced independently
  from guest execution rate while retaining the most recent valid frame.
- **FR-007**: The system MUST preserve a responsive guest display during normal desktop
  interaction even when not every guest frame is presented.
- **FR-008**: The system MUST provide at least one conservative execution profile that
  starts reliably on the SM-T500.
- **FR-009**: The system MUST NOT expose an optimized execution profile as validated on
  the SM-T500 while it causes a native crash or fails correctness tests.
- **FR-010**: A failed optimized startup MUST NOT create a permanent crash loop and MUST
  allow recovery to a conservative profile without deleting or replacing guest media.
- **FR-011**: Performance tuning MUST NOT alter the validated Sound Blaster resource
  contract for the Windows 98 profile: I/O 0220–022F and 0388–038B, IRQ 7, DMA 1 and 5.
- **FR-012**: Performance tuning MUST preserve guest disk correctness across boot, idle,
  pause/resume, orderly shutdown, and abnormal startup failure tests.
- **FR-013**: Debug diagnostics MUST distinguish foreground execution, background state,
  explicit pause, audio focus loss, surface loss, and native failure.
- **FR-014**: Debug diagnostics MUST measure produced audio frames, consumed frames,
  underruns, queue depth, emulator-call rate, and presented video rate over bounded intervals.
- **FR-015**: Public logs MUST NOT include guest media contents, product keys, private
  paths beyond app-owned identifiers, or other proprietary data.
- **FR-016**: The host UI MUST remain entirely RoboWindows-owned during tuning, recovery,
  and testing; upstream menus and overlays remain prohibited.
- **FR-017**: A real-time claim MUST combine host telemetry with an independent
  guest-visible timer or deterministic guest workload; produced frame count alone MUST NOT
  be treated as proof of correct guest speed.
- **FR-018**: Disk-safety validation MUST distinguish an exact no-write startup test from
  workloads where the guest legitimately writes; write-capable tests MUST use cloned media
  and verify filesystem readability and expected persistent state rather than demand an
  unchanged whole-disk checksum.
- **FR-019**: The feature MUST validate at least one redistributable DOS workload in
  addition to the user-owned Windows 98 workload before making DOS-and-Windows-family
  compatibility claims.
- **FR-020**: Debug audio diagnostics MUST distinguish starvation from sample saturation
  using aggregate counters only; they MUST NOT persist or log guest PCM.
- **FR-021**: Experimental machines MUST use the internal `balanced-100ms` timing
  policy under both Normal and DynRec execution. Stable machines MUST retain the
  existing scheduling and 200 ms startup-buffer behavior. The policy MUST be
  selected from a product-owned allowlist and MUST NOT be inferred from a media
  path or exposed as a raw user setting.
- **FR-022**: The experimental scheduler MUST preserve fixed guest-frame deadlines
  across stalls up to 250 ms and run no more than 20 consecutive catch-up calls
  without a cooperative thread yield. After each bounded burst it MUST retain
  the deadline debt and yield so other real-time work can run. Only debt larger
  than 250 ms MUST clamp to the current monotonic time and increment a
  quality-failing resynchronization counter.
- **FR-023**: Experimental playback MUST begin at approximately 100 ms of queued
  guest audio. Outside a 75–125 ms band, scheduling MAY apply at most a temporary
  one-percent cadence correction, with hysteresis back to the 100 ms target. It
  MUST NOT resample, stretch, mute, repeat, or fabricate guest audio.
- **FR-024**: Pause, audio-focus loss, stream recovery, guest restart, and session
  restart MUST clear queued audio, return playback to prebuffering, and reset
  cadence correction, catch-up state, deadline state, and audio-producer-gap state.
- **FR-025**: Debug telemetry schema 3 MUST report the current timing-policy ID,
  maximum `retro_run()` duration and over-budget call count, maximum audio-producer
  gap, maximum scheduler lateness, catch-up calls, deadline resynchronizations, and
  current/minimum/maximum audio queue depth. Any resynchronization invalidates the
  affected quality capture.
- **FR-026**: Only `SET_SYSTEM_AV_INFO` may interpret its payload as libretro AV
  timing. Informational callbacks must never read that structure. Null, non-finite,
  or implausible timing (FPS outside (1, 1000] or sample rate outside (0, 384000])
  must be rejected without changing cadence. Valid advertised refresh changes
  must still reach the scheduler.
- **FR-027**: Experimental timing diagnosis must report bounded per-interval
  emulator-call wall time and process CPU time, gaps between calls, lateness on
  entering the next call, and video/audio callback durations. Process CPU time
  includes all runner threads and must not be labeled DynRec execution time.
  Failed CPU-clock reads must be counted. Measurements must reset at lifecycle
  timing resets and must not change scheduling, PCM, the CPU engine, or the
  presentation cap. No per-call or guest-content logs are permitted.
- **FR-028**: A valid guest refresh-rate change during experimental execution MUST
  update the nominal call interval without resetting outstanding deadline debt,
  queue correction, catch-up burst state, producer-gap continuity or the current
  diagnostic interval. Only lifecycle resets may clear those states. Legacy
  refresh changes retain their existing deadline-reset behavior. Debt exceeding
  250 ms after a refresh change still MUST be counted as a resynchronization.
- **FR-029**: A source-owned disposable stress workload MUST distinguish idle,
  integer, RAM-copy and VGA-write phases, retain independent guest tick timing
  and an unmodified guest tone, and prove that all phases executed. Its identity
  MUST be explicit in capture results so it cannot be confused with the existing
  light timer/tone fixture. It MUST NOT consume Windows or application files or
  be described as reproducing the unmeasured internals of a particular game.

### Key Entities

- **Performance Profile**: A named, recoverable guest execution configuration with memory,
  CPU behavior, cycle target, presentation policy, validation status, and target evidence.
- **Audio Timing Sample**: A bounded measurement of produced frames, consumed frames,
  queue depth, underruns, pause/lifecycle state, and elapsed wall time.
- **Runtime Health Record**: A session result identifying normal exit, user stop, startup
  failure, native crash, and the last known-safe performance profile.
- **Compatibility Evidence**: Reproducible results linking an app build, guest profile,
  target device, workload, and measured success criteria.

## Success Criteria

### Measurable Outcomes

- **SC-001**: During a five-minute foreground Windows 98 validation workload on the
  SM-T500, produced audio sustains the actual opened host rate within ±2% after startup
  settles (48,000 frames/sec on the current target).
- **SC-002**: During each reference sound, the host reports zero underrun callbacks after
  the initial bounded startup period in 10 consecutive runs.
- **SC-003**: Reference sounds complete within ±5% of their expected duration, report no
  unexplained saturated-sample runs at the host boundary, and are judged free of repeated
  clipping, gaps, and tempo changes in 10 consecutive runs.
- **SC-004**: An independent guest-visible timer or deterministic timer workload remains
  within ±5% of monotonic host time across a five-minute idle, window movement, and audio
  workload; audio frame production is supporting evidence, not the timer.
- **SC-005**: The display presents at least 15 frames/sec during the workload and remains
  responsive enough to complete the published keyboard and mouse interaction procedure.
- **SC-006**: Ten pause/resume cycles produce no stale replay, persistent underruns, native
  crash, or tested disk corruption.
- **SC-007**: An unsupported optimized-mode test that is terminated before guest execution
  recovers to a conservative profile on the next attempt without manual file editing and
  without changing the clone's pre-test disk checksum.
- **SC-008**: A 30-minute thermal-soak run maintains at least 95% guest-time rate in every
  settled five-minute window without progressive audio breakup or application termination.
- **SC-009**: A redistributable DOS timer-and-tone workload meets SC-001–SC-005 on the
  SM-T500 before the feature is described as supporting both DOS and Windows-family guests.
- **SC-010**: In a matched foreground fixed-20k Normal/DynRec benchmark pair on the
  experimental copy, each profile presents at least 15 FPS, keeps independent guest
  timing within 5%, reports zero settled underruns and missing frames, reports zero
  deadline resynchronizations and lifecycle errors, and shuts down cleanly.

## Assumptions

- The Samsung SM-T500 remains the primary acceptance device and is available through ADB.
- Windows 98 uses the installed built-in Sound Blaster 16-compatible driver and already
  matches the emulator resource contract; driver replacement is outside this feature.
- User-owned guest media and registration data remain outside source control and artifacts.
- Reduced video presentation is acceptable when it preserves responsiveness and makes
  guest execution real-time.
- The Windows guest timer test may require a small user-copied test utility or an observable
  guest clock procedure. The preferred utility is built from versioned redistributable
  source and writes only timer/result values; proprietary system files are not redistributed.
- Optimized execution may require an upstream-core patch, but the specification does not
  mandate a particular implementation.
- The first scheduler candidate is the experimental-only `balanced-100ms` policy:
  a 100 ms target, a 75–125 ms correction band, at most one-percent cadence
  correction, 250 ms maximum retained debt, and 20 maximum consecutive catch-up
  calls between cooperative thread yields.

## Explicitly Out of Scope

- Reinstalling Windows or replacing the validated guest disk.
- Adding third-party proprietary audio drivers.
- Hiding broken audio by muting it or stretching severely slow guest time without disclosure.
- Promising real-time performance for every DOS or Windows release.
- Exposing DOSBox configuration screens or upstream performance overlays to users.
