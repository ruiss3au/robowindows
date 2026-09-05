# Quickstart: Validate Real-Time Emulation on the SM-T500

## Safety prerequisites

1. Use the app-private clone of the installed Windows image, never the only original.
2. Record its checksum before a crash or dynrec experiment.
3. Keep proprietary media, registration data, reports containing private paths, and disk
   images outside Git.
4. Keep the SM-T500 unlocked, RoboWindows full-screen and foreground, with no split-screen.

ADB is sufficient for installation, launch, logcat, lifecycle control, file transfer within
the app's debuggable sandbox, and tombstone collection available to the app. SSH is not
required for this feature. Root would only be needed for protected system diagnostics and
is not part of the product or acceptance procedure.

## Build and launch

Use the repository's existing build/install path and select the conservative Windows 98
profile. If Debian prerequisites are missing, add them to a user-run shell script as
required by the project workflow; do not install host packages implicitly.

Before measurement, verify:

```sh
adb devices
export ROBOWINDOWS_DEVICE_SERIAL=the-serial-shown-by-adb
adb -s "$ROBOWINDOWS_DEVICE_SERIAL" shell dumpsys window
adb -s "$ROBOWINDOWS_DEVICE_SERIAL" shell dumpsys audio
```

The device must report as authorized, RoboWindows must be the focused full-screen app, and
media output must not be muted or interrupted.

## Reference workload

After startup settles:

1. Start the versioned timer-and-tone test utility built from redistributable source, which
   records guest timer start/end values in a result file; then measure five minutes containing
   desktop idle, window movement, and the same Windows reference sound played ten times.
2. Perform ten pause/resume cycles and repeat the sound after each resume.
3. Run the validated workload for 30 minutes to expose thermal throttling.
4. Capture the bounded runtime telemetry and app/native crash output.

5. Repeat the timer-and-tone test with the pinned redistributable DOS workload.

Pass requires production within ±2% of the actual opened host rate (48,000 on the current
tablet), zero post-start underruns during the ten sounds, independently measured guest-time
rate within ±5%, at least 15 presented fps, correct pitch/duration, and no crash or
persistent degradation.

## Dynrec experiment

Run dynrec only after the conservative path passes, only on a checksummed clone, and only
through the explicitly experimental profile. Capture a symbolized native failure. After an
abnormal exit, confirm that the next start selects the last-known-safe profile and that no
automatic retry loop occurs.

Require an unchanged whole-image checksum only if telemetry proves failure occurred before
guest execution. For booted tests, verify filesystem health and expected files on the clone;
normal Windows writes make whole-image equality an invalid requirement.

## Evidence to retain

- APK/build identifier and checksum
- pinned DOSBox Pure revision and patch identifier
- device fingerprint and thermal-test conditions
- profile and workload identifiers
- clone checksums before and after the stated workload
- telemetry summary and pass/fail reason

Do not commit the guest image, ISO, registration code, private paths, PCM, or screenshots
containing proprietary guest material.
