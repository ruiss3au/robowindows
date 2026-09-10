# Translation/cache control fixture — FR-031

This implements T074–T075's source-owned experiment, not a Windows fix. There
are no core patches or changes to CPU cycles, cache/link policy, audio pacing or
the guest-library interface. The earlier light/stress and x86 fixtures are intact.

## Workload and identity

`tests/cpu/cache_workload.S` reuses the existing CPU eight-sector boot loader.
All five images prepopulate the same 4,096 64-byte routine slots (256 KiB) below
640 KiB. Each routine runs 512 iterations of fixed multiply/add/rotate arithmetic
and returns `0x48cd306a` XOR a final immediate. Each measured case makes exactly
4,096 calls, asserts every return, and sums results modulo 2^32.

| Case | Preparation / measured work | Expected checksum |
| --- | --- | ---: |
| warm | Prime one slot / call it repeatedly | 3,540,426,752 |
| cold | No code execution during preparation / first sweep across all slots | 3,540,426,752 |
| reuse | Untimed full sweep / second sweep across the same slots | 3,540,426,752 |
| data | Prime one slot / alternate a separate data write then call the slot | 3,540,426,752 |
| rewrite | Prime one slot / alternate its trailing XOR immediate then jump/call it | 3,540,428,800 |

Code writes occur outside the called routine's executing block. Data and rewrite
have identical write/call counts but intentionally different target addresses and
expected results; they are not byte-identical guest instruction streams. Reuse
is a second-sweep candidate, not proof the host cache retained all blocks. DynRec
counters must demonstrate the intended mechanisms during device characterization.
The guest initializes VGA mode 13h and the same approximately 440-Hz PIT speaker
tone in every case; this is not animated GPU performance or Windows sound testing.

The protected-mode timer uses PIT channel 0 divisor 1,193 and a dedicated IRQ0
handler. A tick is `1193 / 1193182` seconds, approximately 0.999847 ms, independent
of frontend call counts. Setup, prime and measured boundaries are retained;
no host time is silently subtracted. Timer quantization and different timer
virtualization on QEMU versus DOSBox prevent treating reference timings as tablet
speed evidence. Monotonic, positive measured ticks prove recorded progress, not
5% real-time accuracy. Other guest IRQs are masked during this controlled phase.

After completion or an assertion, the guest restores BIOS PIC vectors/masks and
the default PIT divisor, writes its record to sector 18, and requests real-mode
APM power-off. Reference tests separately validate debug-exit completion and the
normal BIOS APM shutdown path. A bad return, guest deadline, exception, missing
record or host timeout is not a correctness pass.

## Record, build and privacy

The 64-byte little-endian record at byte offset 8,704 is:

| Offset | Type / meaning |
| --- | --- |
| 0 | Eight-byte magic `RWCACHE1` |
| 8 / 10 | u16 protocol 1 / length 64 |
| 12 | u32 case ID 1–5 in the table order |
| 16 / 20 | u32 expected / completed calls, both 4,096 on success |
| 24 / 28 | u32 result checksum / footprint 262,144 bytes |
| 32 / 36 / 40 | u32 PIT ticks at setup end / prime end / measurement end |
| 44 / 48 | u32 completion `0xc04ec04e` / assertion (zero on success) |
| 52 / 56 | u32 inner iterations 512 / slot stride 64 |
| 60 | u32 FNV-1a integrity over bytes 0–59 |

Assertion 1 is a return mismatch, 2 the 30,000-tick guest work limit, and 3 an
unexpected exception. Host timeout independently bounds a guest that cannot make
timer progress. FNV detects corruption; it is not authentication. Parsing requires
exact identity/size/metadata/counts/checksum/integrity and ordered tick boundaries.

`bash scripts/build-cache-fixture.sh CASE [OUTPUT]` pins binutils 2.40 and exact
image hashes. It stages a complete image, atomically publishes without clobbering,
reuses only byte-identical regular outputs, and rejects symlinks or differing
existing files (including mutated disposable runs). Future source revisions must
use a fresh output or explicitly remove only obsolete generated build files.

