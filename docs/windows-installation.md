# Windows installation

RoboWindows can create a Windows-family machine directly from a bootable ISO. The app owns the
entire launch flow; DOSBox Pure's menus and on-screen display are not used.

## Media handling

- The selected ISO is copied into app-private storage, hashed with SHA-256, and made read-only.
- RoboWindows validates the ISO's El Torito catalog and extracts its floppy-emulation boot image.
- A writable 2 GB hard-disk image is created with fixed `512,63,16,4063` geometry.
- The guest uses the pinned core's supported Pentium CPU profile required by Windows 9x.
- Proprietary ISO, floppy, hard-disk, and registration-key files are excluded by `.gitignore`.
- The original selected file is never modified.

Only bootable floppy-emulation ISO images are accepted by this first installer workflow. Ordinary
ISO images remain attachable media for an existing DOS or Windows machine.

## Installing Windows 98

1. Choose **Add machine**, **Windows**, then select the Windows 98 ISO.
2. Start the machine with **Install**. RoboWindows mounts the hard disk as BIOS C:, the ISO as the
   IDE CD-ROM, extracts/mounts its boot floppy as A:, and boots A:.
3. Complete partitioning, formatting, and Windows Setup in the guest.
4. When Setup requests the registration code, enter it directly in the guest. RoboWindows never
   requests, stores, logs, or automates the code.
5. Before Setup's first hard-disk restart, exit the guest, open **Settings**, and choose
   **Boot Windows disk**. Start the machine again. The ISO remains attached for Setup.
6. **Boot installer** in Settings switches back to the installation floppy when recovery is needed.

Patch9x is optional compatibility media, not bundled software. If the installed Windows version
needs its CPU compatibility patch, open the machine's **Settings**, choose **Boot utility disk**,
and select the Patch9x IMG. RoboWindows imports an immutable source plus a private writable clone,
boots that floppy with the Windows hard disk still attached, and keeps the ISO on the IDE CD-ROM.
After applying the patch, return to **Settings** and choose **Boot Windows disk**. Keep all utility
images outside the repository unless their redistribution terms explicitly permit bundling.

## Validated private-media fixture

The developer fixture was verified without recording proprietary content or a product key:

- ISO size: 655,591,424 bytes
- ISO SHA-256: `2adfb46df8a9c7bbd2f67bff07461cc2f9d9ec8e01f0e112cb044c9e3e62f607`
- El Torito media: BIOS-bootable 1.44 MB floppy emulation
- SM-T500 launch sequence: two `IMGMOUNT` operations followed by `BOOT`
- First guest frame: 640x400
- Audio: 48 kHz
