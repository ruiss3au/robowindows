# Implementation Plan: Session Controls and Mouse Capture

## Alpha parity implementation and rollback

Device smoke testing must cover Android canonical paths and the existing copy
layout: a copied profile ID can differ from its writable-disk directory. New
attachments belong to `machines/<profile-id>/media`, as used by the importer;
the isolated media boundary must validate that directory, not infer it from the
guest disk. Reject symbolic-link media entries and traversal without rejecting
platform-owned canonical aliases above the app's storage root.

Live attachment registration preserves the active configuration generation;
stopped-machine imports still increment it. Validate the active Normal marker or
DynRec journal before and after copying, so registration cannot invalidate the
running journal or publish after a session ends. This is attachment metadata,
not permission to edit CPU/display/launch configuration during execution.

Extract a single toolbar factory in MainActivity for Normal and DynRec. Reuse
SessionUiState rather than add another timer. Only explicit debug diagnostics
pin controls and render readiness buttons; ordinary starts never fabricate
readiness evidence. Keep overlay geometry and translucent styling unchanged.
Route change-media through controller/client/service with registered-media checks
in the parent and independent canonical per-machine confinement plus durable
attempt validation in the non-exported child. Do not rely on cross-process
SharedPreferences refreshing after an import; the child owns no registration
mutation. Bad media gets a nonfatal error reply. Import off the UI thread; bind picker and completion to the session
generation so stopped/replaced sessions cannot receive stale media. Imported
files may remain as registered attachments if the session ends before mounting.
Preserve lifecycle/audio-focus pause while importing/restarting. Refresh Pause
button state on restart and renew the child watchdog startup window.
Block another start or restart until an outstanding import finishes.
Host-test timing/state, stale requests and media path rejection. Build both APK
variants; use disposable tests only if the tablet is connected, unlocked and all
guests stopped. Never boot or stop a real guest for this change.
Rollback is a forward toolbar/bridge fix; never rewrite profiles or guest disks.

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
