# Implementation Plan: ARM64 Dynamic Recompilation

## Approach

Build the safety envelope first, reproduce and symbolicate the current ARM64
failure second, make a minimal pinned-core correction only if the cause is
proven, then measure correctness and performance on the experimental copy.

The normative [execution contract](contracts/execution-safety.md) governs
transaction ordering and failure behavior. Its separate emulator process is
required because the current core retains process-global state between sessions.
This preserves the narrow Android/JNI boundary but requires explicit child-process
surface, input, audio-focus and lifecycle plumbing. Process death must leave the
machine library usable; a same-UID process is not a malicious-code sandbox.

Use `dynamic fixed 20000` initially and compare against `normal fixed 20000`
as a controlled experiment as well as the sustainable normal baseline. Extend
LaunchConfig's current normal-only fixed-cycle validation specifically for this
named experimental policy; retain rejection of arbitrary policy combinations.
Confirm the actual decoder with aggregate diagnostics. If the pinned build
cannot run this combination, report a failed capability gate and revise the
spec explicitly before substituting another policy.

After the first clean Windows 20k trial, admit fixed 30k only as a second
debug-diagnostic candidate. Carry the selected cycle value in the durable
attempt record and verify it at every host/child/config boundary. Keep 20k as
the validated baseline, leave the saved Normal fallback unchanged, label the
active candidate prominently, and reuse the same failure quarantine. Compare
30k against 20k before adding any ordinary settings control.

If fixed 30k improves graphics but cracks audio, test one bounded automatic
policy: `auto 80% limit 30000`. Represent it by a project-owned policy ID, not
free-form configuration. Derive the exact config independently on both sides of
the process boundary, reject unlimited/unknown variants, and keep it diagnostic
only. The core's auto controller targets execution time rather than audio
underruns, so audio remains an explicit acceptance observation.

Persist selection independently from execution. A single versioned machine
record is authoritative; launch files are regenerable. Per-attempt journals
remain active until confirmed teardown, and a full normal snapshot supplies
fallback. Keep migration tests for stable profiles and per-machine clean state.

## Delivery Stages

1. **Recovery state** — implement and fault-test the execution contract, media
   ownership, full fallback, persistence failures, and per-machine clean state.
   Add a non-exported `:dynrec` service which accepts only a validated dynamic
   launch description, then verify surface, input, audio-focus and teardown
   bridging with disposable fixtures before exposing dynrec.
2. **Diagnostic reproduction** — produce a symbolized debug build, confirm the
   experimental disk and clean shutdown, reproduce once, and preserve only a
   redacted tombstone and configuration evidence. The debug-only diagnostic
   action must use the production journal and separate-process bridge; it must
   not add a direct native-start escape hatch.
3. **Readiness and bridge correction** — route keyboard, captured mouse, touch,
   input cancellation, pause/focus and restart exclusively to the isolated
   runner. Treat frames/audio as liveness only; require a responsive Windows
   desktop plus explicit keyboard and pointer confirmation before continuing.
   Make the quarantined-card action enter the normal-core recovery session
   directly so the guest can run its disk check.
4. **Disposable CPU fixture** — assemble a reviewed 1.44 MB boot image from
   repository source, copy it to app-private cache for each run, and execute the
   normal and dynamic variants in different non-exported Android processes. The
   boot code writes a fixed result sector after real-mode, x87, self-modifying,
   string and protected-mode checks; the app validates version, mask and checksum
   and compares both modes. Neither service accepts a machine path.
5. **Windows-path CPU fixture** — replace v1's single-sector test with a
   source-built multi-sector v2 fixture. Its protected-mode stage covers paging
   and handled page faults, interrupt/`iret`, invalid-opcode recovery, repeated
   linked branches, and cross-page self-modifying code. Keep a dedicated
   non-overlapping result
   sector and invalidate all v1 capability evidence.
