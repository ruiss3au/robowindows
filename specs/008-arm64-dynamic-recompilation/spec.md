# Feature Specification: ARM64 Dynamic Recompilation

**Feature**: `008-arm64-dynamic-recompilation`  
**Status**: Correctness gate and first Windows DynRec 20k trial passed; performance validation in progress

## Scope and interface

Feature 010 implements the debug settings selector and makes DynRec selection
persist after verified clean shutdown. Interrupted/failed trials still restore
Normal and enforce quarantine. All release and stable-machine gates below remain.

Experimental machine Settings offers mutually exclusive `Normal` and
`Dynamic (experimental)` buttons using the existing selected-button style.
Selection saves a preference; only Start boots the guest. Normal restores the
saved normal configuration, including its cycle value. The first dynamic trial
uses `core=dynamic`, `cycles=fixed 20000`, and `cputype=pentium_slow`.
Fixed-cycle buttons are disabled while Dynamic is selected. No automatic/max
cycle policy is exposed in ordinary settings, and no stable-machine promotion
is included in this feature. The later bounded-auto diagnostic in FR-040 is an
internal performance experiment, not a selectable machine profile.

Dynamic selection is unavailable until implementation safety tests pass. A
diagnostic build may then expose it for authorized trials. Release builds keep
it hidden until the correctness and performance gates below pass; even then,
it remains experimental and restricted to copies.

For one authorized symbolized reproduction, the debug build may expose a
separately labeled `Run dynamic diagnostic` action on a stopped experimental
copy. It is not a normal Start control, cannot target a stable profile, and
uses the same durable child-process start path as a future experimental UI.
Its result is deliberately conservative: a failed, stopped, or interrupted
trial remains quarantined until the documented media-health procedure clears
it; the action never silently retries or starts the normal profile.

For an explicitly user-authorized diagnostic copy, a debug-only normal-core
recovery boot may be used to run the guest's own disk check and perform a normal
Windows shutdown. It cannot select dynrec, is unavailable to stable machines,
and clears quarantine only after that shutdown is observed and the normal runner
has unloaded.

On a quarantined experimental card, pressing `Needs disk check` starts that
normal-core recovery boot directly. It does not show an informational dead end.
The session toolbar identifies the recovery purpose, and any exit other than a
detected Windows shutdown leaves the copy quarantined.

The [execution contract](contracts/execution-safety.md) defines the required
startup ordering, recovery states, media verification, and failure behavior.

## User Story

As the owner of an experimental machine copy, I can try an ARM64 dynamic CPU
profile for better Windows 98 performance without changing the stable machine
or being trapped in a crash loop.

## Requirements

- **FR-001**: Dynamic recompilation MUST be available only to experimental
  machines with an independent writable disk. Stable machines MUST remain on
  their existing CPU profile.
- **FR-002**: Selecting the dynamic profile MUST require no active or
  interrupted session and a most-recent normal guest shutdown.
- **FR-003**: RoboWindows MUST write an atomic pending-start marker before
  entering native dynamic execution. The marker MUST identify only app-owned
  machine and profile IDs, not private paths or guest content.
- **FR-004**: The dynamic session MUST not be marked healthy until the native
  core is running and has produced bounded proof of guest execution. Merely
  entering the Activity is insufficient.
- **FR-005**: If startup fails, the process terminates abnormally, or a pending
  marker remains on the next app start, RoboWindows MUST restore the machine's
  last-known-safe normal-core profile before another guest start.
- **FR-006**: Recovery MUST show a RoboWindows-owned explanation and MUST NOT
  delete, replace, restore, or copy guest media automatically.
- **FR-007**: The first diagnostic run MUST use a verified experimental copy.
  A pre-execution failure requires an unchanged disk SHA-256; a run that may
  have written to the guest requires filesystem-health and expected-persistence
  checks rather than an impossible unchanged-disk requirement.
