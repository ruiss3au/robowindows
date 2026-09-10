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
2,364 worker slices, 1,245 translation attempts and 474 BR_Opcode
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
| BR_Opcode interpreter calls | 0 | 305,484 |
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

## Instrumented Windows desktop capture — 2026-09-10

Feature 011 T011 used the same verified APK above, with no reinstall or settings
change. Both machines were clean/stopped before the guarded experimental start.
The host-only capture began at tablet time 10:16:17; desktop readiness was marked
at 10:18:50. The requested workload was opening/closing My Computer, Control Panel
and ordinary windows, without AoE2 or a benchmark. Individual actions and human
sound/responsiveness observations were not explicitly confirmed. Raw evidence
remains ignored under `artifacts/worker-desktop-qShpI8/`.

The chronological complete intervals ending at or after 10:18:52 and before
10:19:32 form the primary desktop window: 40 intervals / 40,358 ms, starting
approximately 10:18:51.256 and ending 10:19:31.633. This excludes the partial
readiness interval; it is not a claim that startup debt had already settled.
Recurring failures justified ending the planned three-minute check early.
The benchmark telemetry parser validates this window's schema/state, not a
guest benchmark result or a quality pass. All 200 whole-session frontend/worker
diagnostic intervals also validated. Calibration was 297 ns disabled / 10,551 ns
enabled per synthetic slice, retaining the overhead limitations described above.

| Desktop window measure | Result |
| --- | ---: |
| Presented FPS / emulator calls per second | 29.76 / 65.76 |
| Underruns / missing frames / deadline resets | 837 / 124,189 / 10 |
| Maximum retro_run / producer gap us | 74,342 / 86,872 |
| Maximum scheduler lateness us | 272,320 |
| Over-budget / catch-up calls | 515 / 1,023 |
| Frontend call wall / worker-wait total us | 19,854,623 / 17,727,618 |
| Worker completed slices | 2,654 |
| Worker wall total / max us | 36,067,983 / 86,804 |
| Worker CPU total / max us | 35,354,944 / 43,912 |
| Frontend mix total / max us | 26,494 / 1,920 |
| Host video / audio callback total us | 1,691,866 / 203,431 |
| Translation attempts | 1,361,016 |
| Block-entry invalidated / BR_Opcode fallback events | 105,656 / 4,211,741 |

The queue reached zero. Configured/current decoder stayed DynRec, the runtime
was foreground/balanced-100-ms, and requested/active presentation stayed GPU.
Graphics errors/fallbacks, post failures, dropped/saturated audio, stream errors,
diagnostic clock errors and discarded slices were zero. Special-page, SMC-return
and trap fallback counters were zero in this window. Independent guest-clock
accuracy and human audio quality remain unverified.

### Attribution and limits

The 13 underrun intervals span 13,153 ms: frontend call wall 11,874,238 us,
worker wait 11,298,532 us, completed worker wall 12,768,271 us and worker CPU
12,529,303 us. Failures recur through 10:19:20, not just at the readiness boundary.
For an explicit overload subset, select underrun intervals with call wall at least
95% of elapsed time and maximum inter-call gap below 1 ms. Three intervals ending
10:18:52.269, 10:19:04.404 and 10:19:16.518 satisfy this rule. They span 3,062 ms
with 174 calls, 191 underruns, 28,859 missing frames and three deadline resets.
Frontend call wall is 3,054,837 us; worker wait is 2,930,807 us (about 96% of call
wall), worker wall 3,021,846 us and worker CPU 2,970,474 us (about 97% of elapsed
time). Frontend mix is only 1,438 us; host video/audio callbacks total 110,419 us.

These measurements support CPU-heavy emulation-worker work as the dominant
contributor in those overloaded intervals, rather than frontend mixing or
between-call host sleeps. They do not exclude scheduling interference in other
intervals, presentation contention, or descheduling within an individual long
slice. Worker and frontend clocks overlap; never add their totals or subtract
process CPU from wall time. Completed worker slices can cross interval boundaries.
The worker includes CPU execution, translation, guest rendering and emulated
devices. Event counts are not durations: even 242,183 translation attempts and
369,601 opcode fallbacks in the three selected intervals do not establish which
path dominates. Passing stress fixtures also have fallback activity.

The last residency sample before shutdown showed DynRec 13,804, PageFault 15,
Normal/Halt/Other zero, fault queue 357/357, depth zero / high-water three, and
no wipe, double fault or reset. PageFaultCore completed 65,435/65,435 calls,
321,481 us cumulative / 6,376 us maximum, with zero 10-ms slow calls. This does
not reproduce stuck page-fault residency; direct fallback remains invisible to
that residency sampler and is instead covered by the new event counters.

Next is an offline source review and a scoped function/phase attribution or
source-owned reproduction design. Do not change instructions, caching, queue
depth or pacing from counts alone. No further Windows run is automatically queued.

### Teardown

The user confirmed normal shutdown. The core reported `guest requested shutdown`
at 10:19:58.781 and `guest stopped cleanly` at 10:19:58.873. Postflight found no
isolated runner, active-session marker or recovery journal; both profiles retain
clean provenance and unchanged settings. Stable `incoming` remains Normal /
Software generation 1; the experimental copy remains DynRec fixed-20k / GPU
generation 31. Only the host capture process was subsequently terminated.
No fatal/lifecycle error was observed in the selected log tags. This completes
T011 as a diagnostic failure with clean shutdown, not Feature 011 quality
acceptance. No runtime edit, guest force-stop, restore, extra trial or push occurred.

The subsequent [offline worker-path review](worker-path-review.md) corrects the
historical opcode-bucket interpretation, compares failing/non-failing intervals,
and specifies the next disposable investigation. These event counts are unchanged;
`BR_Opcode` is not exclusively an unsupported-instruction diagnosis.