6. **Root-cause analysis** — audit the pinned ARMV8LE dynrec path, executable
   cache permissions, cache flushing, branches, and block linking. Sample only
   bounded decoder classes on the emulator thread so a later guarded hang can
   distinguish dynrec execution from page-fault or interpreter residency without
   collecting guest addresses or contents. If PageFault residency is sustained,
   instrument only aggregate enqueue, completed-return, current/high-water depth,
   and wipe/recovery counts at the pinned queue implementation. Reset and sample
   those counters on the emulator thread and expose them through the existing
   liveness record; do not alter page-fault control flow while diagnosing it.
   After each final result, terminate the isolated Android process on a short
   process-owned timer so Android cannot retain native core globals in a cached
   service process. Result delivery and the durable disk decision precede exit.
   Reproduce any nested-fault hypothesis first in a source-built fixture with
   ordered entry/return validation in Normal and DynRec. Do not tune the queue
   wipe watchdog as a substitute for correct execution. For a reproducible
   fixture failure, add aggregate prepared/delivered page-fault,
   double-fault and guest-reset counters plus an allowlisted halted decoder
   class. Use those counters to guide an instruction-level regression before
   changing exception or reset behavior; counters cannot attribute it alone.
   If a later failure shows sustained PageFault residency without a failing CPU
   fixture, time the PageFault decoder with cumulative entry/return, total/max
   duration and fixed 10 ms slow-call aggregates. Carry them through the same
   bounded liveness record; do not collect per-call or guest-derived data and do
   not alter execution behavior until a reproducible regression identifies it.
   Reproduce call amplification in a stable expanded-gate suite with long,
   sequential and four-level nested fault handlers. Keep device timing out of
   the guest result; use exact functional records for correctness and the
   bounded host aggregate only to characterize the execution policy.
7. **Minimal correction** — isolate a proven fix under `patches/`; do not change
   guest media, UI ownership, input, audio resources, or stable profiles. For
   the reproduced PageFaultCore amplification, replace the one-instruction
   decoder invocation with a fixed maximum of 64 full-core instructions. Stop
   immediately on `iret`, recheck the top fault before restored execution, keep
   cycle/watchdog accounting in guest-work units, and leave queue thresholds and
   every DynRec translation/cache/link path unchanged. Roll back the standalone
   patch if exact fixture parity, queue balance, a 16-fold call reduction,
   three-fold cumulative-duration reduction, or the 50-ms inclusive-call bound
   fails. The 10-ms counter remains diagnostic because an outer call includes
   synchronous work performed by its nested faults.
8. **Experimental UI** — offer one named `Dynamic (experimental)` option only
   on experimental machines, with recovery messaging and normal-core fallback.
9. **Validation** — run correctness first, then benchmark/AoE2 performance and
   thermal soak. Reject the profile if any correctness or real-time gate fails.

## Attributed correction and regression

The identified `dyn_grp7` INVLPG path reads ModR/M but omits effective-address
decoding. Correct it using the existing non-reading `dyn_fill_ea` helper before
the existing TLB clear. Keep this correctness change in its own patch; do not
alter interpreter, exception recovery, privilege policy, or queue watchdogs.
First run the operand-consumption regression without the correction, then with
it on SM-T500 Normal and DynRec. A fixture pass does not establish that every
Windows crash is resolved.

For attribution, first expose fixed fixture assertion identifiers through its
existing checksummed result record, validate against the reference emulator,
and compare Normal/DynRec on disposable images. Restore the complete four-level
gate after diagnostic reduction. Do not infer a root cause from aggregate fault
counts or variant nesting depths; require an identified incorrect operation.

## Rollback

If the bounded PageFaultCore slice fails exact fixture parity, queue balance,
cycle accounting, watchdog preservation, or its device reduction bounds, remove
only patch `0007-bounded-pagefault-core-slice.patch`, rebuild the prior one-cycle
core, and invalidate the build-specific CPU capability token. Do not roll back
or replace any guest disk; no Windows trial is authorized from a failed slice.

Next delivery is T030–T034 from `expanded-cpu-coverage.md`; execution notes are in
`sol-handoff.md`. Implement protocol, P0, P1, P2 and full-gate verification in that
order. Reuse isolated runners and split images into bounded suites. Preserve all
regressions and require a minimized failure before any additional core change.
Broader coverage precedes another Windows trial and makes no performance claim.

T029's first fixture reports assertion 21 only under DynRec: the handler sees
the translated block's earlier instruction address instead of REP STOSD. The
minimal correction will make STOSD's 16/32-bit address helpers use checked writes,
return their remaining count before delivering the fault, and enter dynrec's
existing precise exception path. Preserve completed destination updates and
leave ECX untouched for non-REP forms. Keep this in patch 0003; other string
operations remain an explicit audit gap, not implicitly fixed by this patch.