- **FR-008**: The ARM64 failure MUST be reproduced with symbols and attributed
  before a core patch is accepted. Investigation MUST cover generated-code
  allocation, Android W^X transitions, instruction-cache invalidation,
  generated branches, and block linking.
- **FR-009**: Any emulator modification MUST be minimal, isolated under
  `patches/`, tied to the pinned upstream revision, and documented with its
  license and upstream provenance.
- **FR-010**: RoboWindows MUST expose named project-owned profiles, never raw
  DOSBox configuration, upstream menus, or arbitrary dynrec controls.
- **FR-011**: A dynamic profile MUST preserve the existing Windows 98 memory,
  `pentium_slow`, SB16 I/O/IRQ/DMA, video, keyboard, relative mouse, pause,
  media, shutdown, and lifecycle contracts unless a later specification
  explicitly changes one.
- **FR-012**: Dynamic performance claims MUST use the versioned benchmark and
  timer/audio workloads from Feature 002, plus the repeatable AoE2 scenario;
  subjective visual improvement alone is insufficient.
- **FR-013**: Dynamic mode MUST remain labeled experimental until it passes CPU
  correctness, ten consecutive starts, guest-time, audio, input, shutdown,
  disk-health, and 30-minute thermal-soak gates on the SM-T500.
- **FR-014**: Promotion requires a repeatable improvement over the highest
  sustainable normal-core profile without worse audio continuity, guest-time
  accuracy, input responsiveness, stability, or disk health.
- **FR-015**: A rejected or unsupported dynamic profile MUST remain hidden from
  stable machines and recoverable without manual file editing.
- **FR-016**: Every start path, including debug entry points, MUST enforce
  eligibility immediately before native execution, using freshly loaded profile
  metadata and the effective generated configuration. UI visibility is not an
  authorization boundary. Unknown, inconsistent, or stale state MUST refuse start.
- **FR-017**: Isolation MUST cover every writable attachment, launch file, and
  auxiliary guest output. Shared media MUST be opened read-only. Canonical paths
  and file identities MUST be checked for aliases to another machine's files;
  changing media MUST enforce the same rules.
- **FR-018**: Fallback MUST be a versioned snapshot of the complete normal
  configuration, retained independently of the dynamic preference. Selecting a
  trial or reaching running state MUST NOT promote it to a safe fallback.
- **FR-019**: Failure to persist startup state or restore and verify fallback
  MUST block the affected machine. Dismissing an error MUST NOT clear the block.
  Other machines remain accessible once native execution has ended.
- **FR-020**: Dynamic execution MUST use a separate emulator process which ends
  before another machine starts. No process which executed dynrec may subsequently
  load a stable guest. Shared core changes MUST also pass normal-core regression
  tests using a disposable stable-role fixture.
- **FR-021**: Configuration selection, starts, recovery, deletion, and media
  mutations MUST be serialized and checked against machine/session generations.
  Old callbacks MUST NOT acknowledge or modify a later session.
- **FR-022**: Every host input and lifecycle command for an isolated dynamic
  session MUST be delivered to the child runner rather than the host-process
  native runtime. Bridge failure MUST stop and quarantine the trial; it MUST NOT
  silently discard input or continue with split session state.
- **FR-023**: Native calls, frames, and audio prove only runner liveness. A
  dynamic trial MUST NOT advance to correctness or performance validation until
  Windows reaches a usable desktop and the tester explicitly verifies guest
  keyboard and captured relative mouse response. Failure of this readiness gate
  rejects the build/profile and requires diagnosis before another guest trial.
- **FR-024**: For a quarantined experimental copy eligible for the debug recovery
  route, pressing `Needs disk check` MUST immediately start a normal-core recovery
  session, including when `Stop trial` left Dynamic as the saved execution
  preference in the still-running Activity. Recovery MUST derive and persist the
  attempt's recorded Normal fallback before native handoff. RoboWindows MUST not
  replace that action with an informational dialog.
  After a verified clean recovery shutdown, both the card warning and any
  quarantine banner MUST disappear in the same return to the machine list.
