# Validation: ARM64 Dynamic Recompilation

No dynamic execution is authorized or claimed by this specification alone.

## Existing evidence

- The pinned ARM64 dynamic path has previously terminated during startup; the
  failure has not yet been reproduced with symbols or attributed.
- The experimental machine has an independent writable disk and a normal-core
  fallback.
- The 30k normal-core AoE2 trial looked faster but broke up audio, motivating an
  execution-efficiency investigation rather than further unbounded cycle increases.

## Pending

- Full fault-injection coverage for crash recovery and fallback persistence.
- Symbolized SM-T500 root cause on a verified experimental copy.
- Correctness parity, benchmark improvement, clean audio, and thermal soak.

## Specification review — 2026-09-08

Adversarial findings are addressed by FR-016–021, SC-001–005 and the execution
contract: launch-boundary enforcement, durable pre-load marking, persistent
late-crash detection, explicit cycles, complete fallback, attachment ownership,
failed-recovery blocking, return-to-Normal UI, transactional ordering and media
quarantine. Dedicated dynamic processes also address retained native core state.
All implementation/device tasks remain pending. Separate processes reduce
accidental state sharing; they do not make arbitrary native bugs harmless.

## Safety-envelope implementation — 2026-09-08

The diagnostic-only safety envelope now persists schema-v7 execution selection
and configuration generations; per-machine clean shutdown provenance no longer
uses the former single global marker. `prepared` is durably recorded before
dynamic launch publication, then `executing` before any future native handoff.
An interrupted executing record restores the complete normal launch profile and
quarantines the experimental disk; ordinary Start is disabled until the separate
read-only health-check task is implemented. The dynamic UI and native handoff
remain unavailable.

Host checks and repository hygiene passed, and the Android debug build compiled
on 2026-09-08. No guest was booted, modified, or installed during this work.

## Pinned-core audit — 2026-09-08

The pinned `dosbox-pure` ARM64 build selects `C_DYNREC` and `ARMV8LE` for
`arm64-v8a`; its Android makefile also selects the `arm64` dynarec path. The
ARMV8 emitter explicitly performs data-cache clean, barriers, instruction-cache
invalidation and an instruction barrier whenever it closes generated blocks.
The shared dynrec cache still requests writable-and-executable memory through
`mprotect`, rather than a strict write/execute transition. That is an upstream
behavior, not a RoboWindows change, and this feature must not paper over a
failure by weakening Android executable-memory protections. The next diagnostic
step remains a symbolized reproduction on the experimental copy. Its
decoder-name helper is hidden inside the pinned core library, so effective
decoder evidence requires a minimal, isolated upstream patch after that
reproduction; no core patch is justified from this static audit alone.
