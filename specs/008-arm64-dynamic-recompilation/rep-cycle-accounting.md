# REP element cycle accounting — 2026-09-10

FR-045 / T045–T046 follow the [phased stress finding](../002-realtime-emulation-audio/timing-investigation.md#phased-stress-reproduction-and-rep-accounting-finding).
The pre-correction DynRec run produced 27,035 underruns, 4,852,605 missing frames,
318 deadline resynchronizations and a maximum 351,528-us emulator call. Normal
passed the identical source-owned image. This is a disposable real-mode
reproducer, not a Windows/Age of Empires II correctness or performance claim.

## Attribution and correction

The host regression extracts all 18 production MOVS/LODS/STOS helpers from the
pinned, patched DOSBox Pure checkout; it does not duplicate their implementation.
Before patch 0008, every helper failed the 4,096-element / 20,000-cycle assertion:
all work completed but no element cycles were charged. Exhausted negative budgets
also permitted unintended progress. UBSan then rejected backward word operations
for shifting a negative signed direction.

Patch `0008-dynrec-rep-cycle-accounting.patch` reserves the bounded element budget
in both branches, refunds uncompleted elements on a checked fault and returns
without accessing memory when the budget is nonpositive. Multiplication replaces
negative signed shifts when scaling direction. It retains the existing generated
count/EIP exception handoff and opcode overhead; it changes no Normal code,
PageFaultCore policy, scheduler, audio target, presentation, cache or block linker.
The patch retains the upstream GPL-2.0-or-later license and pinned revision.

The regression now passes under fail-fast UBSan for all helpers, both directions,
zero count, fits/exact/limited/zero/negative budgets (including INT32_MIN), large
counts, source and destination first/middle/final faults, completed-prefix cycle
charges, retry suffixes, load values, and 16/32-bit index wrapping. Existing STOSD
fault tests remain in the host suite. The host memory stubs do not model paging;
the unchanged expanded device gate supplies the actual page-fault regressions.

## Verification

Full host tests passed, including all eight reference x86 suites and the unchanged
stress image in QEMU 7.2.22 (630 ticks, phase mask 15, all phase counts positive).
Pinned-source reconstruction, repository hygiene, whitespace checks and Android
debug build passed. Installation occurred only after stopped-machine checks.
The disposable core/input/lifecycle check with a surface passed, followed by the
complete Normal/DynRec x86 gate. The DynRec `0x0103` suite retained 21/21 fault
returns, final depth zero / high-water four, no wipe, and 16,408 balanced
PageFaultCore calls; cumulative/max duration was 101,855/16,925 us.

Installed build: `6100586474b1+dirty`; CPU capability revision:
`7cfbec67d0f96e79`. APK SHA-256:
`2aa66375e32c93cf908227531a2a1f579b248d11d9e9a531ebf658ed9cbbe704`.
The build-specific capability was invalidated and earned again, not reused.

## Unchanged stress comparison

Both source-owned runs used fixed-20k, balanced-100-ms and GPU presentation. The
stress image remains SHA-256
`816fe821f70ea4ff3929fd2281a1ee47a2487ba9f59e43278ebfad61bd7cd3b1`;
neither the workload nor its acceptance parser was changed for this comparison.

| Measure | Normal | Corrected DynRec |
| --- | ---: | ---: |
| Host / guest elapsed ms | 122,021 / 121,605 | 122,059 / 121,605 |
| Validated phase mask | 15 | 15 |
| Strict settled window ms | 108,253 | 108,259 |
| Settled presented FPS | 29.76 | 29.77 |
| Presenter CPU us/post | 3,070.67 | 3,394.87 |
| Entire telemetry capture ms | 121,380 | 121,364 |
| Underruns / missing frames / resynchronizations | 0 / 0 / 0 | 0 / 0 / 0 |
| Maximum emulator call us | 32,800 | 18,935 |
| Maximum audio producer gap us | 46,569 | 28,725 |
| Maximum scheduler lateness us | 26,434 | 6,079 |
| Strict result | Pass | Pass |

Both captures also have zero dropped audio, stream errors, post failures,
graphics errors/fallbacks and presenter clock errors throughout, not just in the
settled window. Runtime timing diagnostics validate with zero CPU clock errors.
The independently checked guest clock is within 0.4% in both modes, and all four
phase counters are positive. Corrected DynRec's 18,935-us maximum call replaces
the prior 351,528-us maximum; its repeated memory-phase starvation is no longer
reproduced. This is one short matched pair, not medians or thermal qualification.

Both fixtures unloaded and removed their disposable images/launch files and
presentation directories. Postflight found no isolated runner, active-session
marker or recovery journal. Both real machines retained clean provenance and
unchanged selection/generation: stable `incoming` Normal/Software generation 1;
experimental copy Normal/Software fixed-20k generation 30. No Windows machine
was booted and no user disk was opened by these checks.

T045/T046 and Feature 002 T070 are complete. No Windows trial is queued by this
correction. The separate Normal/Windows/AoE2 starvation remains unresolved and
the user's omitted long/manual promotion gates remain untested. Keep DynRec
experimental; rollback is removal of patch 0008 and its build registrations,
never replacement or restoration of a guest disk.
