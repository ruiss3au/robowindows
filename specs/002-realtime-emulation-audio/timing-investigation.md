# Post-slice timing investigation — 2026-09-09

The saved fixed-20k pair used the same APK and workload on the experimental copy.
Normal recorded zero underruns; DynRec recorded all 12 underruns and 1,862 missing
frames in one 1,004-ms interval. That interval contained 66 completed calls,
14 over-budget calls, 31 catch-up calls, 42.228 ms maximum call duration and
112.503 ms maximum deadline debt, without a resynchronization. Adjacent residency
samples retained exactly 209/209 queued/completed faults and 56,868/56,868
PageFaultCore entries/returns. Thus no new PageFaultCore execution explains the
burst. Several calls or scheduling delays accumulated more debt than the queue
could absorb; the old aggregates cannot attribute those delays further.

Source inspection found an independent defect in `core_host.cpp`: eight
informational environment commands shared the `SET_SYSTEM_AV_INFO` body, which
read every non-null payload as `retro_system_av_info`. The preceding DynRec boot
had logged an implausible roughly 2.37e63 FPS with sample rate zero. The new
`av_environment.h` dispatcher acknowledges those informational commands without
reading their payloads. Only the actual AV command reads timing, and invalid
values are rejected before updating the requested cadence. The correction
applies at the shared host boundary, with no guest configuration migration.
It is not yet proven to cause or cure the benchmark underruns.

An unreadable host memory page tests that all informational command paths ignore
their payloads, including null. Valid refresh changes, null AV data, NaN,
infinity, negative/zero and implausibly large rates are covered. The diagnostic
accumulator tests interval continuity, lifecycle reset, parallel process CPU
time exceeding elapsed time, failed/regressing CPU-clock samples and independent
callback totals/maxima. Strict parser tests reject malformed, duplicate, unknown,
partial and unmatched extension records; historical schema-3 captures remain
readable with an explicit unavailable-diagnostics marker.

The extra `RoboWindowsTiming` line measures process CPU during completed call
windows because DOSBox Pure executes guest work on a separate worker thread.
That CPU includes other runner threads and is not exact DynRec CPU cost. Host
gaps include intentional sleeping and maintenance, while wake lateness includes
retained debt. Video/audio callback timings narrow frontend costs without adding
PCM or frame contents to diagnostic records. All extra measurements are enabled
only for the experimental timing policy. The 100-ms queue target, scheduler debt
policy, instruction engine and 15-FPS presentation limit are unchanged.

The complete host suite, eight-suite QEMU reference run, repository hygiene,
pinned-source verification and ARM64 Android debug build passed. The installed
APK and host artifact match SHA-256
`78eaaba73b2db6fb593ef653a9647c4d1d44cb7972dc3e78b39b59b22452d471`.
Installation occurred after verifying both machine Start controls, no active
session preference, no dynamic journal, and no isolated child. The complete
legacy-plus-eight-suite Normal/DynRec CPU gate passed on the SM-T500. Suite
`0x0103` retained exact parity, 21/21 fault completion, depth zero/high-water four,
zero wipes and 16,408/16,408 PageFaultCore calls; cumulative time was 122,180 us
and maximum inclusive time 23,252 us. Postflight found no isolated child, active
session preference or dynamic journal. No Windows disk was opened. The CPU gate
uses legacy timing, so it validates callback/core compatibility; experimental
diagnostic collection itself still needs a Windows capture in addition to its
host tests. T064/T065 are complete; T062 remains open.

A new guest capture must explicitly confirm desktop, keyboard and captured-mouse
readiness and independently measure guest elapsed time; the benchmark result
alone does not establish wall-clock accuracy or human audio quality.

## First instrumented Windows benchmark

The next fixed-20k DynRec run explicitly confirmed all three readiness controls
before capture and completed with correct decoder residency, 15.07 presented
FPS, zero underruns/missing/dropped frames, no deadline resynchronizations, and
verified clean Windows shutdown. The strict guest record and all 53 timing
extension intervals (3,746 calls) passed validation. Independent guest wall-clock
accuracy and human audio were not tested. The same-build Normal comparison is
still outstanding; this is not completion of T062.

