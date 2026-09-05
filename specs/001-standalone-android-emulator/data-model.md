# Data Model

## GuestProfile

- `id`: stable locally generated identifier
- `name`: user-visible name
- `schemaVersion`: migration discriminator
- `guestFamily`: DOS, Windows, or unclassified
- `guestRelease`: optional user-entered/validated release
- `machineConfig`: immutable reference to a versioned configuration snapshot
- `mediaRefs`: ordered attached media roles
- `compatibilityProfileRef`: optional evidence-backed profile
- `createdAt`, `updatedAt`, `lastBootedAt`

States: `ready`, `importing`, `running`, `stopping`, `recovery-required`,
`unsupported-version`. Only `ready` can transition to `running`; interrupted
imports never become ready.

## MediaAsset

- `id`, `displayName`, `role`, `format`, `sizeBytes`
- `sha256`
- `origin`: official-open, user-provided, generated-test, clone
- `licenseClass`: redistributable, user-owned-proprietary, unknown
- `sourceUri`: private local metadata, never included in public diagnostics
- `workspacePath`: app-private relative path
- `writable`: false for imported source; true only for explicit clone
- `parentSha256`: checksum of source from which a clone was made

## InputDeviceRecord

- session-local Android device ID
- descriptor hash (not raw Bluetooth address or unique ID)
- display name, vendor ID, product ID, keyboard type, source bitset
- motion ranges and supported axes
- connection timestamps and diagnostic result summary

Public logs omit serial numbers, Bluetooth addresses, unique IDs, and raw
descriptors.

## CompatibilityProfile

- profile ID and schema version
- guest family/release/edition
- exact emulator source pins and build manifest
- immutable machine configuration
- required media checksums or user-supplied roles
- test matrix version and results
- target device/OS class
- status: experimental, boots, usable, validated

## BuildManifest

- app version and build type
- repository revision/dirty marker
- three upstream source pins and patch hashes
- JDK/Gradle/AGP/SDK/NDK/ABI/compiler flags
- APK/native-library/config checksums
- build timestamp controlled by reproducible-build policy

