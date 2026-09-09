# Validation: ARM64 Dynamic Recompilation

No dynamic execution is authorized or claimed by this specification alone.

## Existing evidence

- The pinned ARM64 dynamic path has previously terminated during startup; the
  failure has not yet been reproduced with symbols or attributed.
- The experimental machine has an independent writable disk and a normal-core
  fallback.
- The 30k normal-core AoE2 trial looked faster but broke up audio, motivating an
  execution-efficiency investigation rather than further unbounded cycle increases.

## Pending

- Full fault-injection coverage for crash recovery and fallback persistence.
- Symbolized SM-T500 root cause on a verified experimental copy.
- Correctness parity, benchmark improvement, clean audio, and thermal soak.

## Specification review — 2026-09-08

Adversarial findings are addressed by FR-016–021, SC-001–005 and the execution
contract: launch-boundary enforcement, durable pre-load marking, persistent
late-crash detection, explicit cycles, complete fallback, attachment ownership,
failed-recovery blocking, return-to-Normal UI, transactional ordering and media
quarantine. Dedicated dynamic processes also address retained native core state.
All implementation/device tasks remain pending. Separate processes reduce
accidental state sharing; they do not make arbitrary native bugs harmless.

## Safety-envelope implementation — 2026-09-08

The diagnostic-only safety envelope now persists schema-v7 execution selection
and configuration generations; per-machine clean shutdown provenance no longer
uses the former single global marker. `prepared` is durably recorded before
dynamic launch publication, then `executing` before any future native handoff.
An interrupted executing record restores the complete normal launch profile and
quarantines the experimental disk; ordinary Start is disabled until the separate
read-only health-check task is implemented. The dynamic UI and native handoff
remain unavailable.

Host checks and repository hygiene passed, and the Android debug build compiled
on 2026-09-08. No guest was booted, modified, or installed during this work.

## Synthetic recovery probe — 2026-09-08

The debug APK was installed without clearing data and its `testPersistence`
entry point passed on the SM-T500. The log reported `persistence and input
bridge probes passed`. This creates and removes only synthetic files in the
debug package's private storage; it does not boot a guest or access either
machine profile. The probe includes the dynamic prepared/executing journal,
path-free fallback, stale handoff rejection, pre-native persistence fault
injection, liveness/quarantine transitions, and the existing input bridge.

This is not dynrec execution evidence. The dynamic UI remains hidden pending
the remaining recovery, health-evidence, and symbolized diagnostic gates.

## First guarded dynrec diagnostic — 2026-09-08

With the user-confirmed stopped experimental copy, the debug-only diagnostic
entered the dedicated `:dynrec` process and ran for approximately fifteen
seconds without a Java or native crash. Bounded telemetry showed continuous
emulator calls, audio production and video publication (roughly 54–60 completed
calls and frames per second). This disproves the previously reported immediate
startup termination for this exact build/profile, but is not a correctness or
performance pass.

The host SurfaceView had painted its waiting frame before the child process
attached, producing repeated BufferQueue producer conflicts and zero presented
frames. The diagnostic view now avoids that host-side paint and keeps its Stop
trial control visible; it needs a fresh trial after media-health recovery.
Because the diagnostic was stopped without a verified Windows shutdown, its
experimental disk is conservatively quarantined. The visible `Needs disk check`
control now explains that state instead of being inert. In-app import of the
read-only health-check evidence remains incomplete, so no further guest start
is authorized from that copy in this milestone.

## Recovery boot result — 2026-09-08

The same quarantined experimental copy then used the explicit debug-only
normal-core recovery boot. It rendered and played audio normally, including
bounded presentation telemetry with no surface post failures. The user ran the
guest disk check and performed Windows' normal shutdown. RoboWindows observed
the shutdown, unloaded the normal runner, and returned to Machines with the
copy's ordinary `Start` action restored. The stable machine was not started or
modified. This is recovery evidence for that copy, not a dynrec correctness or
performance result.

## Second dynrec diagnostic — 2026-09-08

After the SurfaceView correction and a clean normal-core recovery shutdown, a
second guarded dynamic trial produced presented frames and audio without the
former SurfaceView errors. Windows nevertheless remained frozen before a usable
desktop, and the guest pointer did not move. The isolated runner was stopped
and its experimental disk was quarantined again. This rejects dynrec as a
usable profile for the current pinned build; frame/audio liveness is explicitly
insufficient as a readiness or correctness signal. Do not expose the profile or
make a performance claim. The next implementation slice must bridge all input
through the child runner and establish a guest-readiness diagnostic before any
further trial.

Code review found the pointer symptom's host-side cause: the SurfaceView sent
captured mouse events to the child, but Activity-level keyboard, touch, input
cancel, pause/focus and restart paths still addressed the host-process native
runtime. This split bridge could silently discard commands and is now tracked by
FR-022/T017. It does not explain or excuse the desktop freeze; that remains a
dynrec correctness failure, and T019 blocks another Windows trial until a
freely-distributable CPU fixture and explicit readiness gate exist.

## Pinned-core audit — 2026-09-08

The pinned `dosbox-pure` ARM64 build selects `C_DYNREC` and `ARMV8LE` for
`arm64-v8a`; its Android makefile also selects the `arm64` dynarec path. The
ARMV8 emitter explicitly performs data-cache clean, barriers, instruction-cache
invalidation and an instruction barrier whenever it closes generated blocks.
The shared dynrec cache still requests writable-and-executable memory through
`mprotect`, rather than a strict write/execute transition. That is an upstream
behavior, not a RoboWindows change, and this feature must not paper over a
failure by weakening Android executable-memory protections. The next diagnostic
step remains a symbolized reproduction on the experimental copy. Its
decoder-name helper is hidden inside the pinned core library, so effective
decoder evidence requires a minimal, isolated upstream patch after that
reproduction; no core patch is justified from this static audit alone.

