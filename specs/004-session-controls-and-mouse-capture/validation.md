# Validation Record

## 1.0 Alpha parity — 2026-09-10

Shared-toolbar implementation, host state/path/picker tests and both builds pass.
After the user freed storage, the alpha update installed and the disposable
SM-T500 storage/input/core smoke test passed, including presented frames,
pause/resume, Normal media change, restart and clean stop. Device findings fixed
canonical Android storage aliases and copied-profile import-directory validation;
host and device regressions passed. T010/011 implementation is present; native
DynRec media/toolbar interaction acceptance remains pending. Real profiles were
clean/stopped and unchanged. See the [alpha evidence](../../docs/alpha-1.0.md).

## 2026-09-05 — Host and Android build

- `scripts/test-host.sh`: passed. The new state checks cover startup visibility,
  the 4000 ms timeout, capture request de-duplication, success, callback failure,
  timeout, retry, reveal/release, pause/resume, and exit cleanup.
- The native input test confirms captured relative X/Y, all three button bits,
  and both wheel axes remain in the typed event, while uncaptured mouse records
  are rejected by the guest-input boundary.
- `scripts/check-repository.sh`: passed.
- `scripts/build-android.sh --debug`: passed with the pinned source revisions,
  SDK, NDK, and toolchain. The sandboxed attempt could not initialize Gradle's
  local file-lock network service; the same documented build succeeded outside
  that restriction.

## SM-T500 status

- Read-only discovery through `scripts/lib/android-device.sh` found the expected
  SM-T500.
- The debug RoboWindows process was initially running in the background, so the
  disposable test was deferred until the user stopped it.
- `scripts/test-sm-t500-core.sh` then ran with its self-cleaning blank-disk
  fixture. Logs confirmed the persistence/input probes, guest boot, a 640 x 400
  frame, pause/resume, media replacement, restart, and clean shutdown without a
  Java or native fatal entry.
- The script did not print its overall pass marker: its audio assertion expects
  an `audio ... start result=0` record, while this fixture's generated launch
  configuration selects `nosound=true`; this run logged stream creation but no
  prebuffered-start record. No audio-pass claim is made from this run.
- Hands-on pointer capture, stable surface dimensions, top-edge/Back release,
  and Windows 98 application checks remain pending. Run them only after the
  guest is shut down from inside Windows and RoboWindows is stopped; keep the
  tablet unlocked and RoboWindows foregrounded for visual/input checks.
