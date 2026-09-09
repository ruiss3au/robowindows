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

Records MUST NOT contain PCM, framebuffer data, guest-visible text, media filenames or
paths, disk contents, registration data, or Android input contents. Normal reporting is at
most once per second plus lifecycle and terminal events. Release builds disable interval
records and retain only the minimal app-private recovery state.

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
