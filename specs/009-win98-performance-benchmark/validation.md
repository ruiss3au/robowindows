# Validation: Windows 98 Performance Benchmark

## Source/build checks — 2026-09-09

The source-owned workload builds with GNU binutils 2.40 as a PE32 i386 GUI
program targeting Windows subsystem/OS version 4.0. Workload v2 imports only
named KERNEL32, USER32 and GDI32 functions available to the intended guest;
WINMM and every sound path were removed. Xorriso 1.5.4 packages the executable
and instructions as a deterministic ISO 9660 image. Builds separated in time
produced byte-identical EXE and ISO files. Generated binaries, images, maps,
disassembly and checksums remain under ignored `artifacts/`.

A disposable Wine/Xvfb reference run completed workload v2's three 10-second
phases and produced a strictly valid schema-2 record. Live captures showed the
compact window updating CPU ops/ms, memory KiB/ms, off-screen rectangles/s and
preview FPS. The only visible GDI output was a steadily advancing narrow progress
strip at 9 FPS. A separate reference run sent Escape during the CPU phase; the
program exited with its explicit abort status and wrote no completed result.
The strict parser accepts the v2 result and rejects incomplete, wrong-version,
malformed, arithmetically inconsistent and over-10-FPS preview fixtures. This is
a program/UI smoke test, not a performance baseline for the SM-T500 or proof of
Windows 98 compatibility.

The schema-2 telemetry summarizer passes 30-interval foreground input and rejects
a paused interval or configured/observed core mismatch. It exposes raw CPU,
memory, GDI, frame, audio and thermal metrics without a composite score. The
device capture, guest result retrieval, three-run comparisons and long gates
remain pending.

`scripts/test-host.sh`, repository hygiene, diff whitespace validation and the
ARM64 Android debug build pass with the watchdog, schema-2 decoder fields,
benchmark builder/parser and capture tooling. No benchmark artifact or guest
media is tracked.

Clean local revision `7e6e2e8` was installed without clearing app data after
verifying both machine cards at `Start`, no quarantine action, no dynamic
attempt journal and no isolated runner. The installed UI reports that revision
without `dirty`. Its complete disposable x86 gate then passed in Normal and
DynRec; all fixture and DynRec processes exited, and both machines remained
stopped. No machine disk was opened.

The retired workload-v1 ISO SHA-256 was
`16ed5f2b46844cc2df6fb95428a1b92c98e93f0cc762421438f89b88f25174a4`.
It must not be executed again. Workload v2 has executable SHA-256
`831cf0d828bdea11b37d952beac9f819e97cb71aa58d08fdf59168de89217d8b`
and ISO SHA-256
`c49fb6ff193e76f46f6259eab3b3adb8a193c3d1456f05df7a2687a753433b0b`.
The v2 image has not been copied to the tablet, and no further guest trial is
authorized by this reference evidence alone. Stable `incoming` remains untouched.

## Workload v1 rejection — 2026-09-09

The first fixed-20k DynRec execution exposed a benchmark defect before any valid
comparison was accepted. Workload v1 alternated a full 640×400 window using
`PatBlt`; on Windows 98 that repaint path stalled guest-clock progress, flashed
the display, remained in its nominal ten-second GDI phase for minutes, and could
not be closed conveniently. The tester had to use `Stop trial`, after which the
experimental disk followed Feature 008 quarantine and completed a Normal
recovery shutdown. Stable `incoming` was not opened.

The incomplete capture is rejected: it has no guest result and cannot be used as
performance evidence. Workload v1 must not run again. T008 replaces it with a
silent compact window, off-screen 320×200 work, a calm progress strip capped at ten guest FPS,
and an effective Close path. A desktop/reference visual check is required before
new media reaches the tablet.

## Workload v2 Windows 98 smoke test — 2026-09-09

The clean committed app build `712be4b3a417` was installed with app data
preserved while both machines were stopped. Both cards remained at `Start`, and
the main panel showed no `dirty` suffix. The retired v1 ISO previously staged by
the project was hash-verified and removed from Android Downloads; reproducible v2
ISO `c49fb6ff193e76f46f6259eab3b3adb8a193c3d1456f05df7a2687a753433b0b`
was staged as `RWBENCH-v2-c49fb6ff.ISO`.

