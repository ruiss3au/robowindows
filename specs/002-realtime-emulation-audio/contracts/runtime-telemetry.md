# Contract: Runtime Telemetry

Debug builds expose one sanitized snapshot per reporting interval through logcat and may
write the same records to an app-private report. This is an internal diagnostic contract,
not a public network API.

The native log line is one part of this record. Schema 3 adds explicit runtime
scheduling, call-duration, producer-gap, and current queue-depth evidence to the
schema-2 decoder residency fields. The benchmark collector binds those intervals to an
allowlisted profile/cycle declaration and build/thermal envelope. Completion of
the general app-side profile/session/termination envelope remains Feature 002
T010–T011 and is not implied by the benchmark collector.

## Snapshot

Feature 011 emits schema 4: schema 3 fields plus `presentation_requested` and
`presentation_active` (0 Software, 1 GPU), `presentation_interval_max_us`,
`upload_draw_us`, `swap_us`, `presenter_cpu_us`, `graphics_errors`,
`graphics_fallbacks`, `presenter_clock_errors` (nonnegative interval aggregates).
The presenter CPU clock covers rendering/posting, not sleeping. Upload/draw is
CPU-side wall duration, not GPU execution time. Successful posts are not unique
game frames. A requested/active mismatch, error or fallback invalidates a GPU
performance result even if audio and FPS look healthy. Historical schema-3
captures remain readable; mixed schemas or incomplete graphics fields are rejected.

```text
schema_version: 3
session_id: opaque identifier
interval_ms: positive integer
runtime_state: starting | foreground | paused | background | surface_lost | stopping
profile_id: product-owned identifier
cpu_core: normal | auto | dynamic_experimental
configured_decoder: Normal | DynRec | Unknown
current_decoder: Normal | DynRec | PageFault | Halt | Other | Unknown
cycle_policy: auto | fixed | max
cycle_value: integer or null
timing_policy: legacy | balanced_100ms
emulated_scheduler_delta_ms: non-negative integer or null
retro_run_max_us: non-negative integer
retro_run_over_budget_calls: non-negative integer
audio_producer_gap_max_us: non-negative integer
scheduler_lateness_max_us: non-negative integer
scheduler_catchup_calls: non-negative integer
scheduler_deadline_resyncs: non-negative integer
audio_produced_frames: non-negative integer
audio_consumed_frames: non-negative integer
audio_queue_frames_current: non-negative integer
audio_queue_frames_min: non-negative integer
audio_queue_frames_max: non-negative integer
audio_underrun_callbacks: non-negative integer
audio_missing_frames: non-negative integer
audio_dropped_frames: non-negative integer
audio_saturated_samples: non-negative integer
audio_stream_errors: non-negative integer
audio_stream_state: stopped | prebuffering | playing | suspended | recovering
emulator_run_calls: non-negative integer
guest_frames_submitted: non-negative integer
frames_published: non-negative integer
frames_presented: non-negative integer
frames_coalesced: non-negative integer
surface_post_failures: non-negative integer
termination: null | user_stop | orderly_shutdown | native_failure | unknown
```

## Semantics

- Counters describe only `interval_ms`; lifetime totals are reported separately at stop.
- Rates used for acceptance include only `runtime_state=foreground` after startup settles.
- An underrun callback counts once even if several frames are missing; missing frames are
  also counted explicitly.
- Coalescing a video frame is expected behavior and is not a dropped emulation frame.
- Saturation counts samples at the numeric PCM extrema; it identifies a possible clipped
  boundary signal but does not by itself prove audible speaker clipping.
- Queue current/minimum/maximum values are sampled without blocking either
  real-time callback. Current is the latest observed depth; minimum and maximum
  cover the reporting interval.
- `retro_run_max_us` is the longest completed call in the interval. A call is
  over budget when its duration exceeds the nominal guest-frame period.
- `audio_producer_gap_max_us` is the largest interval between consecutive guest
  audio batches since the last lifecycle reset; a first batch has no gap.
- Scheduler lateness is measured after a completed `retro_run()` against its
  fixed deadline. Catch-up calls do not sleep. Any deadline resynchronization is
  an explicit quality failure even if later intervals look healthy.
- Unknown enum values make a consumer reject that record version, not guess.
- Schema 3 adds the timing fields above. Existing schema-1 and schema-2 records
  remain historical evidence but cannot satisfy a new balanced-policy capture.

## Privacy and volume