Completed calls accumulated 6,891,051 us wall time. The synchronous video
callback accounted for 4,470,798 us (about 65% of that wall time), maximum
17,434 us; audio callbacks accumulated 497,724 us, maximum 7,874 us. Process CPU
within call windows totalled 11,176,391 us, maximum 68,200 us per call, with zero
clock errors. This includes parallel runner threads and must not be subtracted
from wall time or labeled DynRec execution cost. Maximum call wall time was
36,126 us, maximum host gap 25,939 us, and maximum wake lateness 60,742 us;
the latter includes retained timing debt rather than only OS wakeup delay.

Video callback work is a substantial measured frontend cost in this capture,
but it is not the asynchronous surface-presenter duration and no underrun
occurred to attribute. The fault counters remained unchanged during the capture;
the final PageFaultCore sample was balanced at 43,328 entries/returns with a
703 us maximum. These data do not justify another instruction-engine change or
prove that the AV-dispatch correction cured the previous intermittent burst.
Continue with a same-build matched pair and independent guest-clock validation;
retain the deferred GPU/FPS roadmap and existing quality gates. Full benchmark
and shutdown evidence is in Feature 009's validation record.

## Same-build Normal comparison

Normal fixed-20k subsequently completed with zero underruns/missing frames or
resynchronizations, 15.07 FPS, correct residency and verified clean shutdown.
Its 55 valid intervals covered 55,601 ms and 3,894 calls, versus DynRec's
53,480 ms and 3,746 calls; raw interval totals therefore cover different windows.
Normal call wall time totalled 2,892,215 us; video callbacks took 1,915,727 us
(about 66%, maximum 1,862 us), audio callbacks 172,596 us (maximum 387 us).
Process CPU within calls totalled 3,596,126 us, maximum 35,429 us per call,
without clock errors. Maximum call wall time was 31,313 us, host gap 21,264 us
and wake lateness 21,862 us. Normal's minimum queue was 3,094 frames versus
DynRec's 1,124, so the latter had less observed audio headroom, but neither
capture reproduced starvation. Callback measurements include elapsed scheduling
delays and do not alone identify an instruction-engine or presenter defect.
No new runtime tuning is justified by this pair alone. Independent guest-clock
validation remains missing; the user dropped the longer acceptance trials from
the current campaign. Keep the diagnostic/promotion distinction explicit.

## Shared Normal Windows stalls and refresh-debt correction — 2026-09-10

Feature 011's same-build Normal fixed-20k GPU and Software Windows captures both
failed audio/timing acceptance and shut down cleanly. GPU recorded 605 underruns,
89,086 missing frames and seven resynchronizations; Software recorded 454,
68,844 and five respectively. Their user workloads are not confirmed equivalent.
The failures are not GPU-exclusive, and differing counts do not establish that
either presenter causes them. Detailed capture evidence remains in Feature 011.

Several bad intervals spent nearly the entire second inside `retro_run()`:
Software at tablet time 08:06:07.554 recorded 41 calls, 998,360 us call wall time,
1,346,477 us process CPU, 23,088 us synchronous video callbacks, 1,772 us audio
callbacks and maximum host gap 680 us. It had 129 underruns and one deadline
resynchronization. GPU at 07:23:25.716 likewise recorded 38 calls, 1,018,125 us
wall time in a 1,019-ms interval, 19,691 us video callbacks and 1,838 us audio
callbacks. These are not predominantly frontend sleeping or callback-copy
intervals. Process CPU includes other threads; the worker hot path remains
unidentified. Repeated roughly 28-ms calls exceed the roughly 14-ms guest-frame
budget, so merely increasing the buffer is not a demonstrated sustainable fix.

