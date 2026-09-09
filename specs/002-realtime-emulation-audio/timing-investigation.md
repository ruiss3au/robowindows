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