Experimental sessions additionally emit one `RoboWindowsTiming` schema-1 line
immediately after each schema-3 interval. All fields are nonnegative integers:
`interval_ms`, `calls`, `wall_total_us`, `process_cpu_total_us`,
`process_cpu_max_us`, `cpu_clock_errors`, `host_gap_max_us`, `wake_late_max_us`,
`video_total_us`, `video_max_us`, `audio_total_us`, `audio_max_us`.

Call wall/CPU durations bracket `retro_run()`. CPU time covers all process threads
only during those call windows; it may exceed wall time and is not decoder CPU
time. Invalid CPU-clock pairs increment `cpu_clock_errors` and add no CPU duration.
Host gap is previous call end to next call start, including intended sleep and
host maintenance. Wake lateness is next call start beyond the previous fixed
deadline, including retained debt; it is not pure OS scheduler latency.
Video/audio durations cover synchronous frontend callbacks, not the separate
AAudio consumer. These aggregates reset on lifecycle timing resets; consecutive
report intervals retain gap continuity. Reset intervals may contain fewer
diagnostic calls than schema-3 run calls. Historical captures without the
extension remain readable as `timing_diagnostics=unavailable`; partial, malformed,
or mismatched extensions are rejected. Diagnostic records contain no guest data.

Live guest refresh-rate changes are not lifecycle resets: retain call totals and
producer-gap history across them. Builds before the FR-028 correction also reset
diagnostics on refresh updates, so their partial intervals must not be treated
as complete attribution of the corresponding telemetry interval.

Records MUST NOT contain PCM, framebuffer data, guest-visible text, media filenames or
paths, disk contents, registration data, or Android input contents. Normal reporting is at
most once per second plus lifecycle and terminal events. Release builds disable interval
records and retain only the minimal app-private recovery state.

## Optional internal core timing (FR-030)

`RoboWindowsWorker` schema 1 accompanies each experimental telemetry interval.
Fields: `schema`, `interval_ms`, `worker_slices`, `worker_wall_total_us`,
`worker_wall_max_us`, `worker_cpu_total_us`, `worker_cpu_max_us`,
`worker_clock_errors`, `wait_calls`, `wait_total_us`, `wait_max_us`, `mix_calls`,
`mix_total_us`, `mix_max_us`, `frontend_clock_errors`, `translations`,
`fallback_special`, `fallback_invalidated`, `fallback_opcode`, `fallback_smc`,
`fallback_trap`, `discarded_slices`. All values are nonnegative integers.

Worker totals include only completed slices, excluding frame/pause semaphore
waits. CPU uses the worker thread clock, not process CPU. Slices may span frontend
report boundaries; their completed contributions belong to the publication
interval. Worker and frontend observations overlap and must not be added into a
single phase budget. Wait/mix totals are inclusive scope wall times; worker CPU
includes guest execution, translation and device/render work. Translation counts
are creation attempts, not generated block counts or execution-time estimates.
Fallback reasons count direct interpreter calls; SMC is not also counted as
unsupported opcode. No guest instructions or addresses are logged.

Frontend snapshots never read live worker counters. Lifecycle reset advances a
generation and discards stale completed work; `discarded_slices` makes such
discard visible when the new generation is enabled. Disabled legacy sessions
emit no extension. The strict parser accepts historical absence, rejects partial
or malformed extensions and clock failures, and reports `core_timing=1` only for
complete valid extensions. A zero-slice interval cannot contain slice totals or
events; slices need not equal frontend calls because of pause/startup boundaries.

Before loading experimental media, `RoboWindowsCalibration` records schema 1,
`iterations=2000`, `events_per_slice=64`, and disabled/enabled mean nanoseconds
per synthetic slice. These are isolated local probes, not guest measurements.
Nonzero calibrated times and enabled overhead below 1% of the nominal frame
budget are the initial smoke threshold; capture-to-capture timing variability
and unmeasured workload-specific overhead remain explicit limitations.

## Acceptance derivations

- Audio production rate = `audio_produced_frames * 1000 / interval_ms`.
- Guest-time rate is derived by the acceptance harness from an independent guest-visible
  timer/workload. `emulated_scheduler_delta_ms`, host `emulator_run_calls`, and audio
  production are supporting diagnostics and cannot independently satisfy SC-004.
- Presentation rate = `frames_presented * 1000 / interval_ms`.
- A performance result is invalid if any included interval is paused, backgrounded,
  surface-lost, locked, split-screen, or affected by audio-focus loss.
- A balanced-policy quality result fails if any included interval reports a
  nonzero `scheduler_deadline_resyncs` value.
