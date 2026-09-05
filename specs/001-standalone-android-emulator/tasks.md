# Tasks: Standalone Android DOS and Windows Emulator

**Input**: [spec.md](spec.md), [research.md](research.md),
[plan.md](plan.md), [data-model.md](data-model.md), and [contracts](contracts/)  
**Strategy**: Deliver and validate one independently useful user story at a time.

## Phase 1: Reproducible setup

- [x] T001 Record host and SM-T500 baseline in `docs/environment.md`
- [x] T002 Establish public repository exclusions in `.gitignore`
- [x] T003 Pin upstream revisions and Android toolchain in `lock/sources.lock`
- [x] T004 Add reviewable user-run prerequisite installer in
  `scripts/bootstrap-debian.sh`
- [x] T005 User reviews and runs `scripts/bootstrap-debian.sh`
- [x] T006 Implement and test exact-SHA checkout verification in
  `scripts/fetch-sources.sh`
- [x] T007 Add GPL-2.0-or-later project license and third-party notice inventory
  in `LICENSE` and `THIRD_PARTY_NOTICES.md`
- [x] T008 Add Gradle 9.1 wrapper and dependency-verification metadata

**Checkpoint**: Toolchain and source trees are reproducible without floating
dependencies.

## Phase 2: Foundational Android build spike

- [x] T009 Create minimal AGP 9.0.1 application with RoboWindows-owned test UI
  under `android/`
- [x] T010 Implement the minimum native frontend callbacks required to host the
  pinned DOSBox Pure core without RetroArch
- [ ] T011 Isolate NDK compatibility and internal menu/OSD suppression changes
  under `native/patches/`
- [ ] T012 Embed source/toolchain identity and dirty state in debug builds
- [x] T013 Implement `scripts/build-android.sh --debug` and artifact checksum
- [x] T014 Install and launch debug APK on the authorized SM-T500 via ADB
- [ ] T015 Validate guest surface, pause/resume, rotation, clean exit, and absence
  of upstream UI; record results in `docs/testing.md`

**Checkpoint**: RoboWindows guest surface launches on SM-T500 and upstream UI is
unreachable. This phase blocks all user-story implementation.

## Phase 3: User Story 1 — Native physical input diagnostics (P1 MVP)

### Tests first

- [x] T016 [US1] Add native bridge serialization/unit tests under
  `tests/input/`
- [ ] T017 [US1] Add Android instrumentation tests for synthetic key/mouse,
  capture, focus loss, and redaction under `android/src/androidTest/`
- [ ] T018 [US1] Add sanitized ADB result collector in
  `scripts/test-sm-t500.sh`

### Implementation

- [x] T019 [US1] Implement `KeyEventRecord`, `MouseEventRecord`,
  `TouchEventRecord`, and `DeviceRecord` bridge from `contracts/input-bridge.md`
- [x] T020 [US1] Implement connected-device capability diagnostics UI
- [x] T021 [US1] Preserve hardware scan codes, matched down/up, repeat count,
  and left/right modifier metadata
- [x] T022 [US1] Keep keyboard handling when sources also include D-pad/joystick
- [x] T023 [US1] Implement generic mouse motion, three buttons, both wheel axes,
  pointer capture, and explicit release
- [x] T024 [US1] Separate touch injection policy from physical mouse input
- [x] T025 [US1] Cancel held device state on focus loss/disconnect and recover on
  reconnect
- [ ] T026 [US1] Run automated and manual Inateck/LIFT matrix 10 times and record
  sanitized evidence

**Checkpoint**: SC-001 and SC-002 pass; diagnostics APK is a useful standalone
MVP even before guest boot.

## Phase 4: User Story 2 — Freely redistributable DOS boot (P2)

### Tests first

- [ ] T027 [US2] Add import failure, insufficient-space, checksum, interrupted
  copy, and recovery tests
- [ ] T028 [US2] Inventory licenses for the selected official FreeDOS test input
  and record its URL/checksum in `lock/checksums.sha256`
- [ ] T029 [US2] Define guest boot/video/audio/input/persistence/lifecycle test
  procedure in `docs/testing.md`

### Implementation

- [x] T030 [US2] Implement document-picker import with staged app-private copy
- [x] T031 [US2] Implement checksum/provenance record and failed-import cleanup
- [x] T032 [US2] Connect typed physical input to DOSBox Pure PC input paths
- [x] T033 [US2] Implement safe session pause/resume, flush, and orderly exit
- [ ] T034 [US2] Boot pinned FreeDOS test input with conservative CPU core
- [ ] T035 [US2] Pass the complete DOS matrix 10 times and archive sanitized logs

**Checkpoint**: SC-003 and SC-004 pass on the SM-T500.

## Phase 5: User Story 3 — Reproducible guest profiles (P3)

### Tests first

- [ ] T036 [US3] Add profile schema, migration, clone isolation, and recovery
  tests
- [ ] T037 [US3] Add build-manifest and prohibited-artifact repository tests

### Implementation

- [x] T038 [US3] Implement versioned `GuestProfile` and `MediaAsset` persistence
- [x] T039 [US3] Implement safe profile create/copy/start/stop/recovery flows
- [ ] T040 [US3] Implement versioned `CompatibilityProfile` records and UI status
- [ ] T041 [US3] Emit complete `BuildManifest` and SHA-256 artifact set
- [ ] T042 [US3] Document corresponding-source and release procedure

**Checkpoint**: SC-005 through SC-007 pass.

## Phase 6: User Story 4 — Evidence-backed Windows profiles (P4)

- [ ] T043 [US4] Add conservative Debian reference build script and checksum
- [ ] T044 [US4] Define the Windows 98 SE profile without redistributing media,
  keys, or restricted drivers
- [ ] T045 [US4] Create one user-owned golden disk on the reference build and
  preserve it as immutable external test input
- [ ] T046 [US4] Run Debian conservative-core compatibility matrix
- [ ] T047 [US4] Copy the exact disk/config to SM-T500 and run Android
  conservative-core matrix
- [ ] T048 [US4] Enable ARM dynrec alone and repeat the same matrix
- [ ] T049 [US4] Publish the precise compatibility level and evidence
- [ ] T050 [US4] Repeat T044–T049 independently for each later DOS/Windows
  family profile

## Final convergence

- [ ] T051 Re-check every constitutional gate against implementation
- [ ] T052 Verify clean-checkout build with user-run bootstrap instructions
- [ ] T053 Verify repository/release contains no prohibited or private assets
- [x] T054 Perform a preliminary project-name and trademark review before public
  GitHub launch and add an explicit non-endorsement/trademark statement
- [ ] T055 Converge code, tests, docs, manifests, and compatibility claims against
  all accepted requirements
- [x] T056 Add a README quick-start for installing Windows from user-owned media
  and importing an already-installed disk

## Dependencies and safe parallelism

- T005 blocks T008–T015; T006 and T007 do not require the Android toolchain.
- T009–T015 are foundational and block all user stories.
- Within US1, tests T016–T018 may proceed independently, then bridge T019 blocks
  UI/integration tasks. T020–T024 touch related Android files and should be
  sequenced unless explicitly partitioned.
- US2 depends on US1 because guest input consumes the typed bridge.
- US3 depends on the import/session behavior proven by US2.
- US4 depends on safe profiles and reproducibility from US3.
- Documentation, license inventory, and test fixture preparation may run in
  parallel only when they do not modify the same files.
