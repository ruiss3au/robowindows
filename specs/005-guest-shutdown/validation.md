# Validation Record

## 2026-09-06 — Implementation

- Root cause confirmed in pinned DOSBox Pure: `DBP_OnBIOSPoweroff` sets both
  `dbp_biospoweroff` and `dbp_biosreboot`, while the reboot condition was tested
  first. The isolated patch now tests power-off first and calls the frontend
  shutdown callback; the BIOS reboot branch remains immediately after it.
- `scripts/test-host.sh` passed, including session-state cleanup coverage.
- `scripts/fetch-sources.sh --verify-only` accepted the updated patch against
  the exact pinned DOSBox Pure revision.
- `scripts/check-repository.sh` and `git diff --check` passed.
- `scripts/build-android.sh --debug` passed and the resulting debug APK was
  installed on the connected SM-T500 without clearing application data. The
  RoboWindows process was confirmed stopped before installation.

## 2026-09-06 — SM-T500 acceptance

- On the unlocked, foregrounded SM-T500, Windows shut down normally and
  RoboWindows returned to Machines without rebooting the guest.
- The user then started the machine again from Machines. The guest ran normally
  with no Android-process crash after the static core's unload/load transition.
- The accepted debug APK was installed without clearing RoboWindows app data.
