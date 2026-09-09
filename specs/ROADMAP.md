# RoboWindows Roadmap

This is the short priority list. Each item gets its own numbered feature
specification, plan, tasks, and validation record when work begins.

## Now

- Keep fixed-20k DynRec as the experimental baseline; fixed 30k and bounded
  auto failed audio or overall quality.
- Diagnose the remaining DynRec workload spikes and audio queue depletion with
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

- If the new matched pair has zero settled underruns, complete Feature 009's
  three-run diagnostic medians on the copy.
- Per the user's 2026-09-09 scope decision, drop further AoE2/audio, ten-cycle,
  pause/resume and 30-minute thermal runs from this campaign. Record them as
  untested/incomplete, not passed; do not automatically queue them afterward.
  Keep DynRec experimental and make no promotion or sustained-stability claim.
  Orderly Windows shutdown is still required after each actual benchmark run.

## Later

- **GPU presentation and higher FPS — after DynRec quality gates pass.** Move
  host framebuffer scaling and color conversion from the CPU to the GPU using
  OpenGL ES, then target 30 presented FPS on experimental copies. The current
  presenter deliberately limits output to about 15 FPS; raising that limit must
  preserve audio continuity, guest timing, input, and sustained thermal behavior.
  This improves host presentation; it does not promise 30 unique game frames per
  second. Defer this feature until the DynRec correctness, audio, stability, and
  performance gates above are satisfied.
- **Windows 98–style Android interface.** Redesign RoboWindows' own machine
  library, settings, dialogs, and session controls to look like a Windows 98
  application, with classic gray panels, beveled buttons, title bars, and period
  typography. Retain usable touch targets, readable text, keyboard/mouse access,
  and clear session/recovery controls. Use project-owned or redistributable
  assets and keep all visible host UI owned by RoboWindows. Define the numbered
  feature and visual acceptance criteria when this future design work begins.
- Complete release-readiness work.

## Never

- Use the only writable guest disk for experiments.
- Bundle proprietary guest software or expose upstream emulator controls.
