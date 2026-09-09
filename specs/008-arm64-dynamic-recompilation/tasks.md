# Tasks: ARM64 Dynamic Recompilation

- [ ] T001 Test the execution contract: full fallback migration, per-machine
  clean provenance, prepared/executing/running/closed-clean/needs-check/blocked
  states, late crash, stale callback, pause, hang and user Exit (FR-002–006, 018–021).
- [ ] T002 Implement authoritative profile records, durable attempt journals and
  verified config regeneration. Inject failure at every persistence boundary;
  assert no native start and no dismissal bypass on failure (FR-003, 019, 021).
- [ ] T003 Implement and test Normal/Dynamic selection with fixed 20k policy,
  disabled cycle controls, and safe migration; expose only after T012–T014 pass.
- [ ] T004 Produce a symbolized debug build and reproduce the failure once on a
  verified, cleanly stopped experimental copy. The debug-only action must route
  surface, input, pause and teardown through the isolated runner, never a
  direct native call.
- [ ] T005 Record and symbolicate the redacted native tombstone.
- [ ] T006 Audit ARMV8LE executable-cache, W^X, instruction-cache, branch, and
  block-linking behavior at the pinned core revision.
- [ ] T007 If proven, isolate the minimal fix under `patches/` with provenance.
- [x] T008 Run host tests, repository hygiene, and the Android debug build.
- [ ] T009 Run ten start/stop cycles and CPU, input, audio, pause/resume,
  shutdown, and disk-health acceptance on the experimental copy.
- [ ] T010 Run the versioned benchmark, AoE2 scenario, and 30-minute thermal
  comparison against the highest sustainable normal-core profile.
- [ ] T011 Record promotion or rejection evidence; do not silently expose an
  unvalidated profile to stable machines.
- [ ] T012 Enforce start-time ownership and effective-config checks, including
  writable attachments, utility disks, aliases and debug launch paths. Test with
  stable-role disposable fixtures and unchanged hashes (FR-001, 016–017; SC-001).
- [ ] T013 Isolate dynamic execution in a dedicated process; test surface/input/
  audio lifecycle, child crash/hang, teardown acknowledgement and mode switching
  without reusing dynamic process state for normal guests (FR-020; SC-002–003).
- [ ] T014 Implement needs-check quarantine and document a versioned read-only
  FAT check procedure for every writable volume. Test both pass and reject paths,
  unsupported filesystems and normal recovery boot (FR-007; scenario 4).
- [x] T015 Assert the effective ARM64 decoder and exact profile in bounded
  diagnostics; detect silent interpreter fallback and redact crash artifacts.
- [ ] T016 Prepare versioned CPU correctness and benchmark fixtures before T009
  and T010. Apply SC-003–005 exactly, including matched runs and soak windows.
- [ ] T017 Route all dynamic-session input, cancel, pause/focus and restart
  commands through the child runner. Add bridge-failure handling and verify that
  the host-process native runtime receives none of them (FR-011, FR-022).
- [x] T018 Replace the quarantined-card dialog with direct normal-core recovery
  boot, identify the session as disk-check recovery, and test clean-shutdown
  clearance versus Exit/process-death retention (FR-024; scenario 11).
- [x] T019 Add a readiness gate that records explicit responsive-desktop,
  keyboard and captured-mouse confirmation separately from native liveness.
  Diagnose the current frozen-desktop failure with a freely distributable CPU
  fixture before authorizing another Windows dynrec trial (FR-023).
- [x] T020 Implement the reviewed boot-sector CPU fixture, reproducible source
  build/hash check, disposable normal/dynamic images, isolated fixture services,
  bounded result validation, timeout, and parity report (FR-025–026; scenarios
  14–15). Keep all generated images out of Git.
- [x] T021 Implement and device-validate the multi-sector v2 fixture for paging,
  handled page faults, interrupt/IRET, invalid-opcode recovery, linked control flow and cross-page
  code invalidation. Verify image layout/hashes and invalidate v1 gate evidence
  before any further Windows trial (FR-028–029; scenarios 16–17).
- [x] T022 Add bounded emulator-thread decoder-residency diagnostics and surface
  them in the guarded trial. Test parsing and ensure no address, path, or guest
  content crosses the child bridge (FR-030; scenario 18).
