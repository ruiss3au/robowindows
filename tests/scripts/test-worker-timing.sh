#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
base='I RoboWindowsTelemetry: schema=4 interval_ms=1000 run=70'
record='I RoboWindowsWorker: schema=1 interval_ms=1000 worker_slices=71 worker_wall_total_us=100 worker_wall_max_us=30 worker_cpu_total_us=50 worker_cpu_max_us=20 worker_clock_errors=0 wait_calls=70 wait_total_us=90 wait_max_us=20 mix_calls=70 mix_total_us=30 mix_max_us=10 frontend_clock_errors=0 translations=4 fallback_special=1 fallback_invalidated=2 fallback_opcode=3 fallback_smc=4 fallback_trap=5 discarded_slices=0'
printf '%s\n%s\n%s\n%s\n' "$base" "$record" "$base" "$record" >"$work_dir/valid"
summary=$(bash "$repo_dir/scripts/summarize-worker-timing.sh" "$work_dir/valid")
rg -q '^core_worker_slices=142$' <<<"$summary"
rg -q '^core_worker_wall_max_us=30$' <<<"$summary"
rg -q '^core_fallback_smc=8$' <<<"$summary"
printf '%s\n' "$base" >"$work_dir/legacy"
bash "$repo_dir/scripts/summarize-worker-timing.sh" "$work_dir/legacy" | rg -q '^core_timing=unavailable$'
for expr in 's/schema=1/schema=2/' 's/translations=4/translations=-1/' \
 's/mix_calls=70/mix_calls=oops/' 's/worker_wall_max_us=30/worker_wall_max_us=101/' \
 's/worker_cpu_max_us=20/worker_cpu_max_us=51/' 's/worker_slices=71/worker_slices=0/' \
 's/worker_clock_errors=0/worker_clock_errors=1/' 's/frontend_clock_errors=0/frontend_clock_errors=1/' \
 's/ mix_calls=70//' 's/mix_calls=70/mix_calls=70 unknown=1/' \
 's/mix_calls=70/mix_calls=70 mix_calls=70/' '4d' \
 '/RoboWindowsWorker:/s/interval_ms=1000/interval_ms=999/'; do
 sed "$expr" "$work_dir/valid" >"$work_dir/invalid"
 if bash "$repo_dir/scripts/summarize-worker-timing.sh" "$work_dir/invalid" >/dev/null 2>&1; then
  echo "Accepted invalid worker diagnostic: $expr" >&2; exit 1
 fi
done
echo 'Worker timing parser checks passed'
