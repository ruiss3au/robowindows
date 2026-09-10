# Dynamic execution safety contract

The 2026-09-10 opt-in amendment in the feature specification supersedes role/build
restrictions for fixed-20k only. Every durable-state, ownership, capability and
recovery rule below applies to ordinary machines as well as experimental copies.

## Durable data and selection

Persist one versioned authoritative machine record containing role, selected
execution mode, explicit cycle policy/value, full normal fallback, media IDs,
configuration generation, and last clean-shutdown generation. The fallback
includes core, cycles, CPU type, memory, sound resources, boot source and media
bindings. Paths resolve through validated app-private metadata, never journal text.
Existing profiles migrate without changing their effective configuration.

Keep a separate durable per-attempt journal containing machine ID, unique attempt
ID, config generation, the allowlisted dynamic cycle candidate, normal
execution-settings snapshot and state. It MUST NOT
contain filesystem paths, media names, media hashes, guest contents, or copied
profile JSON. The authoritative record retains the media bindings; matching the
machine ID and configuration generation proves that recovery can regenerate the
complete normal launch from those bindings plus the journal's normal settings.
Atomic replacement must include flush/durability checks; any failure prevents
native start. Generated launch files are derived artifacts, never an independent
authority. Recovery regenerates and verifies them from the authoritative normal
record.

Selecting Dynamic requires the most recent session of that machine to have
reported guest shutdown and completed native unload/flush. Store this per machine:
running another machine must not erase it. An untouched verified new copy may
inherit clean provenance from its stopped source. Subsequent starts invalidate it.
Selecting Normal is always permitted once the process has stopped, including
during recovery, but does not override media quarantine.

## Start ordering

Under the session/mutation lock:

1. Reload the selected machine; validate role, generation, clean provenance,
   fallback, all media ownership, build capability and absence of an active
   session. Verify no path or file identity aliases another machine's writable
   files; reject symlinks and unresolved ownership. Include utility disks and
   attachments. Shared ISO access must be read-only; auxiliary outputs are scoped
   to this machine. Validate the effective config against these exact bindings.
2. Persist `prepared` with fallback and the new attempt ID. Failure stops here.
3. Generate, atomically publish and verify this attempt's launch configuration.
4. Persist `executing` before handing any writable media to native code. Only
   then may the dedicated emulator process load it. Failure stops here.

The child receives only this validated launch description. Debug launch paths
must use validated disposable fixtures and cannot bypass ownership checks.
Selection does not create an attempt journal or boot anything.

The diagnostic cycle policy is part of the authorization, not a caller hint.
Only the project-owned IDs for fixed 20k, fixed 30k and auto 80% limited to 30k
are allowed. The host writes the ID into `prepared`, derives its exact config,
and the child independently resolves the ID and requires the Binder value,
journal value and sole config line to match before native load. Free-form,
unknown, unlimited or mismatched policies fail. Recovery always restores the
saved Normal fallback, never the diagnostic cycle policy.

## State transitions

Feature 010 makes clean execution selection persistent: after verified clean
shutdown, restore the derived Normal launch description and clean provenance
while retaining selected DynRec. Replaying `closed-clean` performs the same
idempotent completion. All other interrupted attempts still restore Normal;
quarantine cannot be cleared by selecting a mode. Staged UI settings are saved
as one validated generation, never by booting or chaining partial selections.

| State | Meaning and next action |
| --- | --- |
| prepared | No native handoff allowed yet. Interrupted preparation restores normal. |
| executing | Native handoff may have occurred; assume guest writes are possible. |
| running | At least 10 completed emulator calls and two video publications over one second of unpaused execution. This is liveness, not guest correctness. |
| closed-clean | Guest power-off observed, unload/flush completed, child process exited; clean provenance may now be saved. |
| needs-check | Abnormal termination, user Exit without guest shutdown, or uncertain completion; restore normal and quarantine writable media. |
| blocked | Record/config damage or failed fallback persistence/verification; refuse this machine's Start. |

`running` retains the active attempt journal throughout the session. Only
verified closure clears it. Stale callbacks with a different attempt ID or
generation have no effect. Recovery is repeatable after another process death.
Never automatically boot a fallback or retry dynrec.
Restart is forwarded only to the active child within the same attempt; it does
not create, clear, or replace that attempt journal.

A foreground, unpaused attempt with no completed emulator call for 30 seconds
is treated as hung. The host remains responsive and offers Stop trial. Explicit
pause, background and focus-loss intervals suspend this timer. Stop requests
orderly termination first; after five seconds the child may be terminated, with
the media marked needs-check. UI destruction, Android process death and an
unfinished journal trigger conservative recovery, without claiming a proven
dynrec bug. Restart remains in the same attempt and does not clear its journal.

## Recovery and media health

Restore and verify the complete normal fallback before permitting another start.
A failed write, unreadable journal, missing fallback or config mismatch blocks
that machine with a recoverable error. Dismiss only hides the explanation.

Only `prepared`, where native handoff could not occur, qualifies for an exact
no-write recovery claim; verify hashes of all writable media against pre-trial
hashes. Any `executing` or later uncertainty is needs-check even if no frame
appeared. A first frame cannot prove that earlier disk writes did not occur.

