# Contract: Runtime Telemetry

Debug builds expose one sanitized snapshot per reporting interval through logcat and may
write the same records to an app-private report. This is an internal diagnostic contract,
not a public network API.

## Snapshot

```text
schema_version: 1
session_id: opaque identifier
interval_ms: positive integer
runtime_state: starting | foreground | paused | background | surface_lost | stopping
profile_id: product-owned identifier
cpu_core: normal | auto | dynamic_experimental
cycle_policy: auto | fixed | max
cycle_value: integer or null
emulated_scheduler_delta_ms: non-negative integer or null
audio_produced_frames: non-negative integer
audio_consumed_frames: non-negative integer
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
- Queue minima/maxima are sampled without blocking either real-time callback.
- Unknown enum values make a consumer reject that record version, not guess.

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
