# Tooling catalog

Run scripts from the repository root. Local generated output, dependencies,
guest media, and backups are intentionally outside Git.

## Setup and dependencies

| Command | Purpose | Side effects |
|---|---|---|
| `scripts/bootstrap-debian.sh` | Install pinned Debian/Android build prerequisites | Interactive; user-run; installs Debian packages and an SDK |
| `scripts/bootstrap-gradle-wrapper.sh` | Regenerate the pinned Gradle wrapper | Interactive; downloads verified Gradle files |
| `scripts/fetch-sources.sh` | Fetch exact upstream revisions from `lock/sources.lock` | Network and ignored `third_party/` writes |
| `scripts/fetch-sources.sh --verify-only` | Verify existing dependency checkouts | Read-only |

## Build and validation

| Command | Purpose | Side effects |
|---|---|---|
| `scripts/build-android.sh --debug` | Build the ARM64 debug APK and checksum | Writes ignored build/artifact output |
| `scripts/test-host.sh` | Compile and run Java/C++ host checks | Temporary files only |
| `scripts/check-repository.sh` | Check Git hygiene and shell syntax | Read-only |
| `scripts/test-sm-t500-core.sh` | Run the disposable direct-core device test | Installs APK and creates temporary tablet files |
| `scripts/test-sm-t500-realtime.sh` | Legacy realtime clone/environment test modes | Some modes create explicit host/device clones |

## Tablet operations

| Command | Purpose | Safety behavior |
|---|---|---|
| `scripts/install-debug.sh [APK]` | Update the debug app with `adb install -r` | Preserves app data; does not boot a guest |
| `scripts/backup-device-profile.sh --machine-id ID --name NAME` | Stream a stopped machine to Debian | Refuses a running app or existing destination; verifies raw disk bytes |
| `scripts/verify-backup.sh DIRECTORY` | Verify a format-1 backup offline | Read-only |

Device scripts resolve ADB from `ADB`, `ANDROID_SDK_ROOT`, `ANDROID_HOME`,
`PATH`, or the documented per-user RoboWindows SDK. If exactly one authorized
device is attached it is selected automatically. With multiple devices, set:

```sh
export ROBOWINDOWS_DEVICE_SERIAL=your-adb-serial
```

The primary acceptance model is `SM-T500`. A deliberate porting test may set
`ROBOWINDOWS_EXPECTED_MODEL` to another exact Android model.

## Local-data boundary

- `artifacts/`: generated APKs, checksums, screenshots, and test evidence.
- `backups/`: Debian-side proprietary guest backups. Never publish this folder.
- `third_party/`: reproducible ignored dependency checkouts.
- `build/`, `.gradle/`, `android/build/`: generated build state.

No script may silently overwrite a guest disk or backup. Device tests use
disposable fixtures or explicit clones. Shut down the guest from Windows and
stop RoboWindows before creating or restoring a backup.
