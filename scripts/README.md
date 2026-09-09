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
| `scripts/build-win98-benchmark.sh` | Build the source-owned Windows 98 benchmark EXE/ISO, map and checksums | Writes ignored `artifacts/win98-benchmark/` output |
| `scripts/parse-win98-benchmark.sh FILE` | Strictly validate and normalize `RWBENCH.TXT` | Read-only |
| `scripts/summarize-benchmark-telemetry.sh PROFILE LOG` | Validate and summarize matched benchmark telemetry | Read-only |
| `bash scripts/summarize-runtime-timing.sh LOG` | Validate optional per-interval process/callback timing diagnostics | Read-only |
| `scripts/capture-sm-t500-benchmark.sh --profile PROFILE` | Capture bounded telemetry, result screen and temperature for an already-running experimental guest | Does not start, stop, or change the guest; writes ignored evidence |
| `scripts/finalize-sm-t500-benchmark.sh ...` | Merge a strict guest result with host metrics and explicit human checks | Refuses to overwrite an existing final report |
| `scripts/compare-sm-t500-benchmarks.sh ...` | Compare three valid fixed-20k DynRec reports with three matched Normal reports | Emits medians/deltas; never promotes while long gates are pending |
| `scripts/test-host.sh` | Compile and run Java/C++ host checks | Temporary files only |
| `scripts/check-repository.sh` | Check Git hygiene and shell syntax | Read-only |
| `scripts/check-experimental-fat.sh --image FILE --expected PATH` | Read-only FAT health check for a quarantined experimental volume | Requires `fsck.fat`/`dosfsck` and `mtype`; never mounts or repairs |
| `scripts/test-sm-t500-core.sh` | Run disposable settings/storage, input and direct-core device probes; silent fixture validates stream opening and schema-3 presentation, not audio continuity | Requires stopped machines; installs APK and creates temporary tablet files |
| `scripts/test-sm-t500-realtime.sh` | Legacy realtime clone/environment test modes | Some modes create explicit host/device clones |
| `scripts/cleanup-tablet-storage.sh --preflight` | Verify the two disposable tablet areas and protected library | Read-only; requires RoboWindows at Machines |
| `scripts/cleanup-tablet-storage.sh --delete` | Permanently remove only verified disposable tablet areas | Re-runs preflight and checks library invariants |

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

`check-experimental-fat.sh` accepts a raw FAT volume only. It deliberately
rejects VHD/VHDX/QCOW2 containers and partitioned disk images rather than using
loop mounts or attempting a repair. Supply each expected persistence fixture as
a relative DOS-style path (for example `WINDOWS/WIN.INI`). Its output is safe to
record because it omits local paths and fixture contents; it is not an app-side
unquarantine action by itself.