- **FR-025**: Before another Windows dynamic trial, RoboWindows MUST run a
  versioned, freely redistributable CPU fixture from a newly created disposable
  image. Normal and dynamic executions MUST use distinct throwaway Android
  processes and MUST NOT load, mount, hash, or otherwise access any machine
  profile or user media.
- **FR-026**: The CPU fixture MUST produce a bounded machine-readable record
  covering 16-bit integer/flags, 32-bit integer/flags, x87, string operations,
  self-modifying code, and protected-mode execution. RoboWindows MUST accept a
  mode only when the record has the expected fixture version, completion magic,
  complete test mask, and checksum. Timeout, process death, malformed output,
  or a normal/dynamic mismatch MUST fail the diagnostic visibly.
- **FR-027**: CPU-fixture success proves decoder correctness only for its named
  cases; it MUST NOT mark Windows ready. Windows readiness remains a separate,
  per-attempt record requiring three explicit confirmations: responsive desktop,
  keyboard response, and captured relative-mouse response. Performance testing
  is unavailable until all three are recorded for the current build and attempt.
- **FR-028**: Before another Windows dynamic trial, fixture v2 MUST additionally
  compare normal and dynamic execution for paging, a handled page fault with
  validated fault address/error state, interrupt/exception-gate entry and
  `iret`, invalid-opcode recovery, repeated linked control flow, and
  self-modification spanning an x86 page
  boundary. The result MUST identify each case independently; a partial pass
  MUST remain a diagnostic failure.
- **FR-029**: Fixture v2 MAY use a multi-sector loader, but its stage count,
  load addresses, result-sector location, complete image layout, and hashes MUST
  be generated and verified from repository source. Code and result sectors
  MUST NOT overlap, and the app MUST reject an image of the wrong version or
  size. Passing v1 MUST NOT satisfy the v2 build-specific capability gate.
- **FR-030**: A guarded Windows diagnostic MUST show and record bounded decoder
  residency sampled on the emulator thread: configured decoder, current decoder
  class, and counts for dynrec, normal, page-fault, and other/special execution.
  It MUST NOT expose guest instruction addresses, memory, paths, or contents.
  Sustained page-fault or interpreter residency MUST reject performance testing
  and direct the next root-cause investigation.
- **FR-031**: After sustained page-fault residency, the next guarded diagnostic
  MUST expose only aggregate page-fault lifecycle evidence: enqueue and
  completed-return counts, current and high-water queue depth, and wipe/recovery
  counts. Collection and publication MUST occur on the emulator thread. It MUST
  NOT export addresses, register values, memory, guest text, media paths, or
  per-fault records. Another Windows dynamic trial remains blocked until these
  aggregates can distinguish forward progress from a stuck or repeatedly reset
  page-fault sequence.
- **FR-032**: After an isolated CPU fixture or dynamic trial reports its final
  result and releases native state, its dedicated Android process MUST terminate
  within two seconds. A cached empty service process is not an acceptable fresh
  execution boundary. The result message and durable clean/quarantine decision
  MUST be delivered before termination.
- **FR-033**: Before changing page-fault recovery thresholds or retry behavior,
  a fixture v3 MUST compare Normal and DynRec through a deterministic nested
  page-fault chain with independently validated fault levels and complete
  returns. It MUST fail on missing, reordered, duplicated, or unreturned levels.
  Passing the existing single handled fault MUST NOT satisfy this gate. A queue
  wipe is recovery evidence, not correctness, and MUST NOT convert a failed
  nested sequence into a pass.
- **FR-034**: When fixture v3 fails reproducibly, the next
  diagnostic MUST expose only bounded aggregate CPU exception-lifecycle
  evidence: prepared and delivered page-fault counts, successful page-fault
  gate-entry and aggregate `iret` counts, delivered double-fault count,
  guest-reset count, and an allowlisted current decoder class that distinguishes
  the halted state. Counters MUST be reset and sampled on the
  emulator thread. They MUST NOT expose exception addresses, error values,
  registers, memory, guest text, paths, or per-exception records. The result
  MUST be combined with a failing instruction-level regression before any CPU
  or page-fault behavior changes; counters alone do not prove a root cause.