For needs-check, ordinary Start remains disabled. The diagnostic procedure uses
a stopped-machine, read-only filesystem scan on the Debian host: verify partition
layout, FAT allocation/directory consistency, readable expected files and a
predefined persistence fixture. Inspect every writable volume. Record checker
version, result and media hashes. Unsupported filesystems or errors remain
quarantined; no automatic repair or restore is allowed. Explicit user-authorized
repair or replacement of the experimental copy is separate work. A successful
check enables a normal recovery boot, whose normal Windows shutdown re-establishes
clean provenance. The stable machine remains available throughout recovery.

For a user-authorized debug diagnostic, the named experimental copy may instead
take one explicit normal-core recovery boot to run its guest disk check and shut
down normally. This is not an automatic retry, cannot target a stable machine,
and may not clear `needs-check` until guest shutdown has been observed and the
normal native runner has stopped. User Exit, process death, or another uncertain
stop retains quarantine. In the debug UI, the experimental card's `Needs disk
check` action is that explicit choice and enters recovery boot directly; an
informational dialog is not a substitute for the action.

The checker accepts only a raw FAT volume, never a disk container, partitioned
image, or mounted block device. Those inputs are unsupported and remain
quarantined until a separately specified, read-only extractor is available. It
uses `fsck.fat -n` (or `dosfsck -n`) and `mtype` against the image directly,
requires one or more relative persistence fixtures to be readable without
printing their contents, and compares the image SHA-256 before and after every
operation. It never invokes a repair flag, mounts the image, or writes a result
into app storage. Its output contains only checker version, pass/fail, image
hash and fixture count.

## Process and build boundary

Each dynamic attempt runs in a dedicated Android emulator process. Wait for its
termination before releasing session ownership or opening any other machine.
The current process-global core reuse must never bridge dynamic and stable
sessions. This limits accidental retained-state contamination; a separate process
under the same app UID is not a security sandbox against arbitrary native writes.
Ownership validation and normal-core regression testing remain required.

Preserve pinned core/toolchain revisions. Compile-time capability and effective
decoder diagnostics must confirm ARM64 dynrec actually executes; silently falling
back to the interpreter invalidates the trial. Aggregate evidence must exclude
guest memory, PCM, paths and registration data; raw crash artifacts stay ignored.

All Activity-side commands for a dynamic attempt cross the child-process bridge:
surface, physical keyboard, captured relative mouse, touch, input cancellation,
pause/focus, restart and stop. The host process must not call its own native
runtime for an active dynamic attempt. A disconnected or unavailable bridge is
a trial failure and preserves `needs-check`.

The durable `running` state is liveness bookkeeping, not a readiness or
correctness pass. Progression to guest validation additionally requires a usable
Windows desktop and explicit successful keyboard and captured-relative-mouse
checks. Frames or audio produced by a frozen guest cannot satisfy that gate.

Before another Windows trial, run the versioned CPU fixture with no machine
selected. Its normal and dynamic variants execute in different non-exported
processes from separate newly generated app-cache images. Fixture services accept
only canonical paths below their dedicated cache directory and a fixed generated
configuration; they reject machine storage, arbitrary launch files, and caller
supplied commands. Each run is time-bounded. Stop/unload precedes result parsing.
Only a complete, checksummed result with the full expected test mask is accepted,
and normal/dynamic records must match exactly. Fixture evidence is build-specific
and cannot satisfy Windows desktop, input, shutdown, media-health, or performance
requirements.

The current pre-Windows gate is fixture v3. Its disposable image has a minimal
boot sector, a bounded protected-mode test stage loaded from declared consecutive
sectors, and a dedicated result sector outside that range. It retains v2's
cases, it must exercise paging with a handled non-present-page fault, verify CR2
and the fault error code before returning through `iret`, exercise software
interrupt and invalid-opcode exception gates, repeated linked branches, and
invalidate translated
code after a write spanning an x86 page boundary. It must additionally exercise
an ordered nested page-fault chain, validate every entry and reverse-order
return, and resume the original instruction without queue recovery. Build checks
reject sector overlap, truncation, unexpected hashes, or a stale result version.
A v1 or v2 pass does not authorize another Windows trial. The expanded v3 mask
is `0x3fff`, including REP-store partial progress and fault retry as well as
INVLPG operand-consumption checks (32-bit absolute, disp8,
SIB/disp32, and 16-bit absolute). Its reserved result word carries only fixed
source-owned assertion identifiers: zero means no assertion failure; any nonzero
value rejects the record. The fixture source, expected mask, and both core
patches participate in the build-specific capability token. FR-038 now requires
the complete suite set in `../expanded-cpu-coverage.md` before another Windows
trial; the present `0x3fff` fixture alone no longer satisfies that prerequisite.

A guarded guest diagnostic may sample the core's decoder name only from the
emulator thread and publish aggregate residency counts for dynrec, normal,
page-fault and other/special decoder classes. The bridge may also carry the
configured and current class names. It must not carry CS:EIP, linear or physical
addresses, registers, memory, paths, or guest-derived strings. Residency is
diagnostic evidence, not readiness; sustained fallback or page-fault residency
blocks performance testing.

When the source-built fixture has a reproducible failure,
the same emulator-thread snapshot may include aggregate prepared/delivered
page-fault counts, successful page-fault gate-entry and `iret` counts, delivered
double-fault count, guest-reset count, and a fixed halted decoder class. These
values are diagnostic only. Exception addresses,
error values, register state, guest memory or text, paths, and per-exception
records remain prohibited.
