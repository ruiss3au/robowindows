# Feature 010: Classic host UI and CPU settings

## Opt-in modes and optional counters amendment — 2026-09-10

Supersedes FR-004's debug/copy restriction and experimental/trial mode labels:
offer Normal / DynRec · fixed 20k on eligible stopped machines in both builds.
Retain transactional and recovery rules. Describe known audio and unverified
sustained behavior without implying quality qualification.
FR-013: Add app-wide Show performance counters, initially false, remembered
across launches, controllable in Tests and sessions. Hide only existing live
diagnostic labels; retain safety polling, recovery/fallback notices and
stop/readiness controls. Do not add instrumentation or resize guest video.
FR-014: Tests separates current-build CPU status from Run CPU tests / Run again.
Provide progress, cancellation and results, reject busy/recovery state, and never
open machine disks. Reruns invalidate prior success until a complete new pass.

**Status:** Scoped host-UI acceptance complete (2026-09-10)

- **FR-001:** All project-owned screens use a touch-friendly classic Windows
  application treatment: gray panels, navy title bars, square bevels, readable
  scalable text and visible keyboard focus. Interactive targets are at least
  48 dp. Guest pixels and the Android document picker are not redesigned.
- **FR-002:** Machines, Tests and About are separate destinations. Machine rows
  identify role, selected execution, cycles and recovery state. Selection alone
  never starts a machine. Build identity remains visible in the library.
- **FR-003:** Machine properties have General, CPU, Media and Maintenance tabs
  (Feature 011 adds Display)
  and OK/Cancel/Apply. Draft changes never mutate storage until saved. Dirty
  navigation and immediate maintenance/media actions require explicit resolution.
- **FR-004:** Eligible debug experimental copies offer Normal and DynRec
  (experimental). DynRec fixes execution at 20k and preserves the separate Normal
  cycle choice; disabled controls explain eligibility. Stable and release
  restrictions remain intact. Start revalidates and uses the selected machine
  and mode, never an inferred singleton experimental copy.
- **FR-005:** Verified clean DynRec shutdown retains its selected preference;
  failed/interrupted attempts restore Normal and retain existing quarantine.
  Closed-clean recovery is idempotent. Selection never boots or promotes.
- **FR-006:** Batch settings save rejects stale generations and active/recovery
  state; interrupted publication cannot permit stale launch configuration to boot.
- **FR-007:** Tests expose existing disposable CPU and input diagnostics only.
  Results identify their scope; CPU success is not Windows stability evidence.
- **FR-008:** Stop/Exit and Restart require user confirmation without forwarding
  dialog input to the guest. Normal overlay auto-hide, DynRec persistent stop and
  readiness controls, guest geometry and input capture contracts are preserved.
- **FR-009:** Controls drawn over guest video use translucent panel and button
  backgrounds, including confirmed readiness buttons. Labels and keyboard focus
  remain readable; library/properties/dialog panels remain opaque. Do not resize
  the guest or hide the persistent DynRec stop/readiness controls to achieve this.
- **FR-010:** General properties include an editable machine display name in the
  same Apply/OK/Cancel draft. Trim surrounding whitespace; accept 1–64 Unicode
  code points and reject control/format characters. Invalid names preserve the
  draft and show a useful error. Rename only stopped, non-recovery machines;
  preserve machine ID, disk/media paths, ownership, CPU choice and clean-shutdown
  provenance. Names are labels, never filesystem paths or machine selectors.
- **FR-011:** Android Back, including the modern system Back callback, honors
  dirty-properties confirmation and guest reveal/stop confirmation. It must not
  silently abandon drafts or stop a guest by bypassing the host navigation flow.
- **FR-012:** The installed app has an original, recognizable retro-computer
  launcher icon under the existing RoboWindows label. Supply a legacy fallback
  and adaptive launcher resource with mask-safe artwork. Do not use Microsoft or
  DOSBox branding, add permissions, change machine defaults or auto-start guests.

Acceptance includes dirty Cancel, save failure, repeated mode changes, two-copy
target selection, clean preference persistence, crash recovery, disabled-state
explanations, rename validation/persistence/rollback, enlarged text and
keyboard/touch navigation. The engine, audio,
FPS/GPU, benchmark automation and dropped long campaigns are out of scope.