## Corrective implementation after second diagnostic — 2026-09-08

Review confirmed two host integration defects. Activity-level keyboard, touch,
input cancellation, pause/focus and restart commands bypassed the isolated
runner, and controller polling stopped after the journal reached `running`.
Those paths now remain on the child bridge for the full attempt, bridge errors
fail conservatively, and the diagnostic toolbar remains visible.

The quarantined machine card now starts the explicit normal-core recovery boot
instead of showing an informational dialog. The recovery toolbar identifies the
disk-check purpose. A clean recovery close now records clean shutdown, clears
the stale active-session marker, and only then removes the quarantine journal;
an incomplete close remains blocked. Host tests, repository hygiene,
`git diff --check`, and the Android debug build passed. This is implementation
evidence only: no guest was booted, and the dynrec desktop freeze remains an
unresolved correctness failure blocked by T019.

The corrected debug APK was then installed over the existing debug app without
clearing its data. The SM-T500 synthetic persistence/input probe passed. That
probe used only disposable private test files and did not boot or access either
Windows machine. The installed UI is ready for the user-authorized normal-core
recovery boot; its actual Windows disk-check and shutdown result is still pending.

The user completed that recovery boot, Windows disk check, and normal Windows
shutdown. Device inspection confirmed the experimental card returned to `Start`
and `Needs disk check` disappeared. It also exposed a presentation-only defect:
the earlier recovery banner remained in the Activity's in-memory state. The
successful close path now clears that banner before rebuilding the machine list.

## Disposable CPU fixture — 2026-09-08

RoboWindows now builds an operating-system-free 1.44 MB boot image from reviewed
assembly source using GNU binutils 2.40 and verifies the boot-sector SHA-256 at
build time. Each diagnostic creates fresh app-cache images and runs the normal
and dynamic configurations in separate non-exported Android processes. The
services accept no machine ID, profile, path, media, or command. They stop the
native core before parsing a 22-byte versioned result record and delete their
temporary files afterward.

The first SM-T500 attempt failed safely before native execution because the app
loader expected a 512-byte resource while the build correctly packaged the full
1.44 MB image. The loader was corrected to require and copy exactly 1,474,560
bytes. A subsequent run passed both modes. After adding an explicit read-only
decoder diagnostic export, the final observed run again passed and reported the
configured decoders as `Normal` and `DynRec`; silent interpreter fallback was
therefore not responsible for this fixture result. No machine profile or user
disk was opened.

This narrows, but does not resolve, the Windows failure. The fixture passed its
16-bit integer/flags, 32-bit integer/flags, x87, string, self-modifying-code, and
protected-mode cases, while the earlier Windows dynamic run still failed the
responsive-desktop gate. A durable per-attempt readiness mask now keeps desktop,
keyboard, and captured-mouse confirmation separate from liveness and fixture
success. Another Windows dynrec trial is not justified until analysis targets
instructions or state transitions exercised by Windows startup but absent from
fixture v1.

## Upstream comparison and fixture-v2 decision — 2026-09-08

The official `dosbox-pure` repository HEAD resolves to the same revision pinned
by RoboWindows, so there is no later upstream commit to evaluate as a candidate
fix. The pinned core already contains its published dynamic-core page-fault and
Win9x control-flow corrections. Its `Force Normal Core in OS` option defaults to
off, and RoboWindows' diagnostic also observed the exact `DynRec` decoder name.
The frozen desktop therefore remains an unattributed correctness failure rather
than evidence of an obsolete checkout or silent normal-core fallback.

Fixture v1 is not deep enough to clear that failure: its protected-mode case runs
with paging disabled and does not cover handled faults, interrupt/exception
return, linked blocks, or page-boundary invalidation. The pinned core compiles
MMX out and the selected `pentium_slow` model does not advertise it, so v2 tests
invalid-opcode recovery instead of pretending MMX is an available guest feature.
FR-028–029 and T021 define a
multi-sector v2 diagnostic for those paths. The v1 pass remains useful historical
evidence but is no longer sufficient to authorize a Windows dynamic trial.

## Disposable CPU fixture v2 — 2026-09-08

Fixture v2 uses a 512-byte loader in sector 1, an eight-sector reserved stage
range in sectors 2–9, and a result record in sector 18. The current stage occupies
922 bytes of its 4096-byte range. The pinned build verifies the loader, padded
stage, and complete 1.44 MB image SHA-256 values and rejects stage overflow or
result overlap before packaging. Its result version is 2 and its required mask
is `0x07ff`, so the prior v1 preference cannot satisfy this build's gate.

A host reference execution under QEMU completed with the exact mask, completion
marker and checksum. The rebuilt debug APK was then installed over the existing
app without clearing data. With the tablet unlocked and RoboWindows foregrounded,
the SM-T500 diagnostic passed in both isolated processes. The service's decoder
checks required exact `Normal` and `DynRec` identities, and the records matched
for the original cases plus software interrupt/IRET, invalid-opcode recovery,
repeated linked control flow, handled paging fault with CR2/error validation, and
cross-page self-modifying-code invalidation. No Windows machine was selected or
booted and neither machine disk was opened.

This removes several plausible CPU-path gaps but still does not explain the
frozen Windows desktop. T019 remains open, and another Windows dynamic run stays
blocked until the remaining static audit identifies a narrower hypothesis or a
symbolized diagnostic is prepared to capture the failure rather than merely
repeat it.

