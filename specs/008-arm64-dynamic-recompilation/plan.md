# Implementation Plan: ARM64 Dynamic Recompilation

## Approach

Build the safety envelope first, reproduce and symbolicate the current ARM64
failure second, make a minimal pinned-core correction only if the cause is
proven, then measure correctness and performance on the experimental copy.

The normative [execution contract](contracts/execution-safety.md) governs
transaction ordering and failure behavior. Its separate emulator process is
required because the current core retains process-global state between sessions.
This preserves the narrow Android/JNI boundary but requires explicit child-process
surface, input, audio-focus and lifecycle plumbing. Process death must leave the
machine library usable; a same-UID process is not a malicious-code sandbox.

Use `dynamic fixed 20000` initially and compare against `normal fixed 20000`
as a controlled experiment as well as the sustainable normal baseline. Extend
LaunchConfig's current normal-only fixed-cycle validation specifically for this
named experimental policy; retain rejection of arbitrary policy combinations.
Confirm the actual decoder with aggregate diagnostics. If the pinned build
cannot run this combination, report a failed capability gate and revise the
spec explicitly before substituting another policy.

Persist selection independently from execution. A single versioned machine
record is authoritative; launch files are regenerable. Per-attempt journals
remain active until confirmed teardown, and a full normal snapshot supplies
fallback. Keep migration tests for stable profiles and per-machine clean state.

## Delivery Stages

1. **Recovery state** — implement and fault-test the execution contract, media
   ownership, full fallback, persistence failures, and per-machine clean state.
   Add process isolation and verify it with disposable fixtures before dynrec.
2. **Diagnostic reproduction** — produce a symbolized debug build, confirm the
   experimental disk and clean shutdown, reproduce once, and preserve only a
   redacted tombstone and configuration evidence.
3. **Root-cause analysis** — audit the pinned ARMV8LE dynrec path, executable
   cache permissions, cache flushing, branches, and block linking.
4. **Minimal correction** — isolate a proven fix under `patches/`; do not change
   guest media, UI ownership, input, audio resources, or stable profiles.
5. **Experimental UI** — offer one named `Dynamic (experimental)` option only
   on experimental machines, with recovery messaging and normal-core fallback.
6. **Validation** — run correctness first, then benchmark/AoE2 performance and
   thermal soak. Reject the profile if any correctness or real-time gate fails.

## Rollback

Disable the experimental profile and restore its last-known-safe normal-core
configuration. Revert any isolated core patch. Never restore or replace a guest
disk automatically; retain the stable machine and its disk unchanged.

## Dependencies

- Feature 007 supplies independent experimental machine disks and selected
  profile UI.
- Feature 005 supplies orderly guest-shutdown and session lifecycle behavior.
- Feature 002 supplies telemetry, benchmark, timer/audio, and thermal gates.

Feature 008 owns the dynamic implementation and supersedes Feature 002's Phase 7
delivery tasks; that feature remains the source of measurement gates. Benchmark
completion blocks performance claims, not safe diagnostic reproduction. Existing
Feature 007 restrictions remain the default except for the explicit named dynamic
profile and recovery contract defined here.

## Verification and rollback gates

Use fault injection at each journal/config publication boundary, including full
storage and malformed records. Assert native load was never called when a gate
failed. Use independent stable-role fixture hashes for isolation, not a real guest
boot. Then test dynamic-to-normal and normal-to-dynamic transitions in fresh
processes, late crashes, hangs, process death, stale callbacks, pause and shutdown.
Review shared-core patches against normal execution before installing for trials.

Keep the prior APK on the Debian host. Rollback disables dynamic capability and
selects normal snapshots without deleting media. An older APK must not be installed
over newer profile metadata unless downgrade compatibility has been verified;
otherwise provide a forward recovery build. No history rewrite or disk rollback.
