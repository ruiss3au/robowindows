# Phase 0 Research: Standalone Android DOS and Windows Emulator

**Date**: 2026-08-09  
**Spec**: [spec.md](spec.md)

## Decision summary

Build a dedicated RoboWindows Android host around DOSBox Pure's existing C
frontend callback boundary. Using that API does not introduce RetroArch; this
app implements the host callbacks directly. DOSBox Pure Unleashed and ZillaLib
are pinned reference implementations only, with no user-visible interface.

The first deliverable is an input-diagnostics APK on the SM-T500. Emulator boot
follows only after Android input, lifecycle, and storage contracts are observable.

## Upstream snapshot

| Component | Researched revision | License | Relevant finding |
|---|---|---|---|
| DOSBox Pure Unleashed | `4a11412248ca4c862751a7d9e6818023795031e9` | GPL-2.0-or-later stated by upstream | Reference for core callbacks, VFS, rendering, audio, configuration, and lifecycle; its UI is not shipped |
| DOSBox Pure | `7f6e8fb7385fa446d1444d671063268520bf9b54` | GPL-2.0-or-later stated by upstream | Supports boot images, installed OS workflows, dynamic VHD, ARMv8 little-endian dynrec, and Android through libretro builds |
| ZillaLib | `a2796bfe0faebe3e5de14b75d6b45866f1576f14` | zlib | Android/platform reference only; not assumed to be a runtime dependency |

These are immutable starting pins in `lock/sources.lock`, not claims that each
revision has already passed our build or device gates.

## Custom interface decision

### Runtime architecture

- RoboWindows-owned Android screens for library, profiles, import, settings,
  diagnostics, errors, and runtime controls.
- A project-owned guest display view receiving core video frames.
- A small native host implementing DOSBox Pure callbacks for video, audio,
  input, environment/configuration, virtual filesystem, and lifecycle.
- Project-owned storage/profile services passing only validated app-private
  paths and configuration into the native host.

### Explicitly do not ship

- Unleashed `main.cpp`, ZillaLib activity/UI ownership, DOSBox Pure start menu,
  OSD, file browser, controller mapper, on-screen keyboard, upstream hotkeys, or
  upstream notifications.
- A configuration-only attempt to hide upstream UI. The host must not provide
  invocation paths for it, and unavoidable core menu paths must be disabled by
  a small auditable compile-time patch.

### Build-spike exit criterion

Before guest behavior is added, prove that the pinned DOSBox Pure core compiles
into one `arm64-v8a` debug APK and supplies a test frame through the custom host
on the SM-T500. Then load deterministic content/configuration and prove no
upstream UI appears during startup, runtime controls, errors, or exit.

## Gaps found in upstream Android support

ZillaLib's current activity handles basic key codes, text, touchscreen events,
surface changes, pause/resume, and audio. It does not currently provide:

- `onGenericMotionEvent` or captured-pointer handling for a physical mouse;
- pointer request/release state;
- mouse buttons and wheel axes as a typed native contract;
- input-device descriptors/capability diagnostics;
- left/right modifier metadata or physical scan-code provenance;
- Storage Access Framework imports and persistent URI handling;
- a safe profile/media workspace abstraction;
- current Gradle-based APK assembly.

DOSBox Pure also contains internal menu/OSD and auto-start paths. The custom host
must never map an internal menu action. A narrowly scoped compile-time
suppression patch is preferable to relying on hidden settings.

## Toolchain decision

| Tool | Pin | Rationale |
|---|---|---|
| JDK | 17 | Already installed; required/default for AGP 9.0.1 |
| Gradle | 9.1.0 wrapper | Required/default for AGP 9.0.1; wrapper avoids host Gradle dependency |
| AGP | 9.0.1 | Stable modern Android build path with API 36 support |
| Build Tools | 36.0.0 | AGP 9.0.1 default |
| compile/target SDK | 36 | Matches primary Android 16 target and installed API behavior |
| min SDK | 26 | Pointer capture starts at API 26; avoids building an untested fallback product contract |
| NDK | `28.2.13676358` | AGP 9.0.1 documented default (r28c), explicitly pinned |
| ABI | `arm64-v8a` | Primary SM-T500 target; other ABIs deferred |