- **FR-035**: The source-owned CPU fixture may report fixed assertion identifiers
  in its reserved result word. Identify the first failed assertion before
  changing emulator behavior. Identifiers MUST NOT contain user-guest data or
  enable access to machine disks; a nonzero identifier fails the capability gate
  even when the test mask is otherwise complete.
- **FR-036**: Fixture v3's expanded mask `0x1fff` MUST verify that `INVLPG`
  consumes its complete memory operand without executing SIB or displacement
  bytes or reading operand memory. Cover 32-bit absolute, disp8, SIB plus disp32,
  and 16-bit absolute addressing; preserve guest registers and flags. Restore
  and pass the full four-level nested chain after the correction.

**FR-037 — REP fault precision:** The expanded CPU gate (`0x3fff`) MUST execute
REP STOSD across a deliberately non-present destination page. Its handler MUST
check the fault's instruction position, write error code, partial count and
destination, preserved source/value registers, and completed prefix stores.
After mapping and retry, the fixture MUST verify the remaining stores, zero
count, final destination, unchanged boundary sentinels and exactly one fault.
Report a fixed first-failure assertion; do not export user-guest state. Preserve
the INVLPG and nested-fault regressions. No further Windows dynrec run is
authorized until this targeted fixture passes and the copy completes recovery.

**FR-038 — Expanded pre-Windows gate:** Implement the required suites, oracle,
bounded protocol and acceptance rules in [Expanded x86 correctness
gate](expanded-cpu-coverage.md). All required suites MUST pass before another
Windows dynrec trial; a partial pass is not capability evidence. This strengthens
FR-037's prerequisite and does not authorize booting or repairing user media.

**FR-039 — Bounded cycle candidates:** After a clean 20k Windows DynRec trial,
the debug-only guarded diagnostic MAY run at fixed 30k for performance
exploration. Only fixed 20k and fixed 30k are accepted. The selected value MUST
be recorded in the durable attempt journal, sent across the isolated-process
handoff, verified against the generated configuration immediately before native
start, and displayed prominently in the session overlay. A stale, missing,
unsupported or cross-process-mismatched value MUST fail before native start.
The 30k trial MUST retain the same Normal fallback and quarantine behavior as
20k; it does not promote 30k or alter ordinary Start/settings.

**FR-040 — Bounded automatic diagnostic:** After fixed 30k improves graphics
but fails audio continuity, the guarded debug diagnostic MAY test exactly
`core=dynamic` with `cycles=auto 80% limit 30000`. Raw `auto`, `max`, a missing
limit, another percentage/limit, and caller-supplied policy text MUST be rejected
before journal creation or native start. The host MUST persist a named policy
ID; the child MUST derive the sole permitted config value from its own matching
allowlist rather than trusting config text. The active overlay MUST display
`DYNREC AUTO 80% · MAX 30K · EXPERIMENTAL`. Fixed 20k remains the baseline and
the saved Normal fallback remains unchanged. Audio underruns are not controller
feedback, so any audible cracking rejects this candidate even if it scales down.

**FR-041 — Bounded progress watchdog:** A foreground, unpaused dynamic trial
MUST fail visibly and enter `needs-check` when either the child bridge produces
no status response or its completed emulator-call counter makes no progress for
30 seconds. Explicit pause and lifecycle pause intervals MUST suspend the timer;
resume starts a fresh 30-second window. Failure handling MUST request orderly
child teardown before disconnecting and MUST never clear the active journal as
clean.