- [x] T023 Add bounded aggregate page-fault lifecycle diagnostics: enqueue and
  completed-return counts, current/high-water queue depth, and wipe/recovery
  counts. Sample and publish them only from the emulator thread; prohibit
  addresses, registers, memory, guest text, media paths, and per-fault records.
  Use the result to refine or reject a source-level hypothesis before another
  Windows dynamic trial (FR-031; scenario 19).
- [x] T024 Guarantee post-result process termination for dynamic trials and both
  CPU-fixture runners. Deliver the final Binder result first, then exit within
  two seconds; verify no isolated process remains and a later run receives a
  fresh PID (FR-032; scenario 20).
- [x] T025 Build and device-validate fixture v3 with deterministic nested
  page-fault entry/return ordering and completion checks in Normal and DynRec.
  Invalidate v2 as the Windows-trial gate and do not alter queue-wipe thresholds
  unless this evidence attributes a minimal correction (FR-033; scenario 21).
- [x] T026 Add bounded exception-lifecycle attribution for fixture v3: prepared
  and delivered page faults, successful page-fault gate entries, aggregate
  `iret` executions, delivered double faults, guest resets, and an allowlisted
  halted decoder class. Reset/sample on the emulator thread, test
  the bridge parser, and use the four-level disposable failure to select or
  reject a minimal correction (FR-034; scenario 22).
- [x] T027 Identify the first failed fixture assertion with bounded source-owned
  identifiers, test result rejection, compare reference/Normal/DynRec, and restore
  the full nested gate before attributing any core correction (FR-035).
- [x] T028 Add INVLPG operand-consumption regressions, record the unpatched
  failure, apply a standalone minimal decoder patch, and pass reference and
  SM-T500 Normal/DynRec with the restored four-level gate (FR-036).
- [x] T029 Reproduce the post-INVLPG `MSOBWEB.DLL` fault's REP-store boundary
  using source-owned media. Check fault-frame instruction position, partial
  count/destination progress, completed memory, and surrounding sentinels when
  REP STOSD reaches a non-present page. Compare reference, Normal and DynRec
  before changing the string helper or queue behavior; preserve all prior gates.
  Record the exact failed assertion, not only a timeout or queue aggregate
  (FR-037).
- [x] T030 Version expanded suites/results and capability token; test bounded
  execution and malformed/missing/duplicate-suite rejection.
- [x] T031 Audit patch 0003 and implement P0 strings/fault cases, including
  uncorrected sibling helpers; reproduce failures before further corrections.
- [x] T032 Implement P1 integer/flags, stack/control flow, paging and SMC cases.
- [x] T033 Implement P2 x87 and 32 bounded fixed-seed programs; retain minimized
  failures and explicit oracle/coverage decisions.
- [x] T034 Run all reference/host/build checks and three complete tablet
  comparisons; verify cleanup and record sanitized results and remaining gaps.
- [x] T035 Add the debug-only fixed-30k candidate with durable journal,
  child-handoff and effective-config agreement, allowlist rejection tests, a
  prominent active-mode/cycle label, and unchanged Normal fallback (FR-039).
- [x] T036 Build/install after a clean 20k stop, run one guarded 30k Windows
  trial on `incoming - copy`, and record AoE2, audio, input, telemetry and clean
  shutdown relative to the 20k baseline without making a promotion claim.
- [x] T037 Implement the named auto-80%-limit-30k policy across journal, config,
  child allowlist and prominent overlay; reject unlimited, unknown and mismatch
  paths without changing fixed 20k or the Normal fallback (FR-040).
- [x] T038 Run host/repository/build and app-private handoff checks, reinstall
  only while stopped, rerun the build-specific CPU gate, then perform one
  user-observed bounded-auto Windows/AoE2/audio trial and record clean shutdown
  or quarantine.
- [ ] T039 Detect 30 seconds without bridge response or completed emulator-call
  progress, excluding pause intervals; quarantine visibly, request orderly
  teardown, and validate both host logic and isolated-process device behavior
  (FR-041; scenario 26).
- [ ] T040 Reproduce same-Activity `Stop trial` with Dynamic still selected,
  make `Needs disk check` atomically restore the journaled Normal fallback before
  native handoff, and device-verify that recovery boots without restarting the
  app while every non-clean exit remains quarantined (FR-024; scenario 11).
- [x] T041 Add bounded page-fault decoder entry/return and cumulative timing
  aggregates, reset/sample them on the emulator thread, extend the strict
  liveness parser tests, and run the disposable Normal/DynRec CPU gate before
  deciding whether another guarded Windows trial is justified (FR-042;
  scenario 27).
