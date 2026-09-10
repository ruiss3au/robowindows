# Internal worker diagnostics — 2026-09-10

FR-030 / T072 follow the [offline Windows attribution](timing-investigation.md#offline-attribution-of-the-post-rep-capture--2026-09-10).
Patch `0009-worker-timing-diagnostics.patch` adds measurement only to pinned
DOSBox Pure `7f6e8fb7385fa446d1444d671063268520bf9b54`. It retains the upstream
GPL-2.0-or-later license and changes no instruction, cache/link, cycle, scheduler,
audio queue or presentation policy. The separate aggregate schema is specified
in [the telemetry contract](contracts/runtime-telemetry.md#optional-internal-core-timing-fr-030).

Worker-local translation/fallback counters use neither clocks nor locks on each
event. Completed worker slices publish under a short mutex; frontend snapshots
never read live counters. Lifecycle reset advances a generation, discarding
unfinished older contributions. Frame/pause semaphore waits are excluded from
worker timing. Worker CPU is thread CPU; frontend wait/mix is inclusive wall time.
These windows overlap and are not additive or a complete function-level profile.
Historical captures remain parseable; partial extensions and clock failures fail
validation. No raw guest addresses, instructions or contents are collected.

## Build and correctness

Build label `a62047110b25+dirty`, CPU capability `1c6ac3a100f92c22`, APK SHA-256
`d3afc7b8835895de637e6c0a116559a90b5f05d6de91deddebb03a70319a153d`.

Full host tests passed, including fail-fast UBSan accounting, disabled clocks,
all event reasons, completed-only publication, inclusive nested frontend scopes,
reset during active work, failed/reversed clocks, and concurrent snapshots/resets.
Strict parser tests cover historical absence and reject missing, duplicate,
negative, unknown, inconsistent and mismatched records. Existing x86/QEMU, REP,
PageFaultCore and scheduler/audio tests passed. Source reconstruction, repository
hygiene, whitespace checks and Android build passed.

Installation used the existing stopped-machine guards. The disposable legacy
core/input/lifecycle probe passed and emitted zero worker/calibration records.
The CPU fixture now uses balanced timing so its full Normal/DynRec gate actually
exercises enabled instrumentation; it still opens only its own source-owned media.
The complete gate passed exact records. Its 36 diagnostic intervals validated
2,364 worker slices, 1,245 translation attempts and 474 unsupported-opcode
interpreter calls, with zero diagnostic clock errors. No special-page,
invalidation, SMC-return or trap fallback events were observed in these sampled
gate intervals; host tests cover their counter slots, not a claim of device
coverage for every fallback reason. The DynRec page-fault progress suite retained
21/21 queue returns, depth zero / high-water four, no wipe, and 16,408 balanced
PageFaultCore calls (84,817 us total, 19,055 us maximum).

## Overhead characterization

The 18 on-tablet CPU-gate calibrations measured 85–468 ns disabled and
2,408–10,791 ns enabled per synthetic slice with 64 identical counter events.
The enabled maximum is below 0.08% of a nominal approximately 14.3-ms frame,
passing the specified 1% calibration smoke threshold. The probe verifies its
event total before reporting success. These synthetic means do not establish
the total overhead of an arbitrary Windows workload: compiler optimization,
event frequency, contention and scheduling can differ. Clock/mutex costs and
new reporting also affect actual execution. Preserve these limits rather than
claiming exact overhead from noisy cross-build timings.

## Unchanged stress comparison

Both fixed-20k/GPU/balanced-100-ms runs passed the strict timer/audio/presentation
and new diagnostic parsers. The image remains SHA-256
`816fe821f70ea4ff3929fd2281a1ee47a2487ba9f59e43278ebfad61bd7cd3b1`.

| Measure | Normal | DynRec |
| --- | ---: | ---: |
| Host / guest elapsed ms | 122,070 / 121,605 | 122,066 / 121,605 |
| Validated phase mask | 15 | 15 |
| Settled presented FPS | 29.77 | 29.76 |
| Entire telemetry capture ms | 121,308 | 121,414 |
| Underruns / missing frames / deadline resets | 0 / 0 / 0 | 0 / 0 / 0 |
| Maximum retro_run us | 29,716 | 19,496 |
| Maximum producer gap us | 43,331 | 31,805 |
| Completed worker slices | 8,493 | 8,498 |
| Worker wall total / max us | 68,127,602 / 43,142 | 62,536,045 / 31,683 |
| Worker CPU total / max us | 64,730,181 / 39,725 | 58,364,283 / 28,036 |
| Frontend wait total / max us | 2,415,067 / 29,135 | 254,319 / 19,018 |
| Frontend mix total / max us | 131,647 / 1,876 | 146,358 / 2,167 |
| Translation attempts | 0 | 48 |
| Unsupported-opcode interpreter calls | 0 | 305,484 |
| Disabled / enabled calibration ns per slice | 91 / 2,450 | 90 / 2,420 |

Both entire captures also recorded zero dropped/saturated audio, stream errors,
post failures, graphics errors/fallbacks, clock errors and discarded slices.
All phase counters were positive and guest timing was within 0.4%. Other fallback
reason counters stayed zero. Fallback activity in a passing DynRec fixture is
characterization, not evidence that fallback caused the earlier Windows failure.
Worker time legitimately exceeds frontend call time here: the worker runs ahead
between frontend calls. Do not add those overlapping durations or treat their
difference as idle/sleep time. No uninstrumented worker-thread clock baseline
exists, so this pair does not isolate exact workload-wide instrumentation cost.

Postflight confirmed no isolated runner, active-session marker, recovery journal
or presentation cache directory. Both Windows machines retained clean provenance
and unchanged settings: stable `incoming` Normal/Software generation 1;
experimental copy DynRec fixed-20k/GPU generation 31. Neither was booted.
T072 is complete. The build is ready for a separately authorized short Windows
capture; the remaining Windows audio/timing cause is not fixed or attributed by
these disposable results. No push, promotion or long campaign is included.