Source inspection found an independent host defect: the live advertised-FPS
update called the lifecycle `configure()` operation, discarding deadline debt,
pacing correction and catch-up history, and also reset diagnostics and producer
gap history without a lifecycle interruption. In the Software interval at
08:06:19.619, telemetry counted 64 calls but diagnostics retained only 10 following
the refresh update at 08:06:19.484. The same loss occurred in GPU captures.

FR-028 separates live cadence updates from lifecycle resets. Experimental
updates change only the nominal interval, retaining debt/correction/catch-up
state, complete diagnostic totals and producer-gap continuity. Legacy timing
keeps its old deadline-reset behavior; actual lifecycle transitions still reset
all the relevant state. No CPU engine, PCM, queue target, presentation policy,
upstream source or dependency pin changes are involved.

The host regression reproduces the old silent loss of 230 ms of debt during a
refresh update and verifies that the corrected path counts a later 251-ms debt
as a resynchronization. It also covers retained queue hysteresis, bounded bursts
across repeated updates, rate changes, invalid rates, diagnostic continuity and
unchanged legacy/lifecycle behavior. This repairs a proven debt/measurement
defect, not all the sustained long-call bursts; Windows underruns remain
unresolved until measured otherwise. More visible debt after correction is not
to be hidden by weakening the quality gate.

### Corrective-slice verification

Full host suite (including eight-suite QEMU reference), repository hygiene,
diff whitespace, pinned-source verification and Android debug build passed.
Build label `57a4bed59d23+dirty`; installed APK matched host SHA-256
`79d5f1b23cf722b0c3f6e5c265c7a1c9b9321177c0971290fd13e48466053f25`.
Installation followed checks for clean Windows shutdown provenance, no active
session, no isolated runner and no recovery journal. Disposable core/lifecycle
checks with a real surface and the full legacy-plus-eight-suite Normal/DynRec
x86 gate passed.

The existing source-owned timer/tone fixture was unchanged (SHA-256
`8643f9d97386905e71f74e9aee74c3a936701dd3471dfdb48423715abac4b76a`).
Both runs used fixed-20k, balanced 100 ms and GPU presentation, foreground on
SM-T500/API 36. These are regression tests, not reproductions of Windows load.

| Core | Settled ms | Presented FPS | Presenter CPU us/post | Host ms | Guest ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| Normal | 108,349 | 29.76 | 1,435.64 | 122,019 | 121,605 |
| DynRec | 108,344 | 29.78 | 1,335.04 | 122,009 | 121,605 |

Both strict quality reports passed with correct residency and zero settled
underruns, missing/dropped frames, stream errors, deadline resynchronizations,
post failures, graphics errors/fallbacks and presenter clock errors. Guest time
was within 0.4% of host time. The entire captures also had zero audio/timing
failure counters. No claims about physical speaker quality or Windows load
follow from this fixture. Live refresh/debt interactions are covered by the
deterministic host tests, not by a heavy Windows trial on this build.

Postflight found no active-session marker, isolated runner or recovery journal.
Both real machines retained clean shutdown provenance: stable `incoming`
Normal/Software generation 1; experimental copy Normal/Software generation 30.
No Windows machine was booted, no machine setting changed, and no push performed.
T066/T067 are complete; Windows starvation and the older qualification gates
remain open. The next investigation needs attribution of the sustained worker
stalls and a confirmed reproducible workload, not another blind acceptance run.

## Phased stress reproduction and REP accounting finding

The user clarified that the earlier captures included opening windows and then
Age of Empires II. They were not desktop-idle baselines. Exact game state and
phase timestamps are still unknown; do not attribute individual Windows failures
to a specific game operation.

Added a separately allowlisted source-owned stress fixture, described in
`tests/realtime/workloads.md`. It repeats idle, integer, RAM fill/copy and VGA
write phases while retaining BIOS tick timing and the PIT tone. It uses real
mode with 32-bit arithmetic, not a Windows protected-mode/game reproduction.
The original light image retains its exact checksum. The stress image SHA-256 is
`816fe821f70ea4ff3929fd2281a1ee47a2487ba9f59e43278ebfad61bd7cd3b1`.
Results require distinct magic plus all four phase bits and positive batch counts;
the strict parser rejects light/stress identity confusion and incomplete phases.

