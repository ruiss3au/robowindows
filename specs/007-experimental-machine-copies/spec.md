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
- **FR-006**: Experimental profiles MAY select only fixed 10,000, 12,000, or 14,000 cycles on
  the normal CPU core. Automatic, dynamic, and raw upstream configuration MUST not be exposed.
- **FR-007**: An interrupted experimental run MUST restore its last known-safe cycle profile on
  the next app start without changing guest media.

## Acceptance Scenarios

1. Given a normally stopped stable machine, when its owner selects `Create experimental copy`,
   then `<machine name> - copy` appears with a distinct writable disk and unchanged source hash.
2. Given insufficient storage, a checksum mismatch, or interruption while copying, when creation
   ends, then no new usable profile exists and the stable machine is unchanged.
3. Given an experimental copy, when a fixed-cycle trial is selected, then its launch file uses
   the normal core, requested fixed cycles, existing SB16 resources, and `pentium_slow`.
4. Given a failed 14,000-cycle experimental session, when RoboWindows restarts, then the copy
   returns to its safe 12,000-cycle profile while the stable machine remains unchanged.

## Out of Scope

- ARM64 dynamic recompilation, automatic CPU mode, GPU renderer work, and promotion of a trial
  as performance-validated.
- Copying while a guest is running, or modifying a user-owned source disk during testing.