The first v2 device pass exposed a cleanup race: service destruction removed the
scheduled process-kill callback, leaving both dedicated runners cached after
their results were returned. They had no machine-loading interface, but this did
not meet the throwaway-process contract. The service now retains its final kill
callback during intentional teardown. A rebuilt, reinstalled APK passed v2 again,
after which device inspection found neither fixture process nor any fixture file.

## Decoder-residency diagnostic — 2026-09-08

The exact configured decoder alone could not show whether the running core spent
its time in dynrec, normal fallback, page-fault handling, or another special
decoder. The native runner now samples only that decoder class after each
completed emulator call and publishes aggregate counts plus the current class.
The guarded screen displays the bounded summary and logs it at five-second
intervals. No instruction address, register, memory, path, or guest-derived text
crosses the process boundary.

Host parsing tests and the Android build passed. The final APK was installed
without clearing data, then its build-specific v2 normal/dynamic fixture gate
passed again with fixture-process cleanup confirmed. No Windows guest was booted.
This instrumentation prepares one evidence-producing guarded reproduction; it
does not itself complete T019 or establish dynrec readiness.

## Third guarded dynrec diagnostic — 2026-09-08

The user-authorized trial used only the cleanly recovered experimental copy.
Windows initially produced audio, then Explorer failed and the guest displayed
a fatal exception in VWIN32. RoboWindows itself remained responsive: its Stop
trial control returned to Machines, terminated the dedicated dynamic runner,
and conservatively restored `Needs disk check`. The stable machine was neither
started nor modified.

Decoder residency materially narrows the failure. Early execution alternated
between DynRec and PageFault, but the DynRec sample count then stopped at 1462
while PageFault residency rose continuously beyond 12,000 samples. Normal and
Other remained zero. During the same interval the guest stopped making useful
desktop progress and audio telemetry reported persistent underruns before the
fatal exception. These are residency samples, not a count of guest page faults,
so the evidence establishes correlation with a sustained PageFault decoder
state but does not yet identify the faulty queue entry, return path, or paging
operation.

The result rejects the current dynamic profile again and keeps T019 open. The
single handled-fault case in fixture v2 is insufficient to model the sustained
Win9x fault sequence. Before another Windows trial, add bounded aggregate
diagnostics for page-fault enqueue, completed return, queue depth/high-water and
wipe/recovery behavior. Do not export addresses, register values, memory, guest
text, media paths, or per-fault records.

The T023 instrumentation is now implemented at the pinned queue boundaries. It
does not change page-fault control flow. Reset and snapshot run on the emulator
thread; the existing liveness record carries only aggregate enqueue/completed,
current/high-water depth, and wipe/recovery values. Host parsing, patch
reversibility, pinned-source verification, repository hygiene, and the Android
debug build passed. The APK was installed without clearing app data, and the
disposable fixture v2 passed in both Normal and DynRec with both isolated
processes gone afterward. Neither Windows disk was opened by that check.

The experimental copy remains quarantined from the failed Windows run. A normal
recovery boot, guest disk check, and normal Windows shutdown are required before
the one evidence-producing T023 Windows trial. T023 and T019 remain open until
that result is collected and attributed.

## Fourth guarded dynrec diagnostic — 2026-09-09

After the user completed normal-core disk recovery and a normal Windows
shutdown, device inspection showed the experimental copy clean and eligible.
One guarded dynamic run reached the Windows desktop, produced clean audio, and
passed explicit desktop, keyboard, and captured-mouse confirmations. Windows
then shut down normally and RoboWindows returned to Machines with the copy still
eligible. The stable machine was not started or modified.

The page-fault lifecycle remained balanced throughout the observed run. The
final visible aggregate was 1,349 enqueues and 1,349 completed returns, current
depth zero, high-water four, and zero wipe/recovery transitions. DynRec residency
continued advancing beyond 17,000 samples; Normal and Other remained zero. This
does not reproduce the prior sustained PageFault state and disproves a
deterministic startup fault-queue leak for this recovered starting state. It
does not yet explain the earlier VWIN32 failure or constitute soak/performance
promotion evidence.

Post-shutdown inspection found the clean Machines state but also found the
dedicated dynamic process retained as an empty cached process. A prior fixture
process was likewise retained. No guest remained active and the disk decision
was already durable, but retained processes violate the fresh-process contract.
FR-032/T024 block another dynamic run until final-result delivery is followed by
verified process termination.

The lifecycle correction replaces the service-main-loop delayed exit with a
process-owned non-daemon exit thread after result delivery and also schedules
exit on unexpected service destruction. The rebuilt APK passed host checks,
repository hygiene, pinned-source verification and Android compilation, then
was installed without clearing app data. A fresh disposable fixture v2 run
passed in Normal and DynRec; after result delivery, device process inspection
found neither isolated fixture process and only the main app remained. No
machine disk was opened. This validates the shared termination mechanism for
the fixture services; T024 remains open until a later clean dynamic-trial stop
directly confirms the `:dynrec` process also disappears.

## Fifth guarded dynrec diagnostic — 2026-09-09

The post-fix process-isolation check reproduced the Windows failure before a
responsive desktop. Aggregate fault lifecycle evidence first showed 775
enqueues and 774 completed returns at current depth one. Five seconds later it
showed 1,591 enqueues and 1,586 completed returns at current depth five with a
high-water of seven. During that interval DynRec residency stopped at 1,275
while PageFault residency continued from 23 to 304. Normal and Other remained
zero and no queue wipe occurred.

