# Feature Specification: Repository and Backup Tooling

The opt-in promotion adds `scripts/build-android.sh --release`: build an unsigned
release APK and checksum using the same pinned toolchain. No signing keys,
installation or publication are implied; debug remains the default.

**Feature**: `003-repository-tooling`

**Created**: 2026-08-13

**Status**: Implemented

**Input**: Organize the codebase and its local tooling so development, tablet
backup, local Git history, and later GitHub publication are understandable and
safe.

## User Scenarios & Testing

### User Story 1 - Create a verified tablet backup (Priority: P1)

As the owner of a guest machine, I can run one documented command on Debian to
copy a stopped machine from Android app-private storage into an ignored local
backup directory and prove that the disk bytes match the tablet source.

**Acceptance Scenarios**:

1. **Given** RoboWindows is stopped and a machine ID is supplied, **When** a
   backup is created, **Then** the tool records source size and SHA-256, streams
   the disk to Debian, verifies the decompressed result, and publishes the
   backup directory only after every check passes.
2. **Given** the app is running, the machine ID is unsafe, or the destination
   already exists, **When** backup is requested, **Then** the tool refuses
   without changing the tablet or existing backup.
3. **Given** a completed backup, **When** verification is run later, **Then**
   stored-file checksums, gzip integrity, raw size, and raw SHA-256 are checked.

### User Story 2 - Understand and run project tooling (Priority: P2)

As a contributor, I can find a short catalog explaining setup, dependency,
build, host-test, device-test, install, backup, and repository-check scripts,
including which commands mutate a tablet or require proprietary local data.

### User Story 3 - Prepare a public repository safely (Priority: P3)

As the maintainer, I can run a repository hygiene check that rejects tracked
guest media, credentials, generated output, and unexpectedly large files before
publishing to GitHub.

**Acceptance Scenarios**:

1. **Given** the pre-publication repository contains private development
   history, **When** the first public branch is prepared, **Then** GitHub receives
   one audited root snapshot and none of the earlier commits or private refs.

## Requirements

- **FR-001**: Backups MUST be written on the Debian host under an ignored path
  unless an explicit destination is supplied.
- **FR-002**: Backup creation MUST never start a guest, overwrite a backup, or
  modify the source disk.
- **FR-003**: Backup publication MUST be staged and atomic within one filesystem.
- **FR-004**: A backup MUST include the compressed disk, working launch config,
  available profile metadata, a machine-readable manifest, restore notes, and
  checksums. User ISO files MUST remain excluded.
- **FR-005**: Device discovery MUST not encode a developer-specific ADB path or
  tablet serial in tracked source.
- **FR-006**: Existing setup/build/test entry points MUST remain available.
- **FR-007**: Generated artifacts, dependency checkouts, backups, guest media,
  signing material, and local SDK configuration MUST remain ignored by Git.
- **FR-008**: GitHub publication MUST remain blocked until a project license and
  third-party notice review are completed.
- **FR-009**: The first public push MUST contain only the audited source snapshot;
  pre-publication commits and backup refs MUST remain local.
- **FR-010**: The tracked repository MUST expose a public specification index and roadmap that
  link the constitution, numbered feature records, current work, dependencies, and explicit
  non-promises without requiring readers to discover dot-prefixed directories.

## Success Criteria

- **SC-001**: A completed backup's decompressed SHA-256 equals the source tablet
  disk SHA-256.
- **SC-002**: Backup verification detects a changed stored file or raw disk.
- **SC-003**: Host tests and repository hygiene checks pass from the documented
  entry points.
- **SC-004**: No tracked file contains proprietary guest media, credentials, a
  private signing key, or a hard-coded device serial.