NDK r29 is newer, but r28c is selected to reduce variables while modernizing an
old native build and because it is AGP 9.0.1's documented default.

## Storage decision

Use Android's Storage Access Framework (`ACTION_OPEN_DOCUMENT`) for source media.
Copy writable guest media into an app-private profile workspace using a staged
temporary file, checksum verification, atomic rename where supported, and a
recorded provenance entry. Never run writable guests directly against the user's
document URI. This intentionally trades extra storage for recoverability.

## Input decision

- Key events carry Android key code, hardware scan code, action, repeat count,
  meta-state, source bits, and sanitized device identity across the bridge.
- The guest mapping layer—not the diagnostics layer—maps delivered physical
  events to PC scan codes.
- A mixed keyboard/D-pad/joystick source remains a keyboard when it has keyboard
  capability.
- Captured mouse events use Android pointer capture on API 26+ and preserve
  relative deltas, buttons, vertical/horizontal wheels, source, and device ID.
- Touch events remain a separate type and are disabled from guest mouse injection
  whenever physical-mouse mode is active unless the user explicitly changes it.

## Licensing decision

License the application under `GPL-2.0-or-later`, matching the emulator core.
Preserve Unleashed and ZillaLib notices when code is derived from them; mere
research references are documented separately. The release pipeline must provide
corresponding source and exact source pins/patches for distributed binaries.

The working name “RoboWindows” must not imply Microsoft affiliation. A naming and
trademark review remains required before the first public release.

## Redistributable test media decision

FreeDOS is the preferred external first-boot environment because its kernel is
GPL-2.0-or-later and it is designed for DOS-compatible use. Do not bundle a
FreeDOS distribution image until every included package and the distribution's
redistribution terms have been inventoried. Initially, the test script may accept
a user-downloaded, pinned official FreeDOS image by checksum.

A later improvement may generate a minimal boot image from pinned FreeDOS source
packages with a machine-readable license manifest. Minikube media found on the
host is unrelated and must never be used.

## Alternatives rejected

| Alternative | Reason rejected for this phase |
|---|---|
| RetroArch/libretro frontend | Reintroduces the input-routing problem the project exists to eliminate |
| Reusing or re-skinning Unleashed UI | Violates the project-owned UI requirement and remains coupled to internal OSD/menu flow |
| ZillaLib-owned full-screen activity | Conflicts with a modern multi-screen product UI; retain only as reference |
| SDL frontend | Adds another abstraction while Android supplies the required UI, storage, input, audio, and surface APIs |
| Root/evdev input | Unnecessary, non-portable, unsafe for distribution, and bypasses normal Android focus/security semantics |
| Direct writes to selected document URI | Risks the user's sole media and weakens crash recovery |
| NDK r29 immediately | Adds toolchain novelty while legacy build compatibility is still unknown |

## Sources

- DOSBox Pure Unleashed repository and build documentation:
  https://github.com/schellingb/dosbox-pure-unleashed
- DOSBox Pure repository and releases:
  https://github.com/schellingb/dosbox-pure
- ZillaLib repository and Android implementation:
  https://github.com/schellingb/ZillaLib
- Android pointer capture:
  https://developer.android.com/develop/ui/views/touch-and-input/gestures/movement
- Android Storage Access Framework:
  https://developer.android.com/training/data-storage/shared/documents-files
- AGP 9.0.1 compatibility table:
  https://developer.android.com/build/releases/agp-9-0-0-release-notes
- Android NDK revision history:
  https://developer.android.com/ndk/downloads/revision_history
- FreeDOS kernel repository:
  https://github.com/FDOS/kernel