**FR-042 — Page-fault execution timing:** After a DynRec-only Windows failure
correlates with sustained `PageFault` residency, the next diagnostic build MUST
measure the page-fault decoder itself rather than infer its cost from a decoder
sample taken after `retro_run()`. It MUST expose only cumulative decoder-call
entries, completed returns, total and maximum monotonic duration, and the count
of calls lasting at least 10 ms. Counters MUST be reset and sampled on the
emulator thread and carried through the existing bounded liveness record. They
MUST NOT include fault addresses, error codes, registers, memory, guest text,
paths, or per-call records. This instrumentation MUST NOT change page-fault
queue thresholds, exception flow, DynRec translation, caching, or linking.

**FR-043 — Page-fault progress fixture:** Before changing PageFaultCore's
one-cycle execution loop or page-fault recovery behavior, the expanded
source-owned gate MUST add a stable suite that executes a deliberately long
single fault handler, repeated sequential fault handlers, and a four-level
nested fault chain with bounded work at every level. Each case MUST validate
handler entry/return counts, nesting order, completion and the retried access.
Reference, Normal and DynRec result records MUST match exactly. The device run
MUST retain the FR-042 aggregates so PageFaultCore call amplification can be
measured without guest addresses, contents, or per-call logs. A timeout,
unbalanced queue, incomplete record, or wrong nesting result blocks any
PageFaultCore or watchdog correction and another Windows DynRec trial.

**FR-044 — Bounded PageFaultCore slice:** After FR-043 reproduces one-cycle
call amplification, PageFaultCore MUST run at most 64 full-core guest
instructions per decoder invocation. Any `iret` executed inside that slice MUST
end the slice immediately so the current top fault is re-evaluated before an
instruction at the restored address can execute. The slice MUST preserve CPU
cycle accounting and advance the existing nested-fault watchdog in guest-work
units rather than decoder-call units; it MUST NOT change queue limits, wipe
thresholds, exception delivery, DynRec translation, caching, linking, or the
Normal decoder. The `0x0103` suite MUST retain identical reference, Normal and
DynRec result records, zero wipes, zero final depth, and balanced queue/core
entries and returns. On the SM-T500 it MUST reduce the reproduced PageFaultCore
entry count by at least 16 times, reduce cumulative PageFaultCore duration by at
least three times, and keep the maximum inclusive call below 50 ms. The fixed
10-ms counter remains characterization evidence because a parent call includes
synchronous nested-fault work. Failure rolls back the slice patch and blocks
Windows trials.

**FR-045 — REP element cycle accounting:** DynRec MOVS/LODS/STOS helpers MUST
charge one cycle per completed element for all byte/word/dword operand and
16/32-bit address variants, including repeats that fit inside the remaining
budget. Zero count or nonpositive budget MUST perform no memory access or
register progress. Faults MUST preserve the completed prefix, remaining count,
precise exception handoff and retry behavior, charging only completed elements.
Forward/backward address wrapping MUST remain correct without signed-shift
undefined behavior. Preserve generated opcode overhead and all Normal-core,
queue, scheduler, cache and linking policies. Before another Windows trial,
require host regressions, the complete disposable x86 gate and the unchanged
Feature 002 phased stress image in Normal and DynRec, with valid guest time,
zero settled audio errors/resynchronizations and the existing presentation gate.
A failure blocks Windows trials and requires attribution, not buffer retuning.

## Success criteria

- **SC-001 — isolation:** attempted starts with stable roles, aliased disks or
  attachments, stale metadata, and mismatched configurations are rejected before
  native load. Stable fixture file hashes and profile/configuration bytes remain
  unchanged across dynamic success, crash, hang, and recovery tests.
- **SC-002 — recovery:** fault injection at every durable transition in the
  execution contract never starts with an uncommitted journal, retries dynrec
  automatically, or clears a failed recovery. A late crash after running-state
  confirmation is detected just like a startup crash.
- **SC-003 — correctness:** a versioned CPU fixture compares integer, flags,
  floating-point, protected-mode, and self-modifying-code results with the normal
  baseline; ten boot/normal-shutdown cycles, ten pause/resume cycles, and the
  Feature 004 input procedure pass with no crash or filesystem errors.