The post-INVLPG Windows application failure requires a separate REP-store/fault
regression (T029). Keep the corrected INVLPG patch unchanged while locating the
first bad partial-progress or retry result in source-owned media. Compare against
the normal interpreter and reference emulator before altering string execution.
Do not install over the live failed Windows trial. Prefer guest shutdown; if it
cannot finish, require user-authorized Stop trial, verify the runner has exited
and quarantine remains, then use disposable-only diagnostics without opening
the quarantined disk. The REP fixture checks one fault after four of eight stores
and preserves a fixed first-failure identifier before completing its result.

Disable the experimental profile and restore its last-known-safe normal-core
configuration. Revert any isolated core patch. Never restore or replace a guest
disk automatically; retain the stable machine and its disk unchanged.

## REP cycle-accounting correction (FR-045)

The unchanged phased stress fixture passes Normal but produces repeated 350-ms
DynRec calls during memory work. Extracted production STOSD helpers reproduce
4,096 completed writes with zero element cycles charged from a 20,000 budget.
First add failing tests across all 18 MOVS/LODS/STOS helpers, both directions,
fits/exact/limited/exhausted budgets, zero counts, first/middle faults and retry.
Reserve the bounded slice's cycles before execution and refund its uncompleted
elements on checked faults. Guard nonpositive budgets before unsigned conversion.
Use multiplication for signed direction scaling, retaining defined backwards
execution. Keep the generated exception/count handoff and opcode charge intact.

Deliver a standalone pinned-core patch 0008; include it in source reconstruction
and the build-specific CPU capability fingerprint. Verify host/UBSan, pins,
hygiene, Android build, stopped-only install, full disposable x86 gate and the
unchanged fixed-20k Normal/DynRec GPU stress pair. Keep Windows machines stopped.
If correctness regresses, remove only patch 0008 and its registrations, rebuild
and invalidate capability; never restore disks. If stress still fails, stop and
investigate the measured remaining cause. A disposable pass does not resolve
the separate Normal/Windows/AoE2 starvation or waive promotion gates.

## Dependencies

- Feature 007 supplies independent experimental machine disks and selected
  profile UI.
- Feature 005 supplies orderly guest-shutdown and session lifecycle behavior.
- Feature 002 supplies telemetry, benchmark, timer/audio, and thermal gates.

Feature 008 owns the dynamic implementation and supersedes Feature 002's Phase 7
delivery tasks; that feature remains the source of measurement gates. Benchmark
completion blocks performance claims, not safe diagnostic reproduction. Existing
Feature 007 restrictions remain the default except for the explicit named dynamic
profile and recovery contract defined here.

## Verification and rollback gates

On 2026-09-09 the user narrowed the current campaign to benchmark diagnostics,
declining further AoE2/audio, pause/resume, ten-cycle and 30-minute thermal runs
because of manual effort. These checks are removed from the active queue, not
marked passed or waived for release. The risk is unmeasured sustained audio,
lifecycle and thermal stability; contain it by retaining experimental-copy-only
access and making no promotion claim. The full campaign was declined for now;
revisit its missing gates before any later promotion request. Normal shutdown
and stopped-state verification still apply to every actual guest run. See
Feature 009's user-selected diagnostic scope for the execution decision.

Use fault injection at each journal/config publication boundary, including full
storage and malformed records. Assert native load was never called when a gate
failed. Use independent stable-role fixture hashes for isolation, not a real guest
boot. Then test dynamic-to-normal and normal-to-dynamic transitions in fresh
processes, late crashes, hangs, process death, stale callbacks, pause and shutdown.
Review shared-core patches against normal execution before installing for trials.

The fixture source is authoritative. Its build script pins the expected GNU
binutils major/minor and verifies the boot loader, protected-mode stage and
complete image layout/hashes before packaging; the generated image stays in
ignored build output. A source/hash mismatch, stage overflow or overlap fails
the build. Fixture result files are bounded and contain no device or guest data.

Keep the prior APK on the Debian host. Rollback disables dynamic capability and
selects normal snapshots without deleting media. An older APK must not be installed
over newer profile metadata unless downgrade compatibility has been verified;
otherwise provide a forward recovery build. No history rewrite or disk rollback.