| Image | SHA-256 |
| --- | --- |
| warm | `d37a11864fcc864defa77356972f8feac07353c25d6990e9a439bd7ed0025fd4` |
| cold | `a8557469b19a09c682fd5efda571c79941edc349d3a7a26e5351a6c66cba1acb` |
| reuse | `d38a22b957dcf211ce484015c47ac25bb7302e3a6d037ca0fbbb1735e973e3b5` |
| data | `9a02c290d260e6724f7e17981c4a4fa2e274214c13a11ae42a2990226bb561f4` |
| rewrite | `da528da07344ce96ff9e664234c90e9477db3feade1828169fe2f329d29fbe8a` |

The existing non-exported isolated fixture activity accepts only five literal
`cache-*` names and packaged resources, after debug/stopped-session/CPU-gate
checks. It requests balanced timing and fixed 20k, observes guest shutdown, joins
native cleanup before reading buffered disk output, and deletes only its own
generated image/config/directory. A 60-second timeout, focus/surface loss, invalid
native exit or cleanup failure reports failure. No user path or guest disk can
be supplied through this workload selection. Logs contain only bounded fixture
identifiers, scalar results and existing aggregate timing diagnostics.

## Reporting and future device use

After a separately coordinated installation with both machines stopped, run one
case through the existing entry point, Normal before DynRec, for example:

```sh
bash scripts/test-sm-t500-presentation.sh normal 1 cache-warm
```

The tablet must be unlocked and foregrounded. Repeat the appropriate literal case
only as authorized; this document does not queue a matrix or any Windows trial.
The script checks the installed APK against the local build and requires the
disposable CPU gate. `summarize-cache-fixture.sh` validates one exact correctness
result and reports runtime/worker totals from periodic intervals plus a validated
terminal residual group when available. New device runs require that group.
`PASS correctness` is **not** an audio, timing, residency or cache-mechanism pass.
The report always states `quality=not_assessed` and `cache_mechanism=unverified`.

Worker diagnostics include bootstrap, setup, prime and shutdown, not just the
measured loop. Historical captures without terminal reporting can lack timing
entirely; current captures explicitly identify residual coverage and teardown.
Java `host_ms` ends at the shutdown-status poll before the cleanup join, while
native telemetry uses a different start and includes cleanup: these are not
interchangeable elapsed clocks. Guest measured ticks are per case; host worker
time is not measured-loop-only attribution. Setup differences, queue history and
measurement coverage must be considered before comparisons. No new phase signal,
per-call clock or emulator-memory inspection conceals that limitation.

## Verification status

On 2026-09-10, the full host suite passed, including the five QEMU correctness
records, injected wrong-return failure, BIOS APM shutdown, strict binary/text
parser negative cases, no-clobber output checks and all pre-existing x86/REP/
worker tests. Worker/runtime parsers retain their existing coverage. Pinned-source
reconstruction, repository hygiene and whitespace checks passed. Final Android
debug build passed (`50e09dab5a6f+dirty`, unchanged CPU capability
`1c6ac3a100f92c22`); its APK SHA-256 is
`1845f82bb3fd37bae43580135211de64a25aebd2391052f1098c6af17a268c3a`.
The APK contains all five images with exactly the hashes above. Its original
light and stress assets retain SHA-256
`8643f9d97386905e71f74e9aee74c3a936701dd3471dfdb48423715abac4b76a`
and `816fe821f70ea4ff3929fd2281a1ee47a2487ba9f59e43278ebfad61bd7cd3b1`.
Only the pre-existing Gradle source-directory deprecation warning was observed.
Ignored evidence is in `artifacts/cache-host.log` and
`artifacts/cache-build-final.log`. T074–T075 are complete for implementation and
host/build validation. No installation, ADB session, real guest boot or settings
change was performed; the tablet still has its previous diagnostic build.

Device Normal/DynRec characterization, actual cache reuse/invalidation, audio
quality and independent guest/host timing accuracy remain unverified. No Windows
run or promotion is implied by a disposable correctness result.