- **SC-004 — real time:** Feature 002 SC-001–SC-004 and SC-008 apply: correct
  guest time within 5%, correct sound duration/pitch, zero settled reference-sound
  underruns, and a 30-minute soak. Silence and paused intervals are accounted for.
- **SC-005 — improvement:** use three matched runs per profile on the same
  versioned workload and equivalent cloned starting state. Median benchmark
  throughput improves at least 10% over the highest normal profile passing
  SC-004, with no correctness or audio regression. Record the AoE2 scenario,
  game settings, presentation rate and input results; at least 15 presented fps
  and the existing input acceptance procedure must pass. If no normal candidate
  passes the baseline gates, record that limitation and defer the improvement claim.

## Acceptance Scenarios

1. Given a stable machine, when its settings are shown, then no dynamic profile
   is offered and its launch configuration is unchanged.
2. Given a normally stopped experimental copy, when `Dynamic (experimental)` is
   selected, then RoboWindows records pending startup before native execution.
3. Given dynrec crashes before guest execution, when RoboWindows next opens,
   then it selects the last-known-safe normal profile, explains the recovery,
   and the clone disk hash is unchanged.
4. Given dynrec fails after possible guest writes, when recovery occurs, then
   no automatic media rollback occurs and the clone passes the documented
   filesystem-health check before reuse.
5. Given dynrec starts successfully, when the validation workload runs, then
   keyboard, captured relative mouse, video, SB16 audio, pause/resume, and
   normal Windows shutdown behave like the normal-core baseline.
6. Given a candidate passes correctness, when performance is compared, then
   its build, profile, workload, guest-time rate, audio underruns, frame rate,
   benchmark result, and thermal result are recorded before any promotion.
7. Given marker persistence, config publication, or recovery verification fails,
   when Start is attempted, then native load is not called and dismissing the
   message does not bypass the failure.
8. Given a running dynamic session crashes or hangs after startup confirmation,
   then its durable active record survives, the emulator process is ended, and
   recovery follows the same media-check procedure as an early uncertain failure.
9. Given a completed dynamic trial, when a disposable stable-role fixture starts,
   then it uses a fresh emulator process and its original normal configuration.
10. Given a normally shut-down experimental session, when Normal is selected,
    then the saved normal settings are selected without starting a guest.
11. Given an eligible quarantined experimental copy, including immediately after
    `Stop trial` while its saved preference is Dynamic, when `Needs disk check` is
    pressed, then Windows boots with the attempt's recorded normal fallback so its
    disk check can run; a normal Windows shutdown clears quarantine and every
    other exit retains it.
12. Given an isolated dynamic session, when keyboard, pointer, touch, pause,
    focus, restart, or input-cancel events occur, then only the child runner
    receives them, and loss of that bridge fails the trial visibly.
13. Given a dynamic runner that publishes frames and audio but never reaches a
    responsive Windows desktop, then the trial fails readiness and no performance
    validation or further guest trial is authorized from that evidence.
14. Given the CPU diagnostic, when normal and dynamic fixture runs execute, then
    they use separate disposable images and processes, report the same complete
    result record, and leave every machine profile and user disk untouched.
15. Given a fixture timeout, incomplete mask, corrupt checksum, process death, or
    result mismatch, then RoboWindows reports the failed stage and does not enable
    another Windows dynamic trial.
16. Given fixture v2, when paging deliberately accesses a non-present page, then
    its handler validates the page-fault address and error code, makes the page
    present, returns through `iret`, and the retried instruction completes in
    both normal and dynamic modes.
17. Given fixture v2, when its generated image is inspected, then the boot loader,
    test stage and result sector occupy declared non-overlapping sectors and all
    source-derived hashes match before Android packaging.
18. Given a guarded dynamic session, when its status is displayed, then the
    decoder-residency counters distinguish dynrec work from page-fault and normal
    fallback without revealing a guest address or guest data.
