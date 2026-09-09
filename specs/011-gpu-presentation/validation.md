# Validation

Implementation started 2026-09-10. Basic GPU device checks passed; timer/tone and
real Windows acceptance are recorded separately below, not implied by those checks.
Stable `incoming` must remain untouched. Thermal stability and unfinished DynRec
qualification remain unverified regardless of this feature's short checks.

## Initial implementation checks

- Host suite, eight-suite QEMU x86 reference, hygiene, pinned sources and Android
  debug build passed. The graphics fixture booted in QEMU and recorded 54 BIOS
  ticks after approximately four host seconds including boot (basic progress
  check, not a completed timing acceptance run).
- Disposable tablet settings/core checks passed, including schema defaults,
  GPU preference/CPU independence, save rollback, clean-recovery persistence,
  stable rejection and all five properties tabs at enlarged text sizes.
- Disk-free graphics probe passed exact color/orientation/letterbox readback,
  padded rows, resolution changes, context/surface recreation and one injected
  GPU failure followed by successful software posts.
- Four-second synthetic probe: GPU 118 posts / 498,325 presenter CPU microseconds;
  Software 60 posts / 2,366,599 presenter CPU microseconds. Approximately 29.5 vs
  15 FPS and 4,223 vs 39,443 CPU microseconds per frame. This is short graphics
  evidence only, not audio, guest timing or thermal evidence.
- Repeated graphics probe on the timer-harness candidate: GPU 118 posts / 500,698
  CPU microseconds; Software 60 posts / 2,357,379 CPU microseconds; same checks
  passed. Candidate APK SHA-256:
  `5f39c3774fde6abba4b21ba7bde57b3b06114cd797693d7c63407095981044c4`.
- Expanded disposable tablet x86 gate passed Normal and DynRec, all eight suites
  plus legacy fixture. No real guest was started.

## Findings

Normal mode's CPU Canvas waiting frame would take ownership of the surface before
EGL. GPU launches now skip that drawing, while Software keeps it. Mode changes
take effect on a new session SurfaceView; runtime fallback disconnects EGL before
connecting Software. GPU resources are never destroyed on the UI thread.

The first disposable timer harness timed out because floppy writes are buffered
until emulator unload. Its clean audio/video telemetry is diagnostic only, not
a timing pass. Correct the harness to run a continuous guest tick counter, stop
after a bounded host interval, then read the flushed counter. Do not use an
already-completed fixed-duration guest workload, which could hide a fast clock.

## Matched disposable timer/tone matrix

Build: `6efe9be5f295+dirty`, APK
`5f39c3774fde6abba4b21ba7bde57b3b06114cd797693d7c63407095981044c4`.
Packaged source-owned fixture SHA-256:
`8643f9d97386905e71f74e9aee74c3a936701dd3471dfdb48423715abac4b76a`.
SM-T500/API 36, foreground landscape, fixed 20k, balanced 100 ms policy.
The installed APK is compared to the local checksum before each matrix run.

| Core | Presenter | Presented FPS | Presenter CPU us/frame | Host ms | Guest ms | Result |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| Normal | Software | 15.09 | 16,627.95 | 122,094 | 121,605 | Pass |
| Normal | GPU | 29.77 | 1,441.14 | 122,007 | 121,605 | Pass |
| DynRec | Software | 15.09 | 16,694.43 | 122,086 | 121,605 | Pass |
| DynRec | GPU | 29.78 | 1,340.68 | 122,100 | 121,605 | Pass |

Each completed capture includes over 108 seconds of settled foreground telemetry,
with zero underruns, missing/dropped audio frames, stream errors, scheduler
resynchronizations, post failures, GPU errors/fallbacks and presenter clock errors.
Audio state was playing. Guest timing differed from host time by less than
0.5% in all four runs; GPU presenter CPU per frame was about 91% lower under
Normal and 92% lower under DynRec. Correct configured decoders were checked by
the fixture, with matching Normal/DynRec residency observed during the workloads.
These are host posts, not
unique game frames. Windows GPU smoke, physical input alignment and sustained
thermal qualification remain pending; no Windows machine has been booted here.

Final stopped-state check: no active-session marker and no DynRec, CPU-fixture
or presentation-fixture process. Stable `incoming` remains Normal/Software;
`win98 dynrec exp` remains DynRec/Software. GPU was not silently selected on
either real machine. Disposable workload files were deleted after core unload.
Final host suite, repository hygiene and diff whitespace checks passed. No push.
