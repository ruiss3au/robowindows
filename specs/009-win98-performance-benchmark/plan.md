# Implementation Plan: Windows 98 Performance Benchmark

Build a small Win32 program from reviewed assembly with pinned GNU binutils. It
runs timed CPU and memory phases plus an off-screen GDI phase in a compact,
live-metrics utility window. A small progress strip is rate-limited to ten updates
per guest second; randomized GDI work stays off-screen, the program plays no
sound, and it never flashes the full window. It
writes a bounded text result. Package
it on a deterministic data CD image; never commit the image or executable.

Add host tests for result parsing and telemetry aggregation. A device collector
uses the repository ADB discovery helper, records only bounded aggregate logcat
and thermal data, and emits a redacted report under ignored `artifacts/`. The
first version may require the tester to launch the guest program and retrieve its
result; automation must not simulate a clean Windows shutdown or bypass Feature
008 recovery.

Compare fixed-20k DynRec against the highest Normal profile that independently
passes guest-time and audio gates. Use three matched runs and retain subsystem
metrics; do not compute a promotional verdict from synthetic throughput alone.
Existing trials reject Normal fixed-30k for audio breakup, so fixed-20k is the
current Normal comparison candidate unless new matched evidence rejects it.

Rollback removes only generated benchmark artifacts and collector code. It does
not alter a machine, disk, execution selection, or existing DynRec patches.

## Build and debug strategy

### User-selected diagnostic scope — 2026-09-09

The user requested the same-build Normal fixed-20k comparison but dropped new
AoE2/audio trials, pause/resume repetitions, ten shutdown cycles and the
30-minute thermal run because of the manual effort. Remove those runs from the
current execution queue; retain their acceptance status as untested/incomplete,
not passed. Existing observations remain valid only for their recorded scope.
The alternative of completing the full acceptance campaign now was declined.
Consequently this milestone provides diagnostic subsystem comparisons only,
not a supported-DynRec or sustained-stability claim. Three-run collection remains
conditional on the short comparison; it cannot waive the missing release gates.
Keep DynRec experimental and stable `incoming` untouched. Each actual benchmark
session still requires orderly Windows shutdown and stopped-state verification;
dropping repeated shutdown tests does not permit unsafe guest termination.
Independent guest-clock validation remains in scope. Revisit the omitted gates
only if a later request seeks promotion or broader compatibility claims.

- Pin GNU binutils 2.40 and xorriso 1.5.4; build a PE32 GUI executable requiring
  only Windows 98-era KERNEL32, USER32 and GDI32 APIs.
- Emit the executable, ISO, SHA-256 manifest, linker map and disassembly under
  ignored output. Rebuild twice and require identical executable/ISO hashes.
- Inspect PE version, entry point and imports automatically. Run the binary in a
  disposable Wine/Xvfb prefix as a reference smoke test and strictly parse its
  result before Windows 98 testing. Capture the reference window and reject a
  build that fills or alternates the whole client area.
- Use named phase symbols and the linker map to translate an invalid-page-fault
  address. A failure is attributed to the benchmark only after reproduction in
  the reference run; a Windows-98-only failure is minimized by phase before any
  emulator change.
- On-device debug captures retain bounded phase/result and host aggregate data,
  never guest memory, PCM, framebuffer contents, private paths or raw crash data.
