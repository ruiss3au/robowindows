# Offline tablet workflow

The debug app is installed on the SM-T500 as `org.robowindows.app.debug`. It does not need
ADB, Debian, SSH, or a network connection for normal guest use.

## Before experimenting

The verified restore point is:

`backups/win98-installed-20260810/`

That directory contains the compressed Windows disk, boot floppy, launch configuration, and
SHA-256 manifest. The protected source image and the writable runtime clone are separate. Keep
the current clone unchanged until you are satisfied with the experiments; a fresh full clone
export should be made when Debian/ADB is available again.

## Launching Windows without Debian

1. Open RoboWindows on the tablet.
2. Select the Windows 98 machine and start it.
3. Leave the app in the foreground for sound and best performance.
4. Use the in-app Exit control for a clean shutdown. Do not force-stop the app while Windows
   is writing to its disk.

## Mounting an ISO from Downloads

While Windows is running, tap **Change media** in the RoboWindows controls. Android's document
picker opens; choose the ISO from **Downloads** (or another local provider). RoboWindows copies
it into app-private storage, verifies and protects the imported source, and queues it as the
DOSBox optical image. The existing CD drive is exposed to Windows as `D:`; refresh Explorer or
open `D:` from a command prompt after insertion.

The ISO is not modified in Downloads. The app keeps its own read-only copy, so the tablet can
be disconnected from Debian immediately after the import. ISO selection is also available from
the Windows machine's utility/media controls when preparing boot or installer media.

## Practical limits

The stable tablet profile presents about 15 fps. Windows 98 applications, installers, DOS games,
and strategy titles should be usable; fast action games may feel less smooth. Keep several GB of
free tablet storage for imported ISOs and Windows-generated runtime changes.