RoboWindows stopped the trial immediately, returned to Machines, quarantined
the experimental copy, and the updated dedicated dynamic process disappeared
within three seconds. This directly closes T024 for the dynamic runner as well
as the already-verified fixture runners. The stable machine was not started or
modified.

Unlike the earlier residency-only failure, this evidence shows nested faults
remaining unreturned during the freeze. It does not yet show
whether the first incorrect state comes from translated exception entry,
handler execution, or return. The existing v2 fixture covers only one handled
fault and cannot decide that boundary. FR-033/T025 require a deterministic
nested-fault fixture before any page-fault watchdog or control-flow change. The
experimental disk requires normal-core recovery again before reuse.

## Fixture v3 nesting boundary — 2026-09-09

Correction: the depth-based conclusions below are superseded. A layout-preserving
three-level reduction also fails; a single level-four fault completes but reports
mask `0x07ff` under DynRec while Normal passes. Aggregate counters do not identify
the first incorrect instruction, exclude double-fault conditions the core does
not detect, or establish the Windows failure's root cause. T027 will identify
the failed assertion before any emulator behavior change. No Windows disk is
opened during this work.

The source-built v3 image passes its complete four-level nested page-fault
record in the host reference emulator and in the SM-T500 Normal runner. On the
same installed build, DynRec passes complete two- and three-level variants but
does not produce a complete record with four levels. The final packaged gate was
restored to four levels. Each isolated process terminated after its result or
timeout, and no machine profile or disk was opened.

The four-level DynRec timeout reported configured DynRec, zero recursive
page-fault-queue activity, and only the collapsed other/special decoder class.
This did not locate the faulty instruction or prove a depth boundary.
FR-034/T026 added bounded exception-lifecycle counters and an allowlisted halted
class; T025 remained open at that point.

The first instrumented four-level run logged the pinned core crash twice as an
illegal descriptor type while delivering interrupt 14. Its final bounded
snapshot showed 814 prepared and 814 delivered page faults, zero double faults,
zero halted samples, and one guest reset. After correcting all checked-page
paths to participate in the same aggregate counter, a second run reproduced the
same crash and the same 814/814, zero, zero, and one values. Both isolated
processes terminated and neither machine disk was opened.

This showed repeated exception-delivery attempts and no sampled recursive queue
activity or halt. It did not exclude undetected double-fault conditions or prove
correct handler execution. Stack growth toward the IDT was an explanation for
the later invalid gate, not an observed first incorrect operation. No Windows
run was authorized from this evidence.

A temporary fixture with the same four nested faults but reversed CR2 dispatch
order passed the host reference and reproduced the identical SM-T500 DynRec
failure: 814 prepared/delivered page faults, no double fault or halt, one reset,
and the same invalid interrupt-14 gate crash. Reversing dispatch did not resolve
the failure; it did not exclude every branch-related defect. The canonical
fixture order and hashes were then restored.

## INVLPG operand-decoding correction — 2026-09-09

The reduced single-level-four run completed with mask `0x07ff`, assertion 3
(incorrect handler return count), and two prepared/delivered/entered page faults.
Inspection of pinned `dyn_grp7` showed that INVLPG consumes the ModR/M byte but
never calls the effective-address decoder. SIB/displacement bytes are therefore
decoded as subsequent instructions. The interpreter's Group 7 path does consume
the address. This is a concrete dynrec correctness defect, not a nesting limit.

A focused regression executes `invlpg [0x40404040]` before enabling paging. Its
four displacement bytes encode `INC EAX` if treated as opcodes. Before correction,
SM-T500 Normal passed; DynRec completed with mask `0x01ff`, assertion 11, and
zero page faults or resets. After adding `dyn_fill_ea(FC_ADDR)` before the existing
TLB clear, the same image passed both cores, including the restored four-level
fault chain. No queue threshold, recovery policy, normal interpreter, or machine
configuration was changed. The correction is isolated in patch 0002.

Final coverage checks absolute disp32, base/disp8, SIB/disp32, and 16-bit absolute
addressing, register and flag preservation, plus all prior fixture tests. Readback
pages are explicitly initialized. The expanded mask is `0x1fff`; fixed assertion
identifiers occupy the checksummed reserved result word, and any nonzero value
fails the gate. Source/mask/core-patch changes invalidate prior capability tokens.

The final image passes QEMU 7.2.22 on the Debian host and the installed SM-T500
Normal/DynRec comparison with identical complete records. Host tests, repository
hygiene, source-pin verification, Android debug build, and `git diff --check`
pass. Both patches also apply against the pristine pinned source index and pass
reverse checks against the working dependency. Regenerating patch 0001 preserved
upstream trailing whitespace that its earlier hand-edited form had lost; no
additional upstream behavior was changed by that regeneration.

Final generated image SHA-256:
`7b33146120a437a92fd5d36238d98a88d8bbf027f202da6ab2ee942619f239ad`.
Installed debug APK (`c75946b7288f+dirty`) SHA-256:
`d008aef2dcebfbee3f99baaedaa1e7edc4eb9cfa5ba910d6f618eae13468e45e`.

The tablet was awake and the diagnostic foregrounded. The final APK passed three
consecutive Normal/DynRec comparisons. Fixture processes exited after every run
and their cache directory was empty after completion. Neither Windows machine
was booted or its disk opened. This closes the demonstrated fixture defect, not
Windows stability: `incoming - copy` remains quarantined pending normal-core
disk-check recovery, followed by a separately authorized guarded Windows trial.

## First post-INVLPG Windows trial — 2026-09-09 (guest application failure)

