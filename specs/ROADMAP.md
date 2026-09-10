# RoboWindows Roadmap

This is the short priority list. Each item gets its own numbered feature
specification, plan, tasks, and validation record when work begins.

## Now

- DynRec REP cycle accounting is corrected in isolated patch 0008. All 18
  actual-helper regressions, the full x86 gate and the unchanged Normal/DynRec
  stress pair pass. DynRec's maximum call fell from 352 ms to 19 ms, with zero
  audio errors/resynchronizations and 29.77 presented FPS. This closes the
  disposable memory-work regression, not the earlier Normal/AoE2 failures.
  The subsequent authorized Windows desktop check still failed audio/timing;
  its clean shutdown is verified. Keep further Windows trials paused
  ([evidence](008-arm64-dynamic-recompilation/rep-cycle-accounting.md)).

- Shared audio investigation: Feature 002's live-refresh correction is installed
  and verified. Refresh changes no longer silently discard experimental timing
  debt or diagnostic intervals; legacy cadence and the 100 ms queue target are
  unchanged. Host/build, disposable core/x86 and Normal/DynRec timer/tone checks
  passed. Windows long-call starvation remains unresolved: attribute the worker
  stalls with a confirmed workload before more acceptance trials
  ([investigation](002-realtime-emulation-audio/timing-investigation.md#shared-normal-windows-stalls-and-refresh-debt-correction--2026-09-10)).

- Feature 011 GPU presentation is implemented as an independent experimental-copy-only
  feature, with Software/GPU selection independent of Normal/DynRec. Disposable
  graphics/lifecycle and all four short timer/tone checks passed: GPU approximately
  29.8 presented FPS versus Software 15.1, zero settled audio/scheduling/graphics
  errors, and guest timing within 0.5%. Normal Windows GPU smoke sustained 29.78
  presented FPS without graphics failures, but failed audio/timing acceptance
  (605 underruns, 89,086 missing frames, seven resynchronizations). Windows shut
  down cleanly. The later post-REP DynRec desktop-only smoke held 29.78 FPS but
  recorded 763 underruns, 112,203 missing frames and nine deadline resets in
  73.615 seconds. Page-fault returns stayed balanced with no wipe; shutdown was
  clean. Hold further Windows trials pending attribution of the remaining worker
  overruns ([DynRec evidence](011-gpu-presentation/validation.md#post-rep-dynrec-desktop-smoke--failed-timing-clean-shutdown-2026-09-10)). These results
  do not establish that GPU presentation caused the timing failure
  ([evidence](011-gpu-presentation/validation.md#windows-gpu-smoke--normal-settled-capture)).
  The authorized Normal/Software comparison also failed (15.09 FPS, 454 underruns,
  68,844 missing frames, five resynchronizations). Failure is not GPU-exclusive;
  workload equivalence is unconfirmed, so differing counts do not establish
  causality. Software-session clean Windows shutdown was verified
  ([comparison](011-gpu-presentation/validation.md#windows-software-comparison--completed-capture)).
  Short targeted validation replaces the previous dependency on full DynRec
  qualification; it does not promote DynRec or establish thermal stability.

- Feature 010 is delivered and its scoped host-UI acceptance is complete:
  classic touch-friendly screens, staged properties with editable machine names,
  Normal/DynRec selector, Tests panel, translucent session controls and modern
  Android Back confirmations. Clean shutdown retains DynRec selection; recovery
  and experimental-copy-only restrictions remain intact.
- DynRec performance investigation is paused by user choice after the clean
  same-build pair. Clock accuracy, medians and long-term stability remain
  unverified; do not automatically queue more benchmark or long-gate runs.

- Keep fixed-20k DynRec as the experimental baseline; fixed 30k and bounded
  auto failed audio or overall quality.
- If runtime investigation resumes, diagnose workload spikes and audio queue depletion with
  Feature 002's implemented balanced 100 ms scheduler and schema-3 diagnostics.
  The post-PageFaultCore matched pair completed cleanly, but DynRec recorded
  12 underruns and 1,862 missing audio frames while Normal recorded zero
  ([evidence](009-win98-performance-benchmark/validation.md#post-pagefaultcore-matched-pair--2026-09-09)).
- The shared AV-dispatch correction and new call/callback timing diagnostics
  are installed and tested. The instrumented same-build Normal/DynRec fixed-20k
  diagnostic pair recorded zero underruns/missing frames/resynchronizations,
  correct residency, 15.07 presented FPS and clean shutdowns for both cores
  ([evidence](009-win98-performance-benchmark/validation.md#av-dispatch-same-build-diagnostic-pair--2026-09-09)).
  Independent guest-clock validation remains outstanding before median runs.
  Make further runtime corrections only from measured evidence; one clean run
  does not establish that the intermittent failure is cured.

## Next

- Internal worker timing and translation/interpreter-fallback counters are
  installed and validated (Feature 002 T072 / patch 0009). Legacy probe, enabled
  full x86 gate and unchanged Normal/DynRec stress pair pass with zero quality
  errors; tablet synthetic calibration stays below 11 us per slice. The authorized
  instrumented Windows desktop capture then reproduced underruns with clean
  shutdown. In overloaded intervals, worker wait was about 96% of frontend call
  wall and worker CPU about 97% of elapsed time; frontend mixing was small.
  Offline review found translation attempts about 7.68 times as frequent in
  underrun intervals, while fallback rates changed much less. The opcode bucket
  also includes translator stops at invalidated code, not only unsupported
  instructions. The five source-owned warm/cold/reuse/data-write/code-rewrite
  controls are now implemented, with exact checksums and independent guest ticks;
  host/QEMU, failure checks and Android build pass. Existing fixtures are unchanged.
  The build is installed; the full x86 gate and all ten Normal/DynRec cache-case
  correctness checks passed with clean cleanup and unchanged real profiles.
  Eight cases finished before the first timing report; reuse had just one complete
  interval per core. Next, add bounded terminal diagnostic coverage before another
  comparison, preserving the exact images. Counts still do not isolate a costly
  function. No speculative engine/buffer tuning or Windows run is queued
  ([fixture and limits](002-realtime-emulation-audio/cache-control-fixture.md)).
- Use the completed UI and address specific reported issues. Resume Feature
  009's clock/median campaign only if the user later requests it; no new guest
  trial is queued by completing the UI milestone.
- Per the user's 2026-09-09 scope decision, drop further AoE2/audio, ten-cycle,
  pause/resume and 30-minute thermal runs from this campaign. Record them as
  untested/incomplete, not passed; do not automatically queue them afterward.
  Keep DynRec experimental and make no promotion or sustained-stability claim.
  Orderly Windows shutdown is still required after each actual benchmark run.

## Later

- Qualify GPU presentation for sustained use before expanding beyond experimental
  copies. Feature 011 targets 30 presented FPS, not 30 unique game frames or guest
  3D acceleration. Sustained thermal behavior is outside its initial short checks.
- Complete release-readiness work.

## Never

- Use the only writable guest disk for experiments.
- Bundle proprietary guest software or expose upstream emulator controls.
