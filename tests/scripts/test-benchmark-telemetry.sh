#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
valid="$(mktemp)"
invalid="$(mktemp)"
capture="$(mktemp -d)"
trap 'rm -f -- "$valid" "$invalid"; rm -rf -- "$capture"' EXIT

for index in $(seq 1 30); do
  printf '09-09 I RoboWindowsTelemetry: schema=2 interval_ms=1000 state=foreground audio_state=playing decoder=DynRec current=DynRec run=60 audio_produced=48000 audio_consumed=48000 queue_min=100 queue_max=500 underruns=0 missing=0 dropped=0 saturated=0 stream_errors=0 submitted=60 published=30 presented=30 coalesced=30 post_failures=0\n' >>"$valid"
done
summary="$($repo_dir/scripts/summarize-benchmark-telemetry.sh dynrec-fixed-20k "$valid")"
grep -q '^cycle_value=20000$' <<<"$summary"
grep -q '^presented_fps=30.00$' <<<"$summary"
grep -q '^audio_produced_hz=48000.00$' <<<"$summary"
grep -q '^lifecycle_valid=1$' <<<"$summary"

cp "$valid" "$invalid"
printf '09-09 I RoboWindowsTelemetry: schema=2 interval_ms=1000 state=paused audio_state=suspended decoder=DynRec current=DynRec run=0 audio_produced=0 audio_consumed=0 queue_min=0 queue_max=0 underruns=0 missing=0 dropped=0 saturated=0 stream_errors=0 submitted=0 published=0 presented=0 coalesced=0 post_failures=0\n' >>"$invalid"
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
rm -rf -- "$reports"
echo "Benchmark telemetry checks passed"