The user reported completion of the experimental copy's normal-core recovery.
The runtime logged BIOS APM power-off, guest-requested shutdown and clean stop;
the machine list showed Start and the copy's durable clean-shutdown marker was
present. The installed build retained its passing expanded CPU-fixture gate.

The initial warm-Activity diagnostic requests did not create a runner or attempt
journal, and the copy remained Normal at generation seven. Their cause is not
yet attributed. After confirming no runner or journal, restarting only the idle
Android host and using the guarded cold-launch entry started the experimental
trial. No safety marker was removed or bypassed. A subsequent UI dump could not
obtain idle state; its old XML was not treated as current-screen evidence.

The corrected child confirmed decoder DynRec and produced non-silent audio.
Successive aggregate samples advanced DynRec residency from 1,534 to 2,576 and
fault enqueue/return counts from 961/961 to 1,035/1,035. Current queue depth was
zero, high-water two, with no queue wipe or guest reset. These are liveness
observations, not Windows correctness or audio-quality confirmation.

The tablet must remain unlocked with RoboWindows foregrounded. The trial is
left running for the user's desktop, physical keyboard, captured-mouse and
audio checks. Clean trial shutdown and longer stability remain unverified.
Stable `incoming` was not started. No screenshot was retained; screen capture
was blocked by the approval reviewer, so verification used bounded app metadata,
UI labels and aggregate diagnostics.

The user subsequently authorized screen recording. A local, untracked screenshot
then confirmed that the Windows desktop and a Notepad window were displayed;
no screenshot or guest text was added to the repository. DynRec residency
continued advancing, with 1,693/1,693 queued faults returned, current depth zero,
high-water two, and no wipe or reset. A still image does not establish working
physical input or audible sound quality; those confirmations remain pending.

The user then reported a crash while opening Internet Explorer. An authorized
local screenshot showed a Windows illegal-operation dialog titled `Msoobe`,
with Details still collapsed; it did not show an Android app crash or BSOD.
The dynamic child process remained alive and DynRec residency continued advancing.
Immediately before the observed transition, queue counts were 2,079/2,079 with
depth zero/high-water two and no wipe. The following sample showed 2,540 enqueues,
2,537 completed returns, depth zero/high-water four, and one wipe/recovery.
Those values then stayed fixed while DynRec continued. No guest reset was counted.

This is a failed Windows application-correctness trial despite the passing CPU
fixture. The aggregate transition correlates with the reported failure but does
not attribute its first incorrect operation, establish whether it also occurs
under Normal, or invalidate the separately reproduced INVLPG correction. Do not
change queue thresholds or retry Internet Explorer from these counters alone.
The fault dialog's Details are the next evidence needed. The guest was left
unchanged with the dialog open; no stop, restart, repair, or stable-machine boot
was performed. Trial shutdown and media disposition remain pending.

The expanded dialog subsequently identified `MSOBWEB.DLL`. The displayed
faulting instruction decodes to `REP STOSD`; the displayed destination is
page-aligned and the repeat count is nonzero. Raw guest addresses, registers,
instruction bytes and screenshots remain outside tracked evidence. This is
the reported fault site, not proof of the first incorrect emulator operation.
The queue totals and single wipe/recovery remained unchanged while DynRec
continued running.

Source inspection found that dynrec's string helper calls unchecked memory
writes while tracking its iteration count locally; the normal string path has
explicit page-fault cleanup that publishes partial progress. Fixture v3 tests
strings before paging, and tests fault retry with scalar loads, but does not
combine REP stores with a handled fault. A source-owned REP-store/fault regression
must check saved instruction position, partial count/destination progress and
exact final memory before another emulator correction. Normal-mode comparison
of the reported application action remains unperformed. Do not attribute the
application failure from the instruction name or source difference alone.

The user subsequently reported that Windows shutdown and End Task were both
unresponsive. Normal guest shutdown therefore remains unconfirmed. The advised
exit is RoboWindows' Stop trial, preserving the abnormal-stop recovery marker;
no console shutdown command or host-side forced termination was executed by the
agent. Actual trial termination and the resulting quarantine still need checking.

After the user requested an in-guest orderly shutdown command, the agent
verified Microsoft KB 234216 (archived) and supplied
`rundll32.exe shell32.dll,SHExitWindowsEx 1`, without a FORCE flag. The user
reported another Not Responding dialog. Clean shutdown is still unconfirmed;
the command is not evidence of successful buffer flushing or guest power-off.
No further shutdown command, forced task termination, or automatic guest retry
was performed by the agent. Stop trial with subsequent disk-check recovery
remains the advised fallback; actual stop/quarantine confirmation is pending.

After the user confirmed Stop trial, ADB verified the dynamic runner was absent
and the experimental copy displayed Needs disk check. Stable incoming retained
Start. Further work is restricted to the source-owned disposable CPU fixture;
neither Windows disk is authorized to boot during this investigation.

## REP STOSD correction and GPT Sol handoff — 2026-09-09

The new fixture faults halfway through eight stores and checks its fault frame,
partial progress, completed memory and guards. QEMU and SM-T500 Normal passed;
DynRec failed with mask `0x1fff`, assertion 21 (wrong saved instruction position),
without a queue wipe/reset.

Patch 0003 uses checked STOSD writes and returns remaining work before generated
code publishes REP count and delivers the precise exception. Other string
helpers and queue behavior are unchanged. The same fixture subsequently passed
both SM-T500 cores with mask `0x3fff`. Host tests, including tests extracted from
the actual patched helper for both address widths/directions, partial faults,
cycle limits, zero counts and wrapping, passed. Android build and repository
checks passed; the corrected debug APK is installed. No Windows disk was opened.
This is one corrected tablet comparison, not expanded-gate or Windows validation.

