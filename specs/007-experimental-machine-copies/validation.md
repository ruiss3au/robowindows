# Validation: Experimental Machine Copies

## Host

- 2026-09-06: `scripts/test-host.sh`, `scripts/check-repository.sh`, and `git diff --check`
  passed.
- 2026-09-06: `scripts/build-android.sh --debug` passed. The normal sandbox cannot provide
  Gradle with a wildcard interface, so the identical local build was rerun with approved local
  interface access.

## SM-T500

- Pending user-authorized acceptance. The tablet must be unlocked and RoboWindows foregrounded.
  Shut down Incoming inside Windows before selecting `Create experimental copy`; no test may boot
  or modify Incoming after copy creation.
