# Validation Record

## 1.0 Alpha — 2026-09-10

Host alpha-label tests and both APK manifest checks passed: versionCode 2,
`1.0-alpha-debug` / `1.0-alpha`, unchanged package IDs and dirty provenance.
After user storage cleanup, the tablet received versionCode 2 through a
data-preserving update. The Machines UI hierarchy shows
`v1.0-alpha-debug · 4cd374c6f1ca+dirty`; both machine names and settings remain
unchanged. No real guest was booted. See [handoff](../../docs/alpha-1.0.md).

## 2026-09-06 — Host, build, and SM-T500

- `scripts/test-host.sh`, `scripts/check-repository.sh`, and `git diff --check`
  passed.
- `scripts/build-android.sh --debug` passed and generated
  `BuildConfig.VERSION_NAME = "0.1.0-dev-debug"` and
  `BuildConfig.SOURCE_REVISION = "93b76e8169fa+dirty"`.
- The debug APK installed on the connected SM-T500 without clearing app data.
- RoboWindows was stopped before installation. Only the Machines screen was
  launched for validation; no guest was booted.
- The foreground activity's UI hierarchy contained the exact visible label
  `v0.1.0-dev-debug · 93b76e8169fa+dirty`.
