# Validation

## Opt-in availability — 2026-09-10

T012 passed host eligibility/recovery-policy checks, both build variants and
ordinary-machine Properties visibility without changing the saved preference.
The disposable colors/padding/resolution/context/surface/fallback probe also
passed on the installed build; no guest media was opened.
See [promotion evidence](../../docs/opt-in-promotion.md). Existing Windows
audio/timing failures and sustained-quality gaps remain unpassed.

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
unique game frames. At this matrix milestone, Windows GPU smoke, physical input
alignment and sustained thermal qualification remained pending; no Windows
machine had been booted for these disposable checks.

Final stopped-state check: no active-session marker and no DynRec, CPU-fixture
or presentation-fixture process. Stable `incoming` remains Normal/Software;
`win98 dynrec exp` remains DynRec/Software. GPU was not silently selected on
either real machine. Disposable workload files were deleted after core unload.
Final host suite, repository hygiene and diff whitespace checks passed. No push.

## Windows GPU smoke — Normal startup (2026-09-10)

User authorized the short Windows checks. Through properties, changed only
`win98 dynrec exp` from DynRec/Software to Normal fixed-20k/GPU (generation
28 to 29). Other profile-field fingerprints were unchanged; stable `incoming`
remained Normal/Software, generation 1. Installed APK matched the matrix build.

Started the experimental copy and observed the Windows desktop with correct
aspect ratio and GPU active. The 24-interval startup capture averaged 29.49
presented FPS, with zero graphics errors, fallbacks or scheduler resynchronizations.
Boot workload recorded 15 underruns / 1,399 missing audio frames; final captured
intervals had zero underruns. This is startup evidence, not settled acceptance.
User subsequently confirmed desktop readiness; the completed foreground capture
and shutdown are recorded below.

## Windows GPU smoke — Normal settled capture

Same APK and experimental-copy configuration as startup. The capture began after
the user's desktop-ready confirmation and contains 179 telemetry intervals,
180,396 ms total. Every interval reported Normal configured/current residency,
foreground state, playing audio and requested/active GPU presentation.

- 5,373 successful posts: **29.78 presented FPS**; zero post failures, graphics
  errors, fallbacks or presenter clock errors.
- **Failed audio/timing acceptance:** 605 underruns, 89,086 missing audio frames
  and seven scheduler deadline resynchronizations. Zero dropped frames,
  saturation events or audio stream errors.
- Maximum `retro_run()` duration 84,011 us, producer gap 84,034 us and scheduler
  lateness 263,695 us. Queue depth reached zero. Maximum presentation interval
  was 76,697 us; presenter thread CPU totaled 11,265,416 us.
- Battery temperature increased from 26.0 to 27.0 degrees C; Android thermal
  status remained 0 at the two endpoint samples. This is not thermal qualification.

Failures occurred well before shutdown. Busy intervals included substantial time
inside emulation calls while GPU posts continued, but these aggregate measurements
do not isolate a CPU-engine hot path or prove GPU caused the failure. No matched
Windows Software capture was taken on this build. Specific user workload, human
audio judgment and physical input alignment remain unconfirmed; independent
Windows guest-clock accuracy was not measured by this smoke.

The user shut down from Windows: the core recorded `guest requested shutdown`
at tablet time 07:24:12.939 and `guest stopped cleanly` at 07:24:12.997. Follow-up
checks found no active-session marker or isolated/fixture process, and persisted
clean-shutdown provenance for both machines. Stable `incoming` remained unchanged
(Normal/Software, generation 1). The stopped experimental copy retains the
explicitly selected Normal/GPU settings, generation 29; no forced stop, reinstall
or automatic mode rollback was performed.

DynRec/GPU Windows smoke is **on hold**, not passed. Diagnose this Normal failure
before any further Windows trial; no scheduler or CPU-engine correction is
justified by these aggregate results alone. Local raw logs/screenshots remain
ignored under `artifacts/`; only bounded aggregate evidence is recorded here.

## Windows Software comparison — preparation

