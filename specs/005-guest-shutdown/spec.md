# Feature Specification: Guest Shutdown Returns Home

**Feature**: `005-guest-shutdown`

**Created**: 2026-09-06

**Status**: Implemented; SM-T500 acceptance pending

## User Scenario & Testing

### User Story 1 - Finish a Windows session normally (Priority: P1)

As a Windows guest user, I can choose Shut Down inside Windows and return to the
RoboWindows machine list instead of watching the guest boot again.

**Acceptance Scenarios**:

1. **Given** a running Windows 9x guest, **When** Windows completes an APM power-
   off, **Then** the core reports guest shutdown, flushes and unloads the guest,
   and RoboWindows returns to Machines.
2. **Given** a running guest, **When** the user selects RoboWindows Restart,
   **Then** the same guest resets and remains on the session screen.
3. **Given** a guest performs a BIOS reboot rather than power-off, **When** the
   core handles that reboot, **Then** it reloads the generated configuration
   without exposing the upstream menu.
4. **Given** shutdown is in progress, **When** RoboWindows observes completion,
   **Then** it releases pointer capture, cancels held input, and records an
   orderly stopped session once.

## Requirements

- **FR-001**: Windows APM power-off MUST be distinguished from BIOS reboot before
  the core's reboot handling runs.
- **FR-002**: A guest power-off MUST emit the libretro frontend shutdown signal;
  it MUST NOT reload the guest configuration.
- **FR-003**: The native host MUST expose a distinct guest-shutdown session state
  until Android acknowledges it by stopping/joining the session.
- **FR-004**: The session screen MUST monitor a running guest and return to the
  machine list after observing guest shutdown.
- **FR-005**: Explicit Restart and genuine BIOS reboot behavior MUST remain
  unchanged.
- **FR-006**: No DOSBox Pure menu, shutdown screen, or other upstream interface
  may become visible during the transition.

## Success Criteria

- **SC-001**: Host tests verify that guest-shutdown state survives core cleanup
  while every non-shutdown completion becomes stopped.
- **SC-002**: Patch verification proves the pinned source cleanly accepts the
  power-off-before-reboot distinction.
- **SC-003**: On the SM-T500, Shut Down in the backed-up Windows 98 profile
  returns to Machines once without rebooting Windows.

## Explicitly Out of Scope

- Treating Windows Restart as shutdown.
- Android activity termination after guest shutdown.
- Changing Windows power-management configuration or drivers.