The user ran v2 from `D:` on `incoming - copy` under its Normal fallback and
reported that the live-metrics interface looked excellent. The result screen
showed `complete=1`: CPU elapsed 10,000 ms, 29,741,056 operations and 2,974
ops/ms; memory elapsed 10,000 ms, 108,672 KiB and 10 KiB/ms; GDI elapsed 10,004
ms, 36,219 off-screen 64×64 rectangles and 3,620 rectangles/s; the calm progress
preview produced 99 updates at 9 FPS. The screenshot is retained only under
ignored host artifacts.

The user then shut Windows down normally. Device inspection found both cards at
`Start`, no dynamic-attempt journal, no active-session marker and only the main
app process. Stable `incoming` was not opened. Because telemetry capture did not
start before program launch and the run used Normal from `D:`, this is UI and
Windows 98 compatibility evidence only. It is not one of the three matched
Normal runs or any DynRec performance result. V2 must be copied to `C:` during a
clean Normal session before a guarded DynRec run can use it.

## First matched fixed-20k pair — 2026-09-09

The first complete matched pair used app version `0.1.0-dev-debug` with installed
APK SHA-256
`725857fc4d8f1afe6794f7d8660ea408baefc338935d53272dbbfebc81866fd1`.
Both bounded captures ran on the SM-T500 against `incoming - copy`, used workload
v2, retained complete strict schema-2 guest records, stayed foreground, observed
the declared decoder throughout, reported no stream or surface-post failures,
and completed clean Windows shutdowns. Device inspection after each run found
both cards at `Start`, no active attempt or session residue, and stable
`incoming` untouched.

| Metric | DynRec fixed 20k | Normal fixed 20k |
|---|---:|---:|
| CPU operations/ms | 3,198 | 3,187 |
| Memory KiB/ms | 12 | 10 |
| Off-screen GDI rectangles/s | 15,061 | 3,614 |
| Guest phase elapsed | 10,000 / 10,000 / 10,000 ms | 10,000 / 10,000 / 10,000 ms |
| Presented FPS | 15.08 | 15.07 |
| Audio production, frames/s | 47,728.95 | 47,971.87 |
| Audio underrun callbacks | 121 | 19 |
| Audio missing frames | 17,088 | 2,662 |
| Audio drops / stream errors | 0 / 0 | 0 / 0 |

CPU throughput is effectively equal, memory is close at this resolution, and
DynRec makes the bounded GDI phase about 4.2 times faster. Neither result is
promotion evidence because the settled audio gate requires zero underruns and
zero missing frames. T004 is complete because capture, strict result merge,
decoder/lifecycle validation, thermal collection, and reporting all operated as
specified. T005 and T006 remain open: one run is not a three-run median, and
repeating a known scheduling failure would add no promotion evidence.

The next change belongs to Feature 002's experimental-only balanced-100ms
scheduler and schema-3 telemetry. Stable `incoming` must retain its existing
runtime path. A new single matched pair must pass audio, timing, presentation,
decoder, lifecycle, resynchronization, and clean-shutdown gates before the
remaining median runs or longer acceptance gates resume.

## Balanced-100ms diagnostic pair — 2026-09-09

The first balanced pair used installed debug APK SHA-256
`24a88e84818c1b4ad47470dbafc8633a0774fccb55150c2fa6a934f4e8799018`
on `incoming - copy`. Both strict guest records completed in exactly three
10,000 ms phases, both stayed above 15 presented FPS with correct decoder
residency, and both Windows sessions shut down cleanly. The benchmark is silent,
so human audio quality and physical-input quality are recorded as `not_tested`
rather than inferred from counters.

| Metric | DynRec fixed 20k | Normal fixed 20k |
|---|---:|---:|
| CPU operations/ms | 3,221 | 3,218 |
| Memory KiB/ms | 12 | 10 |
| Off-screen GDI rectangles/s | 15,091 | 3,621 |
| Presented FPS | 15.10 | 15.06 |
| Maximum `retro_run()` | 71,980 us | 64,339 us |
| Maximum scheduler lateness | 136,852 us | 53,879 us |
| Catch-up calls | 58 | 23 |
| Deadline resynchronizations | 1 | 1 |
| Audio queue maximum | 11,683 frames | 5,485 frames |
| Audio underruns / missing frames | 0 / 0 | 0 / 0 |

