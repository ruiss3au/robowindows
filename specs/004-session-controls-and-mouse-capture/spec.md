# Feature Specification: Auto-Hiding Session Controls and Relative Mouse Capture

## 1.0 Alpha session parity — 2026-09-10

FR-001–010 apply equally to ordinary Normal and DynRec starts: identical Exit,
machine title, Pause/Resume, Restart, Change media and optional counter controls.
Both use the same four-second timeout and Back/top-edge reveal; pause, focus
loss, capture failure and host dialogs retain controls. No persistent bottom
readiness bar in ordinary DynRec sessions. Explicit debug diagnostic launches
may retain readiness evidence controls; hidden controls never disable watchdogs.
Change media must target the originating session only, use document-mediated
app-private imports and route DynRec operations to its isolated process. Failed,
cancelled or stale imports must not change another session or force guest exit.
Restart clears manual pause and stale media requests, releases input and resets
UI/child progress bookkeeping while preserving the active journal.

**Feature**: `004-session-controls-and-mouse-capture`

**Created**: 2026-09-05

**Status**: In implementation

## User Scenarios & Testing

### User Story 1 - Use the full session display (Priority: P1)

As a guest user, I see Windows at a stable size while RoboWindows controls appear
temporarily over the guest instead of reserving display space.

**Acceptance Scenarios**:

1. **Given** a session has started, **When** four seconds pass without host
   interaction, **Then** the controls hide and the guest surface keeps exactly
   the same dimensions.
2. **Given** controls are hidden, **When** touchscreen contact begins within the
   top 32 dp or Android Back is invoked, **Then** mouse capture is released,
   held guest mouse state is cancelled, and controls are shown for four seconds.
3. **Given** controls are visible, **When** Exit, Pause, Restart, or Change Media
   is used, **Then** RoboWindows performs that action without producing guest
   mouse movement.

### User Story 2 - Use an unbounded physical mouse (Priority: P1)

As a physical-mouse user, I can move continuously in every direction without
the Android cursor reaching a display edge and stalling the Windows pointer.

**Acceptance Scenarios**:

1. **Given** a running, uncaptured session, **When** the first physical-mouse
   button press occurs inside the guest, **Then** RoboWindows requests Android
   pointer capture and does not forward that press to the guest.
2. **Given** capture succeeds, **When** the mouse moves, clicks any of its first
   three buttons, or scrolls either wheel axis, **Then** only captured relative
   events are sent to the guest and the Android cursor is hidden.
3. **Given** capture fails or times out, **When** RoboWindows detects the failure,
   **Then** controls remain visible and a RoboWindows-owned error is shown.
4. **Given** capture was released, **When** focus returns to the running session,
   **Then** controls are visible and capture is not re-entered until another
   physical-mouse click inside the guest.

## Edge Cases

- An uncaptured mouse moves over the guest or toolbar without clicking.
- Capture changes asynchronously after a request or explicit release.
- Pause, focus loss, peripheral disconnect, session exit, or activity destruction
  occurs while a guest button is held.
- Touch and captured physical-mouse events arrive close together.
- A physical keyboard sends Escape while capture is active.

## Requirements

- **FR-001**: The guest surface MUST fill the session content area and MUST NOT
  be resized when session controls appear or disappear.
- **FR-002**: Exit, machine name, Pause/Resume, Restart, and Change Media MUST be
  presented in a RoboWindows-owned top overlay.
- **FR-003**: Controls MUST be visible at session start and auto-hide after 4000 ms.
- **FR-004**: The first uncaptured physical-mouse button press inside the guest
  MUST request pointer capture and MUST NOT be forwarded to the guest.
- **FR-005**: While a session is running, guest mouse input MUST be produced only
  by captured pointer events using Android relative X/Y, button state and action
  button, and vertical/horizontal wheel axes.
- **FR-006**: Uncaptured cursor coordinates MUST NOT produce guest movement.
- **FR-007**: A touchscreen down event in the top 32 dp and Android Back while
  captured or controls are hidden MUST release capture, cancel held guest mouse
  state, and reveal controls for 4000 ms.
- **FR-008**: Capture MUST be released and guest input cancelled on pause, focus
  loss, input-device disconnect, session exit, and activity destruction.
- **FR-009**: Returning to a running session MUST show controls without
  automatically recapturing the pointer.
- **FR-010**: Keyboard Escape and ordinary external-keyboard events MUST remain
  guest input and MUST NOT serve as the capture-release gesture.

## Success Criteria

- **SC-001**: Host state tests cover startup, timeout, capture request/success/
  failure, reveal actions, pause/resume, and exit cleanup.
- **SC-002**: Host and source checks establish that uncaptured session motion is
  not forwarded and `GuestDisplayView.onCapturedPointerEvent` is the single
  session mouse-event producer.
- **SC-003**: On the SM-T500, repeated motion beyond every physical screen edge
  continues to move the guest pointer while captured.
- **SC-004**: On the SM-T500, showing and hiding controls leaves guest-surface
  dimensions unchanged.

## Explicitly Out of Scope

- Changing Android system-bar behavior.
- Mouse sensitivity scaling or acceleration.
- Automatic recapture on focus return or pointer movement.
- Touchscreen control of the guest beyond the release gesture.
