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
| `scripts/build-android.sh --release` | Build unsigned ARM64 release APK and checksum for verification | Writes ignored output; does not sign, install or publish |
| `scripts/build-win98-benchmark.sh` | Build the source-owned Windows 98 benchmark EXE/ISO, map and checksums | Writes ignored `artifacts/win98-benchmark/` output |
| `scripts/parse-win98-benchmark.sh FILE` | Strictly validate and normalize `RWBENCH.TXT` | Read-only |
| `scripts/summarize-benchmark-telemetry.sh PROFILE LOG` | Validate and summarize matched benchmark telemetry | Read-only |
| `bash scripts/summarize-runtime-timing.sh LOG` | Validate optional per-interval process/callback timing diagnostics | Read-only |
| `bash scripts/summarize-worker-timing.sh LOG` | Validate optional completed-worker, frontend-wait/mix and fallback aggregates; included by runtime timing validation | Read-only |
| `scripts/capture-sm-t500-benchmark.sh --profile PROFILE` | Capture bounded telemetry, result screen and temperature for an already-running experimental guest | Does not start, stop, or change the guest; writes ignored evidence |
| `scripts/finalize-sm-t500-benchmark.sh ...` | Merge a strict guest result with host metrics and explicit human checks | Refuses to overwrite an existing final report |
| `scripts/compare-sm-t500-benchmarks.sh ...` | Compare three valid fixed-20k DynRec reports with three matched Normal reports | Emits medians/deltas; never promotes while long gates are pending |
| `scripts/test-host.sh` | Compile and run Java/C++ host checks | Temporary files only |
| `bash scripts/test-stress-reference.sh` | Verify all idle/integer/RAM/VGA stress phases and timer progress in pinned QEMU | Disposable source-owned floppy only; approximately 35 seconds |
| `bash scripts/test-cache-reference.sh` | Validate five protected-mode cache controls, exact checksums, malformed records, injected failure and APM shutdown in pinned QEMU | Fresh generated floppies only; not tablet performance evidence |
| `bash scripts/build-cache-fixture.sh warm\|cold\|reuse\|data\|rewrite [OUTPUT]` | Build a pinned, separately identified cache-control image | Staged no-clobber publication; differing existing outputs are rejected |
| `scripts/check-repository.sh` | Check Git hygiene and shell syntax | Read-only |
| `scripts/check-experimental-fat.sh --image FILE --expected PATH` | Read-only FAT health check for a quarantined experimental volume | Requires `fsck.fat`/`dosfsck` and `mtype`; never mounts or repairs |
| `scripts/test-sm-t500-core.sh` | Run disposable settings/storage, input and direct-core device probes; silent fixture validates stream opening and schema-3 presentation, not audio continuity | Requires stopped machines; installs APK and creates temporary tablet files |
| `bash scripts/test-sm-t500-graphics.sh` | Synthetic GPU colors, padded rows, resolution/context/surface recreation, pacing and forced software fallback | Requires installed build, stopped machines and unlocked tablet; opens no guest media; not an audio/guest-clock test |
| `bash scripts/build-presentation-fixture.sh [OUTPUT [tone\|stress]]` | Build pinned BIOS-timer/VGA/PIT-tone boot image; optional phased stress variant | Generated output only; no proprietary OS required |
| `bash scripts/test-sm-t500-presentation.sh normal\|dynamic 0\|1 [tone\|stress]` | Two-minute Software (0) or GPU (1) light/stress timer/tone run | Requires passed CPU gate and stopped machines; isolated disposable image; writes ignored evidence; tablet stays foreground |
| `bash scripts/summarize-presentation.sh normal\|dynamic 0\|1 LOG [tone\|stress]` | Strict settled timer/tone telemetry and workload identity validation | Read-only; short experiment, not sustained thermal certification |
| `bash scripts/test-sm-t500-presentation.sh normal\|dynamic 0\|1 cache-warm\|cache-cold\|cache-reuse\|cache-data\|cache-rewrite` | Run one packaged fixed-work cache control in the existing isolated fixture process | Passed CPU gate, stopped machines and unlocked/foreground tablet required; 60-second internal timeout; no guest-library paths |
| `bash scripts/summarize-cache-fixture.sh normal\|dynamic 0\|1 warm\|cold\|reuse\|data\|rewrite LOG` | Validate cache correctness and combine periodic plus terminal residual diagnostics when available | Read-only; setup/teardown remain included; neither settled quality nor Windows causality is inferred |
| `bash scripts/normalize-session-timing.sh LOG` | Validate terminal residual group order/coverage and opt into combined diagnostic parsing | Read-only; preserves terminal header and stopping state; never feed normalized output to settled quality gates |
| `bash scripts/summarize-cache-attribution.sh NORMALIZED_LOG` | Validate sampled translation/publication timing, coverage and direct cache-clear reasons | Read-only; run the terminal normalizer first; no extrapolated whole-worker cost or per-miss history |
| `bash scripts/summarize-cache-calibration.sh LOG` | Validate one four-mode bounded cache calibration and its fixed 1% boot-frame smoke budget | Read-only; rejects missing modes, accounting/clock failures and budget overruns; not a guarantee of real-workload overhead |
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
