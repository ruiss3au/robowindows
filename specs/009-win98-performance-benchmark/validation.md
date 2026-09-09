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