Host checks passed, including the production stress image in pinned QEMU 7.2.22:
630 guest ticks after the bounded 35-second reference run, phase mask 15 and all
phase counters positive. Pinned sources, repository hygiene, Android build and
disposable core/lifecycle checks with a surface passed. The CPU diagnostic
revision is unchanged from the passing complete x86 gate in the preceding slice;
no native runtime or engine code changed for this fixture addition.

Build `4624fc33e184+dirty`, APK SHA-256
`bab8d4723396d93c217c7350e39c804d2a35af49fd7eef048f51ab91e2bb9ad2`.
Both disposable tablet runs used fixed-20k, balanced 100 ms and GPU presentation.

| Measure | Normal | DynRec |
| --- | ---: | ---: |
| Capture telemetry ms (including startup) | 121,417 | 121,707 |
| Audio underruns (entire capture) | 0 | 27,035 |
| Missing audio frames (entire capture) | 0 | 4,852,605 |
| Deadline resynchronizations (entire capture) | 0 | 318 |
| Maximum emulator call us | 29,837 | 351,528 |
| Maximum producer gap us | 43,658 | 351,551 |
| Maximum scheduler lateness us | 27,533 | 538,239 |
| Result | Pass | Fail |

Normal's strict settled window was 108,305 ms at 29.76 presented FPS and
3,153.25 presenter CPU us/post, with zero quality errors. Its independent guest
timer reported 121,605 ms versus 122,003 host ms, all phases validated. DynRec's
entire capture averaged only 6.51 posts/s, dropping to approximately 3–4 calls
and posts/s during sustained stalls. Neither run had dropped audio frames,
stream errors, post failures, graphics errors/fallbacks or presenter clock errors.

DynRec's initially clean idle/integer period was followed around 16 host seconds
by repeated approximately 350-ms calls; this corresponds to entry into the RAM
phase while prior guest pacing was still near real time. That phase attribution
is an inference, not a recovered per-phase guest record. The final harness
reported `FAIL missing guest timer record`, its generic message for any rejected
magic/status/phase/tick record. It does not prove the record was absent. The
disposable result was deleted on unload, so exact partial ticks/phase mask are
unavailable and guest-clock accuracy cannot be claimed for DynRec.

Source inspection identified an independently demonstrable cycle-accounting
defect in the DynRec MOVS/LODS/STOS helpers: when a repeat fits within the current
`CPU_Cycles` budget, the helper performs the work without subtracting its element
count. The generated block bills decoded instructions, not REP elements; Normal
charges each element. A local UBSan-enabled probe extracted the actual patched
STOSD helpers and invoked each address-size variant with 4,096 elements and a
20,000-cycle budget. Both performed 4,096 writes, returned zero remaining elements
and left `CPU_Cycles` at 20,000: **zero element cycles charged**. The same missing
charge branch is present in the other affected helpers. The existing fault tests
covered progress and exhausted-budget resumption but not this fits-in-budget case.

This provides a concrete next engine fix and a disposable performance reproducer.
It does not yet prove that the defect caused either earlier Windows/AoE2 capture,
especially the Normal failure. No engine patch or buffer/cycle retuning was made
in this slice. Before patching, add explicit cycle-accounting/fault-progress
regressions and extend Feature 008; then rerun the full x86 and unchanged stress
gates. No more Windows trial is queued.

Both fixtures unloaded and deleted their own files, including the failing run.
Postflight found no fixture/isolated process, active-session marker, recovery
journal or remaining presentation cache directory. Both Windows machines retained
clean provenance and unchanged selection/generation: stable Normal/Software 1,
experimental copy Normal/Software 30. Neither Windows machine was booted.
