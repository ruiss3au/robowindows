#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
result='I RoboWindowsCacheFixture: PASS correctness core=dynamic presentation=1 workload=cache-cold host_ms=2300 decoder=DynRec calls=4096 checksum=3540426752 setup_ticks=20 warmup_ticks=0 measured_ticks=1200 total_ticks=1220 pit_divisor=1193'
telemetry='I RoboWindowsTelemetry: schema=4 interval_ms=1000 run=70 presented=30 decoder=DynRec current=DynRec state=foreground timing=balanced_100ms presentation_requested=1 presentation_active=1 underruns=2 missing=20 dropped=0 saturated=0 stream_errors=0 resync=1 graphics_errors=0 graphics_fallbacks=0 post_failures=0 presenter_clock_errors=0'
printf '%s\n%s\n' "$telemetry" "$result" >"$work_dir/valid"
summary=$(bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/valid")
rg -q '^correctness=pass$' <<<"$summary"
rg -q '^quality=not_assessed$' <<<"$summary"
rg -q '^underruns=2$' <<<"$summary"
rg -q '^timing_diagnostics=unavailable$' <<<"$summary"
printf '%s\n' "$result" >"$work_dir/short"
short=$(bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/short")
for expected in complete_intervals=0 terminal_coverage=unavailable whole_session_presented_fps=unavailable underruns=unavailable; do
  rg -qx "$expected" <<<"$short"
done
for expr in 's/cache-cold/cache-warm/' 's/host_ms=2300/host_ms=60001/' \
  's/checksum=3540426752/checksum=1/' 's/total_ticks=1220/total_ticks=1200/' \
  's/total_ticks=1220/total_ticks=40000/' 's/measured_ticks=1200/measured_ticks=0/' \
  's/setup_ticks=20/setup_ticks=-1/' 's/calls=4096/calls=4095/' \
  's/PASS correctness/FAIL assertion/' 's/pit_divisor=1193/pit_divisor=1194/' \
  's/decoder=DynRec/decoder=Normal/' 's/calls=4096/calls=4096 extra=1/' \
  's/calls=4096/calls=4096 calls=4096/' 's/resync=1/resync=oops/' \
  's/state=foreground/state=paused/' 's/ missing=20//' '2d' '2p'; do
  sed "$expr" "$work_dir/valid" >"$work_dir/bad"
  if bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/bad" >/dev/null 2>&1; then
    echo "Accepted invalid cache summary: $expr" >&2; exit 1
  fi
done
echo 'Cache summary identity, failure, partial-coverage and non-promotion checks passed'
