#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
valid="$(mktemp)"
invalid="$(mktemp)"
capture="$(mktemp -d)"
trap 'rm -f -- "$valid" "$invalid"; rm -rf -- "$capture"' EXIT

for index in $(seq 1 30); do
  printf '09-09 I RoboWindowsTelemetry: schema=3 interval_ms=1000 state=foreground audio_state=playing decoder=DynRec current=DynRec timing=balanced_100ms pacing=nominal run=60 retro_max_us=9000 retro_over=0 producer_gap_max_us=17000 scheduler_late_max_us=2000 catchup=1 resync=0 audio_produced=48000 audio_consumed=48000 queue_current=4800 queue_min=3600 queue_max=6000 underruns=0 missing=0 dropped=0 saturated=0 stream_errors=0 submitted=60 published=30 presented=30 coalesced=30 post_failures=0\n' >>"$valid"
done
summary="$($repo_dir/scripts/summarize-benchmark-telemetry.sh dynrec-fixed-20k "$valid")"
grep -q '^cycle_value=20000$' <<<"$summary"
grep -q '^presented_fps=30.00$' <<<"$summary"
grep -q '^audio_produced_hz=48000.00$' <<<"$summary"
grep -q '^telemetry_schema=3$' <<<"$summary"
grep -q '^retro_run_max_us=9000$' <<<"$summary"
grep -q '^scheduler_catchup_calls=30$' <<<"$summary"
grep -q '^scheduler_deadline_resyncs=0$' <<<"$summary"
grep -q '^audio_queue_frames_current=4800$' <<<"$summary"
grep -q '^lifecycle_valid=1$' <<<"$summary"

cp "$valid" "$invalid"
printf '09-09 I RoboWindowsTelemetry: schema=3 interval_ms=1000 state=paused audio_state=suspended decoder=DynRec current=DynRec timing=balanced_100ms pacing=nominal run=0 retro_max_us=0 retro_over=0 producer_gap_max_us=0 scheduler_late_max_us=0 catchup=0 resync=0 audio_produced=0 audio_consumed=0 queue_current=0 queue_min=0 queue_max=0 underruns=0 missing=0 dropped=0 saturated=0 stream_errors=0 submitted=0 published=0 presented=0 coalesced=0 post_failures=0\n' >>"$invalid"
if "$repo_dir/scripts/summarize-benchmark-telemetry.sh" dynrec-fixed-20k "$invalid" >/dev/null 2>&1; then
  echo "Telemetry parser accepted a paused interval" >&2
  exit 1
fi
if "$repo_dir/scripts/summarize-benchmark-telemetry.sh" normal-fixed-20k "$valid" >/dev/null 2>&1; then
  echo "Telemetry parser accepted a core mismatch" >&2
  exit 1
fi
printf 'capture_schema=1\nprofile=dynrec-fixed-20k\nguest_result=pending_strict_parse\nhuman_audio_quality=pending\n' >"$capture/report.txt"
printf '%s\n' "$summary" >"$capture/telemetry-summary.txt"
"$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$capture" \
  --guest-result "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" \
  --audio pass --input pass --gdi pass --shutdown clean >/dev/null
grep -q '^gdi_megapixels_per_s=0.246$' "$capture/final-report.txt"
grep -q '^short_run_quality=pass$' "$capture/final-report.txt"

resync_capture="$(mktemp -d)"
printf 'capture_schema=1\nprofile=dynrec-fixed-20k\nguest_result=pending_strict_parse\nhuman_audio_quality=pending\n' >"$resync_capture/report.txt"
sed 's/^scheduler_deadline_resyncs=0$/scheduler_deadline_resyncs=1/' \
  "$capture/telemetry-summary.txt" >"$resync_capture/telemetry-summary.txt"
"$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$resync_capture" \
  --guest-result "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" \
  --audio pass --input pass --gdi pass --shutdown clean >/dev/null
grep -q '^short_run_quality=fail$' "$resync_capture/final-report.txt"
rm -rf -- "$resync_capture"

low_fps_capture="$(mktemp -d)"
printf 'capture_schema=1\nprofile=dynrec-fixed-20k\nguest_result=pending_strict_parse\nhuman_audio_quality=pending\n' >"$low_fps_capture/report.txt"
sed 's/^presented_fps=30.00$/presented_fps=14.99/' \
  "$capture/telemetry-summary.txt" >"$low_fps_capture/telemetry-summary.txt"
"$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$low_fps_capture" \
  --guest-result "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" \
  --audio pass --input pass --gdi pass --shutdown clean >/dev/null
grep -q '^short_run_quality=fail$' "$low_fps_capture/final-report.txt"
rm -rf -- "$low_fps_capture"

wrong_decoder_capture="$(mktemp -d)"
printf 'capture_schema=1\nprofile=dynrec-fixed-20k\nguest_result=pending_strict_parse\nhuman_audio_quality=pending\n' >"$wrong_decoder_capture/report.txt"
sed 's/^observed_current_decoders=DynRec$/observed_current_decoders=DynRec,Normal/' \
  "$capture/telemetry-summary.txt" >"$wrong_decoder_capture/telemetry-summary.txt"
