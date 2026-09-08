# Dynamic execution safety contract

## Durable data and selection

Persist one versioned authoritative machine record containing role, selected
execution mode, explicit cycle policy/value, full normal fallback, media IDs,
configuration generation, and last clean-shutdown generation. The fallback
includes core, cycles, CPU type, memory, sound resources, boot source and media
bindings. Paths resolve through validated app-private metadata, never journal text.
Existing profiles migrate without changing their effective configuration.

Keep a separate durable per-attempt journal containing machine ID, unique attempt
ID, config generation, normal execution-settings snapshot and state. It MUST NOT
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

## State transitions

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