User authorized the proposed comparison after reviewing the GPU failure.
Rechecked both machines' clean-shutdown provenance and absence of an active
session/isolated process; installed APK still matched the matrix checksum.
Changed only the experimental copy's presentation preference through properties
from GPU to Software (generation 29 to 30), preserving Normal fixed-20k and all
other profile fields. Stable `incoming` remained unchanged.

Started only the experimental copy. Initial telemetry confirms Normal residency,
foreground playing audio and Software requested/active, approximately 15 posts/s.
Startup is not settled acceptance. The user subsequently confirmed desktop
readiness and the three-minute comparison capture completed as recorded below.

## Windows Software comparison — completed capture

Same installed APK, Normal fixed-20k and experimental copy as the GPU capture;
only the presentation preference changed. The 180-second read-only capture
contains 178 complete telemetry intervals totaling 179,544 ms. All intervals
reported Normal configured/current residency, foreground playing audio and
Software requested/active. User activity equivalence remains unconfirmed, so
these are same-configuration diagnostic runs, not a controlled workload pair.

| Normal fixed-20k | GPU | Software |
| --- | ---: | ---: |
| Telemetry duration (ms) | 180,396 | 179,544 |
| Successful posts | 5,373 | 2,709 |
| Presented FPS | 29.78 | 15.09 |
| Presenter CPU us/post | 2,096.67 | 15,534.90 |
| Audio underruns | 605 | 454 |
| Missing audio frames | 89,086 | 68,844 |
| Deadline resynchronizations | 7 | 5 |
| Maximum emulator call (us) | 84,011 | 72,512 |
| Maximum scheduler lateness (us) | 263,695 | 261,972 |

Software also failed audio/timing acceptance. Its queue reached zero; maximum
producer gap was 76,022 us. It recorded zero dropped audio frames, saturation,
stream errors, post failures, graphics errors/fallbacks and presenter/CPU clock
errors. Battery temperature was 26.5 to 27.0 degrees C with Android thermal
status 0 at both endpoints; this does not qualify sustained thermal behavior.

Underruns and timing debt therefore occur without GPU rendering. GPU delivered
roughly twice the posts at substantially lower presenter CPU cost in these
captures, but workload uncertainty and one run per mode prevent attributing the
different audio-failure counts to the presenter. Neither a GPU-only explanation
nor a specific shared scheduler/CPU hot path is established. Windows guest-clock
accuracy and human audio/input judgment remain unverified. DynRec Windows smoke
stays on hold; no code change or further run is automatically authorized.

The capture did not stop Windows. Afterward, the core recorded `guest requested
shutdown` at tablet time 08:08:18.431 and `guest stopped cleanly` at 08:08:18.504.
Follow-up preferences showed no active session and clean-shutdown provenance for
both machines. The experimental copy remains Normal/Software, generation 30;
stable `incoming` remains Normal/Software, generation 1, and was not started or
changed. Repository hygiene and diff whitespace checks passed for this evidence
update. No runtime change, reinstall or push was performed.

### User workload clarification

The user subsequently clarified that they opened windows and then ran Age of
Empires II during the captures. These were mixed desktop/game sessions, not
desktop-idle baselines. Exact launch times, game state and action equivalence
between captures remain unknown, so individual failure intervals cannot yet be
assigned to a game phase. The failures remain valid; the clarification changes
their workload interpretation, not their counts or acceptance result.

## Post-REP DynRec desktop smoke — failed timing, clean shutdown (2026-09-10)

The user authorized one desktop-only check after patch 0008's complete x86 and
unchanged stress gates passed. The installed APK was verified as SHA-256
`2aa66375e32c93cf908227531a2a1f579b248d11d9e9a531ebf658ed9cbbe704`
(`6100586474b1+dirty`, CPU capability `7cfbec67d0f96e79`). Both machines were
stopped and clean before selection. Through the existing Properties UI, only
the experimental copy changed to DynRec fixed-20k / GPU; its settings generation
became 31. Stable `incoming` remained Normal/Software generation 1.

