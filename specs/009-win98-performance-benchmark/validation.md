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
