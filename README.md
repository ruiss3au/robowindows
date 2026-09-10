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

Development follows Specification-Driven Development. Start with the public
[specification index and roadmap](specs/README.md), which links the governing
constitution and each numbered feature record.

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

## Run Windows

For the current usable features, CPU/GPU settings and intentionally paused
performance work, see the [practical-use handoff](docs/current-handoff.md).

RoboWindows does not include Windows, installation media, product keys, or
drivers. Use only media you are licensed to use. Keep the tablet unlocked and
RoboWindows in the foreground while importing large files.

### Install Windows from an ISO

The current installer workflow is intended for Windows 9x-style, BIOS-bootable
ISOs that use El Torito floppy emulation. Other ISO layouts are rejected, but an
ordinary ISO can still be attached to an existing machine.

1. Make sure the tablet has room for the ISO plus a new 2 GB working disk.
2. On the RoboWindows home screen, choose **Add machine** → **Windows**, then
   select the ISO with Android's document picker.
3. After the import completes, choose **Install**. Inside the guest, partition
   and format the new disk and run Windows Setup normally.
4. Enter any registration key only inside the guest. RoboWindows does not ask
   for, store, or log it.
5. Before Setup's first restart from the hard disk, leave the guest with
   **Exit**, open the machine's **Settings**, and choose **Boot Windows disk**.
6. Choose **Start** to continue Setup from the installed disk. The ISO remains
   attached as the guest CD-ROM. Use **Boot installer** in Settings if recovery
   requires the installer again.

See [Windows installation](docs/windows-installation.md) for the detailed
Windows 98 workflow and optional utility-disk handling.

### Load an already-installed Windows disk

1. Prepare the existing boot disk as an `.img`, `.ima`, or `.vhd` file. An ISO
   alone is installation/optical media, not an installed machine disk.
2. Choose **Add machine** → **Windows**, then select the disk image with
   Android's document picker.
3. Wait for the import to finish. RoboWindows keeps the imported source
   read-only and creates a separate private writable working copy, so allow
   roughly twice the image size as free tablet storage.
4. Choose **Start** beside the new machine. The default **Windows compatible**
   configuration uses the conservative CPU core; arbitrary images and Windows
   releases are not guaranteed compatible.
5. To attach an ISO after Windows starts, reveal the session controls and choose
   **Change media**.

Shut Windows down inside the guest before choosing **Exit** whenever possible.
The writable machine lives in RoboWindows app-private storage; back it up before
clearing app data or uninstalling the app. The developer backup procedure is in
[Backup and restore operations](docs/backup-and-restore.md).

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
