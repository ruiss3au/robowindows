# Tasks: Session Controls and Mouse Capture

- [ ] T010 Share normal/DynRec toolbar, auto-hide/reveal and pause/restart state;
  retain readiness only in explicit diagnostic sessions.
- [ ] T011 Route session-bound imported media through the isolated DynRec bridge
  with owned-path validation and nonfatal errors; test stale/invalid requests.
- [ ] T012 Run host/hygiene/pins and both builds; record device acceptance or
  disconnected-device limitation without touching real guests.

- [x] T001 Specify observable overlay, timeout, capture, release, and lifecycle behavior.
- [x] T002 Define the session input contract and rollback approach.
- [x] T003 Add host-testable session UI/capture state transitions.
- [x] T004 Replace the reserved toolbar with a non-resizing overlay.
- [x] T005 Route only captured relative mouse events to a running guest.
- [x] T006 Implement top-edge, Back, lifecycle, disconnect, and exit release.
- [x] T007 Run host tests, repository hygiene, and the debug Android build.
- [ ] T008 Validate the disposable-guest checklist on the SM-T500.
- [ ] T009 Validate Windows 98 desktop, dragging, SimCity 2000, and AoE2 using
      the already-backed-up profile.
