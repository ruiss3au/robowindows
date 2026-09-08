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
host probe covers selection, journal ordering, path-free normal-settings fallback
regeneration and quarantine. This is not completion: fault injection, all state transitions,
read-only health checking, separate-process execution and every device gate
remain open. Dynamic UI and native handoff remain disabled.

T013 has begun with the process boundary: a non-exported `:dynrec` service will
accept only an already-validated dynamic launch file and proxy native lifecycle
and input operations. The next substep is a host-side binder client; binding it
to the session UI, child-death acknowledgement, and disposable-fixture
validation remain open.

T014 has begun with a versioned host checker for raw FAT volumes. It is tested
with pass, filesystem-reject, unreadable-fixture, unsupported-container and
unchanged-image paths. App-side evidence import/unquarantine and real-media
validation remain open.

T001 now has host-testable transition coverage for durable dynamic attempt
states. Wiring liveness evidence, child teardown acknowledgement, fault
injection at storage boundaries, and device validation remain open.

The internal dynamic trial controller owns status-to-journal decisions: only a
guest-shutdown status followed by native stop may close cleanly; every other
runner failure or stop is quarantined. Visible Start wiring remains gated.

Liveness uses bounded aggregate evidence only: elapsed unpaused foreground time,
completed `retro_run` calls, and published video frames. It requires at least
one second, ten calls and two publications before the durable `running` state.

The next fault-injection slice covers the three pre-native persistence points:
prepared journal, dynamic launch publication and executing journal. Each must
leave the normal launch configuration intact and prohibit native handoff.
