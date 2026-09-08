# Feature Specification: ARM64 Dynamic Recompilation

**Feature**: `008-arm64-dynamic-recompilation`  
**Status**: Specified; implementation and device validation pending

## Scope and interface

Experimental machine Settings offers mutually exclusive `Normal` and
`Dynamic (experimental)` buttons using the existing selected-button style.
Selection saves a preference; only Start boots the guest. Normal restores the
saved normal configuration, including its cycle value. The first dynamic trial
uses `core=dynamic`, `cycles=fixed 20000`, and `cputype=pentium_slow`.
Fixed-cycle buttons are disabled while Dynamic is selected. No automatic/max
cycle policy or stable-machine promotion is included in this feature.

Dynamic selection is unavailable until implementation safety tests pass. A
diagnostic build may then expose it for authorized trials. Release builds keep
it hidden until the correctness and performance gates below pass; even then,
it remains experimental and restricted to copies.

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

## Out of Scope

- Changing the stable machine automatically.
- Supporting x86/x86-64 Android hosts or other dynamic-core implementations.
- Root access, kernel changes, unsafe executable-memory workarounds, or
  unpinned replacement emulator cores.
- Promising that dynrec will be fixable or faster before evidence exists.