Startup capture began at tablet time 09:30:18; the normal guarded `Start DynRec`
control entered the isolated runner. Initial telemetry confirms configured/current
DynRec, balanced-100-ms scheduling and requested/active GPU. Bounded host-only
evidence is under ignored `artifacts/dynrec-desktop-84hHFd/`. The user confirmed
desktop readiness; the coordinated window-opening start was marked at 09:31:51.
The requested workload was My Computer, Control Panel and ordinary windows, not
AoE2 or a benchmark. Exact individual actions and subjective sound quality were
not reported; desktop readiness alone does not establish either.

Audio starvation was already visible near readiness and recurred during the
desktop interval. The planned three-minute check was therefore ended early after
failure was established; its uncompleted duration is not a pass. To retain the
existing strict parser's 25–60-second bounds, the contiguous complete telemetry
intervals after readiness through 09:33:05 were validated in two chronological
pieces (36,352 and 37,263 ms), then their counts summed. Selection was by time,
not by whether the intervals passed. No benchmark guest result is implied by
reuse of the telemetry parser.

| Desktop interval measure | Result |
| --- | ---: |
| Complete intervals / elapsed ms | 73 / 73,615 |
| Presented FPS | 29.78 |
| Audio underruns / missing frames | 763 / 112,203 |
| Deadline resynchronizations | 9 |
| Maximum emulator call / producer gap us | 72,893 / 80,575 |
| Maximum scheduler lateness us | 265,157 |
| Over-budget calls / catch-up calls | 570 / 1,176 |
| Graphics errors / fallbacks / post failures | 0 / 0 / 0 |

Both pieces validated schema 4, foreground state, configured/current DynRec,
balanced-100-ms timing and requested/active GPU. They had zero dropped or
saturated audio, stream errors, and presenter/CPU clock errors. The audio queue
reached zero. A 49,386-ms subwindow starting about twenty seconds
after readiness still recorded 21 underruns and 2,847 missing frames: the failure
is not confined to the readiness boundary. Independent guest-clock accuracy and
human audio quality remain unverified.

The last residency sample before shutdown showed DynRec 12,767, PageFault 11,
Normal/Halt/Other zero, balanced fault queue 259/259, final depth zero / high-water
one, no wipe, double fault or reset. PageFaultCore had 29,200 balanced calls with
155,752-us cumulative duration, 160-us maximum and no 10-ms slow call. This does
not reproduce sustained PageFault residency or the disposable REP 350-ms stall.
Repeated workload overruns still exhaust the queue; their Windows-path cause
remains unattributed. GPU presentation passing its rate check does not turn the
overall smoke into a pass or prove a CPU-only root cause.

The user ended the test and shut Windows down normally. At tablet time
09:33:38.858 the core reported `guest requested shutdown`, followed by
`guest stopped cleanly` at 09:33:38.908. Postflight confirmed no active-session
marker, DynRec runner or recovery journal. Both profiles have clean provenance:
stable `incoming` remains Normal/Software generation 1; the experimental copy
retains the user-selected DynRec fixed-20k/GPU configuration, generation 31.
No guest force-stop, restore, reinstall, additional trial or push was performed.
T010 is complete as a failed diagnostic with clean teardown; T008's quality
acceptance remains open. Further Windows repetitions are not queued.

## Worker-instrumented desktop check — 2026-09-10

T011 repeated only the authorized desktop check on the already verified worker
diagnostic APK, preserving experimental DynRec fixed-20k/GPU generation 31 and
stable Normal/Software generation 1. The complete 40,358-ms desktop window held
29.76 presented FPS without graphics errors, but recorded 837 underruns, 124,189
missing frames and ten deadline resets. The check ended early for failure.
Overloaded intervals predominantly waited for a CPU-busy emulation worker;
frontend mixing was small. Translation/fallback event counts do not identify a
function-level cause. Workload actions and human sound quality were not confirmed.

Normal Windows shutdown was verified at 10:19:58.873, followed by absent runner,
active marker and recovery journal and clean, unchanged profiles. T011 is complete
as diagnostic evidence; T008 remains open. See the [full timing evidence and
limits](../002-realtime-emulation-audio/worker-diagnostics.md#instrumented-windows-desktop-capture--2026-09-10).
No new runtime change, reinstall, additional Windows trial or promotion is implied.