- [ ] T042 Reproduce the Windows-only PageFaultCore call amplification in a
  source-owned disposable workload with a deliberately long handler and nested
  fault progression. Compare reference, Normal and DynRec results plus bounded
  aggregates before changing the one-cycle PageFaultCore loop, queue recovery,
  exception delivery, or DynRec execution (FR-033–038, FR-042).

T030–T034 implement FR-038 and are deferred to GPT Sol. See
[coverage](expanded-cpu-coverage.md) and [handoff](sol-handoff.md).

Execution order: T001–002, T012–014, T003, T008, T004–007, T015–016,
then repeat applicable T008 checks and execute T009–011. Keep ordinary dynamic
UI hidden until the safety stages pass; release visibility also requires SC-001–005.

## Implementation progress — 2026-09-08

T001/T002/T012 have begun with a schema-v7 authoritative selection/generation,
per-machine clean-shutdown provenance, atomic `prepared`/`executing` journals,
fresh normal-start ownership checks, and conservative startup recovery. The
host probe covers selection, journal ordering, path-free normal-settings fallback
regeneration and quarantine. This is not completion: fault injection, all state transitions,
read-only health checking, separate-process execution and every device gate
remain open. Dynamic UI and native handoff remain disabled.

T013 has begun with the process boundary: a non-exported `:dynrec` service will
accept only an already-validated dynamic launch file and proxy native lifecycle
and input operations. The next substep is a host-side binder client; binding it
to the session UI, child-death acknowledgement, and disposable-fixture
validation remain open.

T014 has begun with a versioned host checker for raw FAT volumes. It is tested
with pass, filesystem-reject, unreadable-fixture, unsupported-container and
unchanged-image paths. App-side evidence import/unquarantine and real-media
validation remain open.

T001 now has host-testable transition coverage for durable dynamic attempt
states. Wiring liveness evidence, child teardown acknowledgement, fault
injection at storage boundaries, and device validation remain open.

The internal dynamic trial controller owns status-to-journal decisions: only a
guest-shutdown status followed by native stop may close cleanly; every other
runner failure or stop is quarantined. Visible Start wiring remains gated.

Liveness uses bounded aggregate evidence only: elapsed unpaused foreground time,
completed `retro_run` calls, and published video frames. It requires at least
one second, ten calls and two publications before the durable `running` state.

The next fault-injection slice covers the three pre-native persistence points:
prepared journal, dynamic launch publication and executing journal. Each must
leave the normal launch configuration intact and prohibit native handoff.

The first guarded device diagnostic entered the isolated runner and produced
bounded liveness evidence without reproducing the former immediate crash. It
also found a host/child SurfaceView producer conflict; the child view no longer
draws a host waiting frame, pending a fresh trial after health recovery. The
stopped copy remains quarantined, and the app now explains its disk-check state;
T014 remains open because it cannot yet import passing host evidence.

The debug-only normal-core recovery boot is implemented and completed once on
the experimental copy after its user-run guest disk check and normal shutdown.
It is not release UI and does not complete T014's general host-evidence path.

The second guarded dynrec trial produced frames/audio but froze before a usable
Windows desktop and had no working guest pointer. Dynrec remains rejected. T017
and T018 are the immediate corrective implementation slice. T019 then blocks any
further Windows dynrec trial until the input bridge and a non-proprietary CPU
fixture can distinguish guest readiness from mere native liveness.

T017 implementation now routes Activity keyboard, captured pointer, touch,
cancel, pause/focus and restart commands through the active child controller;
bridge errors fail visibly, and status polling continues after the liveness
transition. Android compilation passes; isolated-runner device validation stays
open and no new Windows dynrec run is authorized.

T018 implementation now makes the `Needs disk check` card action enter the
normal-core recovery session directly and labels its toolbar `Disk check`.
Clean recovery closure durably clears the active-session marker as well as the
quarantine journal; other exits keep quarantine. The synthetic store probe
covers the clean-close transition; UI/device confirmation remains open.

T019/T020 use a fixture deliberately independent
of the machine library: separate normal and dynamic service components accept
only app-created cache paths, each gets a fresh image, and neither is allowed to
load a user profile. A passing fixture will not override the separate Windows
desktop/keyboard/mouse readiness gate.

