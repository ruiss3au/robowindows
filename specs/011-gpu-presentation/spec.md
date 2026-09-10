# Feature 011: Experimental GPU presentation

**Status:** Implemented; disposable acceptance passed, Normal and post-REP DynRec
Windows smoke failed audio/timing acceptance; further trials on hold (2026-09-10)

- **FR-001:** Offer Software · 15 FPS and GPU · 30 FPS (experimental) in a
  Display properties tab, independently of Normal/DynRec. GPU is diagnostic-build,
  stopped-experimental-machine only. Defaults and stable machines use Software.
- **FR-002:** Use the existing transactional draft, generation and recovery guards.
  Schema 8 adds presentation mode; reading older profiles defaults to Software
  without rewriting them. Recovery uses Software without erasing the preference.
- **FR-003:** Allowlist presentation policy across Java/JNI and isolated DynRec
  boundaries. Revalidate launch eligibility; never infer policy from disk paths.
- **FR-004:** GPU uploads existing XRGB8888 frames and performs channel conversion
  and nearest-neighbor aspect-correct scaling at surface resolution. Preserve
  letterboxing, input mapping and translucent controls. No guest 3D acceleration.
- **FR-005:** All EGL/GL work belongs to the presenter thread, never the emulator
  callback. Keep bounded latest-frame storage, target 30 FPS without catch-up
  bursts, and preserve independent emulation/audio scheduling.
- **FR-006:** Handle padded rows, resolution changes, surface replacement and
  context loss. Surface absence is not a graphics failure. Unrecoverable GPU
  failure visibly falls back to Software for the session, without rebooting the
  guest, repeatedly retrying, or changing the saved preference.
- **FR-007:** Schema-4 aggregate telemetry identifies requested/active backend,
  successful posts, maximum presentation interval, CPU-side upload/draw time,
  swap time, presenter thread CPU time, errors and fallbacks. Do not label swaps
  unique game frames or CPU measurements GPU execution time. No guest-content logs.

## Acceptance

Host tests cover migration/defaults, settings transactions, eligibility, policy
validation, geometry/rows, pacing, lifecycle and fallback. Disposable device
fixtures cover colors/orientation/scaling, resolution/surface changes, overlays,
input alignment and injected failures. Run Normal before DynRec.

Run a two-minute animated timer-and-tone matrix (Software/GPU, Normal/DynRec).
Exclude the first ten seconds and marked lifecycle intervals. With a sufficiently
fast source, GPU must sustain at least 28 successful posts/s, independent guest
timing within 5%, lower presenter CPU per frame than Software, and zero settled
underruns, missing audio frames, resynchronizations or unexpected graphics errors.
A fallback is a recovery test, not a performance pass.

After disposable gates pass, authorize one three-minute Windows GPU smoke per core
on the experimental copy, followed by orderly Windows shutdown. No stable guest
changes, benchmark medians, AoE2 campaign, ten-cycle or thermal campaign. Short
acceptance does not establish sustained thermal stability or promote DynRec.
