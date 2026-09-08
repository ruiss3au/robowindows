# Tasks: ARM64 Dynamic Recompilation

- [ ] T001 Test the execution contract: full fallback migration, per-machine
  clean provenance, prepared/executing/running/closed-clean/needs-check/blocked
  states, late crash, stale callback, pause, hang and user Exit (FR-002–006, 018–021).
- [ ] T002 Implement authoritative profile records, durable attempt journals and
  verified config regeneration. Inject failure at every persistence boundary;
  assert no native start and no dismissal bypass on failure (FR-003, 019, 021).
- [ ] T003 Implement and test Normal/Dynamic selection with fixed 20k policy,
  disabled cycle controls, and safe migration; expose only after T012–T014 pass.
- [ ] T004 Produce a symbolized debug build and reproduce the failure once on a
  verified, cleanly stopped experimental copy.
- [ ] T005 Record and symbolicate the redacted native tombstone.
- [ ] T006 Audit ARMV8LE executable-cache, W^X, instruction-cache, branch, and
  block-linking behavior at the pinned core revision.
- [ ] T007 If proven, isolate the minimal fix under `patches/` with provenance.
- [ ] T008 Run host tests, repository hygiene, and the Android debug build.
- [ ] T009 Run ten start/stop cycles and CPU, input, audio, pause/resume,
  shutdown, and disk-health acceptance on the experimental copy.
- [ ] T010 Run the versioned benchmark, AoE2 scenario, and 30-minute thermal
  comparison against the highest sustainable normal-core profile.
- [ ] T011 Record promotion or rejection evidence; do not silently expose an
  unvalidated profile to stable machines.
- [ ] T012 Enforce start-time ownership and effective-config checks, including
  writable attachments, utility disks, aliases and debug launch paths. Test with
  stable-role disposable fixtures and unchanged hashes (FR-001, 016–017; SC-001).
- [ ] T013 Isolate dynamic execution in a dedicated process; test surface/input/
  audio lifecycle, child crash/hang, teardown acknowledgement and mode switching
  without reusing dynamic process state for normal guests (FR-020; SC-002–003).
- [ ] T014 Implement needs-check quarantine and document a versioned read-only
  FAT check procedure for every writable volume. Test both pass and reject paths,
  unsupported filesystems and normal recovery boot (FR-007; scenario 4).
- [ ] T015 Assert the effective ARM64 decoder and exact profile in bounded
  diagnostics; detect silent interpreter fallback and redact crash artifacts.
- [ ] T016 Prepare versioned CPU correctness and benchmark fixtures before T009
  and T010. Apply SC-003–005 exactly, including matched runs and soak windows.

Execution order: T001–002, T012–014, T003, T008, T004–007, T015–016,
then repeat applicable T008 checks and execute T009–011. Keep ordinary dynamic
UI hidden until the safety stages pass; release visibility also requires SC-001–005.

## Implementation progress — 2026-09-08

T001/T002/T012 have begun with a schema-v7 authoritative selection/generation,
per-machine clean-shutdown provenance, atomic `prepared`/`executing` journals,
fresh normal-start ownership checks, and conservative startup recovery. The
host probe covers selection, journal ordering, normal fallback regeneration and
quarantine. This is not completion: fault injection, all state transitions,
read-only health checking, separate-process execution and every device gate
remain open. Dynamic UI and native handoff remain disabled.