T020 is complete. The reproducibly assembled v1 boot fixture and bounded parser
cover the required test mask; separate normal and dynamic Android processes use
fresh cache images and delete them after each run. The SM-T500 reported exact
`Normal` and `DynRec` decoder identities and matching complete records. T019's
durable readiness mask and three explicit controls are implemented, but T019
remains open because the Windows frozen-desktop cause has not been attributed.

Static comparison with the pinned core identified a material coverage gap:
fixture v1 does not exercise paging/page-fault recovery, interrupt/exception
return, linked translated blocks, or cross-page code invalidation. MMX is
intentionally unavailable in the pinned `pentium_slow` build (`C_MMX=0`) and is
therefore replaced by an invalid-opcode recovery check. T021's host reference
run and isolated SM-T500 normal/dynamic runs passed the complete v2 mask. Device
inspection then caught cached fixture processes caused by a service-destruction
cleanup race. After correcting it, a fresh pass left neither fixture process nor
fixture file behind. This closes T021 but not the unattributed T019 freeze.

T022 is implemented and host-tested. The guarded trial now shows configured and
current decoder classes plus dynrec, page-fault, normal and other sample counts,
and records the same bounded summary every five seconds. Sampling occurs on the
emulator thread and carries no guest address, register, path, memory or content.
The installed build's v2 gate was rerun and passed after this native change.

The third guarded Windows trial reached audio output, then Explorer failed and
the guest displayed a fatal VWIN32 exception. The runner stayed controllable and
Stop trial quarantined the experimental copy; the stable machine was untouched.
DynRec residency stopped advancing at 1462 samples while PageFault residency
continued beyond 12,000, with Normal and Other both zero. This narrows T019 to a
sustained PageFault-decoder sequence but does not attribute its internal cause.
T023 is the next implementation slice; no further Windows dynrec trial is
authorized until its bounded lifecycle evidence is available.

T023 instrumentation was implemented before its evidence run. The pinned core
now counts page-fault enqueue/completed-return, current/high-water depth and unique
wipe/recovery transitions without changing the queue algorithm. RoboWindows
resets and samples it on the emulator thread and appends only those aggregates
to the existing bounded status. Host tests, patch reversibility, pinned-source
verification, repository hygiene and the Android build passed. The installed
APK's disposable v2 fixture passed in Normal and DynRec, and its isolated
processes exited. The quarantined experimental disk was not opened by that
fixture check; recovery and one guarded evidence run were still required.

That guarded evidence run is now complete. It reached a responsive desktop,
passed explicit keyboard and captured-mouse confirmation, kept audio usable,
and shut down normally. All 1,349 observed fault enqueues had completed returns,
depth returned to zero with a high-water of four, and no wipe occurred. T023 is
complete: the earlier sustained PageFault state is not a deterministic startup
queue leak, but its VWIN32 failure remains unexplained and T019 stays open.

T024 implementation is installed. A post-install fixture run passed in Normal
and DynRec, delivered its result, and left neither isolated fixture process
alive; only the main app process remained. Dynamic-trial process exit uses the
same process-owned terminator but still needs direct device confirmation after
a later clean guest shutdown, so T024 remains open.

The next guarded start reproduced the failure before the desktop. Fault
enqueues/returns moved from 775/774 at depth one to 1591/1586 at depth five,
with a high-water of seven, while DynRec residency stopped advancing and
PageFault residency continued. Stop trial quarantined the experimental copy,
and the updated dynamic process terminated within three seconds. This completes
T024's process-exit observation and shows unreturned nested page faults during
the freeze; it does not identify their initiating cause. T025 blocked further
Windows dynamic runs pending a reproducible CPU regression.

Fixture v3 is source-built and passes the host reference emulator. On the
SM-T500, Normal passes its complete four-level nested-fault record. DynRec passes
reduced two- and three-level variants but the restored four-level
gate does not write a complete result. Its failure snapshot reports no recursive
page-fault-queue activity and only the collapsed other/special decoder class.
Those reductions did not establish a minimum failing nesting depth: a later
layout-preserving three-level variant also failed. A single level-four fault
completed but failed its return-count assertion. That failure led to the
INVLPG operand-decoding defect and a direct pre-paging regression (T027/T028).

