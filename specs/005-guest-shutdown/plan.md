# Implementation Plan: Guest Shutdown Returns Home

**Feature**: `005-guest-shutdown` | **Date**: 2026-09-06

## Design

- Extend the existing isolated DOSBox Pure patch so `dbp_biospoweroff` invokes
  `RETRO_ENVIRONMENT_SHUTDOWN` before the broader `dbp_biosreboot` branch.
- Add a native `GUEST_SHUTDOWN` session state. Preserve it across core cleanup
  until `stopSession` joins the core thread and acknowledges completion.
- Poll session status after startup with a generation token. A shutdown state
  returns to Machines; stale callbacks from an earlier session do nothing.
- Keep explicit Restart on the existing `retro_reset` path.
- Treat DOSBox Pure initialization as process-scoped. Each RoboWindows session
  unloads its guest, but later sessions reuse that initialized core and load a
  new guest; `retro_deinit` is not a per-session operation for the statically
  linked core.

## Safety and Rollback

- Shutdown continues through audio stop, `retro_unload_game`, and the existing
  orderly session marker before the UI changes screen. The core stays
  initialized for the Android process so its global state is not reinitialized
  on the next launch.
- No guest media or profile schema changes are made.
- Rollback consists of the second upstream patch hunk, native status, Android
  monitor, and their tests.

## Constitution Check

- Observable behavior and regression boundaries are specified first.
- The upstream change is one ordered condition in the existing isolated patch.
- Guest state uses the existing clean unload/join path.
- RoboWindows remains the sole visible host interface.
