# Feature Specification: Visible Build Identity

**Feature**: `006-visible-build-identity`

**Created**: 2026-09-06

**Status**: Implemented

## User Scenario & Testing

### User Story 1 - Identify the installed build (Priority: P1)

As a tester, I can read the application version and source revision from the
main Machines screen so device findings can be tied to the APK that produced
them.

**Acceptance Scenarios**:

1. **Given** RoboWindows opens the Machines screen, **When** the user looks below
   the top-left `ROBOWINDOWS` brand, **Then** the app version and abbreviated Git
   revision used by the build are visible.
2. **Given** the APK is built from a working tree with source changes, **When**
   its identity is generated, **Then** the revision ends in `+dirty` rather than
   claiming to be the clean commit.
3. **Given** a source archive or build environment has no Git metadata, **When**
   no explicit revision override is supplied, **Then** the UI shows `unknown`
   and the build still succeeds.

## Requirements

- **FR-001**: The main screen MUST display `v<version name> · <revision>` in its
  visible library status bar (Feature 010 replaces the former brand-adjacent placement).
- **FR-002**: The revision MUST default to a 12-character abbreviated `HEAD` and
  MUST add `+dirty` when tracked or untracked non-ignored files differ.
- **FR-003**: `ROBOWINDOWS_BUILD_REVISION` MUST allow reproducible archive/CI
  builds to supply the revision without Git discovery.
- **FR-004**: Build revision input MUST be restricted to a short display-safe
  character set before it is embedded in generated Java source.
- **FR-005**: Missing or invalid revision metadata MUST fall back to `unknown`
  without preventing the Android build.

## Success Criteria

- **SC-001**: Host tests cover clean, dirty, and unknown display labels.
- **SC-002**: The Android build compiles a generated source-revision constant.
- **SC-003**: The SM-T500 Machines screen visibly identifies the installed APK.
