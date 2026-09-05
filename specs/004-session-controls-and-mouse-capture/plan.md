# Implementation Plan: Session Controls and Mouse Capture

**Feature**: `004-session-controls-and-mouse-capture` | **Date**: 2026-09-05

## Design

- Put the guest and controls in one `FrameLayout`. The guest always uses match
  parent bounds; the toolbar is a top-aligned overlay.
- Keep timing and capture policy in a small Android-independent
  `SessionUiState` so transitions are executable as host Java tests.
- Let `MainActivity` observe only the first uncaptured physical-mouse button
  press inside the guest. Let `GuestDisplayView.onCapturedPointerEvent` be the
  only producer of mouse events for a running guest.
- Treat pointer capture as asynchronous. A callback confirms success; a short
  timeout reports failure. An explicit release changes policy state before the
  Android callback so it is not mistaken for a failed request.

## Safety and Rollback

- Every lifecycle release also emits native input cancellation, preventing held
  buttons from surviving capture or focus loss.
- No emulator-core or guest-media format changes are required. Rollback is the
  isolated Android UI/state change plus its tests and specification.
- Device validation uses a disposable guest first. The backed-up Windows 98
  profile is used only for the final non-destructive compatibility check.

## Constitution Check

- The requirements and contract precede implementation.
- The design uses standard API 26 pointer-capture behavior and stays portable.
- Relative motion, buttons, wheels, capture, release, and cancellation are
  independently observable.
- The overlay and all failures remain RoboWindows-owned UI.
- No dependencies, upstream patches, proprietary media, or device identifiers
  are introduced.

