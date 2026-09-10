# Offline worker-path review — 2026-09-10

T073 follows the [instrumented desktop capture](worker-diagnostics.md#instrumented-windows-desktop-capture--2026-09-10).
This is source review and experiment design, not a runtime fix or another device
run. Pinned Pure `7f6e8fb7385fa446d1444d671063268520bf9b54` plus patches 0001–0009
was verified with `scripts/fetch-sources.sh --verify-only`. No dependency, engine,
cache/link, cycle, queue, scheduler or presentation setting changed.

## What the current counters actually distinguish

Paths below are relative to the pinned Pure checkout under ignored
`third_party/dosbox-pure/`; function names identify the reviewed sites.

| Site | What happens / implication |
| --- | --- |
| `src/dosbox.cpp`: `Normal_Loop` | Alternates PIC queue processing, decoder calls, guest callbacks, timer ticks and host-event handling. Worker CPU is not solely instruction execution. |
| `src/hardware/pic.cpp`: `PIC_RunQueue`, `TIMER_AddTick` | Invoke event and ticker handlers; emulated device/render work can consume worker CPU outside the decoder. |
| `src/cpu/core_dynrec.cpp`: `CPU_Core_Dynrec_Run` | Checks code pages and cached blocks, translates on a miss, runs generated code, handles returns and direct interpreter fallback. Linked generated blocks can run without returning to this dispatcher. |
| `src/cpu/core_dynrec/decoder.h`: `CreateCacheBlock` | Builds up to 32 instructions per attempt; unsupported/state-dependent cases **and** an invalidation-map threshold within a block share the `illegalopcode` exit that emits `BR_Opcode`. |
| `src/cpu/core_dynrec/decoder_basic.h`: `MakeCodePage`, `dyn_closeblock` | Code-size mismatch and exhausted page-handler pools can release cached code. Block completion also finalizes generated code and performs host instruction-cache maintenance. |
| `src/cpu/dyn_cache.h`: `InvalidateRange`, `cache_openblock`, `cache_closeblock` | Code writes can clear intersecting blocks and links; allocation reuses/merges blocks and wraps the bounded cache. Translation counts do not identify which miss/eviction reason occurred. |
| `src/cpu/core_dynrec/risc_armv8le.h`: `cache_block_closing`, `cache_flush` | ARM code publication performs data/instruction-cache maintenance and barriers. This is part of translation completion, not the GPU presenter. |
| `src/cpu/paging.cpp`: `PAGING_PageFault` | Can enter `DOSBOX_RunMachine` recursively with PageFaultCore, then restore the previous decoder. Future phase timing must handle nesting rather than double-counting it. |

The diagnostic hook increments `fallback_opcode` when dispatch receives
`BR_Opcode`. It cannot distinguish an unsupported instruction from the
translator's decision to stop before frequently modified code. By contrast,
`fallback_invalidated` is the block-entry check that directly executes one
interpreter instruction. The code-write handlers can invalidate blocks without
producing `BR_SMCBlock`; zero `fallback_smc` therefore does not exclude cache churn.
The contract and evidence labels now reflect these facts without changing schema
1 or its historical values. No guest instruction bytes or addresses are needed.

## Comparison within the retained desktop window

Use exactly the previously selected 40 complete intervals (40,358 ms), not a new
favorable window. Join each telemetry/worker/timing triplet in order after strict
validation. Group by `underruns > 0` versus `underruns == 0`; sum counts and elapsed
milliseconds within each group, then compute rates from those sums.

| Measure | Underrun intervals | No-underrun intervals |
| --- | ---: | ---: |
| Intervals / elapsed ms | 13 / 13,153 | 27 / 27,205 |
| Worker CPU / elapsed | 95.26% | 83.90% |
| Translation attempts | 1,072,350 | 288,666 |
| Translation attempts/s | 81,528.93 | 10,610.77 |
| Block-entry invalidated fallback events/s | 2,769.25 | 2,544.83 |
| BR_Opcode fallback events/s | 109,591.50 | 101,829.96 |

Translation attempts are about 7.68 times as frequent in the underrun group;
the two fallback rates differ much less. The peak translation-rate interval
ends 10:19:00.356 (137,855 attempts in 1,006 ms, 117 underruns). The highest-rate
block-entry invalidation and BR_Opcode intervals end 10:19:13.481 and
10:19:28.615 respectively, both with zero underruns. These observations favor
investigating translation/retranslation before adding instruction support.

This is association, not a causal measurement. Queue history can shift underruns
away from the work that depleted it, completed slices can cross reporting
boundaries, and user actions were not timestamped. No-underrun intervals are not
an independent clean baseline. Attempts do not measure emitted bytes, unique
blocks, cache hit ratio or time spent translating. Generated execution, device
handlers and cache maintenance remain potential contributors; there is no basis
yet to resize caches, remove ARM barriers or change cycle accounting.

## Selected next experiment: source-owned translation/cache-reuse control

Prefer an automated disposable fixture first. It can vary code reuse without
requiring another manual Windows session or introducing clocks on millions of
fallback calls. Reuse the existing protected-mode CPU-fixture boot/exception
scaffolding and isolated fixture boundary, but give the new workload a separate
identity, result format and image hash. Preserve the existing light/stress images
and full correctness gate unchanged.

The implementation specification must include these controls:

1. A warm, immutable 32-bit integer routine set small enough to reuse cached
   blocks, with deterministic return values and expected checksums.
2. A cold-code phase executing equivalent routines at distinct guest locations,
   with a bounded preinitialized footprint and fixed operation count. A second
   sweep over the same footprint tests reuse. If footprint exceeds effective
   cache capacity, do not label that sweep a warm-cache control.
3. A rewrite phase changing a routine from outside its executing block, then
   serializing guest control flow and calling it with alternating expected
   results. Pair it with a data-write control using the same write/call counts
   without modifying code. Require observed invalidation/fallback counters before
   claiming that a particular DynRec path was exercised.

Run the exact same guest workload under Normal and DynRec at fixed 20k. Compare
per-phase guest progress and checksums first, then host duration, completed worker
CPU, translation/fallback rates and audio/deadline errors. Use bounded phase IDs
and independent guest tick records; aggregate completion alone cannot attribute
cost to a phase. Keep presentation, guest tone and memory footprint controlled,
and count setup/warm-up separately, never silently discard failures. Correctness
or timeout failure stops performance interpretation. A workload that overwhelms
both cores is diagnostic, not automatically an emulator regression.

Before any device use: specify observable requirements and executable tasks;
implement fixed bounds, generated-media-only access and cleanup guards; prove
phase checksums/progress with host/QEMU tests; run repository/host/pinned-source
checks and Android build. Device correctness comes before performance, Normal
before DynRec, with both user guests stopped. A disposable result can establish
the cost of its controlled mechanism, not that it reproduces Windows internals.
No installation or device run is authorized merely by this review.

If the fixture does not discriminate the mechanisms, the next diagnostic should
measure sampled translation wall/worker CPU, distinguishing code generation and
cache publication, alongside cheap creation/eviction-reason counters. It needs
an explicit per-slice probe cap, recorded sample/skip coverage, unbiased-selection
analysis, nested/reset handling, and measured overhead before implementation.
Sampled durations must not be reported as exact whole-interval phase totals.
Do not instrument every fallback or linked block with a clock. Avoid raw guest
addresses, opcode dumps, cache flush removal or cache/link policy changes.

Rollback is removal of the new fixture/diagnostic selection only; retain current
APK, machine metadata and guest disks. Windows remains stopped and further
manual trials remain unqueued. This review does not change acceptance status.

## Verification

Pinned-source reconstruction, repository hygiene and `git diff --check` passed.
Worker/runtime parser regression tests and strict revalidation of the retained
desktop window passed. Changes are documentation-only; no Android build,
installation or device trial was needed. Raw captures remain ignored. T073 is
complete; the proposed fixture and any additional instrumentation remain future
implementation work, not claimed test coverage.
