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

## Pending hands-on acceptance

- Start the already-backed-up Windows 98 profile on the unlocked, foregrounded
  SM-T500 and choose Start > Shut Down > Shut down the computer.
- Pass condition: the core logs `guest requested shutdown`, unloads cleanly, and
  RoboWindows returns once to Machines without showing another Windows boot.
- Also press the RoboWindows Restart control once to confirm it still resets the
  guest and remains in the session.
