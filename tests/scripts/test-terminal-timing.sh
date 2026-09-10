#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
header='I RoboWindowsTerminal: schema=1 interval_us=700123 periodic_records=0 reason=guest_shutdown clock_errors=0'
telemetry='I RoboWindowsTerminalTelemetry: schema=4 interval_ms=700 run=40 presented=20 decoder=DynRec current=DynRec state=stopping audio_state=stopped timing=balanced_100ms presentation_requested=1 presentation_active=1 underruns=2 missing=20 dropped=0 saturated=0 stream_errors=0 resync=1 graphics_errors=0 graphics_fallbacks=0 post_failures=0 presenter_clock_errors=0'
worker='I RoboWindowsTerminalWorker: schema=1 interval_ms=700 worker_slices=41 worker_wall_total_us=600000 worker_wall_max_us=30000 worker_cpu_total_us=500000 worker_cpu_max_us=20000 worker_clock_errors=0 wait_calls=42 wait_total_us=90000 wait_max_us=20000 mix_calls=40 mix_total_us=3000 mix_max_us=100 frontend_clock_errors=0 translations=4 fallback_special=1 fallback_invalidated=2 fallback_opcode=3 fallback_smc=4 fallback_trap=5 discarded_slices=0'
timing='I RoboWindowsTerminalTiming: schema=1 interval_ms=700 calls=40 wall_total_us=100000 process_cpu_total_us=120000 process_cpu_max_us=20000 cpu_clock_errors=0 host_gap_max_us=15000 wake_late_max_us=100 video_total_us=1000 video_max_us=100 audio_total_us=500 audio_max_us=100'
result='I RoboWindowsCacheFixture: PASS correctness core=dynamic presentation=1 workload=cache-cold host_ms=800 decoder=DynRec calls=4096 checksum=3540426752 setup_ticks=20 warmup_ticks=0 measured_ticks=500 total_ticks=520 pit_divisor=1193'
printf '%s\n' "$header" "$telemetry" "$worker" "$timing" "$result" >"$work_dir/terminal"
summary=$(bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/terminal")
for expected in terminal_records=1 terminal_coverage=available complete_intervals=0 \
  telemetry_elapsed_ms=700.123 timing_calls=40 core_worker_slices=41 underruns=2 quality=not_assessed; do
  rg -qx "$expected" <<<"$summary"
done
# Unchanged ordinary consumers do not mistake shutdown for a settled interval.
bash "$repo_dir/scripts/summarize-runtime-timing.sh" "$work_dir/terminal" | rg -qx 'core_timing=unavailable'
printf '%s\n' "$telemetry" "$worker" "$timing" | \
  sed -e 's/RoboWindowsTerminal/RoboWindows/g' -e 's/interval_ms=700/interval_ms=1000/g' \
      -e 's/state=stopping/state=foreground/' -e 's/audio_state=stopped/audio_state=playing/' >"$work_dir/mixed"
sed 's/periodic_records=0/periodic_records=1/' "$work_dir/terminal" >>"$work_dir/mixed"
summary=$(bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/mixed")
for expected in complete_intervals=1 terminal_records=1 timing_calls=80 core_worker_slices=82 \
  core_worker_wall_max_us=30000 telemetry_elapsed_ms=1700.123; do rg -qx "$expected" <<<"$summary"; done
for us in 0 999; do
  sed -e "s/interval_us=700123/interval_us=$us/" -e 's/interval_ms=700/interval_ms=0/g' \
    "$work_dir/terminal" >"$work_dir/subms"
  bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/subms" >/dev/null
done
for expr in '1d' '2d' '3d' '4d' '1p' '2p' '3p' '4p' \
  's/periodic_records=0/periodic_records=1/' 's/interval_us=700123/interval_us=701000/' \
  's/clock_errors=0/clock_errors=1/' 's/schema=1/schema=2/' 's/interval_us=700123/interval_us=-1/' \
  's/reason=guest_shutdown/reason=unknown/' 's/reason=guest_shutdown/reason=host_stop/' \
  's/periodic_records=0/periodic_records=0 extra=1/' \
  's/periodic_records=0/periodic_records=0 periodic_records=0/' \
  's/state=stopping/state=foreground/' 's/audio_state=stopped/audio_state=playing/' \
  's/TerminalWorker/TerminalTiming/' 's/TerminalWorker/TerminalUnknown/' \
  's/worker_slices=41/worker_slices=0/' 's/process_cpu_total_us=120000/process_cpu_total_us=1/' \
  's/cpu_clock_errors=0/cpu_clock_errors=1/' 's/presenter_clock_errors=0/presenter_clock_errors=1/' \
  's/worker_wall_max_us=30000/worker_wall_max_us=900000/'; do
  sed "$expr" "$work_dir/terminal" >"$work_dir/bad"
  if bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/bad" >/dev/null 2>&1; then
    echo "Accepted invalid terminal timing: $expr" >&2; exit 1
  fi
done
# Missing complete ordinary groups, ordinary output after shutdown, and swapped records.
sed '1,3d' "$work_dir/mixed" >"$work_dir/bad"
if bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/bad" >/dev/null 2>&1; then exit 1; fi
printf '%s\n' "$header" "$worker" "$telemetry" "$timing" "$result" >"$work_dir/bad"
if bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/bad" >/dev/null 2>&1; then exit 1; fi
cp "$work_dir/terminal" "$work_dir/bad"
printf '%s\n' 'I RoboWindowsTelemetry: schema=4 interval_ms=1000 run=0' >>"$work_dir/bad"
if bash "$repo_dir/scripts/summarize-cache-fixture.sh" dynamic 1 cold "$work_dir/bad" >/dev/null 2>&1; then exit 1; fi
echo 'Terminal-only/mixed/sub-ms, coverage, corruption and ordinary-consumer checks passed'
# Guard the production handoff itself, not only the fake-clock state machine.
awk '
/terminal_report.outputs_stopped\(\)/ { outputs=NR }
/retro_unload_game\(\)/ { unload=NR }
/terminal_report.worker_stopped\(\)/ { worker=NR }
/if \(terminal_report.claim\(\)\) report_telemetry_if_due\(true\)/ { report=NR }
/robowindows_core_timing_configure\(0\)/ { disable=NR }
END { if(!(outputs && outputs<unload && unload<worker && worker<report && report<disable)) exit 1 }
' "$repo_dir/android/src/main/cpp/core_host.cpp"
echo 'Production terminal drain-before-disable ordering passed'