## Authorized device characterization — 2026-09-10

The user authorized installation of the verified APK and the disposable CPU gate,
then one five-case Normal/GPU matrix followed by DynRec/GPU. Both real machines
must remain stopped and unchanged. Stop on correctness, lifecycle or cleanup
failure; record diagnostic timing failures without promoting them. Preserve all
case captures, distinguish complete telemetry coverage from whole guest work,
and verify runner/cache cleanup and clean machine metadata afterward. This
authorization does not include a Windows workload or engine changes.

### Installed build and CPU gate

Preflight confirmed both profiles clean/stopped, no isolated runner, active-session
marker or recovery journal. The exact APK above was installed without clearing
data, then read back and hash-verified. The full disposable x86 gate passed in
Normal and DynRec at tablet time 11:01:50.829. Its 36 frontend/worker intervals
validated with zero diagnostic clock errors. The DynRec page-fault suite retained
21/21 queue returns, depth zero / high-water four, no wipe, and 16,408/16,408
PageFaultCore calls (84,879 us cumulative / 11,084 us maximum, one 10-ms slow call).
This is correctness evidence, not a real-time pass for that intentional stress.
Raw CPU evidence remains ignored in `artifacts/cache-device-x86.log`.

### Five-case results

All five Normal cases ran before all five DynRec cases, fixed 20k and requested
GPU, on the same APK. Each produced 4,096 checked returns, the exact expected
checksum, a valid integrity record, the expected configured decoder and its own
APM shutdown. Results span tablet time 11:02:33.736–11:04:04.240.

| Case | Normal host ms | DynRec host ms | Normal measured PIT ticks | DynRec measured PIT ticks | Complete telemetry intervals, Normal / DynRec |
| --- | ---: | ---: | ---: | ---: | ---: |
| warm | 705 | 710 | 528 | 527 | 0 / 0 |
| cold | 703 | 707 | 528 | 528 | 0 / 0 |
| reuse | 1,207 | 1,202 | 528 | 528 | 1 / 1 |
| data | 705 | 702 | 528 | 528 | 0 / 0 |
| rewrite | 706 | 712 | 529 | 529 | 0 / 0 |

All cases recorded 14 setup ticks; reuse additionally recorded 527 prime ticks
under both cores, and the other cases recorded zero prime ticks. Host durations
include bootstrap/setup and shutdown polling, so the small differences are not
CPU throughput or an independent 5% guest-clock validation. Similar guest ticks
at the same fixed cycle budget do not imply identical host CPU cost.

The ten strict correctness summaries passed. Ten synthetic enabled calibrations
ranged from 2,432 to 2,917 ns per slice; retain the previously documented overhead
limits. Complete raw captures and strict summaries are in ignored
`artifacts/presentation-{normal,dynamic}-1-cache-*/`; per-case script output is
in `artifacts/cache-device-{normal,dynamic}-{case}.log`.

### Measurement gap, not a timing pass

Eight sessions ended before the one-second reporting interval. Their reports
explicitly say frontend/worker timing is unavailable. Printed zero aggregate
errors and zero presented FPS in those empty reports are **not observations of
zero errors or zero actual presentation**. There is no settled audio or residency
measurement for those eight cases.

Only reuse had a complete interval: 1,000 ms under Normal and 1,008 ms under
DynRec. In those limited windows, worker CPU was 501,164 / 307,055 us and worker
wall 519,310 / 328,120 us respectively; these overlap frontend timing and cover
setup/prime/measured work, not the measured loop alone. DynRec recorded 12,324
translation attempts, Normal zero. All fallback buckets were zero in those two
windows. Both recorded zero underruns, missing/dropped/saturated audio, deadline
resets, stream/graphics/clock errors and post failures. Requested/active GPU and
sampled current decoder matched. Those observations exclude the unreported tail
and do not establish whole-session or settled quality. The reports intentionally
retain `quality=not_assessed` and `cache_mechanism=unverified`.

