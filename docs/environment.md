# Environment baseline

Captured 2026-08-09 before implementation changes.

## Development host

| Item | Observed value |
|---|---|
| Kernel | Linux 6.1.0-49-amd64, x86_64 |
| Git | 2.39.5 |
| Java | OpenJDK 17.0.20 |
| Gradle | Not found on `PATH` |
| CMake | 3.25.1 |
| Ninja | 1.11.1 |
| ADB | 1.0.41 / Debian platform-tools 29.0.6 at `/usr/bin/adb` |
| Specify CLI | Not found on `PATH` |
| Free workspace | Approximately 292 GiB |

No Android SDK/NDK directory was identified through `ANDROID_SDK_ROOT` or
`ANDROID_HOME`. ADB is installed independently under `/usr/lib/android-sdk`.
No local DOSBox Pure, DOSBox Pure Unleashed, or ZillaLib checkout was found in
the initial search. No relevant user guest media was found; unrelated Minikube
boot media was ignored.

## Primary target device

Observed through `adb` while physically connected:

| Item | Observed value |
|---|---|
| Manufacturer/model | Samsung SM-T500 |
| Device codename | `gta4lwifi` |
| Android | LineageOS 23.2 (Android 16), API 36 |
| LineageOS build | `23.2-20260804-NIGHTLY-gta4lwifi` |
| SoC reported by Android | Qualcomm SM611 |
| ABIs | `arm64-v8a`, `armeabi-v7a`, `armeabi` |
| Physical RAM reported by kernel | Approximately 2.62 GiB |
| Physical display | 1200 x 2000, 240 dpi |
| Pointer capture at inspection | Supported by OS; uncaptured |

The ADB serial, build fingerprint, Bluetooth addresses, and input unique IDs are
intentionally omitted because this repository is intended for public release.

## Connected acceptance-test peripherals

### Inateck Bluetooth keyboard

- USB/HID identity reported through Android: vendor `0x3373`, product `0x7713`
- Android combines three event nodes into one logical input device.
- Reported sources: keyboard, D-pad, and joystick.
- Keyboard type: alphabetic.
- Consumer-control and system-control interfaces are also present.

This mixed classification is a required regression case: the app MUST preserve
keyboard events and MUST NOT route or discard the device merely because it also
advertises game-controller sources.

### Logitech LIFT Bluetooth mouse

- USB/HID identity reported through Android: vendor `0x046d`, product `0xb031`
- Reported source: mouse.
- Left/right/middle buttons and both vertical and horizontal scroll axes are
  exposed by Android.

The app must test relative movement, button transitions, wheel input, pointer
capture/release, rotation, disconnect, and reconnect with this device.

## Discovery constraints

- No guest media was opened, hashed, copied, or modified.
- No dependency was downloaded.
- SDK/NDK versions remain a planning decision and must be pinned before use.