This proves the 100 ms prebuffer/fixed-deadline direction removed the original
121/19 underruns and 17,088/2,662 missing frames. It also exposed an implementation
error: the scheduler discarded debt after 20 consecutive calls even though
neither core approached the specified 250 ms clamp threshold. The relevant
resynchronization intervals had only 15.522 ms DynRec and 51.805 ms Normal
lateness. Feature 002 now treats 20 calls as a bounded burst followed by a
cooperative yield while retaining debt; only debt above 250 ms resynchronizes.
These two captures remain diagnostic quality failures and do not count toward
the three-run medians.

## Corrected scheduler recheck and DynRec boot rejection — 2026-09-09

After host tests, repository hygiene, pinned-source verification and the ARM64
build passed, corrected debug APK SHA-256
`7fa5fab7096b75c34db3dacf89cf856e6bf3c632392767701bad78cfffbeff45`
was installed while both machines were stopped. The complete disposable x86
gate passed in Normal and DynRec, then removed its temporary processes and files.

The new Normal fixed-20k capture completed with a strict schema-2 record: CPU
elapsed 10,001 ms, 32,163,984 operations and 3,216 ops/ms; memory elapsed
10,000 ms, 109,376 KiB and 10 KiB/ms; GDI elapsed 10,000 ms, 36,215 rectangles
and 3,621 rectangles/s; preview remained 99 frames at 9 FPS. Across 55 foreground
schema-3 intervals it presented 15.08 FPS, produced and consumed approximately
48 kHz, held 3,720–5,141 queued frames, and recorded zero over-budget calls,
catch-ups, resynchronizations, underruns, missing frames, drops, stream errors,
or surface-post failures. Windows shut down cleanly.

The matched DynRec start was rejected before workload launch. Windows displayed
an Explorer illegal-operation dialog and the shell terminated. Bounded telemetry
showed configured DynRec with extended `PageFault` residency, `retro_run()` up
to 105.527 ms, scheduler lateness above 250 ms, repeated legitimate deadline
resynchronizations, and repeated audio underruns/missing frames during the fault
episode. This differs from the shared cap defect: the corrected Normal path
passed, while DynRec alone crossed the specified debt clamp and failed Windows
startup. No DynRec benchmark result was collected.

The user selected `Stop trial`; RoboWindows stopped the isolated worker and
marked only `incoming - copy` as `Needs disk check`. The copy then completed its
Normal recovery boot and clean Windows shutdown. Final device inspection found
two `Start` actions, no quarantine, no DynRec worker and no attempt journal.
Stable `incoming` was never opened. Per Feature 002 T063, benchmark/AoE2/median
and long-gate repetitions stop here pending investigation of the measured DynRec
page-fault hot path.

## Post-PageFaultCore matched pair — 2026-09-09

After Feature 008 T043/T044 passed, one new matched fixed-20k pair ran against
only `incoming - copy` with installed APK SHA-256
`25a0e64ff3872b3ead65d491dfd0559213559ab4e3fb4d198e785592f7abe5ca`.
Normal ran first and shut down cleanly before the guarded DynRec attempt began.
The tablet remained awake with RoboWindows foregrounded for both 40-second
captures. Workload v2 completed three exact 10,000-ms phases in each run, both
strict guest records parsed successfully, declared and observed decoders
matched, lifecycle validation passed, and neither run reported a stream error,
dropped audio frame, saturated sample, surface-post failure, or nonzero thermal status.

| Metric | Normal fixed 20k | DynRec fixed 20k |
|---|---:|---:|
| CPU operations/ms | 3,204 | 3,193 |
| Memory KiB/ms | 10 | 12 |
| Off-screen GDI rectangles/s | 3,615 | 15,048 |
| Presented FPS | 15.09 | 15.05 |
| Maximum `retro_run()` | 23,915 us | 42,228 us |
| Maximum scheduler lateness | 9,721 us | 112,503 us |
| Catch-up calls | 6 | 72 |
| Deadline resynchronizations | 0 | 0 |
| Audio queue min/max | 3,932 / 5,814 frames | 0 / 6,882 frames |
| Audio underruns / missing frames | 0 / 0 | 12 / 1,862 |