"$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$wrong_decoder_capture" \
  --guest-result "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" \
  --audio pass --input pass --gdi pass --shutdown clean >/dev/null
grep -q '^short_run_quality=fail$' "$wrong_decoder_capture/final-report.txt"
rm -rf -- "$wrong_decoder_capture"

slow_guest_capture="$(mktemp -d)"
slow_guest="$(mktemp)"
printf 'capture_schema=1\nprofile=dynrec-fixed-20k\nguest_result=pending_strict_parse\nhuman_audio_quality=pending\n' >"$slow_guest_capture/report.txt"
cp "$capture/telemetry-summary.txt" "$slow_guest_capture/telemetry-summary.txt"
sed 's/elapsed_ms=10000 work=1000000 throughput=100/elapsed_ms=11000 work=1100000 throughput=100/' \
  "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" >"$slow_guest"
"$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$slow_guest_capture" \
  --guest-result "$slow_guest" --audio pass --input pass --gdi pass --shutdown clean >/dev/null
grep -q '^short_run_quality=fail$' "$slow_guest_capture/final-report.txt"
rm -rf -- "$slow_guest_capture"
rm -f -- "$slow_guest"

not_tested_capture="$(mktemp -d)"
printf 'capture_schema=1\nprofile=dynrec-fixed-20k\nguest_result=pending_strict_parse\nhuman_audio_quality=pending\n' >"$not_tested_capture/report.txt"
cp "$capture/telemetry-summary.txt" "$not_tested_capture/telemetry-summary.txt"
"$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$not_tested_capture" \
  --guest-result "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" \
  --audio not_tested --input not_tested --gdi pass --shutdown clean >/dev/null
grep -q '^human_audio_quality=not_tested$' "$not_tested_capture/final-report.txt"
grep -q '^physical_input=not_tested$' "$not_tested_capture/final-report.txt"
grep -q '^short_run_quality=fail$' "$not_tested_capture/final-report.txt"
rm -rf -- "$not_tested_capture"
if "$repo_dir/scripts/finalize-sm-t500-benchmark.sh" --capture-dir "$capture" \
  --guest-result "$repo_dir/tests/realtime/fixtures/benchmark-valid.txt" \
  --audio pass --input pass --gdi pass --shutdown clean >/dev/null 2>&1; then
  echo "Finalizer overwrote an existing report" >&2
  exit 1
fi

reports="$(mktemp -d)"
for index in $(seq 1 6); do
  profile=dynrec-fixed-20k
  (( index > 3 )) && profile=normal-fixed-20k
  cpu=$((100 + index))
  memory=$((50 + index))
  gdi=$((60 + index))
  fps=$(awk -v i="$index" 'BEGIN { printf "%.2f", 20 + i }')
  {
    printf 'profile=%s\ninstalled_apk_sha256=abcdef\ncomplete=1\n' "$profile"
    printf 'cpu_throughput=%s\nmemory_throughput=%s\ngdi_throughput=%s\ngdi_fps=9\npresented_fps=%s\n' "$cpu" "$memory" "$gdi" "$fps"
    printf 'short_run_quality=pass\n'
  } >"$reports/$index.txt"
done
comparison="$($repo_dir/scripts/compare-sm-t500-benchmarks.sh \
  "$reports/1.txt" "$reports/2.txt" "$reports/3.txt" \
  "$reports/4.txt" "$reports/5.txt" "$reports/6.txt")"
grep -q '^cpu_throughput_dynrec_median=102$' <<<"$comparison"
grep -q '^cpu_throughput_normal_median=105$' <<<"$comparison"
grep -q '^promotion_eligible=0$' <<<"$comparison"
printf 'telemetry_schema=4\npresentation_requested=1\n' >>"$reports/1.txt"
if "$repo_dir/scripts/compare-sm-t500-benchmarks.sh" "$reports/1.txt" "$reports/2.txt" "$reports/3.txt" \
    "$reports/4.txt" "$reports/5.txt" "$reports/6.txt" >/dev/null 2>&1; then
  echo "Mixed presentation policies accepted" >&2; exit 1
fi
rm -rf -- "$reports"
echo "Benchmark telemetry checks passed"

sed 's/schema=3/schema=4/; s/$/ presentation_requested=1 presentation_active=1 presentation_interval_max_us=34000 upload_draw_us=5000 swap_us=20000 presenter_cpu_us=6000 graphics_errors=0 graphics_fallbacks=0 presenter_clock_errors=0/' "$valid" >"$invalid"
summary=$("$repo_dir/scripts/summarize-benchmark-telemetry.sh" dynrec-fixed-20k "$invalid")
grep -q '^telemetry_schema=4$' <<<"$summary"
grep -q '^presentation_requested=1$' <<<"$summary"
sed -i 's/ graphics_errors=0//' "$invalid"
if "$repo_dir/scripts/summarize-benchmark-telemetry.sh" dynrec-fixed-20k "$invalid" >/dev/null 2>&1; then
  echo "Incomplete graphics telemetry accepted" >&2; exit 1
fi
echo "Schema-4 graphics telemetry checks passed"
