# Feature 010: Classic host UI and CPU settings

**Status:** In implementation

- **FR-001:** All project-owned screens use a touch-friendly classic Windows
  application treatment: gray panels, navy title bars, square bevels, readable
  scalable text and visible keyboard focus. Interactive targets are at least
  48 dp. Guest pixels and the Android document picker are not redesigned.
- **FR-002:** Machines, Tests and About are separate destinations. Machine rows
  identify role, selected execution, cycles and recovery state. Selection alone
  never starts a machine. Build identity remains visible in the library.
- **FR-003:** Machine properties have General, CPU, Media and Maintenance tabs
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

Acceptance includes dirty Cancel, save failure, repeated mode changes, two-copy
target selection, clean preference persistence, crash recovery, disabled-state
explanations, enlarged text and keyboard/touch navigation. The engine, audio,
FPS/GPU, benchmark automation and dropped long campaigns are out of scope.