Current fixture image SHA-256:
`a439417c22576a86266349b9e8f943448e30a37ef90753fcbcb99354876156c9`.

At the user's request, implementation stops here for a lower-cost GPT Sol handoff.
Expanded coverage is specified in `expanded-cpu-coverage.md` and T030–T034, not
implemented. `sol-handoff.md` identifies the remaining audit and execution steps.
Both Windows guests remain stopped; the experimental copy still needs recovery.
No commit or push was made.

## Expanded x86 correctness gate — 2026-09-09

Protocol v4 now supplies seven source-built, fixed-record suites: strings,
fault/retry, integer/flags, stack/control, paging/self-modifying code, x87 and
32 fixed-seed mixed programs. The report requires exact Normal/DynRec parity for
every suite plus the preserved v3 regression before writing a build-specific
capability token. Host tests reject corrupt, oversized, incomplete, unknown,
missing and duplicate records. Each stage has a ten-second deadline and uses a
fresh app-private image and isolated process.

The first expanded SM-T500 run produced a bounded P0 failure in DynRec:
fault/retry case 1, assertion 2013, identified a wrong saved instruction address
at the first REP MOVSB source fault. Patch 0004 extends checked-access and precise
exception handoff to the remaining DynRec MOVS, LODS and STOS widths/address
sizes. CMPS/SCAS use the existing fallback because the pinned DynRec has no
generated helpers for them. With that correction, case 4 assertion 204 then
showed a missing supervisor write-protection fault. Patch 0005 makes DynRec's
checked paging path honor CR0.WP and derives the page-fault user bit from the
active privilege level. Neither correction changes Normal execution or queue
wipe/recovery policy.

The finalized fixture adds explicit sign/zero extension, signed divide and
`RET imm` coverage. Every fixed-seed program stays below 64 instructions and
combines reversible arithmetic, call/return, a two-dword REP copy, a recovered
invalid-opcode fault and immediate-field code modification with block re-entry.
The generic ARM x87 backend does not synthesize arithmetic exception-status
bits; this remains a documented gap. The oracle checks the exact positive
infinity result for masked divide by zero, plus separately defined control-word,
rounding, TOP/compare and conversion results. The preserved v3 UD2 recovery is
also the explicit unsupported-ISA negative test for the configured CPU profile.

GNU binutils 2.40 reproduced all packaged hashes, and QEMU 7.2.22 completed all
seven records. `scripts/test-host.sh`, `scripts/check-repository.sh`,
`scripts/fetch-sources.sh --verify-only`, `git diff --check`, and
`scripts/build-android.sh --debug` passed. The build was installed over the
debug package without clearing app data only after confirming no runner or guest
session was active.

The same installed build passed three consecutive complete comparisons on the
SM-T500: one cold Activity launch and two already-running Activity launches.
Each comparison ran v3 and all seven v4 suites in both Normal and ARM64 DynRec,
with exact results. The warm requests each produced one terminal pass instead
of silently doing nothing. Final inspection found neither isolated fixture
process, disposable fixture file nor fixture cache root. No real guest or user
disk was opened; stable `incoming` remained untouched and experimental
`incoming - copy` remained quarantined.

## Post-gate experimental recovery — 2026-09-09

After the three expanded-gate passes, the user ran the quarantined experimental
copy through RoboWindows' normal-core disk-check recovery and reported a normal
Windows shutdown. Device inspection then showed `incoming - copy` with its
ordinary `Start` action and no `Needs disk check` action. The dynamic and both
CPU-fixture isolated processes were absent. Stable `incoming` still showed
`Start` and was not opened. This restores eligibility for a separately
authorized guarded Windows DynRec trial; it is not itself DynRec validation.

## Successful guarded Windows trial — 2026-09-09

The user authorized one guarded DynRec start on `incoming - copy` after its
clean recovery and the three complete CPU-gate passes. RoboWindows used the
separate dynamic launch policy (`core=dynamic`, fixed 20k,
`cputype=pentium_slow`), not the copy's saved 12k Normal fallback. Bounded status
confirmed the configured and active decoder as DynRec throughout; Normal
residency remained zero.

The user reported the test successful, including Internet Explorer—the action
that had triggered the earlier `Msoobe` failure—and a short AoE2 play session.
Performance was still somewhat slow, but no application fault, freeze or audio
failure was reported. Windows then shut down normally. Device inspection found
the isolated DynRec process stopped, no dynamic-attempt journal, both cards at
`Start`, and no `Needs disk check` action. The last bounded sample had 519 fault
enqueues and 519 completed returns, current depth zero/high-water two, and no
queue wipe, double fault or guest reset.

This completes the responsive-Windows diagnosis/readiness checkpoint T019 and
provides one clean T009 cycle. It does not complete the required ten start/stop
cycles or the matched benchmark, audio-quality and 30-minute thermal comparison.
Stable `incoming` was not opened.

A second fixed-20k DynRec start subsequently passed the user's AoE2, audio and
input check and shut down normally. Device inspection again found the child
stopped, no attempt journal and no quarantine. This brings the selected 20k
profile to two clean Windows start/shutdown cycles; the separate 30k exploration
below does not count toward the 20k ten-cycle gate.

## Guarded DynRec 30k candidate — 2026-09-09

After another clean 20k shutdown, RoboWindows added fixed 30k as a debug-only
diagnostic candidate. The cycle value is allowlisted to 20k/30k, persisted in
the attempt journal, sent through the child-process handoff, checked against the
effective config before native load, and shown as `DYNREC 30K · EXPERIMENTAL`.
Host tests reject arbitrary 25k configuration and cross-boundary cycle mismatch.
The app-private synthetic persistence/handoff probe, complete disposable CPU
gate, repository checks and Android build passed before the Windows start.

