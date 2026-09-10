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
result and reports runtime/worker totals from complete telemetry intervals.
`PASS correctness` is **not** an audio, timing, residency or cache-mechanism pass.
The report always states `quality=not_assessed` and `cache_mechanism=unverified`.

Host duration and worker diagnostics cover the whole session, including bootstrap,
setup, prime and shutdown; final partial telemetry intervals are not available.
Very short captures can have no complete diagnostic interval and explicitly report
unavailable timing. Guest measured ticks are per case; host worker time is not
measured-loop-only attribution. Setup differences, queue history and measurement
coverage must be considered before comparisons. No new phase signal, per-call
clock or emulator-memory inspection was added to conceal that limitation.

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
