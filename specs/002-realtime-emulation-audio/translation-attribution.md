# Targeted translation/cache attribution — FR-033

Status: implemented, installed and disposable-device validated (2026-09-10,
T080–T082). Windows underrun attribution remains unresolved.

## Decision and limits

The [terminal cache matrix](cache-control-fixture.md#terminal-device-results--2026-09-10)
resolved missing short-session reports. It distinguished 52 warm, 12,334 cold,
12,338 two-sweep reuse, 53 data-write and 4,148 code-rewrite translation attempts
under DynRec, without reproducing Windows underruns. Attempts do not measure
translation cost, and zero fallback counts do not exclude code invalidation.

Add bounded samples of translation wall/worker-thread CPU time, with a separately
measured publication subset, and clock-free lookup/cache-clear event counts.
These answer whether sampled translations are expensive and which cache-clearing
paths are active. They do **not** establish the cause of every miss, an exact
cache-hit ratio, total translation CPU or Windows causality. Do not keep an
address-indexed miss-history map or change cache metadata to manufacture that
claim. T076's exact phase/reason attribution remains open where evidence cannot
support it.

No guest phase markers or fixture changes: retain all five existing cache image
hashes and the light/stress/x86 inputs. No CPU instruction, block/link, allocator,
page-handler, ARM cache-barrier, scheduler, queue, presentation or settings change.
Debug balanced sessions enable the extension under both Normal and DynRec through
the existing internal timing-policy boundary. Legacy and release paths do no new
diagnostic clock reads, counter updates, allocation or logging. Normal emits a
valid extension with zero DynRec events, not fabricated translation samples.

## Reviewed sites and definitions

Paths are relative to pinned Pure `7f6e8fb7385fa446d1444d671063268520bf9b54`
plus patches 0001–0009. A new isolated patch must add hooks only.

| Hook site | Observable quantity and exclusions |
| --- | --- |
| `src/cpu/core_dynrec.cpp`, initial `FindCacheBlock` result in `CPU_Core_Dynrec_Run` | Dispatcher lookup hit/miss. Direct linked execution and other lookup sites are excluded; never label the ratio a global cache-hit rate. |
| Same dispatcher, existing `RW_TRANSLATE` / `CreateCacheBlock` call | Translation attempt; sampled outer call includes allocation, decoding/code generation, publication and any nested fault/device work. It excludes the preceding `MakeCodePage` call and later generated-code execution. |
| `src/cpu/core_dynrec/decoder_basic.h`, `dyn_closeblock` call to `cache_block_closing` | Publication wall/CPU subset inside a selected translation. Includes the existing ARM flush/barrier path, not GPU execution. Do not alter `risc_armv8le.h` instructions or instrument every cache-line operation. |
| `src/cpu/dyn_cache.h`, `InvalidateRange` | Calls and intersecting-block clear operations caused by code writes. An empty range scan need not clear anything. |
| Same file, `cache_openblock` | Clear operations while reusing/merging storage, classified as cache reclamation. |
| `decoder_basic.h`, `MakeCodePage` calls to `ClearRelease` | Distinguish code-size mismatch from exhausted page-handler pool using the existing call-site branches. |
| `dyn_cache.h`, remaining `ClearRelease` / `CacheBlockDynRec::Clear` | Generic page-release or other clear operations; preserve an unknown/other bucket rather than guessing why a later lookup missed. |

Count `Clear` invocations, including recursive cross-page partners, not unique
blocks, bytes or evictions. `ClearRelease` clears `page.handler` before calling
`Clear`; do not test that field to decide whether to count the operation. Use
worker-local scoped reason context, inherited by recursive clears and restored
on every return/unwind. Innermost explicit reason wins; generic page-release
context must not overwrite an already specific caller reason. No new traversal,
locks or clocks in lookup, write, clear or fallback hooks. No guest pointers,
addresses, instruction bytes, registers or content leave the worker.

## Selected sampling policy: stride 64, cap 4

For each enabled worker slice, number outer translation attempts from one. Select
attempts 1, 65, 129, 193, then stop selecting for that slice. All later attempts
are cap-skipped; intervening attempts before the cap are stride-skipped. Nested
translation attempts are counted separately and never independently timed.
Increment the existing `translations` counter exactly once per attempt as today.

Each selected outer call reads monotonic wall and worker-thread CPU at entry and
exit. Within it, time at most four outer publication scopes, using the same
clocks. Nested publication work is inclusive in its outer scope, not timed twice;
additional publication scopes are counted as skipped. Thus the added duration
clocks are bounded by 80 reads per worker slice: four translation pairs of two
clocks plus sixteen publication pairs. Unsampled translations, nested attempts
and reason counters read no clocks. Existing worker clocks are separate.

This deterministic policy deliberately favors early eligible work in a slice.
It is **not random or unbiased** and may miss the slowest translation or later
phases of an overloaded slice. Record the selection/cap/skip counts. Report only
observed sample totals, maxima and coverage. Never multiply a sample mean by
attempt count, report a sampled maximum as the true maximum, or subtract sampled
translation time from whole worker CPU to obtain execution time. Lack of a slow
sample cannot clear a suspected hot path. A future randomized/phase-tagged policy
would require its own version, overhead validation and authorization.

Publication times are a subset of the sampled translation times, not additional
CPU. Translation-minus-publication is not pure code-generation cost: allocation,
nested guest work, unsampled publication and measurement overhead remain inside.
Wall time can include descheduling; thread CPU excludes other threads but still
includes nested guest work on this worker. Accumulate nanoseconds internally and
convert once per published aggregate, retaining real zero/sub-microsecond results.

## Ownership, resets and completed-work publication

Extend FR-030's worker-owned accumulator and generation protocol. Only completed
slices cross the existing publication lock; the frontend never reads live
sampling state. Drain old worker and new cache fields atomically through one
combined narrow native snapshot operation, preventing an interval-boundary race
between the two records. Preserve the original worker schema and meanings.

Stage sample durations locally until the selected outer translation returns
normally. A failed/reversed clock, exception/early abort, open scope at a worker
boundary or stale generation discards the entire sample and its publication
subset; expose discard and error counts, never partly valid durations. At a
worker boundary, finalize the discard before publishing that slice. A later
destructor from the discarded scope must neither read clocks nor change the new
slice. Nested-depth/reason scopes must restore correctly even across a pause and
resume; do not reset a live scope's depth to zero while its stack frame survives.
Existing stale-slice discard remains visible through the worker record. Any
discard prevents a claim of complete attribution, not a correctness result.

Use fixed-size worker-local state and scoped guards; no per-event heap allocation,
mutex/atomic operation or guest execution changes. If a source path bypasses scope
cleanup, stop implementation and resolve it explicitly before enabling the hooks.

## Internal wire contract

New `RoboWindowsCache` schema 1 follows each experimental `RoboWindowsTiming`
record, with the same `interval_ms`. All fields below are nonnegative integers;
names and meanings are fixed for the implementation task:

```text
schema=1 interval_ms sample_stride=64 sample_cap=4 publication_cap=4
lookup_hits lookup_misses translation_attempts nested_attempts
stride_skipped cap_skipped samples_started samples_completed samples_discarded
translation_wall_sample_us translation_wall_sample_max_us
translation_cpu_sample_us translation_cpu_sample_max_us
publication_scopes publication_skipped
publication_wall_sample_us publication_wall_sample_max_us
publication_cpu_sample_us publication_cpu_sample_max_us
invalidate_calls clear_calls clear_code_write clear_cache_reclaim
clear_code_size clear_page_pressure clear_page_release clear_other
clock_errors scope_errors
```

`publication_scopes`/`publication_skipped` count measured/skipped outer publication
scopes only within **completed valid** translation samples. Dropped samples commit
neither their publication counts nor durations. `samples_discarded` exposes their
loss. Out-of-sample publication is intentionally unmeasured, not counted as zero
work. `clock_errors` counts invalid clock pairs, `scope_errors` broken ownership/
nesting invariants; expected lifecycle/sample discards alone are not scope errors.

Validation must require:

- Exact fields/version/policy, matching interval and one record per worker group.
- `translation_attempts == worker.translations` from the same atomic snapshot.
- Attempts equal nested attempts + stride skips + cap skips + samples started.
- Samples started equal completed + discarded; started <= 4 × worker slices.
- Publication scopes <= 4 × completed samples; publication wall/CPU sample totals
  <= corresponding translation sample totals; maxima <= their own totals.
- Clear calls equal the sum of the six reason buckets. These operations are not
  required to equal lookup misses or translations.
- Zero completed samples imply zero sample durations and publication counts;
  zero worker slices imply zero cache events. Clock/scope errors fail attribution.

New terminal header **schema 2** retains the five schema-1 header fields and
appends `RoboWindowsTerminalCache` after `RoboWindowsTerminalTiming`. Require that
fifth record for schema 2, with interval_ms matching the header's floored
microseconds. Schema-1 terminal captures retain the original four-record group
and must not contain a cache extension. New experimental runs always use schema
2, even Normal with zero events. Missing/duplicate/reordered/mixed groups fail.
Periodic cache coverage must also be complete or entirely absent for historical
captures; a schema-2 tail requires cache records for every preceding periodic
group. Keep telemetry schema 4 and worker/frontend schema 1 unchanged.

The terminal-aware normalizer and opted-in cache reporter must accept historical
captures with `translation_attribution=unavailable`; absence is not zero cost.
New captures report `translation_attribution=sampled`, selection coverage and
`cache_reason_scope=clear_operations_not_miss_history`. Zero samples additionally
report `translation_sample_timing=unavailable`. Existing periodic-only consumers
must ignore the new tags and retain their previous quality windows. Preserve
teardown/decoder/queue caveats and `quality=not_assessed` / `cache_mechanism=unverified`.

## Overhead and acceptance tasks

Add a separate bounded calibration record before loading disposable media; do
not reinterpret the existing worker calibration schema. Use 2,000 synthetic
slices with 512 outer attempts per slice to exercise stride and cap skips,
four publication scopes per selected attempt and clear-reason counter traffic.
Compare disabled, existing-worker-only and full extension modes; record mean
ns/slice, attempts/slice, sample/clock counts and valid-clock status. Also measure
clock-free event-only cost, so low per-sample cost cannot hide high counter volume.

Initial smoke budget: full enabled synthetic slice cost below 1% of the nominal
70.086-Hz boot-frame period (fixed 142,680 ns), no accounting errors, exact
expected sample/skip counts, and zero
disabled hook clocks/updates. This is a synthetic smoke bound, not proof of <1%
real workload overhead: slices per frame and event volume vary. Record all modes,
budget failures and device variability without selecting a favorable run. No
further guest campaign if calibration or correctness fails; do not silently
raise the cap or loosen thresholds.

All four calibration records form one schema-1 `RoboWindowsCacheCalibration`
group, modes 0 disabled / 1 worker-only / 2 full / 3 events-only. Fields are
`schema mode iterations attempts_per_slice publication_per_sample mean_ns
cache_clock_reads samples_completed stride_skipped cap_skipped cache_events
clock_errors accounting_errors`. Counts cover all 2,000 slices, not per-slice
means. `cache_events` is lookup + invalidate + clear calls, three per attempted
event iteration in modes 2/3. The full mode must report 160,000 extra clock reads,
8,000 samples, 378,000 stride skips and 638,000 cap skips; other modes report zero
sample clocks/counts. Enabled cache events total 3,072,000. Invalid calibration
or a failed smoke budget aborts debug experimental loading before guest execution;
legacy/release loading is unchanged. This budget is a fixed diagnostic smoke
reference, not a threshold silently adjusted by guest refresh updates.

Host verification must cover disabled/Normal behavior, attempts 0/1/64/65/193/194/
512, nested translations/publications, publication cap, clock failures, rounding,
every clear reason and cross-page recursion, exceptions, generation resets,
worker-boundary discards, restart and delayed stale-scope destruction. Add actual
pinned-hook tests, not just a fake accumulator: lookup scope excludes linked
execution, the old translation count is unchanged, and ARM publication remains
byte-source unchanged. Verify atomic snapshot/terminal drain, schema-1 historical
and schema-2 terminal-only/mixed captures, corrupt coverage and ordinary consumers.

Run `scripts/test-host.sh`, `scripts/check-repository.sh`,
`scripts/fetch-sources.sh --verify-only`, `git diff --check` and
`scripts/build-android.sh --debug`. Include the new patch/ABI/helper inputs in the
CPU capability hash; preserve all fixture hashes. Host/build completion does not
authorize installation or invalidate the currently installed evidence.

Separately coordinated device gate: both real machines clean/stopped, tablet
unlocked/foreground, verify installed APK hash, then legacy/no-extension smoke,
full 18-stage CPU gate, calibration, unchanged five-case Normal/GPU matrix before
DynRec/GPU, and unchanged Normal/DynRec stress pair. Require exact guest results,
strict coverage, clean shutdown/cleanup and no lifecycle/clock/scope failures.
Use existing stress quality gates; short cache tails are not settled quality.
Report sampled costs and direct clear reasons without assuming numerical counts
repeat exactly or declaring every miss explained. All media are generated
disposable fixtures. No real Windows boot or user-dropped manual/thermal campaign
is queued by this plan.

## Rollback and status

Retain the installed T077 APK and T078 evidence. Rollback removes only the new
diagnostic patch, combined snapshot extension and consumer opt-in, restoring the
previous capability identity when inputs match. Do not modify guest disks or
machine selections, remove ARM barriers, or tune caches/queues on rollback.
Source/toolchain pins, licenses and public-data restrictions remain unchanged.

T079 covers this specification/source review only. T080–T082 are executable
future implementation, host/build and separately coordinated device tasks. No
new hooks, build, ADB command, installation or guest session occurred in T079.

The user subsequently authorized unattended continuation through implementation,
verification and local commits. T080–T081 may proceed immediately. T082 may run
without another checkpoint when read-only preflight verifies stopped/clean real
machines and an available unlocked tablet; pause only for necessary user action.
This does not authorize a real Windows boot, unsafe stop, promotion or push.

## Implementation and host/build evidence — 2026-09-10

Isolated patch 0010 adds worker-local scopes and cache-clear hooks, retaining
patches 0001–0009 and all upstream pins. The combined native take operation drains
both accumulators under the existing publication lock/generation check. Duration
totals remain nanoseconds until the aggregate is drained. A surviving old scope
cannot contribute clocks/data after its worker boundary; recursive depth/reason
state restores on stack unwind. Existing manual translation-count callers and
worker-only calibration retain their original meaning.

The frontend enables the new extension only with the debug NDK build flag and
balanced timing. Legacy leaves it disabled; release does not enable the new
cache sampler/records. Four calibration modes precede loading, and invalid
calibration or an over-budget full-mode result refuses guest loading. Normal
has real zero-event records, not invented sample times. The strict parser reports
sample coverage, direct clear reasons and explicit unavailable sample timing.
Schema-1 terminal/history support and existing settled-only consumers remain.

Full host tests passed, including new C++11/UBSan sampling, nesting, caps, clock
errors, nanosecond aggregation, exceptions, stale publication/translation scopes,
worker-boundary discards and concurrent reset/atomic drain. Actual pinned Clear,
InvalidateRange, ClearRelease, cache_openblock and dyn_closeblock bodies were
compiled against bounded mocks; recursive code-write clears, specific versus
generic reasons and publication ordering passed. Source guards verify the single
dispatcher lookup/translation hook and byte-identical ARM publication source.
Existing CPU/REP/APM/checksum, runtime/audio/presentation tests passed too.

Parser tests reject missing/reordered/duplicate/mixed fields and groups, invalid
sample/clear arithmetic, caps, clocks, wrong calibration modes/counts and budget
overruns. All 20 historical device cache captures reparse successfully without
claiming the new extension. The full-suite synthetic calibration (host with
UBSan, not tablet overhead) recorded mean ns/slice 30,548 disabled, 36,199 worker
only, 113,056 full, 6,853 events only; accounting/clock checks passed. A standalone
run was faster, illustrating why these probes are not whole-workload overhead.

Pinned reconstruction, repository hygiene and whitespace checks passed. Android
debug build passed in 63 seconds, source label `22dba188cde3+dirty`, CPU capability
`13cb1ca6e18ea3db`, APK SHA-256
`926af541ff2873ca7c62a6f7250d166c392f6d45b837c04590cf5d830871cf67`.
All five packaged cache hashes remain exact. The existing upstream OSD C++ VLA
warning was observed; no diagnostic compile failure. Ignored logs are
`artifacts/cache-attribution-host.log` and `artifacts/cache-attribution-build.log`.
The prior APK is preserved at ignored `artifacts/cache-attribution-baseline.apk`
with SHA-256 `4bae989c5385adeb5e43882a1c2ade439cd5324e755e03f95b3fef1c13a6e1ee`.

Read-only preflight after build confirmed the tablet awake/unlocked and both
real profiles clean/stopped, generations 1 and 31 unchanged, with no isolated
runner or recovery journal. No new build has been installed at this checkpoint;
the already authorized T082 disposable sequence may proceed after stopped guards.
Windows underruns, exact miss history and per-phase causality remain unresolved.

## Device attribution validation — 2026-09-10

The authorized stopped installation completed; APK readback matched `926af541...871cf67`
above. The legacy surface/persistence/input/media/pause/resume/restart smoke passed
with zero terminal, cache or cache-calibration records in a separately captured
extension-tag log. Full x86 correctness passed all 18 stages at device-log time
12:05:51.909. All 18 per-runner schema-2 terminal groups, combined worker/cache
records and four-mode calibrations passed strict validation. Their completed
worker slices total 3,071; calibration now consumes part of the fixed host gate
window, so this is not directly comparable to the prior gate's slice count.
The full-mode synthetic means ranged 107,422–109,040 ns/slice, below 142,680 ns;
clock and accounting errors were zero. Deliberate DynRec PF stress retained
PFQ 21/21, depth 0/4, wipe 0/0, PFCore 16,408/16,408 with total/max
121,762/28,780 us, two Slow10 calls, no double fault or reset. Correctness is not
a general long-call quality pass.

All five unchanged Normal/GPU cache cases ran before all five DynRec/GPU cases.
Exact return/checksum/tick records, calibration, schema-2 residual coverage and
cleanup passed. Each session emitted one ordinary group plus one terminal group.
All ten reported zero underruns, missing/dropped/saturated audio, stream errors,
deadline resets, graphics errors/fallbacks, post failures, diagnostic clock/scope
errors, discarded worker slices and discarded samples. Normal's new fields were
zero with sample timing explicitly unavailable. Full-mode calibration means were
109,103–109,466 ns (Normal) and 108,704–109,834 ns (DynRec).

The DynRec measurements below aggregate ordinary plus terminal contributions;
CPU columns are **observed samples**, not estimated whole-interval phase totals.

| Case | Attempts / completed samples | Sample coverage | Translation CPU total / max us | Publication CPU subset us | Code-write clear calls | Dispatcher hits / misses |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| warm | 52 / 2 | 3.846% | 74 / 50 | 2 | 0 | 10,543 / 52 |
| cold | 12,334 / 148 | 1.200% | 1,695 / 57 | 189 | 0 | 6,429 / 12,334 |
| reuse | 12,338 / 149 | 1.208% | 1,742 / 67 | 184 | 0 | 16,673 / 12,338 |
| data | 53 / 2 | 3.774% | 59 / 38 | 2 | 0 | 10,551 / 53 |
| rewrite | 4,148 / 75 | 1.808% | 1,251 / 64 | 110 | 4,095 | 10,549 / 4,148 |

All five DynRec cases reported zero cache-reclamation, page-pressure, generic
page-release and other clears. Code-size clears were 23 / 20 / 24 / 24 / 24 in
table order; these include bootstrap/mode changes. The rewrite control now
directly exercises code-write invalidation, unlike the data-write control.
The first alternating rewrite stores the already-zero value, so 4,095 changed
writes across 4,096 iterations are expected from source. Linked execution is not
included in dispatcher hit counts. A second sweep adds only four translation
attempts, with no observed storage/handler-pressure clears; this supports retained
code reuse at this footprint, not a global cache capacity or per-miss-history claim.

Sampled translation maxima were 38–67 us and the measured publication subset was
small; no sampled call reproduces the Windows worker overrun. Only 1.2–3.846% of
attempts were sampled with deterministic early-slice bias. Do not multiply these
times by the old Windows translation rate or treat absence of a slow sample as
proof that translation cannot cause stalls. The fixture still does not reproduce
the Windows fallback storm; T076's exact per-phase attribution remains limited.

Calibration is before guest load but after the Java/native session clocks start.
It adds roughly 0.4–0.5 seconds of startup work to these short captures. Whole
observed presentation averages are consequently 13.97–19.32 FPS, **not** a settled
presentation pass or evidence that active presentation slowed. Java durations are
1,104–1,710 ms; native totals 1,112.538–1,680.811 ms. All measured guest ticks remain
528 except DynRec warm 527 and both rewrite cases 529; setup/priming identities
are unchanged. Never silently subtract calibration from these clocks to claim
independent guest speed or compare whole-session FPS to the pre-calibration build.
The unchanged longer stress pair supplies the separately gated settled check.

Ignored evidence: `artifacts/cache-attribution-device-legacy{,-extension}.log`,
`artifacts/cache-attribution-device-x86.log`, per-session validation under
`artifacts/cache-attribution-x86-sessions-f9Q7Wr/`, and wrappers
`artifacts/cache-attribution-device-{normal,dynamic}-{case}.log`. Matrix raw
directories use the existing `artifacts/presentation-{core}-1-cache-{case}-{suffix}/`
layout with these suffixes:

| Case | Normal | DynRec |
| --- | --- | --- |
| warm | hTcVlO | ULNx6e |
| cold | bCM8M8 | 9uNi1c |
| reuse | PvcIwM | 75DFzO |
| data | DALUij | EF1lsG |
| rewrite | dZ7E1W | v7MfRx |

### Unchanged stress pair and final postflight

Both unchanged GPU stress cases passed their strict settled quality gate, Normal
before DynRec, with `phase_mask=15`. Each recorded Java host 122,055 ms and guest
120,616 ms (98.82%, within 5%). Normal's 107 settled intervals cover 108,252 ms;
DynRec's 107 cover 108,288 ms. Both presented 29.77 FPS. All 120 ordinary groups
plus each terminal residual validated, and their full captured totals also show
zero underruns, missing/dropped/saturated audio, stream errors, resynchronizations,
graphics errors/fallbacks, post failures and presenter clock errors. Worker/cache
clock, scope and discard counters remained zero.

DynRec stress recorded 48 translation attempts and six completed samples:
330 us sampled translation CPU, 74 us sampled maximum, 17 us publication subset,
with zero clear operations. The ordinary worker records counted 304,930 BR_Opcode
fallback events without a timing-quality failure. These counts do not reproduce
the Windows translation storm or prove fallback cost is harmless in other work.
Full-mode calibration means were 109,101 ns Normal and 109,468 ns DynRec; the
four-mode accounting and fixed smoke budget passed. This bounds the diagnostic
smoke result, not total real-workload overhead or sustained/thermal stability.

Terminal exits were host_stop after the disposable timer completed; native cleanup
finished before its buffered record was read. PASS records are at device-log
times 12:10:14.159 and 12:12:20.151. Raw captures/strict summaries are ignored
`artifacts/presentation-normal-1-stress-xpBBnQ/` and
`artifacts/presentation-dynamic-1-stress-bITEEP/`, with wrapper logs
`artifacts/cache-attribution-device-{normal,dynamic}-stress.log`.

Final read-only postflight verified no isolated runner, active-session marker,
recovery journal, presentation fixture directory or legacy staging/private test
files. The pre-test stay-awake setting was restored. Both real profiles remain
clean/stopped and unchanged: stable `incoming` Normal/Software generation 1;
`win98 dynrec exp` fixed-20k DynRec/GPU generation 31. No Windows guest was opened,
and no real guest settings/media, CPU engine policy or publication barriers changed.

T082 is complete for disposable calibration, correctness, coverage and stress
quality. Repository hygiene, whitespace and pinned-source checks passed after
recording the evidence. No rebuild/reinstall is needed for these documentation
changes. The next useful measurement requires a separately coordinated Windows
session with confirmed user activity; it cannot be inferred from these control
fixtures. No automatic Windows boot, promotion, push or dropped long/manual gate
is queued. Preserve the deterministic-sampling and exact-attribution limits above.