The user reported that AoE2 graphics were definitely better at 30k than 20k,
but audio cracked. Windows shut down normally. Device inspection found the
DynRec process stopped, no attempt journal, both machines at `Start`, and no
quarantine action. The final aggregate sample remained in DynRec with 284/284
fault enqueues/returns, depth zero/high-water one, and no wipe, double fault or
guest reset.

Fixed 30k therefore fails the audio-continuity gate and is not a promotable
profile despite its visual improvement. Fixed 20k remains the current validated
DynRec baseline, and the experimental copy's saved Normal fallback is unchanged.
This short subjective comparison does not complete the matched benchmark or
thermal-soak requirements.

## Bounded automatic candidate — 2026-09-09

RoboWindows next added one project-owned automatic policy:
`cycles=auto 80% limit 30000`. The durable attempt carries the ID
`auto-80-limit-30k`; the isolated child independently resolves that ID and
requires the journal, Binder request and sole effective `cycles` line to agree.
Raw unlimited auto, max, unknown IDs, free-form policy text and cross-boundary
mismatch are unavailable or rejected before native start. The active overlay
labels the session `DYNREC AUTO 80% · MAX 30K · EXPERIMENTAL`.

Host tests, pinned-source verification, repository hygiene and the Android
build passed. After installation on the stopped tablet, the app-private
synthetic persistence/handoff probe and complete disposable Normal/DynRec CPU
gate passed before the Windows start. Stable `incoming` was not opened.

During the user-observed AoE2 trial, the user found both graphics and audio not
good enough. The initial thermal snapshot showed battery 28.5 °C, hottest
exposed CPU sensor 46.4 °C, GPU 41.4 °C and Android thermal status zero. Roughly
eight minutes later those values were battery 29.0 °C, hottest CPU sensor
49.2 °C, GPU 41.4 °C and status zero. This short observation found no throttling
but is not the required 30-minute soak.

Windows shut down normally. Final inspection found the DynRec runner stopped,
no attempt journal, both machines at `Start`, battery 29.0 °C and thermal status
zero. The final bounded CPU sample remained in DynRec with 339/339 fault
enqueues/returns, depth zero/high-water three, and no wipe, double fault or
guest reset. The automatic candidate is rejected on user-visible graphics and
audio quality. Fixed 20k remains the best validated DynRec balance; fixed 30k
and bounded auto remain diagnostic-only failed candidates.

## Host-side progress watchdog — 2026-09-09

An audit against the execution-safety contract found that the guarded controller
polled indefinitely when the isolated runner stopped completing emulator calls.
A bounded host-side watchdog now distinguishes bridge responses from emulator
progress and fails after 30 seconds without either one. Explicit pause suspends
the deadline and resume starts a fresh window. The failure path records
`needs-check`, asks the child to stop, and only then disconnects.

The host suite passes silent-runner, responsive-but-stalled, continuously
advancing, pause/resume and restart-counter cases. A device fault-injection run
is still required before the full hang/teardown portions of T001 and T013 can be
closed.

## Same-Activity recovery rejection — 2026-09-09

An authorized fixed-20k benchmark experiment had to be ended with `Stop trial`
after its full-window GDI loop stalled guest progress. The isolated runner ended
and the experimental copy was correctly marked `Needs disk check`, but pressing
that action immediately reported `This machine is not eligible for recovery
boot`. The stable `incoming` machine was not started.

The durable attempt was in `needs-check`; the rejection occurred because the
same in-memory machine profile still selected Dynamic. Startup-time recovery
wrongly required a pre-restored Normal selection even though FR-024 requires the
recovery action itself to enter the recorded Normal fallback. T040 covers this
same-Activity path. No recovery or disk-health claim is recorded until a fixed
build boots the copy with `core=normal`, Windows performs its disk check, and the
guest shuts down normally.

The corrected build's app-private persistence probe passed the same-Activity
Dynamic-selection recovery case. The real `incoming - copy` then entered a
Normal recovery boot, reached the Windows desktop with no isolated DynRec process,
and the user shut Windows down normally. Device inspection found no attempt
journal or active-session marker; both `incoming` and `incoming - copy` showed
`Start`. Stable `incoming` was not opened. A future forced-stop fixture still
needs to exercise the exact real-card action without an intervening Activity
restart before T040 is marked complete.

## Balanced scheduler DynRec startup failure — 2026-09-09

After the complete disposable CPU gate and a passing corrected-scheduler Normal
benchmark on `incoming - copy`, the matched fixed-20k DynRec start failed before
the workload launched. Windows reported that Explorer performed an illegal
operation; closing the dialog left the desktop shell absent. The overlay never
reported Normal execution. During the failure, schema-3 telemetry identified
extended `PageFault` residency, `retro_run()` calls as long as 105.527 ms,
scheduler lateness above the 250 ms retained-debt limit, quality-failing deadline
resynchronizations, and audio starvation. This is a DynRec-only Windows startup
failure, not evidence against the corrected shared scheduler, because the
immediately preceding Normal workload recorded no lateness, catch-up, audio or
lifecycle failure.

The benchmark was not launched and no result was accepted. The user selected
`Stop trial`; the child exited and the experimental disk alone entered
`Needs disk check`. Its required Normal recovery boot then reached Windows and
shut down cleanly. Device inspection found both cards at `Start`, no quarantine,
no attempt journal and no DynRec worker. Stable `incoming` was not opened. Stop
further Windows trials until the measured DynRec page-fault path is investigated.