DynRec CPU throughput was effectively equal to Normal, memory retained its
coarse 12-versus-10 KiB/ms result, and GDI was 4.16 times faster. The new
PageFaultCore slice eliminated the prior startup failure: the final bounded
sample showed 214 balanced fault enqueues/returns at depth zero, 58,031 balanced
PageFaultCore entries/returns, a 195-microsecond maximum call, and no slow-10-ms
call, wipe, double fault, or reset.

The pair nevertheless fails the strict promotion-quality gate. Normal had no
audio underrun or missing frame, while DynRec's queue reached zero and recorded
12 underrun callbacks with 1,862 missing frames during the benchmark. The
benchmark is silent, so human audio and the separate physical-input procedure
remain `not_tested`; they are not inferred from counters. Both sessions ended
through Windows BIOS APM shutdown, followed by `guest requested shutdown` and
`guest stopped cleanly`. Postflight found no isolated process, active-session
marker, or dynamic-attempt journal. Stable `incoming` was never opened.

This pair confirms that sustained PageFaultCore residency is no longer the
benchmark blocker. It does not justify the remaining T005/T006 median runs:
DynRec still fails zero settled underruns/missing frames and the user separately
reported somewhat laggy graphics in AoE2. Further work should measure the
remaining DynRec workload spikes and presentation path before repetition; the
ten-cycle, pause/resume, thermal, and promotion gates remain open.

## AV-dispatch diagnostic DynRec run — 2026-09-09

Following the shared AV callback correction and experimental timing extension
in commit `3ac681b`, one fixed-20k DynRec run completed on `incoming - copy`.
The installed APK SHA-256 was
`78eaaba73b2db6fb593ef653a9647c4d1d44cb7972dc3e78b39b59b22452d471`.
Desktop, keyboard and captured-mouse readiness were explicitly confirmed in the
attempt journal (mask 7) before capture. The tablet stayed awake and foregrounded.
The 55-second collector retained 53 valid intervals covering 53,480 ms; all
3,746 calls had matching valid timing-extension records, without CPU-clock errors.

| Metric | DynRec fixed 20k |
|---|---:|
| CPU operations/ms | 3,211 |
| Memory KiB/ms | 12 |
| Off-screen GDI rectangles/s | 15,031 |
| Guest phase elapsed | 10,000 / 10,000 / 10,000 ms |
| Presented FPS | 15.07 |
| Maximum `retro_run()` / over-budget calls | 36,126 us / 14 |
| Maximum audio-producer gap | 36,301 us |
| Maximum scheduler lateness / catch-up calls | 60,697 us / 31 |
| Deadline resynchronizations | 0 |
| Audio queue current / min / max | 5,199 / 1,124 / 5,503 frames |
| Audio production / consumption | 48,025.86 / 48,025.13 frames/s |
| Audio underruns / missing / dropped frames | 0 / 0 / 0 |

The visible completion dialog was transcribed and passed the strict schema-2
parser. CPU work was 32,112,640 with integrity `8C017B1D`; memory work was
127,360 KiB with integrity `AEEBE000`; GDI work was 150,313 rectangles with
integrity `4D9F3634` and a 99-frame, 9-FPS guest preview. Declared and observed
decoders remained DynRec. Lifecycle validation passed; saturation, stream errors
and surface-post failures were zero. Battery temperature rose from 26.5 to
27.0 degrees C and Android thermal status remained zero at both endpoints.
The final capture residency sample retained 285/285 completed faults, depth
zero/high-water two, no wipes, and 43,328/43,328 PageFaultCore entries/returns
with 218,341 us cumulative time, 703 us maximum, and no slow-10-ms call, double
fault or reset. Those fault counters were unchanged during the capture.

The user closed the benchmark and shut down through Windows. Postflight verified
BIOS APM power-off, `guest requested shutdown`, `guest stopped cleanly`, and no
active-session preference, dynamic-attempt journal or isolated guest process.
Stable `incoming` was not opened. The final report and bounded observations are
retained under ignored host artifacts.

