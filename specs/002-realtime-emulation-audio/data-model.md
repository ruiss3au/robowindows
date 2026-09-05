# Data Model: Real-Time Emulation and Clean Audio

## PerformanceProfile

| Field | Type | Rules |
|---|---|---|
| `id` | stable string | Product-owned identifier; no user path |
| `guestFamily` | enum | `dos`, `windows_3x`, `windows_9x`, `other_windows` |
| `cpuCore` | enum | `normal`, `auto`, `dynamic_experimental` |
| `cyclePolicy` | enum | `auto`, `fixed`, `max` |
| `fixedCycles` | positive integer/null | Required only for `fixed` |
| `memoryMb` | integer | Existing supported values only |
| `presentationCapFps` | integer | At least 15 for validated interactive profiles |
| `validationState` | enum | `candidate`, `validated`, `experimental`, `rejected` |
| `targetFingerprint` | string/null | Required for target-specific validation |
| `evidenceId` | string/null | Required for `validated` |

`dynamic_experimental` cannot transition to `validated` without the full conservative
correctness suite. A rejected profile cannot be auto-selected.

## FrameSlot

| Field | Type | Rules |
|---|---|---|
| `sequence` | monotonic integer | Identifies newest complete frame |
| `width`, `height` | positive integer | Revalidated on every geometry change |
| `pitch` | positive integer | At least the source row byte count |
| `pixelFormat` | enum | Explicitly supported core format |
| `pixels` | bounded byte buffer | Never references expired core callback memory |
| `publishedAtNs` | monotonic timestamp | Diagnostics only |

State: `empty → writing → published → reading → reusable`. A producer never overwrites a
slot being read; the consumer chooses the newest published sequence.

## AudioQueueState

| Field | Type | Rules |
|---|---|---|
| `phase` | enum | `stopped`, `prebuffering`, `playing`, `suspended`, `recovering` |
| `readFrame`, `writeFrame` | monotonic integer | SPSC-owned positions |
| `capacityFrames` | positive integer | Fixed at session initialization |
| `prebufferFrames` | positive integer | Bounded and less than capacity |
| `sampleRate`, `channels` | integer | Actual host format; stereo currently required |
| `generation` | monotonic integer | Invalidates stale samples across resume/reopen |

Transitions:

- `stopped → prebuffering`: session/audio stream initialized.
- `prebuffering → playing`: queue reaches threshold and guest is runnable.
- `playing → suspended`: explicit pause, background/focus interruption, or session stop.
- `suspended → prebuffering`: stale queue cleared and a new generation begins.
- `playing → recovering`: AAudio disconnect/error.
- `recovering → prebuffering`: stream reopened successfully.
- Any state → `stopped`: terminal session teardown.

## RuntimeTelemetrySnapshot

See [contracts/runtime-telemetry.md](contracts/runtime-telemetry.md). Snapshots are bounded,
rate-based evidence. Counter ownership follows producer/consumer ownership to avoid locks.

## RuntimeHealthRecord

| Field | Type | Rules |
|---|---|---|
| `sessionId` | random opaque string | Contains no path or guest content |
| `profileId` | string | Profile attempted |
| `lastKnownSafeProfileId` | string | Always conservative and previously healthy |
| `startupState` | enum | `pending`, `healthy`, `stopped`, `abnormal` |
| `startedAt`, `healthyAt` | timestamps | App-private recovery metadata |
| `termination` | enum/null | `user_stop`, `orderly_shutdown`, `native_failure`, `unknown` |

An uncleared `pending` record found at next launch becomes `abnormal`; the experimental
profile is not automatically retried. This record never owns or mutates guest media.

## CompatibilityEvidence

| Field | Type | Rules |
|---|---|---|
| `evidenceId` | string | Stable report identifier |
| `buildId`, `coreRevision` | string | Exact reproducible inputs |
| `deviceFingerprint` | string | Includes model, ABI, Android build |
| `profileId`, `workloadId` | string | Versioned definitions |
| `diskClonePreChecksum` | digest | Test clone only |
| `diskClonePostChecksum` | digest | Interpreted by workload write expectations |
| `metrics` | snapshot series/summary | Contract-compliant and bounded |
| `result` | enum | `pass`, `fail`, `invalid_environment` |

