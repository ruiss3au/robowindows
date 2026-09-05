# RoboWindows

RoboWindows is a work-in-progress standalone Android application for running
DOS-family software and compatible Microsoft Windows guests through a pinned,
reproducible DOSBox-based emulator core. The primary hardware target is the
Samsung Galaxy Tab A7 Wi-Fi (SM-T500), with ordinary Bluetooth keyboard and
mouse input and no RetroArch input layer or exposed DOSBox interface.

The project will contain only open-source code and redistributable assets.
Users must supply any proprietary operating systems, applications, product
keys, and drivers they are licensed to use.

RoboWindows is an independent project and is not affiliated with, endorsed by,
or sponsored by Microsoft. Microsoft and Windows are trademarks of the
Microsoft group of companies.

Development follows Specification-Driven Development. Product intent begins in
[`specs/001-standalone-android-emulator/spec.md`](specs/001-standalone-android-emulator/spec.md),
with later numbered specifications recording runtime and repository evolution.

## Status

An ARM64 debug APK is built reproducibly and exercised on the SM-T500. The app
currently provides a machines-first RoboWindows UI, app-private disk import and
writable clones, DOS/Windows-family profiles, compatibility presets, direct
IMG/IMA/VHD boot, guest video/audio hosting, physical keyboard/mouse bridging,
session controls, media attachment, interrupted-session recovery, and a
RoboWindows-owned bootable-ISO installation path for Windows-family machines.

Automated device testing confirms core boot, nonblank 640 x 400 frame output,
48 kHz audio startup, pause/resume, media replacement, orderly shutdown, typed
input serialization, bounded input history, schema migration, and interrupted
import cleanup. Physical keyboard/mouse and strict visible-surface checks have
also passed. Bootable Windows installation ISOs create a private hard disk and
installer profile; other ISO files remain attachable media rather than being
misrepresented as directly bootable machine disks.

## Working scope

- Primary target: Samsung SM-T500, ARM64, Android 16/API 36
- First milestone: standalone debug APK plus input diagnostics and a freely
  redistributable DOS boot/test environment
- Entirely project-owned Android UI; the emulator is visible only as the guest
  display and never exposes its built-in menus, browser, mapper, or OSD
- Later compatibility profiles: DOS-family systems and Windows releases that
  the selected emulator stack can support reliably
- Windows 98 SE: an important validation profile, not the sole product focus

See [`DOSBOX_WIN98_ANDROID_PROJECT.md`](DOSBOX_WIN98_ANDROID_PROJECT.md) for the
original handoff and [`docs/environment.md`](docs/environment.md) for the
observed development and target-device baseline.

The user-facing Windows workflow is documented in
[`docs/windows-installation.md`](docs/windows-installation.md).

Contributor workflow and repository layout are in
[`docs/development.md`](docs/development.md). The command catalog is in
[`scripts/README.md`](scripts/README.md), local guest backups are covered by
[`docs/backup-and-restore.md`](docs/backup-and-restore.md), and the remaining
public-GitHub gates are explicit in [`docs/publishing.md`](docs/publishing.md).

## License

RoboWindows is free software licensed under
[GPL-2.0-or-later](LICENSE). Third-party components and build tooling are listed
in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
