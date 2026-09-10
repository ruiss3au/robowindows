#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT
base='09-09 I RoboWindowsTelemetry: schema=3 interval_ms=1000 run=70'
record='09-09 I RoboWindowsTiming: schema=1 interval_ms=1000 calls=70 wall_total_us=10000 process_cpu_total_us=20000 process_cpu_max_us=500 cpu_clock_errors=0 host_gap_max_us=15000 wake_late_max_us=2000 video_total_us=100 video_max_us=5 audio_total_us=30 audio_max_us=2'
printf '%s\n%s\n%s\n%s\n' "$base" "$record" "$base" "$record" >"$work_dir/valid"
summary="$(bash "$repo_dir/scripts/summarize-runtime-timing.sh" "$work_dir/valid")"
grep -q '^timing_calls=140$' <<<"$summary"
grep -q '^timing_process_cpu_total_us=40000$' <<<"$summary"
grep -q '^timing_host_gap_max_us=15000$' <<<"$summary"
grep -q '^timing_video_total_us=200$' <<<"$summary"
printf '%s\n' "$base" >"$work_dir/legacy"
legacy_summary=$(bash "$repo_dir/scripts/summarize-runtime-timing.sh" "$work_dir/legacy")
grep -q '^timing_diagnostics=unavailable$' <<<"$legacy_summary"
for expression in 's/calls=70/calls=-1/' 's/calls=70/calls=71/' \
    's/wall_total_us=10000/wall_total_us=bad/' 's/schema=1/schema=9/' \
    's/video_max_us=5/video_max_us=101/' 's/cpu_clock_errors=0/cpu_clock_errors=71/' \
    's/process_cpu_max_us=500/process_cpu_max_us=20001/' \
    's/ audio_max_us=2//' 's/audio_max_us=2/audio_max_us=2 extra=7/' \
    's/audio_max_us=2/audio_max_us=2 audio_max_us=2/' \
    '/RoboWindowsTiming:/s/interval_ms=1000/interval_ms=999/' \
    '4d'; do
  sed "$expression" "$work_dir/valid" >"$work_dir/invalid"
  if bash "$repo_dir/scripts/summarize-runtime-timing.sh" "$work_dir/invalid" >/dev/null 2>&1; then
    echo "Timing parser accepted malformed extension: $expression" >&2
    exit 1
  fi
done
echo "Runtime timing parser checks passed"
