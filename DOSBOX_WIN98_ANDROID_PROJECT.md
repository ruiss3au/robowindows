# Handoff: Reproducible standalone Windows 98 emulator for Android

## User goal

Build a standalone Android APK for a rooted Samsung Galaxy Tab A7 Wi-Fi
(SM-T500, Snapdragon 662/ARM64, 3 GB RAM, LineageOS) that installs and runs
Windows 98 SE without RetroArch. A Bluetooth keyboard and Bluetooth mouse must
work as ordinary PC input devices.

Do not use RetroArch as the final frontend. Its Android input routing is the
reason for this project.

The preferred starting point is:

- DOSBox Pure core: <https://github.com/schellingb/dosbox-pure>
- DOSBox Pure Unleashed standalone frontend:
  <https://github.com/schellingb/dosbox-pure-unleashed>
- ZillaLib frontend/platform layer: <https://github.com/schellingb/ZillaLib>

DOSBox Pure Unleashed currently documents desktop builds, not an Android APK.
Expect to add or complete an Android frontend/build target. Keep changes small
and upstream-friendly.

DOSBox-X is a useful reference for Windows 98 configuration and validation,
but it does not currently provide a maintained official Android build:
<https://dosbox-x.com/wiki/Guide%3AInstalling-Windows-98>

## Important reproducibility answer

If a pinned DOSBox build boots a particular Windows 98 disk image on Debian,
that is a strong and necessary baseline, but it does **not** guarantee the APK
will work on Android.

The portable state consists of:

- the exact emulator source revisions and build options;
- the complete machine/core configuration;
- the same raw/VHD disk image bytes;
- the same ISO/floppy images;
- the same guest drivers and their configuration.

Platform-dependent behavior still exists in:

- CPU core/backend (normal interpreter versus ARM dynamic recompiler);
- compiler and optimization flags;
- endianness/alignment/undefined-behavior bugs;
- graphics and audio backends;
- Android lifecycle and storage APIs;
- Android keyboard, mouse, pointer-capture, and Bluetooth event translation.

Therefore use two validation gates:

1. Debian ARM64 or x86_64 reference build boots and passes guest tests with the
   interpreter/normal CPU core.
2. Android ARM64 APK boots a byte-for-byte copy of the same disk image and
   passes the same guest tests, first with the normal core and then with the
   faster ARM dynamic core if available and stable.

Do not reinstall Windows independently on each platform. Produce one known-good
golden disk image, checksum it, copy it to Android, and boot that exact image.

## Legal and data constraints

- Do not download or redistribute Windows 98, its product key, or proprietary
  driver packages without the user's authorization and appropriate license.
- The user supplies the Windows 98 SE ISO and key.
- The APK should not embed Windows files.
- Open-source/free drivers may be bundled only if their licenses permit it;
  otherwise provide a documented import flow and checksums supplied by the
  user.
- Never modify the user's only disk image. Work from versioned copies.

## First task: inspect before changing anything

Record results in `docs/environment.md`:

```sh
uname -a
git --version
java -version
gradle --version
cmake --version
ninja --version
${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/nonexistent}}/platform-tools/adb version
```

Also locate Android SDK/NDK installations without printing secrets. Check free
disk space. Search the Debian development host for existing ISO, IMG, VHD,
QCOW2, Android
projects, and cloned DOSBox sources, but do not upload, delete, or alter them.

Before cloning or downloading dependencies, ask for network approval if the
environment requires it. Pin every dependency by commit SHA; do not build from
floating branches.

## Repository layout

Create a dedicated project directory, preferably:

```text
<development-workspace>/win98-android/
  README.md
  Makefile
  lock/
    sources.lock
    checksums.sha256
  scripts/
    fetch-sources.sh
    build-linux.sh
    build-android.sh
    make-test-disk.sh
    verify-assets.sh
  config/
    win98-install.conf
    win98-runtime.conf
  android/
  docs/
    environment.md
    architecture.md
    input.md
    win98-install.md
    drivers.md
    testing.md
  third_party/
  build/
  artifacts/
```

