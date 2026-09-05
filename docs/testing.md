# Testing record

## 2026-08-09 — Windows 98 installer media (SM-T500)

- The private Windows 98 fixture passed El Torito extraction on the host and app-private import on
  the tablet.
- The generated launch reached `IMGMOUNT`, `IMGMOUNT`, `BOOT`, initialized 48 kHz audio, and
  submitted a 640 x 400 guest frame while the secure keyguard covered the activity.
- The path contains no upstream UI command or fallback.
- Patch9x 0.9.91 was imported through the utility-media path and booted with the same Windows disk
  and ISO attached. It reached a distinct 640 x 400 guest frame and 48 kHz audio without a CPU
  option warning, Java exception, or native fatal entry.
- Unlock-screen visual confirmation and interactive Setup remain the final device gates.

## 2026-08-09 — Custom Android shell build spike

### Build

- Source revisions verified against `lock/sources.lock` before building.
- JDK 17, Gradle 9.1.0, AGP 9.0.1, API/Build Tools 36, and NDK
  `28.2.13676358` used.
- `scripts/build-android.sh` completed successfully.
- Java UI/resources and `arm64-v8a` JNI library compiled and packaged.
- APK and SHA-256 sidecar emitted under ignored `artifacts/`.

### SM-T500 deployment

- `adb install -r` succeeded on the connected SM-T500.
- Cold launch of `org.robowindows.app.debug/org.robowindows.app.MainActivity`
  completed successfully in approximately 1.0 seconds.
- Activity reached `RESUMED`, visible, focused, and fully drawn state at the
  native 2000 x 1200 landscape configuration.
- Crash-focused log capture contained no Android runtime or native fatal entry.

### Visual inspection

- RoboWindows-owned navigation, diagnostics entry point, and guest-display
  boundary rendered correctly.
- JNI build identity appeared in the custom UI.
- No DOSBox Pure, DOSBox Pure Unleashed, libretro, or ZillaLib interface,
  branding, menu, OSD, mapper, or notification was visible.
- Guest boundary displayed only the project-owned placeholder frame.

### Not yet claimed

- The diagnostics inventory screen was not validated because the device changed
  foreground application before the automated tap. The test was stopped rather
  than disrupting active tablet use.
- Pause/resume, rotation, input event streams, pointer capture, and reconnect
  remain pending.
- The DOSBox Pure core is not linked or running yet; this milestone validates
  only the custom UI, build, packaging, JNI boundary, and target deployment.

## 2026-08-09 — Core-host and machine-layer checkpoint

- The pinned DOSBox Pure source compiles as an `arm64-v8a` shared core and is
  packaged beside the RoboWindows JNI host.
- Cold launch with both native libraries loaded completed on SM-T500 without a
  Java, linker, or native fatal log entry.
- The native host now provides lifecycle, XRGB8888 framebuffer, AAudio output,
  keyboard, relative mouse, buttons, two-axis wheel, pause, and cancellation
  callbacks.
- Core frames are accepted only for generated `.conf` launch content. This
  bypasses DOSBox Pure's start menu; no upstream menu is used as a fallback.
- Boot-media import stages and hashes the selected source, makes imported disk
  media immutable, creates a writable runtime clone, and atomically records a
  versioned machine profile.
- Profile migrations, persistent memory/CPU/sound presets, and interrupted
  session detection are implemented and compile successfully.
- On-device framebuffer boot validation remains pending because the tablet was
  behind the Samsung notification/keyguard layer during the latest ADB pass.
  No claim is made for guest video or audio until a direct-boot disk test is
  observed on the unlocked device.

## 2026-08-09 — Direct-boot lifecycle test on SM-T500

- `scripts/test-sm-t500-core.sh` installed the current debug APK and used only
  disposable, self-cleaning blank disk fixtures in app-private storage.
- DOSBox reached `BOOT`; the host started at 70.09 fps and produced a nonblank
  640 x 400 XRGB8888 guest frame.
- AAudio opened and started successfully at 48 kHz.
- Debug-only automation confirmed pause, resume, disk-control media replacement,
  and orderly core unload/deinitialization without a Java or native fatal error.
- The product-source scan continues to reject references to the upstream Pure
  menu/OSD entry points.
- Visible Android-surface presentation was not revalidated in this run: the
  tablet remained in Samsung ambient Doze with NotificationShade owning the
  display, so Android did not create an Activity surface. The test reports this
  condition separately and makes no visible-rendering claim from this run.

### Subsequent regression coverage

- Native key, mouse, touch, and cancellation records are serialized by one
  shared formatter covered by `tests/input/input_event_test.cpp` and by a JNI
  round-trip probe on the SM-T500. Required scan-code, repeat, modifier, source,
  session-handle, pointer, button, wheel, pressure, timestamp, and capture fields
  are checked.
- The on-device probe fills the bounded 4096-record input history and verifies
  overflow accounting, then cancels all held input state.
- Profile schema v5 retains attached-media SHA-256 provenance. Android-runtime
  probes cover v1 migration, v5 round-trip, atomic launch-config replacement,
  and interrupted `.part` cleanup.
- The keyguard was subsequently confirmed as secure and showing, not merely a
  short display timeout. `REQUIRE_SURFACE=1 scripts/test-sm-t500-core.sh` is the
  strict visible-surface gate to run after a manual unlock.

## 2026-08-09 — Unlocked strict surface and current UI

- With the SM-T500 manually unlocked, the strict surface gate passed. A 640 x
  400 guest frame rendered into the 1946 x 1074 app surface as 1718 x 1074
  content with 114-pixel left/right letterboxing.
- The same run reconfirmed 48 kHz audio, pause/resume, media replacement,
  orderly stop, storage/persistence probes, and typed input probes.
- Current machines, recovery, empty-library, input diagnostics, and Add machine
  screens were inspected. No upstream component interface or removed
  engineering subtitle was visible.
- The connected Inateck keyboard and LIFT mouse were correctly inventoried and
  virtual ADB input remained excluded. Two live observation windows recorded no
  physical actions, so hands-on event delivery is not claimed from this run.

### Physical input and restart follow-up

- A later hands-on diagnostics run recorded 60 Inateck key transitions and 454
  LIFT mouse events with no queue drops. Moving the app through Home/foreground
  incremented cancellation from zero to two and input resumed normally.
- In a disposable guest, capture/release recorded 175 physical LIFT events and
  one cancellation with no drops. Android ended in `Pointer Capture:
  UNCAPTURED`.
- A restart regression initially exposed that the pinned core crashes when
  fully deinitialized and initialized again in one process. Restart was changed
  to queue libretro `retro_reset` on the core thread. The strict SM-T500 test
  then reached BOOT after restart and stopped cleanly without a fatal entry.