Source review confirms the gap: `report_telemetry_if_due()` returns before
1,000 ms, and runtime cleanup has no terminal partial-interval report before
worker diagnostics are disabled. The retained logs cannot reconstruct counters
that were never emitted. Thus actual warm/cold/rewrite cost and the intended
invalidation/reuse mechanisms remain uncharacterized; the Windows underrun cause
is still unresolved. Do not infer cache effectiveness or a DynRec speedup from
the two partial reuse windows.

Next, specify and test bounded terminal diagnostic reporting for these short
fixtures, with completed-worker publication, explicit partial-interval/lifecycle
semantics and unchanged legacy behavior. Preserve these exact guest images;
blindly repeating a cold sweep to extend duration changes the mechanism. No
engine/cache/queue adjustment or additional trial was performed in this step.

### Postflight and acceptance

Postflight found no DynRec/CPU/presentation runner, active-session marker,
recovery journal or presentation cache directory. Both real profiles retained
clean provenance and unchanged settings: stable `incoming` Normal/Software
generation 1; experimental copy DynRec fixed-20k/GPU generation 31. Neither real
guest was booted. No native/Java failure or fixture cleanup failure was observed
in the captured records. Repository hygiene and whitespace checks passed.

Installation and all ten correctness cases are complete. T076 remains open only
for adequate diagnostic coverage and mechanism characterization, not another
blind repeat. No Windows acceptance, human audio judgment, promotion or push is
included in this milestone.

## Terminal reporting implementation — 2026-09-10