19. Given a guarded session with sustained page-fault residency, when lifecycle
    evidence is displayed or logged, then aggregate enqueue, completed-return,
    current/high-water depth and wipe/recovery counts identify whether the
    sequence advances, while no per-fault or guest-derived value leaves native
    execution.
20. Given an isolated runner reports its final stopped result, when two seconds
    elapse, then that runner process no longer exists and a later run starts in
    a new process without losing the prior result or durable disk decision.
21. Given fixture v3 deliberately triggers nested faults from its page-fault
    handler, when Normal and DynRec execute it, then every expected level is
    observed in order, every handler returns, the original instruction resumes,
    and both bounded result records match without queue recovery.
22. Given fixture v3 fails under DynRec,
    when the run times out, then its bounded status distinguishes
    prepared page faults, delivered page faults, successful page-fault gate
    entries, aggregate `iret` executions, double faults, guest resets, and an
    allowlisted halted decoder without exporting guest-derived data.

23. Given the INVLPG operand regression, when it runs against the uncorrected
    DynRec decoder, then it fails with an identified assertion before paging is
enabled; after the correction, Normal and DynRec produce identical complete
`0x1fff` records, including all four nested fault levels.

24. Given an eligible experimental copy and a debug request for fixed 30k,
when the guarded runner starts, then the journal, child handoff, generated
configuration and visible `DYNREC 30K · EXPERIMENTAL` label agree; any unsupported
or mismatched value is rejected before native execution, and a failed or
unclean stop follows the existing quarantine path.

25. Given an eligible copy and the named bounded-auto request, when the guarded
runner starts, then journal, child allowlist, effective config and visible label
all identify auto 80% with a 30k cap. An unlimited, malformed, mismatched or
unknown policy fails before native execution, and shutdown/recovery behavior is
identical to a fixed-cycle trial.

26. Given a foreground dynamic trial, when the bridge is silent or continues
replying without an emulator-call counter change for 30 seconds, then
RoboWindows quarantines the attempt, reports the failure, and requests runner
teardown. A paused trial does not time out, and resume receives a fresh window.

27. Given page-fault timing diagnostics, when a disposable fixture or guarded
trial runs, then its bounded status distinguishes page-fault decoder entries,
completed returns, cumulative/maximum duration and calls of at least 10 ms. A
malformed or negative aggregate is rejected or clamped without exposing any
guest-derived value, and legacy liveness records remain parseable.

28. Given the page-fault progress suite, when its single, sequential and nested
handler workloads run, then all bounded work completes and the original accesses
retry in reference, Normal and DynRec with identical records. The target log
reports balanced completed PageFaultCore calls and a zero final queue depth; its
aggregate call count may characterize the current one-cycle amplification but
is not encoded as a device-speed-dependent guest assertion.

29. Given the bounded PageFaultCore slice, when the same `0x0103` image runs on
the SM-T500, then its exact DynRec result still matches reference and Normal,
the queue reaches depth four and settles without a wipe, PageFaultCore entries
and returns balance, entries fall by at least 16 times from the recorded
1,049,545-call baseline, cumulative duration falls by at least three times from
444,155 microseconds, and the maximum inclusive call remains below 50 ms. An
`iret` boundary or nested-fault mismatch rejects the correction.

30. Given the REP accounting correction, when every extracted helper executes
4,096 elements with 20,000 cycles available, then exactly 4,096 element cycles
are charged. Faults charge only the completed prefix and retry only the suffix;
zero count and exhausted budgets access no memory. The unchanged disposable
stress image then completes all phases in Normal and DynRec within 5% of host
time, passes the existing GPU presentation gate and reports zero settled audio
errors or deadline resynchronizations. Full x86 parity remains mandatory.

## Out of Scope

- Changing the stable machine automatically.
- Supporting x86/x86-64 Android hosts or other dynamic-core implementations.
- Root access, kernel changes, unsafe executable-memory workarounds, or
  unpinned replacement emulator cores.
- Promising that dynrec will be fixable or faster before evidence exists.
