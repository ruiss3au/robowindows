# Tasks: Guest Shutdown Returns Home

- [x] T001 Specify shutdown, reboot, cleanup, and UI behavior.
- [x] T002 Document the minimal patch and rollback design.
- [x] T003 Distinguish APM power-off from BIOS reboot in the pinned core patch.
- [x] T004 Preserve a native guest-shutdown status through core cleanup.
- [x] T005 Return the running Android session to Machines on guest shutdown.
- [x] T006 Add host regressions and verify patch reproducibility.
- [x] T007 Build and install the Android debug APK on the SM-T500.
- [x] T008 Shut down the backed-up Windows 98 profile normally and confirm one
      return to Machines without a guest reboot.
- [x] T009 Reuse the initialized static core for the next machine launch.
- [x] T010 Start a machine after normal Windows shutdown on the SM-T500 and
      confirm no Android-process crash.