The existing decoder-residency value is a post-`retro_run()` sample, not a timed
trace of the page-fault handler. The failing interval therefore establishes a
correlation, not a new page-fault correctness defect: all four prior targeted
corrections remain covered by the passing expanded gate. The next build will add
only bounded `PageFaultCore` entry/return and cumulative timing aggregates under
FR-042. No queue threshold or CPU execution behavior is changed by this
diagnostic step.

## PageFaultCore timing diagnostic — 2026-09-09

The standalone pinned-core patch adds monotonic cumulative PageFaultCore entry,
completed-return, total-duration, maximum-duration and at-least-10-ms call
counters. The emulator thread resets and samples them; the isolated status and
fixture log expose only the bounded aggregates. Host parser tests cover the new
fields, negative inputs and legacy records. Host tests, repository hygiene,
pinned-source reconstruction and the ARM64 debug build passed.

With both machine cards stopped and no isolated runner present, the installed
APK passed the complete source-owned Normal/DynRec x86 gate. No machine disk was
opened, and all fixture processes exited. The DynRec fault/retry suite reported
86 entries, 86 returns, 330 microseconds total, a 31-microsecond maximum and zero
calls of at least 10 ms; its queue also balanced 2/2 at depth zero without a
wipe. Normal correctly reported zero PageFaultCore calls because the pinned
normal core uses its separate exception path. T041 is complete. This passing
control permits one guarded fixed-20k boot on `incoming - copy` to attribute the
intermittent Windows-only interval; it does not authorize a benchmark workload,
stable-machine use, or any execution-path change.

That single guarded boot reproduced the failure before a usable desktop. The
guest remained on the teal Windows transition screen with an hourglass; the
benchmark was not launched. At the last complete five-second status sample,
the decoder was `PageFault`, fault enqueue/return was 190/183 at depth 7 with a
high-water of 8, and no queue wipe had occurred. PageFaultCore reported
9,771,585 entries and 9,771,578 completed returns, 2,530,837 microseconds of
cumulative measured time, a 491,819-microsecond maximum and 67 calls of at least
10 ms. The visible overlay advanced further to 10,490,698/10,490,691 while the
same seven nested calls remained active. Schema-3 intervals fell to seven
completed `retro_run()` calls and seven presented frames per second, with
roughly 165–168 ms call maxima, repeated deadline resynchronization, an empty
audio queue and about 48,000 missing audio frames per second.

This is direct evidence that the Windows failure executes inside the
PageFaultCore path: the entry/return difference tracks the nested queue depth,
while millions of completed one-cycle decoder calls make no timely progress
toward resolving those faults. It is not yet evidence for changing the queue
watchdog or increasing the PageFaultCore cycle allocation. T042 requires a
source-owned reproduction of that amplification before either behavior changes.
Further Windows DynRec and benchmark trials remain stopped.

RoboWindows' `Stop trial` ended the isolated child and quarantined only
`incoming - copy`. The immediate Normal recovery boot ran ScanDisk, reached the
desktop, and the user shut Windows down through BIOS APM. The core logged
`guest requested shutdown` followed by `guest stopped cleanly`. Final device
inspection showed both cards at `Start`, only the main app process, zero dynamic
attempt journals and zero active-session markers. Stable `incoming` was never
opened.

## Source-owned PageFaultCore progress reproduction — 2026-09-09

Protocol v4 now includes stable suite `0x0103`, with three cases: one
deliberately long handler, sixteen sequential handlers, and a four-level nested
handler chain with bounded work at every level. Each faulting stack read begins
a separate translated block and uses a mapped guard page for exception state.
The 52-byte result asserts only exact guest entry/return order, completed work
and successful instruction retry. Device-dependent call counts and durations
remain outside the guest oracle in the bounded liveness record. The source-built
stage is 2,058 bytes with SHA-256
`1070a9af59e747cec03a12f7225f5e71892d07eb8375ccdb37f6622354bec043`;
the disposable image SHA-256 is
`745f2005c5649cfa9d53fd84f919a8251c91f4ef5f8ef277c30580e28e8387bc`.

The pinned QEMU 7.2.22 oracle accepted all three cases, and the complete host
gate accepted all eight protocol-v4 suites. On the SM-T500, Normal produced the
same complete `0x0103` result with 21 delivered and entered guest page faults,
zero PageFaultCore calls and no queue activity. DynRec produced the identical
result with 21 enqueues and 21 completed returns, final depth zero, high-water
four and zero wipes. Those 21 guest faults required 1,049,545 PageFaultCore
entries and the same number of completed returns. Cumulative PageFaultCore time
was 444,155 microseconds, the maximum call was 210,125 microseconds, and three
calls lasted at least 10 milliseconds.

The complete legacy-plus-eight-suite Normal/DynRec gate then passed. Postflight
inspection found no dynamic guest or CPU-fixture child, no active-session marker
and no dynamic-attempt journal. The installed APK and host artifact both had
SHA-256 `d6b18109ab06aff1e3a1b4c6b2c056397d9a23dd020d19a2895cf17fae6a8cce`.
No machine disk was opened and stable `incoming` remained untouched.

This source-owned reproduction satisfies T042 and isolates the amplification to
the existing fixed one-cycle PageFaultCore loop: a bounded handler workload can
consume more than a million host decoder calls while preserving exact guest
correctness and fully balanced nested fault state. It does not justify a queue
wipe or another Windows trial. The next execution change must be evaluated
against this suite and retain the same reference/Normal/DynRec record, zero
wipes, bounded nesting and complete return balance.
