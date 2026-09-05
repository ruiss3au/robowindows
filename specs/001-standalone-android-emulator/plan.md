# Implementation Plan: Standalone Android DOS and Windows Emulator

**Feature**: `001-standalone-android-emulator` | **Date**: 2026-08-09  
**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Summary

Create a reproducible GPL-2.0-or-later ARM64 Android application with a completely
project-owned UI and native host around the pinned DOSBox Pure core. Unleashed
and ZillaLib are implementation references, never visible product UI. Deliver
input diagnostics first, then a freely redistributable DOS boot, safe guest
profiles, and evidence-backed Windows profiles.

## Technical Context

**Languages**: C/C++, Java, Gradle Kotlin DSL, POSIX shell  
**Runtime dependency**: pinned DOSBox Pure core  
**Reference sources**: pinned DOSBox Pure Unleashed and ZillaLib  
**Storage**: Storage Access Framework inputs; app-private files for profiles  
**Testing**: host unit tests, Android instrumentation, ADB smoke/regression scripts,
guest-side checks with checksummed media  
**Target**: SM-T500, LineageOS 23.2/Android 16 API 36, `arm64-v8a`  
**Minimum Android**: API 26  
**Rendering/audio baseline**: project-owned Android guest surface and audio host  
**Build**: JDK 17, Gradle 9.1 wrapper, AGP 9.0.1, NDK r28c  
**Constraints**: no RetroArch, no root requirement, no proprietary bundled media,
approximately 2.62 GiB device RAM, crash-safe guest copies

## Constitution Check

| Gate | Status | Evidence |
|---|---|---|
| Specification is source of truth | Pass | Requirements and acceptance scenarios precede implementation |
| Reproducible pins | Pass for plan | `lock/sources.lock`; artifact verification still to implement |
| Legal media boundary | Pass | Import-only proprietary workflow and release scan required |
| Primary hardware, portable architecture | Pass | SM-T500 target; standard API 26+ Android APIs; no root |
| Native PC input semantics | Pass by design | Typed key/mouse bridge; diagnostics precede guest mapping |
| Safe guest state | Pass by design | copy/stage/checksum/atomic publish flow |
| Correctness before optimization | Pass | interpreter/conservative gate precedes ARM dynrec |
| Incremental delivery | Pass | diagnostics → free DOS → profiles → Windows |
| Upstream-friendly open source | Pass by design | reuse ZillaLib boundary; isolated compatibility patches |

Re-check after the build spike and native bridge contract implementation.

## Project Structure

```text
android/                         # Entire visible product UI and Android boundary
├── build.gradle.kts
├── src/main/
│   ├── AndroidManifest.xml
│   ├── java/.../                # screens, guest view, diagnostics, profiles
│   └── cpp/                     # core frontend callbacks and JNI bridge
└── src/androidTest/             # target-device instrumentation tests
native/                          # project-owned native adapter/patch boundary
tests/                           # host tests and fixtures
scripts/
├── bootstrap-debian.sh          # user-run prerequisite installer
├── fetch-sources.sh             # pinned source checkout and verification
├── build-linux.sh
├── build-android.sh
├── verify-assets.sh
└── test-sm-t500.sh
config/                          # versioned guest/compatibility profiles
docs/                            # architecture, input, media, testing, licenses
lock/                            # exact sources/toolchains/checksums
specs/001-standalone-android-emulator/
third_party/                     # ignored generated checkouts
build/                           # ignored generated output
artifacts/                       # ignored distributable output/manifests
```

## Delivery Phases

### Phase 1: Reproducible build spike

1. Bootstrap missing Debian packages and Android SDK/NDK through the user-run
   pinned script.
2. Implement a source-fetch script that checks out exact SHAs as sibling trees
   under `third_party/` and refuses dirty or mismatched revisions.
3. Create a Gradle ARM64 application with a RoboWindows-owned test screen.
4. Compile DOSBox Pure behind a project-owned frontend callback adapter.
5. Disable internal menu/OSD entry paths with the smallest auditable patch.
6. Produce, install, and launch a debug APK; record its manifest and checksum.

**Exit**: The RoboWindows guest view receives a native test/core frame on the
SM-T500, survives one lifecycle round trip, and cannot expose upstream UI.

### Phase 2: Input diagnostics MVP

1. Define and test the typed Java/native input bridge contract.
2. Display sanitized connected-device capabilities and live key/mouse events.
3. Implement pointer capture/release and mixed-source keyboard handling.
4. Add ADB-driven diagnostics tests plus a short manual peripheral checklist.

**Exit**: User Story 1 and SC-001/SC-002 pass on the named peripherals.

### Phase 3: Freely distributable DOS boot

1. Implement safe document import into app-private staged storage.
2. Select and checksum an official FreeDOS test input after license inventory.
3. Connect physical events to DOSBox Pure PC input paths.
4. Validate boot, video, audio, persistence, pause/resume, and shutdown.

**Exit**: User Story 2 and SC-003/SC-004 pass with the conservative core.

### Phase 4: Guest profiles and reproducibility

1. Implement versioned profile/media/build-manifest models.
2. Add copy-before-write, checksum, free-space, and recovery behavior.
3. Automate manifests, checksums, source offer, notices, and prohibited-file scan.

**Exit**: User Story 3 and SC-005 through SC-007 pass.

### Phase 5: Windows compatibility profiles

1. Establish the Debian conservative-core reference for user-owned media.
2. Port a byte-identical disk/config to SM-T500 conservative core.
3. Validate ARM dynrec without changing other settings.
4. Publish only results supported by the compatibility matrix.

**Exit**: Windows 98 SE becomes the first evidence-backed Windows profile;
additional releases follow the same process independently.

## Risk Controls

| Risk | Control |
|---|---|
| Core wrapper is coupled to Pure menu/OSD | Compile-time suppression plus tests proving upstream UI has no reachable/rendered path |
| Android consumes system shortcuts | Diagnostics distinguish delivered vs intercepted keys; document unavoidable OS behavior |
| Guest disk corruption | immutable source, staged copies, flush protocol, checksums, kill/recovery tests |
| GPL/source-distribution failure | GPL project license, preserved notices, exact corresponding source and patch manifest |
| Product name/trademark confusion | explicit non-affiliation now; naming review before public release |
| Performance pressure causes premature dynrec use | conservative-core correctness gate blocks optimization claims |

## Complexity Tracking

No constitutional exception is approved. The Android application owns product
UI because profiles, document access, diagnostics, and runtime controls are
product behavior. Emulator execution remains behind a narrow native boundary.
