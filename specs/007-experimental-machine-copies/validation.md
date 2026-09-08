# Validation: Experimental Machine Copies

## Host

- 2026-09-06: `scripts/test-host.sh`, `scripts/check-repository.sh`, and `git diff --check`
  passed.
- 2026-09-06: `scripts/build-android.sh --debug` passed. The normal sandbox cannot provide
  Gradle with a wildcard interface, so the identical local build was rerun with approved local
  interface access.
- 2026-09-08: The SM-T500 reported about 4.2 GB free, sufficient for the source runtime disk
  plus the 256 MiB margin. Copy creation nevertheless failed because the initial implementation
  queried free space on its not-yet-created target directory, which reports zero on this Android
  filesystem. The check now queries the existing `machines` parent directory.
- 2026-09-08: Host checks and a debug Android build passed after adding throttled copy progress
  across source verification, copying, target verification, and finalization. The UI update is
  installed for the next user-authorized copy acceptance run.
- 2026-09-08: Host checks and a debug Android build passed after adding confirmed deletion. The
  debug self-test deletes only an isolated synthetic copy and verifies its stable source remains.
  The tablet update is installed without exercising deletion against user machines.
- 2026-09-08: `git diff --check`, `scripts/test-host.sh`, `scripts/check-repository.sh`, and the
  debug Android build passed after adding the 20,000-cycle normal-core experimental profile.
  Installation is deferred until the active AoE2 trial is stopped, so it cannot interrupt a guest.
- 2026-09-08: The same host checks and debug Android build passed after adding the bounded
  30,000-cycle normal-core trial. Installation remains deferred until the active guest is stopped.

## SM-T500

- 2026-09-08: The 30,000-cycle normal-core trial on the experimental Windows 98/AoE2 copy was
  visually faster than lower fixed-cycle trials but audio broke up. This establishes that 30k
  exceeds the SM-T500's sustainable normal-core real-time budget for this workload; it is useful
  clone-only evidence, not a stable-machine recommendation.
- Additional acceptance remains pending. The tablet must be unlocked and RoboWindows foregrounded.
  Shut down Incoming inside Windows before selecting `Create experimental copy`; no test may boot
  or modify Incoming after copy creation.
