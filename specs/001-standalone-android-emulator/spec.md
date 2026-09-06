# Feature Specification: Standalone Android DOS and Windows Emulator

**Feature**: `001-standalone-android-emulator`

**Created**: 2026-08-09

**Status**: Draft

**Input**: Create a GitHub-shareable standalone Android application for the
SM-T500 that runs DOS-family software and compatible Windows guests, with
ordinary Bluetooth keyboard and mouse behavior and without RetroArch.

## User Scenarios & Testing

### User Story 1 - Validate native physical input (Priority: P1)

As an SM-T500 user, I can open an input diagnostics screen and verify exactly
how my physical keyboard and mouse are recognized before starting a guest.

**Why this priority**: Correct Bluetooth input is the reason the project exists
and can be proven without proprietary guest software or a completed emulator.

**Independent Test**: Install the debug APK on the target tablet, connect the
known Inateck keyboard and Logitech LIFT mouse, and observe every required event
without launching a guest.

**Acceptance Scenarios**:

1. **Given** the mixed-source Inateck keyboard is connected, **When** ordinary,
   modifier, arrow, navigation, and function keys are pressed and released,
   **Then** diagnostics show distinct down/up events and preserve left/right
   modifiers without converting the device into a gamepad.
2. **Given** the LIFT mouse is connected, **When** it moves, clicks three
   buttons, and scrolls, **Then** diagnostics show motion, button transitions,
   and wheel axes from the physical mouse only.
3. **Given** pointer capture is active, **When** the user invokes the documented
   release action, **Then** Android regains the pointer without trapping the
   user or terminating the app.
4. **Given** either peripheral disconnects and reconnects, **When** Android
   reports the device again, **Then** diagnostics resume without restarting the
   app or duplicating input.

---

### User Story 2 - Boot freely distributable DOS test media (Priority: P2)

As a user, I can install a standalone APK, import or select freely
redistributable DOS test media, and boot it without RetroArch or root access.

**Why this priority**: This proves the Android frontend, emulator integration,
storage flow, rendering, audio baseline, and native input end to end while
remaining publishable and reproducible.

**Independent Test**: On a clean app install, import a checksummed open test
image, boot it, exercise keyboard/mouse/video/audio/disk tests, suspend/resume,
and shut down cleanly.

**Acceptance Scenarios**:

1. **Given** a clean installation, **When** the user selects supported media
   through Android's document UI, **Then** the app imports it into a persistent
   VM workspace and never requires unrestricted storage access.
2. **Given** valid DOS test media, **When** the user starts it, **Then** the guest
   reaches its expected prompt or UI and accepts physical PC keyboard and mouse
   input.
3. **Given** a running guest, **When** the app pauses and resumes, **Then** the
   guest remains usable and committed disk writes are not corrupted.
4. **Given** invalid, inaccessible, or unsupported media, **When** import or boot
   is attempted, **Then** the app reports a useful error and preserves existing
   guest state.
5. **Given** the user navigates, configures, starts, or stops a guest, **When**
   any host interface is shown, **Then** it uses the RoboWindows interface and
   never reveals an emulator-provided menu, browser, mapper, settings page, or
   on-screen display.

---

### User Story 3 - Manage reproducible guest profiles (Priority: P3)

As a user, I can create, inspect, start, stop, and copy named guest profiles so
different DOS or Windows configurations do not silently alter one another.

**Why this priority**: Multiple operating-system families require explicit
machine configurations and safe state management rather than one global setup.

**Independent Test**: Create two profiles from known media, change one profile,
and demonstrate that its configuration and writable disks do not affect the
other.

**Acceptance Scenarios**:

1. **Given** supported boot media, **When** a profile is created, **Then** its
   media, configuration, provenance, and writable state are visibly associated.
2. **Given** an existing profile, **When** it is copied for a destructive test,
   **Then** the original disk remains byte-for-byte unchanged.
3. **Given** a running profile, **When** the user requests orderly exit, **Then**
   writes are flushed before the session closes.
4. **Given** a running guest, **When** the guest completes a normal power-off,
   **Then** writes are flushed and RoboWindows returns to the machine list
   without rebooting the guest.

---

### User Story 4 - Run evidence-backed Windows profiles (Priority: P4)

As a licensed user, I can import my own Windows installation media and use a
documented compatibility profile for releases validated by this project.

**Why this priority**: Windows compatibility is important, but depends on the
safe, testable emulator and input foundation established by earlier stories.

**Independent Test**: Use user-owned media to install or import a checksummed
guest disk, then run the profile's published boot, input, persistence, display,
audio, suspend/resume, and stability checks on the SM-T500.

**Acceptance Scenarios**:

1. **Given** user-owned installation media, **When** a documented Windows
   profile is selected, **Then** the app explains required configuration and
   never bundles or uploads proprietary content.
2. **Given** a guest release listed as validated, **When** its published test
   procedure is repeated on the target configuration, **Then** observed results
   meet the profile's stated compatibility level.
3. **Given** an unvalidated Windows release, **When** the user attempts to use
   it, **Then** the app does not present it as guaranteed compatible.

## Edge Cases

- Android classifies one peripheral as keyboard, D-pad, and joystick at once.
- Android reserves or intercepts system key combinations before the app sees
  them; diagnostics must distinguish this from emulator loss where possible.
- Mouse capture is lost on rotation, app switching, lock, or Bluetooth dropout.
- Touch and physical mouse events arrive concurrently.
- Storage permission or document access is revoked after import selection.
- The process is killed while a guest disk has pending writes.
- Media is truncated, read-only, too large for available space, or has changed
  since its checksum was recorded.
- A profile created by a newer application version is opened by an older one.
- Guest video changes resolution or aspect ratio during boot.
- An optimized CPU core behaves differently from the conservative core.

