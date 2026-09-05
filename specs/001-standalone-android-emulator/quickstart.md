# Quickstart Validation

This document defines the first implementation checkpoint. Commands are targets,
not yet expected to work until their tasks are implemented.

1. Review and run `scripts/bootstrap-debian.sh` manually.
2. Run `scripts/fetch-sources.sh`; confirm all three revisions match
   `lock/sources.lock`.
3. Run `scripts/build-android.sh --debug`.
4. Confirm exactly one authorized SM-T500 appears in `adb devices -l`.
5. Install the debug APK and launch the diagnostics activity.
6. Verify the Inateck mixed-source keyboard emits matched key down/up events.
7. Verify the Logitech LIFT emits relative motion, three buttons, vertical wheel,
   capture/release, disconnect, and reconnect.
8. Background and resume the app; confirm capture is released safely and the
   activity remains responsive.
9. Save sanitized diagnostic output and the APK/build-manifest checksums under
   `artifacts/` without device serials or Bluetooth identifiers.
10. Exercise startup, pause/resume, runtime controls, error handling, and exit;
    fail the checkpoint if any upstream DOSBox Pure/Unleashed/ZillaLib interface
    or notification becomes visible.

The checkpoint fails if it requires root, maps the keyboard as a gamepad, traps
the pointer, exposes device identifiers, reveals upstream UI, or cannot be
reproduced from pins.