This run clears the measured short-capture audio, decoder, presentation and
lifecycle checks. It does not establish that the intermittent underrun is cured
or that the AV callback defect caused it. No Normal comparison exists on this
APK yet. The silent benchmark leaves human audio `not_tested`; readiness checks
do not replace the complete physical-input procedure. Independent guest/host
clock accuracy was not measured: three guest-timed ten-second phases cannot
prove it. The finalizer therefore reports `short_run_quality=fail` for incomplete
acceptance evidence, not a newly observed audio failure. T005/T006 and Feature
002 T062 remain open pending a same-build matched pair with the missing quality
checks; GPU/FPS work and stable promotion remain deferred.

## AV-dispatch same-build diagnostic pair — 2026-09-09

The subsequent Normal fixed-20k run completed on `incoming - copy`, using the
same installed APK hash as the instrumented DynRec run above. Before starting,
device inspection verified no active session, attempt journal or isolated child;
the copy's settings showed normal CPU, selected 20k cycles and sound enabled.
No rebuild, reinstall or stable-machine start occurred. The user confirmed a
settled desktop before the 55-second foreground capture and launched workload v2.
This is a sequential diagnostic pair on the copy, not three-run medians or a
claim that both runs began from an identical restored disk snapshot.

| Metric | Normal fixed 20k | DynRec fixed 20k |
|---|---:|---:|
| CPU operations/ms | 3,202 | 3,211 |
| Memory KiB/ms | 10 | 12 |
| Off-screen GDI rectangles/s | 3,613 | 15,031 |
| Presented FPS | 15.07 | 15.07 |
| Maximum `retro_run()` / over-budget calls | 31,313 us / 4 | 36,126 us / 14 |
| Maximum audio-producer gap | 31,412 us | 36,301 us |
| Maximum scheduler lateness / catch-up calls | 21,836 us / 16 | 60,697 us / 31 |
| Deadline resynchronizations | 0 | 0 |
| Audio queue min / max | 3,094 / 5,646 frames | 1,124 / 5,503 frames |
| Audio underruns / missing / dropped frames | 0 / 0 / 0 | 0 / 0 / 0 |

Normal retained 55 valid intervals covering 55,601 ms, 3,894 completed calls,
correct Normal residency, valid lifecycle and matching timing-extension records
with no CPU-clock errors. Production/consumption were 48,018.90/48,019.86 frames/s;
the final queue was 5,455 frames. Saturation, stream errors and surface-post
failures were zero. Battery temperature was 27.5 degrees C at both endpoints,
with Android thermal status zero. These bounded readings are not a thermal soak.

The visible completion dialog passed strict schema-2 parsing after transcription:
CPU 10,000 ms, 32,022,528 operations, integrity `60B20D5A`; memory 10,006 ms,
109,120 KiB, integrity `AEEBE000`; GDI 10,000 ms, 36,132 rectangles, integrity
`F4EF105C`, preview 99 frames at 9 FPS. Independently replaying the source CPU
loop for the observed work count confirmed its integrity value. The user then
closed the benchmark and shut down through Windows. Postflight verified the
Normal process's BIOS APM request, `guest requested shutdown` and `guest stopped
cleanly`, with no active-session preference, attempt journal or isolated child.
The finalized report and bounded observations remain in ignored host artifacts.

For this pair, CPU throughput differs by only 0.28%; the coarse memory result
is 12 versus 10 KiB/ms and DynRec's off-screen GDI throughput is 4.16 times
Normal's. Both captures meet the measured zero-underrun/missing-frame/resync,
decoder, presentation and lifecycle checks and both shut down cleanly. This is
encouraging diagnostic evidence, not proof that an intermittent failure is cured
or that GDI throughput increases game or presented FPS.

Independent guest-clock accuracy was not measured. Guest-timed phase lengths
alone do not satisfy that gate. The silent workload and lack of the full input
procedure leave human audio and Normal physical-input quality `not_tested`, so
the conservative finalizer retains `short_run_quality=fail` for incomplete
acceptance evidence, not a newly observed runtime failure. T062 and T005/T006
remain open. Per the user's narrowed scope, no further AoE2/audio, pause/resume,
shutdown-cycle or 30-minute thermal trials are queued. DynRec stays experimental;
stable `incoming` is untouched and no promotion claim is made.