## Requirements

### Functional Requirements

- **FR-001**: The system MUST provide a standalone ARM64 Android application
  that does not require RetroArch or root access.
- **FR-002**: The system MUST treat the Samsung SM-T500 as the primary release
  validation device while avoiding unnecessary device locks.
- **FR-003**: The system MUST provide an input diagnostics view before guest
  input integration is considered complete.
- **FR-004**: The system MUST report input descriptor, sources, vendor/product
  identifiers, keyboard type, keys, buttons, motion axes, and capture state in
  debug diagnostics without exposing Bluetooth addresses in public logs.
- **FR-005**: The system MUST preserve physical keyboard down/up transitions,
  left/right modifiers, arrows, navigation keys, and function keys that Android
  delivers to the app.
- **FR-006**: The system MUST retain keyboard handling for devices that also
  advertise D-pad, joystick, consumer-control, or system-control capabilities.
- **FR-007**: The system MUST support relative physical-mouse movement, at least
  left/right/middle buttons, vertical wheel input, pointer capture, and an
  explicit pointer-release action.
- **FR-008**: The system MUST keep touchscreen-derived pointing separate from
  physical mouse events and prevent duplicate guest input.
- **FR-009**: The system MUST use Android's document-mediated storage flow for
  user media and a persistent application-owned location for writable guests.
- **FR-010**: The system MUST never modify the user's sole source media and MUST
  offer a copy-based workflow for destructive or writable testing.
- **FR-011**: The system MUST save or flush writable guest state on orderly exit
  or guest-requested power-off, return a powered-off guest to the machine list,
  and handle Android pause/resume without silent corruption.
- **FR-012**: The system MUST support named guest profiles with explicit media,
  machine configuration, provenance, and compatibility status.
- **FR-013**: The system MUST boot a checksummed, freely redistributable DOS test
  environment before any proprietary Windows profile can pass release gates.
- **FR-014**: Each advertised DOS or Windows compatibility profile MUST identify
  the guest release, emulator revision, configuration, test procedure, target
  device, and repeatable result.
- **FR-015**: The system MUST accept user-owned proprietary media without
  embedding it in the APK, repository, logs, or published artifacts.
- **FR-016**: Debug and release artifacts MUST expose build provenance sufficient
  to identify source revisions and configuration.
- **FR-017**: The project MUST be publishable on GitHub with an open-source
  license, third-party notices, build instructions, and no prohibited assets.
- **FR-018**: The system MUST begin validation with the conservative CPU path and
  advertise an optimized path only after it passes the same correctness suite.
- **FR-019**: The system MUST own the entire visible host interface. Upstream
  emulator menus, file browsers, controller mappers, settings, notifications,
  branding, and on-screen displays MUST NOT be exposed to users.
- **FR-020**: Emulator output MUST appear only as the guest display inside a
  RoboWindows runtime screen; all host controls, errors, progress, and overlays
  MUST use RoboWindows components.

### Key Entities

- **Guest Profile**: Named configuration linking guest family/release, machine
  settings, attached media, writable state, and compatibility evidence.
- **Media Asset**: User-provided or redistributable file with type, origin,
  checksum, size, access mode, and license/provenance classification.
- **Input Device Record**: Sanitized Android identity and capability snapshot
  used for diagnostics and regression evidence.
- **Compatibility Profile**: Versioned claim tying an exact guest and emulator
  configuration to a test matrix and observed target-device results.
- **Build Manifest**: Source revisions, toolchain versions, flags, artifact
  checksums, and build identity.

## Success Criteria

### Measurable Outcomes

- **SC-001**: On the connected SM-T500, 100% of Android-delivered keys in the
  documented keyboard diagnostic set show matched down/up events across 10 runs.
- **SC-002**: The connected LIFT mouse completes relative motion, three-button,
  vertical-wheel, capture/release, and reconnect tests in 10 consecutive runs
  without duplicate physical/touch events.
- **SC-003**: A clean install boots the pinned redistributable DOS test image and
  completes the defined video, audio, input, persistence, and shutdown checks in
  10 consecutive runs.
- **SC-004**: The app survives 10 pause/resume cycles and one process recreation
  test without corrupting the tested writable disk clone.
- **SC-005**: Every published compatibility claim is backed by a checked-in test
  procedure and result referencing exact checksums and build provenance.
- **SC-006**: A clean checkout can produce the documented APK using only pinned
  open-source dependencies plus a separately installed documented toolchain.
- **SC-007**: Automated repository checks find no guest OS images, product keys,
  private keystores, or unapproved proprietary drivers in tracked files or
  release artifacts.

## Assumptions

- “DOS and Windows family” denotes incremental, evidence-backed compatibility;
  it does not promise every version or every application at initial release.
- The first public artifact targets `arm64-v8a`; additional ABIs are deferred.
- The SM-T500 remains available by ADB for acceptance testing.
- The user supplies and is responsible for licenses to proprietary guest media.
- Windows 98 SE is the first planned Windows validation profile after the free
  DOS milestone because the original handoff contains its baseline.
- Networking, 3D acceleration, touchscreen controls, and gamepad support are
  optional later capabilities unless required by a validated profile.

## Explicitly Out of Scope for the First Milestone

- Bundling DOS or Windows distributions whose licenses do not permit it.
- Claiming universal DOS/Windows compatibility.
- Root-only installation or direct block-device access.
- RetroArch integration or RetroPad input translation.
- Re-skinning or configuring an upstream DOSBox menu as the product interface;
  upstream UI is bypassed rather than cosmetically hidden.
- Network play, cloud synchronization, Voodoo acceleration, and polished
  touchscreen gaming controls.
- Release-store publishing; the first milestone is a reproducible debug APK.