T025–T028 are complete. Uncorrected DynRec fails the direct INVLPG test at
assertion 11 while Normal passes. The isolated one-helper-call correction passes
the complete expanded fixture in reference QEMU and both SM-T500 cores, including
all four nested faults. The normal interpreter and queue algorithm are unchanged.
T019 and the Windows correctness/performance gates remain open. Normal-core
recovery subsequently completed cleanly and the first corrected guarded Windows
trial is running, pending explicit physical-input/audio and shutdown checks.
That trial subsequently displayed an `Msoobe` illegal-operation dialog when the
user opened Internet Explorer; one queue wipe/recovery occurred while DynRec
continued. T019 remains open. Capture the expanded fault details, then establish
a safely authorized Normal comparison or targeted fixture before attributing
another emulator correction. The user subsequently stopped the trial; the runner
exited and the experimental copy remains quarantined. T029 then reproduced the
wrong saved instruction address in a disposable REP STOSD fault. Patch 0003
passes that reference/Normal/DynRec regression and host helper checks. Windows
confirmation remains unperformed; T030–T034 now precede any further trial.
Warm-Activity diagnostic requests failed to start a runner before a cold host
launch succeeded; that launch-path issue remains unattributed and must be
investigated without treating it as a guest CPU failure.

T030–T034 are complete. Protocol v4 divides the expanded source-owned gate into
seven stable suites and accepts exactly one Normal and DynRec record per suite,
plus the preserved v3 record. Parser/report tests reject malformed, incomplete,
unknown, missing and duplicate records. A build-specific capability token is
written only after exact full-report parity. Cold and already-running Activity
requests now enter the same guarded controller exactly once.

The P0 audit first reproduced two independent DynRec failures: fault/retry case
1 assertion 2013 identified the wrong saved instruction address on REP MOVSB;
after generalizing precise checked string accesses, case 4 assertion 204 showed
that supervisor writes ignored CR0.WP. Patches 0004 and 0005 isolate those
corrections to DynRec string exception handoff and DynRec supervisor write
protection/error attribution. Normal execution and the page-fault queue policy
remain unchanged. P0 strings and retry cases then passed.

P1 and P2 cover the specified integer/flags, sign/zero extension, signed and
unsigned divide, stack/control, paging/SMC, x87 and 32 fixed-seed mixed paths.
Each mixed program follows the committed bounded rule and combines call/return,
REP copying, handled invalid opcode and code modification/re-entry. The fixed
x87 arithmetic-status limitation described in the coverage document remains an
explicit gap; the gate checks its architecturally stable masked result instead
of claiming unsupported exception-status fidelity.

On 2026-09-09, all host tests, the seven-suite QEMU 7.2.22 reference run,
repository hygiene, pinned-source reconstruction, diff whitespace validation
and the ARM64 Android debug build passed. The finalized APK then passed three
consecutive complete SM-T500 Normal/DynRec comparisons: one cold launch and two
already-running Activity launches. Both isolated processes were absent after
the final result, and neither disposable fixture file nor cache root remained.
No Windows disk was opened. Stable `incoming` was untouched; `incoming - copy`
remains quarantined and still requires normal-core recovery before any separate,
user-authorized Windows trial.

T019 is complete after the post-gate Windows trial. The user reported the
desktop test successful and exercised Internet Explorer and AoE2; Windows then
shut down normally. The isolated runner exited, its attempt journal was removed,
and the experimental card returned to `Start`. Final telemetry remained in
DynRec with balanced fault enqueue/return, zero depth, no wipe, double fault or
reset. Two clean 20k cycles have now passed, not T009's ten-cycle stability
gate or T010's matched benchmark and thermal comparison.

T035–T036 are complete. Journal, child request and effective config carry an
allowlisted 20k/30k value; mismatches and arbitrary values fail before native
start, and the overlay names the active candidate. A clean 30k trial improved
AoE2 graphics subjectively but introduced audible cracking, so it fails the
audio gate. The 20k DynRec baseline and saved Normal fallback are unchanged.

T037–T038 are complete. Bounded auto is a named allowlist policy rather than
free-form config; the journal, child and sole config line must agree. Synthetic,
host, full CPU-gate and device handoff checks passed. The user found both
graphics and audio unsatisfactory, so bounded auto is rejected. Shutdown was
clean, telemetry and temperature showed no CPU correctness or throttling event,
and fixed 20k remains the diagnostic baseline.

T039 implementation closes the previously missing 30-second progress watchdog.
Host tests cover a silent bridge, status replies without completed emulator-call
progress, sustained progress, pause/resume and counter reset on restart. Timeout
durably quarantines the attempt and requests orderly child teardown before the
host disconnects. Device timeout injection and the broader unfinished T001/T013
matrix remain open; this test does not claim those gates complete.
