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