Keep generated build output, SDK paths, ISO files, product keys, and disk images
out of Git. Add explicit `.gitignore` rules.

`lock/sources.lock` must contain repository URLs, commit SHAs, toolchain
versions, Android API level, NDK version, ABI, and compiler flags.

## Phase 1: establish the Debian reference

Build DOSBox Pure Unleashed from pinned commits following its upstream layout:

```text
parent/
  dosbox-pure-unleashed/
  dosbox-pure/
  ZillaLib/
```

Start with an unmodified Linux build. Record the exact binary SHA-256 and full
configuration. Confirm that physical keyboard and mouse work in the standalone
frontend.

Create a small, non-proprietary FreeDOS test disk first. Validate:

- keyboard make/break events, modifiers, arrows, function keys, and Alt/Ctrl;
- relative mouse movement and left/right/middle buttons;
- pointer capture and release;
- video mode changes;
- audio start/stop;
- clean shutdown and disk persistence.

Only after that, create the Windows 98 reference image using the user's ISO.

## Phase 2: deterministic Windows 98 machine profile

Prefer DOSBox Pure's current OS-install workflow and dynamic VHD support where
stable. If manually configuring an image, use a raw image or fixed/dynamic VHD
supported identically by both builds. Avoid QCOW2 unless the chosen DOSBox code
explicitly supports it.

Pin and document at least:

- machine/SVGA type;
- guest RAM (start with 64 or 128 MB);
- CPU type, core, and cycles;
- IDE controller and disk geometry;
- CD-ROM attachment;
- mouse type;
- sound device;
- network device or disabled state;
- Voodoo state and memory;
- BIOS-relevant settings;
- keyboard layout;
- every non-default core option.

Use the normal/interpreter CPU core for installation unless upstream explicitly
documents a safe alternative. After installation, test the faster core without
changing any other setting.

Maintain separate configs:

- `win98-install.conf`: conservative, normal core, minimal peripherals;
- `win98-runtime.conf`: performance settings added one at a time after a stable
  snapshot/checksum.

Do not assume QEMU/Limbo fixes apply to DOSBox. Patcher9x was necessary in the
failed Limbo/QEMU experiment, but may be unnecessary or harmful in DOSBox Pure.
Start with an unpatched Windows 98 install under DOSBox Pure's supported machine
profile. Use Patcher9x only if a reproducible CPU/TLB protection error occurs,
and record exactly which patch categories changed which files.

## Guest drivers

Choose emulated hardware with Windows 98 SE in-box drivers wherever possible.
Install one optional device at a time, reboot, test, and checksum/snapshot.

Suggested order:

1. Base Windows installation with networking, Voodoo, and optional audio off.
2. Chipset/IDE devices using in-box drivers.
3. Basic VGA/SVGA driver supported by the selected DOSBox machine.
4. Mouse.
5. Sound Blaster-compatible audio.
6. Optional NE2000 networking.
7. Optional Voodoo driver and DirectX only after the base image is stable.

For every driver record:

- exact filename/version/source URL;
- SHA-256;
- license/redistribution status;
- emulated PCI/ISA IDs where applicable;
- install procedure and rollback point.

Do not market “right drivers” as universal. The correct driver is determined by
the exact hardware DOSBox exposes. Confirm the emulated IDs and upstream code,
then select the matching Windows 98 driver.

## Phase 3: Android frontend/APK

Investigate ZillaLib's Android capabilities first. Reuse its platform support if
present rather than creating a second frontend. If it cannot produce an Android
app, implement the smallest native Android shell around the standalone core,
preferably with SDL2 or GameActivity/NativeActivity and OpenGL ES.

Minimum APK behavior:

- ARM64 (`arm64-v8a`) build; optionally `armeabi-v7a` later;
- scoped-storage file picker for ISO/VHD import;
- persistent app-private VM directory;
- no requirement for root;
- save/flush disk image on pause and orderly exit;
- robust handling of activity pause/resume and display rotation;
- immersive fullscreen with an explicit escape/menu gesture;
- physical keyboard passed as PC scancodes, not mapped to a gamepad;
- Android mouse captured as relative motion while the VM is focused;
- left/right/middle buttons and wheel forwarded;
- visible method to release pointer capture;
- touchscreen input optional and separate from physical mouse input;
- no RetroArch hotkey or RetroPad translation layer.

Android input implementation requirements:

- Log Android `InputDevice` descriptor, sources, vendor/product IDs, keyboard
  type, and motion axes in debug builds.
- Handle `KeyEvent` down/up and preserve left/right modifiers.
- Do not discard keys merely because Android reports the Bluetooth keyboard as
  a combined keyboard/game-controller device.
- Use pointer capture on supported Android versions for relative mouse input;
  provide a fallback using captured hover/motion events.
- Avoid sending both touchscreen-emulated mouse and physical mouse events at
  once.
- Add an in-app input diagnostics screen before connecting input to DOSBox.

## Automation and scripts

Scripts should be non-interactive where practical, fail on errors, and never
silently download floating “latest” artifacts.

Required reproducibility outputs:

- `scripts/fetch-sources.sh` verifies pinned commit SHAs;
- `scripts/verify-assets.sh` verifies user-provided asset checksums without
  embedding or exposing keys;
- `scripts/build-linux.sh` creates the Debian reference binary;
- `scripts/build-android.sh` creates a signed debug APK and an unsigned or
  locally signed release APK;
- build metadata embedded in an About screen and emitted to
  `artifacts/build-manifest.json`;
- SHA-256 files for binaries, APKs, configs, and golden disk images.

Never commit a release keystore. For local debug builds use the standard debug
keystore. Document how the user can provide a release keystore separately.

## Validation matrix

Use the same golden disk checksum for Linux and Android.

Test at minimum:

| Test | Debian normal | Android normal | Android dynamic |
|---|---:|---:|---:|
| Boot to Win98 desktop 10 times | | | |
| Clean shutdown and reboot | | | |
| Keyboard diagnostic | | | |
| Relative mouse + 3 buttons | | | |
| Disk write persists | | | |
| 640x480/800x600 mode switch | | | |
| SB16 WAV/MIDI playback | | | |
| Suspend/resume app | N/A | | |
| Bluetooth reconnect | N/A | | |
| 30-minute stability run | | | |

Treat a test as passing only if it is repeatable. Capture emulator logs and the
full config for failures. Never overwrite the golden image during destructive
tests; clone it first.

## Performance expectations

The Snapdragon 662 is ARM64. DOSBox's ARM dynamic recompiler may make Windows
98 substantially faster than Limbo/QEMU's generic x86 translation, but validate
correctness with the normal core first. Performance optimizations must be made
one at a time with before/after measurements.

Measure:

- boot time;
- UI responsiveness;
- emulated CPU/cycle setting;
- audio underruns;
- sustained temperature/throttling;
- battery use over 30 minutes.

## Definition of done

The project is complete only when:

1. A clean checkout plus documented toolchain can reproduce the APK.
2. The source/dependency lock file pins every revision.
3. The APK imports user-owned media and creates or imports a VM disk.
4. The exact Debian-tested golden disk boots on Android.
5. Bluetooth keyboard and mouse pass the diagnostic and Windows tests.
6. Windows boots repeatedly without protection errors or filesystem damage.
7. Driver provenance, hashes, configuration, and installation order are
   documented.
8. No Microsoft software, product key, private keystore, or user disk image is
   committed or redistributed.

## Recommended first milestone

Do **not** begin with Windows installation. First produce a debug ARM64 APK that
boots to DOSBox Pure Unleashed's menu or a FreeDOS image and proves correct
Bluetooth keyboard and relative-mouse input. Once that works, port the exact
Linux-tested Windows 98 disk/config. This isolates the original RetroArch input
problem from guest OS and driver problems.
