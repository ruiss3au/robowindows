# Tasks: Windows 98 Performance Benchmark

- [x] T001 Define the versioned workload, result schema, comparison rules, and
  safety boundary (FR-001–009).
- [x] T002 Build the Win32 CPU, memory, and visible GDI benchmark reproducibly
  from source and package generated media outside Git (FR-001–03).
- [x] T003 Add strict guest-result parser and host telemetry aggregation tests
  for invalid, incomplete, lifecycle-invalid, and redacted inputs (FR-003–05, 008).
- [x] T004 Add the SM-T500 capture/report entry point with core/cycle, FPS,
  audio, decoder, lifecycle and thermal metrics (FR-004–05, 008–09).
- [ ] T005 Run three matched fixed-20k DynRec captures on `incoming - copy` and
  retain the human audio/input/AoE2 and clean-shutdown evidence separately.
- [ ] T006 Run three matched captures at the highest Normal profile meeting the
  same quality gates; compare medians without opening or changing `incoming`.
- [ ] T007 Complete the separate ten-cycle and 30-minute thermal gates before any
  promotion decision (Feature 008 T009–T011). **Out of the current work scope by
  user request, 2026-09-09; untested, not passed.**
- [x] T008 Retire workload v1 after its full-window GDI loop stalled Windows 98.
  Implement workload v2 as a compact, closable, silent utility with phase text,
  off-screen 320×200 GDI work, a calm progress strip capped at ten guest FPS, guest preview
  FPS in the strict result, and a desktop/reference visual check before another
  tablet trial (FR-002, FR-010; scenarios 1 and 5).

T005 and T006 remain open. The first matched run for each profile is valid
diagnostic evidence, but both fail the zero-underrun quality gate. Balanced-100ms
removed those underruns in its first complete pair, while exposing an erroneous
catch-up-cap resynchronization. After correcting that shared policy, Normal
passed its short gate but the matched DynRec start failed in Windows Explorer
before the benchmark. The bounded PageFaultCore correction then permitted a
new complete matched pair: Normal retained zero underruns, while DynRec recorded
12 underruns and 1,862 missing frames despite balanced fault state and no
deadline resynchronization. Median repetition remains deferred; no promotion
claim is available.

After the AV-dispatch correction, one additional DynRec diagnostic run completed
with zero underruns/missing frames/resynchronizations, 15.07 presented FPS,
correct decoder residency and verified clean shutdown. This is not yet a new
matched pair or proof of an intermittent-failure cure: the same-build Normal
comparison, independent guest-clock measurement and separate human quality
checks remain outstanding. T005/T006 stay open; see the latest validation entry.

Current scope update: run the same-build Normal fixed-20k diagnostic comparison.
The user dropped further AoE2/audio, pause/resume, shutdown-cycle and 30-minute
thermal trials from this campaign. Do not schedule them automatically after the
comparison or medians. Missing acceptance evidence remains explicitly incomplete;
this does not block the requested diagnostic run or authorize promotion.

The requested same-build Normal run is now complete with strict guest parsing,
zero underruns/missing frames/resynchronizations, 15.07 presented FPS, correct
Normal residency and verified clean shutdown. The diagnostic pair shows nearly
equal CPU throughput and 4.16 times DynRec GDI throughput. T005/T006 remain open:
this is one run per core, independent guest-clock validation is still missing,
and omitted acceptance checks are not passes. Do not queue the dropped tests.
