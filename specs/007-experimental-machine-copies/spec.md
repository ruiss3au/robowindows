# Feature Specification: Experimental Machine Copies

**Feature**: `007-experimental-machine-copies`
**Status**: Implemented; SM-T500 acceptance pending

## User Story

As an owner of a working RoboWindows machine, I can create `<machine name> - copy` for
performance trials without sharing its writable disk with my stable machine.

## Requirements

- **FR-001**: Copy creation MUST require that RoboWindows has no interrupted or active session and
  that the source guest most recently reported normal shutdown from inside the guest.
- **FR-002**: The copied profile MUST have a separate writable runtime disk, created by a
  staged, flushed, SHA-256-verified copy before it is published.
- **FR-003**: A copy failure MUST remove only its newly created incomplete directory and MUST
  not alter the source machine, its disk, or its launch configuration.
- **FR-004**: Read-only installation media may be shared; every attached writable runtime
  medium MUST be copied independently.
- **FR-005**: Existing profiles MUST remain stable profiles after metadata migration. New copies
  MUST be visibly marked experimental and default to `core=normal`, `pentium_slow`, and
  `cycles=fixed 12000`.
- **FR-006**: Experimental profiles MAY select only fixed 10,000, 12,000, 14,000, 20,000, or 30,000
  cycles on the normal CPU core. Automatic, dynamic, and raw upstream configuration MUST not be
  exposed. The 20,000-cycle profile remains an experimental trial and MUST recover to the safe
  12,000-cycle profile after an interrupted session.
- **FR-007**: An interrupted experimental run MUST restore its last known-safe cycle profile on
  the next app start without changing guest media.
- **FR-008**: While copy creation is in progress, RoboWindows MUST show a product-owned
  determinate progress indicator and current stage: source verification, copying, target
  verification, or finalization.
- **FR-009**: Machine settings MUST offer `Delete machine`. It MUST require explicit
  confirmation, refuse while a session is active or interrupted, and remove only the selected
  machine's app-private directory and profile; no machine is deleted merely by showing the
  control.
- **FR-010**: The selected experimental cycle profile MUST use the shared RoboWindows selected
  button treatment: dark surface, primary checkmarked label, and no redundant status line.

## Acceptance Scenarios

1. Given a normally stopped stable machine, when its owner selects `Create experimental copy`,
   then `<machine name> - copy` appears with a distinct writable disk and unchanged source hash.
2. Given insufficient storage, a checksum mismatch, or interruption while copying, when creation
   ends, then no new usable profile exists and the stable machine is unchanged.
3. Given an experimental copy, when a fixed-cycle trial is selected, then its launch file uses
   the normal core, requested fixed cycles, existing SB16 resources, and `pentium_slow`.
4. Given a failed 14,000-cycle experimental session, when RoboWindows restarts, then the copy
   returns to its safe 12,000-cycle profile while the stable machine remains unchanged.
5. Given a multi-gigabyte source disk, when copy creation runs, then RoboWindows visibly reports
   progress through source verification, copying, target verification, and finalization until it
   either creates the new machine or reports failure.
6. Given a stopped machine, when its owner selects `Delete machine`, then RoboWindows explains
   that the selected machine and its disk will be permanently removed and changes nothing until
   the owner confirms. Given confirmation, no other profile or directory is removed.
7. Given a 12,000-cycle experimental profile, when its settings are shown, then only its
   `12k cycles` button has the shared dark, checkmarked selected state.
8. Given an experimental copy, when its owner selects `20k cycles` or `30k cycles`, then its
   launch file uses the corresponding fixed-cycle value; the stable machine remains unchanged.

## Out of Scope

- ARM64 dynamic recompilation (Feature 008), automatic CPU mode, GPU renderer work, and promotion of a trial
  as performance-validated.
- Copying while a guest is running, or modifying a user-owned source disk during testing.
