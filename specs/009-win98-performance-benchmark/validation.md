# Validation: Windows 98 Performance Benchmark

## Source/build checks — 2026-09-09

The source-owned workload builds with GNU binutils 2.40 as a PE32 i386 GUI
program targeting Windows subsystem/OS version 4.0. Its imports are limited to
named KERNEL32, USER32, GDI32 and WINMM functions available to the intended
guest. Xorriso 1.5.4 packages the executable and instructions as a deterministic
ISO 9660 image. Builds separated in time produced byte-identical EXE and ISO
files. Generated binaries, images, maps, disassembly and checksums remain under
ignored `artifacts/`.

A disposable Wine/Xvfb reference run completed the three 10-second phases and
produced a strictly valid schema-1 record. The strict parser accepted it and
rejects incomplete, wrong-version, malformed and arithmetically inconsistent
fixtures. This is a program smoke test, not a performance baseline for the
SM-T500 or proof of Windows 98 compatibility.

The schema-2 telemetry summarizer passes 30-interval foreground input and rejects
a paused interval or configured/observed core mismatch. It exposes raw CPU,
memory, GDI, frame, audio and thermal metrics without a composite score. The
device capture, guest result retrieval, three-run comparisons and long gates
remain pending.

`scripts/test-host.sh`, repository hygiene, diff whitespace validation and the
ARM64 Android debug build pass with the watchdog, schema-2 decoder fields,
benchmark builder/parser and capture tooling. No benchmark artifact or guest
media is tracked. The resulting app build has not yet been installed for the
device phase.