FR-032/T077 adds one residual group after presenter/audio stop and completed
worker shutdown, before disabling worker diagnostics. It uses distinct terminal
tags so existing periodic-only quality consumers remain unchanged. A microsecond
header identifies tail duration, preceding periodic-group count and exit reason;
the triplet retains the existing field schemas. See the [terminal contract](contracts/runtime-telemetry.md#terminal-residual-group-fr-032).
Legacy sessions leave the terminal gate disabled and perform no terminal clock
reads. No upstream patch, CPU instruction/cache/link change or guest work was added.

The cache reporter now explicitly opts into terminal validation and aggregates
ordinary plus residual counts once, with maxima across both. The header prevents
missing whole ordinary groups from silently looking complete. Historical logs
without terminal records still parse with unavailable terminal coverage; an empty
capture now reports unavailable error/FPS observations instead of misleading
zeros. The device capture entry point requires terminal coverage for new cache
runs. A terminal group with missing/duplicate/reordered records or bad clocks is
rejected. Zero/sub-ms tails do not receive a fabricated minimum duration.

Terminal values include teardown, `state=stopping`, stopped audio and potentially
cleared queue depth. They are not settled audio or presentation samples. Worker
and frontend clocks overlap; a completed slice may begin in a preceding interval.
Existing lifecycle-generation resets can discard partial work and are not undone
by terminal reporting. Load failures/crashes need not yield a terminal group.
The reports retain `quality=not_assessed` and `cache_mechanism=unverified`; actual
cache behavior and the Windows underrun cause are still open.

Full host tests passed: once-only/disabled/early shutdown gating, fresh-session
reset, microsecond/zero/bad-clock arithmetic, final completed-worker drain before
disable, lifecycle stale-slice discard, terminal-only/mixed histories, dropped
whole groups, corrupt/unknown/duplicate fields, missing/reordered records and
unchanged ordinary consumers. A source guard verifies the production cleanup
ordering. Existing cache checksums, injected failure/APM QEMU checks and x86/REP/
worker/graphics host checks also passed. Pins, repository hygiene and whitespace
checks passed. No device calls or installation occurred in this implementation.

Android debug build passed: source label `42e6c31b9f30+dirty`, new CPU capability
`2e6caa2dcea9a5bf`, APK SHA-256
`4bae989c5385adeb5e43882a1c2ade439cd5324e755e03f95b3fef1c13a6e1ee`.
All five packaged cache images and the original light/stress images retain the
previously recorded hashes. Ignored logs are `artifacts/terminal-host.log` and
`artifacts/terminal-build.log`. Device shutdown-tail coverage, legacy smoke and
the repeated unchanged Normal/DynRec comparison remain separately coordinated
T078 work. Rollback removes only terminal reporting/consumer opt-in; real machine
settings and disks remain untouched.

## Terminal device validation authorization — 2026-09-10

The user authorized T078: install the verified terminal-reporting APK with both
real machines stopped, run the disposable legacy smoke and full Normal/DynRec
x86 gate, then the unchanged five-case cache matrix Normal-first and DynRec
second. Read-only preflight confirmed both profiles clean, no active session,
isolated runner or recovery journal, and the candidate APK hash above. Stable
generation 1 and experimental generation 31 remain unchanged. Require terminal
coverage in each cache capture and stop on correctness, lifecycle, cleanup or
diagnostic validation failure. This does not authorize Windows boots, engine
tuning, promotion, a broader manual campaign or a push.

## Terminal device results — 2026-09-10

### Installation, legacy and x86 validation

The authorized stopped installation completed. Installed APK readback matched
`4bae989c5385adeb5e43882a1c2ade439cd5324e755e03f95b3fef1c13a6e1ee`
(source label `42e6c31b9f30+dirty`, CPU capability `2e6caa2dcea9a5bf`).
`REQUIRE_SURFACE=1 scripts/test-sm-t500-core.sh` passed rendering, persistence,
input, media change, pause/resume, restart and clean-stop probes. A separate
terminal-tag logcat capture before clearing the logs confirmed zero terminal
records under legacy timing. Its silent fixture is not an audio quality test.

The full 18-stage Normal/DynRec x86 gate passed at device-log time 11:31:46.769.
All 18 experimental sessions emitted exactly one valid terminal group. Splitting
the capture by runner process and applying the strict normalizer and existing
runtime/worker parsers validated every group. Ordinary-only parsing still reports
36 intervals and 2,367 completed worker slices; adding each session's terminal
group yields 3,640 completed slices. Diagnostic clocks reported no errors.
DynRec suite 103 retained PFQ 21/21, depth 0/4, wipe 0/0 and PFCore 16,408/16,408;
PFCore total/max were 130,959/33,011 us with two over-10-ms calls in this deliberate
stress case. No double fault or reset was recorded. This correctness gate is not
a timing-quality pass or evidence that every long page-fault call is eliminated.

### Unchanged Normal-first cache matrix

All five Normal/GPU cases ran before all five DynRec/GPU cases. Every strict
record passed 4,096 return checks, the expected checksum and the guest APM
shutdown/cleanup check. Setup was 14 guest ticks in all cases, reuse priming was
527 ticks in both cores, and other priming recorded zero ticks. Each image is
unchanged from the hashes above.

Pairs below are Normal / DynRec. Worker CPU is aggregate observed CPU, including
setup, priming and teardown; it is not the measured loop's translation time.

| Case | Java host ms | Measured guest ticks | Native observed ms including tail | Worker CPU us | DynRec translation attempts |
| --- | ---: | ---: | ---: | ---: | ---: |
| warm | 705 / 705 | 528 / 527 | 670.043 / 701.275 | 301,685 / 163,281 | 52 |
| cold | 703 / 710 | 528 / 528 | 674.536 / 695.016 | 307,876 / 206,400 | 12,334 |
| reuse | 1,205 / 1,207 | 528 / 528 | 1,211.005 / 1,232.627 | 604,564 / 350,513 | 12,338 |
| data | 705 / 703 | 528 / 528 | 672.864 / 700.412 | 303,846 / 157,174 | 53 |
| rewrite | 705 / 704 | 529 / 529 | 676.831 / 692.786 | 302,766 / 176,126 | 4,148 |

Each capture has exactly one terminal group with `reason=guest_shutdown` and
`terminal_coverage=available`. Eight have no periodic group; reuse has one per
core. Completed worker slices total 37 per short case and 74 per reuse case, with
zero reported discarded slices. All Normal translation counts and all five
fallback buckets in both cores are zero. Every capture records zero underruns,
missing/dropped/saturated audio, stream errors, resynchronizations, graphics
errors/fallbacks, post failures and diagnostic clock errors.

Requested/active presentation remained GPU. The two periodic reuse records have
the expected current decoder. Every terminal record has `current=Other` after
APM shutdown and `queue_current=0` after audio stop; hence each summary reports
one `other_decoder_intervals`. These are shutdown observations, not evidence of
foreground residency failure or a running empty queue. Configured decoder checks
passed, but the eight terminal-only sessions have no periodic residency sample.
Presented FPS over the observed intervals including teardown ranges 22.82–26.42;
it is not settled FPS or unique game frames. The zero error counts likewise do
not establish settled Windows audio quality, independent 5% clock accuracy or
sustained stability. Retain `quality=not_assessed`.

### Bounded mechanism interpretation and next step

The controls now distinguish low-translation warm/data work from a cold sweep
and repeated code rewriting. The two-sweep reuse session has only four more
translation attempts than cold's one sweep, consistent with substantial retained
code reuse. Rewrite has 4,096 more attempts than warm and 4,095 more than data,
consistent with code-write-triggered retranslation. These are inferences from
aggregate controls, not per-phase cache-hit or invalidation-reason measurements.
Source review of `src/cpu/dyn_cache.h` confirms that write handlers can clear
cached blocks before entry without hitting any of the fallback counters. Their
zero values therefore do not contradict the rewrite result.

DynRec's observed worker CPU is lower than Normal's in every case. Cold and
rewrite cost more worker CPU than DynRec's warm/data controls, but those whole
sessions differ in setup/control instructions and host scheduling. A single run
cannot assign the difference exclusively to translation, measure an exact cache
hit rate or identify a costly function. Neither this fixture nor its low fallback
counts reproduces the retained Windows translation/fallback storm. Keep the
report's `cache_mechanism=unverified` for exact mechanism attribution.

T078 is complete. T076's missing diagnostic coverage is resolved; its strict
actual reuse/invalidation attribution remains open at the limits above. The next
useful work is to specify bounded translation-cost/cache-miss-reason attribution,
using these controls to validate it before any engine change or Windows trial.
Do not resize caches, remove publication barriers or tune audio buffers from
these counts alone. No more trials follow automatically.

### Evidence and postflight

Ignored wrapper logs are `artifacts/terminal-device-legacy.log`,
`artifacts/terminal-device-legacy-terminal.log`, `artifacts/terminal-device-x86.log`
and `artifacts/terminal-device-{normal,dynamic}-{case}.log`. Per-session x86
validation is in `artifacts/terminal-x86-sessions-NKcbJv/`. Raw matrix captures are
the `artifacts/presentation-{core}-1-cache-{case}-{suffix}/` directories below;
each contains `telemetry.log`, `summary.txt` and APK identity.

| Case | Normal suffix | DynRec suffix |
| --- | --- | --- |
| warm | lAicCp | X6mFVF |
| cold | RjVPAi | lVAdQy |
| reuse | INmSw7 | 2qJz1T |
| data | Qs3VdV | z8TyDp |
| rewrite | ZgPEy5 | Ta1Z9j |

Postflight confirmed no isolated runner, active-session marker, recovery journal
or presentation cache directory. The legacy staged/private fixture files were
removed and the original stay-awake setting restored. Both real profiles remain
clean and unchanged: stable `incoming` Normal/Software generation 1;
`win98 dynrec exp` fixed-20k DynRec/GPU generation 31. Neither Windows guest was
opened. Only sanitized aggregate evidence is tracked; raw generated captures
remain ignored. No engine change, promotion or push is included.

After recording the device results, repository hygiene, whitespace checks,
pinned-source verification and the terminal/cache-summary parser regressions
passed. Full host tests and Android build are the unchanged T077 results above;
this device-evidence step changes documentation only and does not rebuild or
reinstall for documentation.
