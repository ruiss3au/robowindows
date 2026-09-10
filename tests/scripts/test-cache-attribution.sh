#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
telemetry='I RoboWindowsTelemetry: schema=4 interval_ms=1000 state=foreground audio_state=playing timing=balanced_100ms presenter_clock_errors=0'
worker='I RoboWindowsWorker: schema=1 interval_ms=1000 worker_slices=1 translations=512'
timing='I RoboWindowsTiming: schema=1 interval_ms=1000 cpu_clock_errors=0'
cache='I RoboWindowsCache: schema=1 interval_ms=1000 sample_stride=64 sample_cap=4 publication_cap=4 lookup_hits=10 lookup_misses=512 translation_attempts=512 nested_attempts=0 stride_skipped=189 cap_skipped=319 samples_started=4 samples_completed=4 samples_discarded=0 translation_wall_sample_us=100 translation_wall_sample_max_us=40 translation_cpu_sample_us=80 translation_cpu_sample_max_us=30 publication_scopes=16 publication_skipped=1 publication_wall_sample_us=20 publication_wall_sample_max_us=5 publication_cpu_sample_us=10 publication_cpu_sample_max_us=3 invalidate_calls=4 clear_calls=9 clear_code_write=3 clear_cache_reclaim=2 clear_code_size=1 clear_page_pressure=1 clear_page_release=1 clear_other=1 clock_errors=0 scope_errors=0'
printf '%s\n' "$telemetry" "$worker" "$timing" "$cache" > "$work_dir/periodic"
header='I RoboWindowsTerminal: schema=2 interval_us=1000999 periodic_records=0 reason=guest_shutdown clock_errors=0'
printf '%s\n' "$header" > "$work_dir/tail"
sed -e 's/RoboWindows/RoboWindowsTerminal/' -e 's/state=foreground/state=stopping/' \
 -e 's/audio_state=playing/audio_state=stopped/' "$work_dir/periodic" >> "$work_dir/tail"
bash "$repo_dir/scripts/normalize-session-timing.sh" "$work_dir/tail" > "$work_dir/normalized"
summary=$(bash "$repo_dir/scripts/summarize-cache-attribution.sh" "$work_dir/normalized")
for expected in translation_attribution=sampled cache_intervals=1 cache_samples_completed=4 \
 cache_translation_attempts=512 cache_clear_code_write=3 cache_translation_cpu_sample_us=80; do
 rg -qx "$expected" <<< "$summary"
done
cp "$work_dir/periodic" "$work_dir/mixed"
sed 's/periodic_records=0/periodic_records=1/' "$work_dir/tail" >> "$work_dir/mixed"
bash "$repo_dir/scripts/normalize-session-timing.sh" "$work_dir/mixed" > "$work_dir/normalized"
bash "$repo_dir/scripts/summarize-cache-attribution.sh" "$work_dir/normalized" | rg -qx 'cache_samples_completed=8'
for expr in '1d' '2d' '3d' '4d' '5d' '1p' '2p' '3p' '4p' '5p' \
 's/schema=2/schema=1/' 's/sample_cap=4/sample_cap=5/' \
 's/publication_cap=4/publication_cap=5/' 's/sample_stride=64/sample_stride=63/' \
 's/translation_attempts=512/translation_attempts=511/' \
 's/samples_started=4/samples_started=5/' 's/samples_completed=4/samples_completed=0/' \
 's/cap_skipped=319/cap_skipped=318/' 's/nested_attempts=0/nested_attempts=1/' \
 's/clear_calls=9/clear_calls=8/' 's/worker_slices=1/worker_slices=0/' \
 's/translations=512/translations=511/' 's/publication_scopes=16/publication_scopes=17/' \
 's/translation_wall_sample_max_us=40/translation_wall_sample_max_us=101/' \
 's/publication_cpu_sample_us=10/publication_cpu_sample_us=81/' \
 's/clock_errors=0/clock_errors=1/' 's/scope_errors=0/scope_errors=1/' \
 's/scope_errors=0/scope_errors=0 extra=0/' 's/scope_errors=0/scope_errors=0 scope_errors=0/' \
 's/scope_errors=0/scope_errors=-1/' 's/TerminalCache:/TerminalUnknown:/'; do
 sed "$expr" "$work_dir/tail" > "$work_dir/bad"
 if bash "$repo_dir/scripts/normalize-session-timing.sh" "$work_dir/bad" > "$work_dir/normalized" 2>/dev/null &&
    bash "$repo_dir/scripts/summarize-cache-attribution.sh" "$work_dir/normalized" >/dev/null 2>&1; then
  echo "Accepted invalid cache capture: $expr" >&2; exit 1
 fi
done
for expr in '4d' '9d' '1,4d' 's/periodic_records=1/periodic_records=0/'; do
 sed "$expr" "$work_dir/mixed" > "$work_dir/bad"
 if bash "$repo_dir/scripts/normalize-session-timing.sh" "$work_dir/bad" > "$work_dir/normalized" 2>/dev/null; then
  echo 'Accepted mixed cache coverage' >&2; exit 1
 fi
done
sed -e '5d' -e 's/schema=2/schema=1/' "$work_dir/tail" > "$work_dir/old"
bash "$repo_dir/scripts/normalize-session-timing.sh" "$work_dir/old" > "$work_dir/normalized"
bash "$repo_dir/scripts/summarize-cache-attribution.sh" "$work_dir/normalized" | rg -qx 'translation_attribution=unavailable'
# A real zero-event Normal extension still has coverage, but no timing sample.
sed -E 's/([a-z_]+)=([0-9]+)/\1=0/g; s/schema=0/schema=1/; s/interval_ms=0/interval_ms=1000/; s/sample_stride=0/sample_stride=64/; s/sample_cap=0/sample_cap=4/; s/publication_cap=0/publication_cap=4/' \
 "$work_dir/periodic" > "$work_dir/zero"
bash "$repo_dir/scripts/summarize-cache-attribution.sh" "$work_dir/zero" | rg -qx 'translation_sample_timing=unavailable'
echo 'Cache schema/coverage/caps/reasons/partial and historical parser checks passed'
for mode in 0 1 2 3; do
 clocks=0; samples=0; stride=0; cap=0; events=0
 if [[ $mode == 2 ]]; then clocks=160000; samples=8000; stride=378000; cap=638000; fi
 if [[ $mode -ge 2 ]]; then events=3072000; fi
 printf 'I RoboWindowsCacheCalibration: schema=1 mode=%d iterations=2000 attempts_per_slice=512 publication_per_sample=4 mean_ns=100 cache_clock_reads=%d samples_completed=%d stride_skipped=%d cap_skipped=%d cache_events=%d clock_errors=0 accounting_errors=0\n' \
  "$mode" "$clocks" "$samples" "$stride" "$cap" "$events" >> "$work_dir/calibration"
done
bash "$repo_dir/scripts/summarize-cache-calibration.sh" "$work_dir/calibration" | rg -qx 'cache_calibration=pass'
for expr in '1d' '2d' '3d' '4d' '1p' '3p' 's/mode=2/mode=3/' \
 's/mean_ns=100/mean_ns=0/' 's/mean_ns=100/mean_ns=142680/' \
 's/cache_clock_reads=160000/cache_clock_reads=159999/' \
 's/cache_events=3072000/cache_events=0/' 's/clock_errors=0/clock_errors=1/' \
 's/accounting_errors=0/accounting_errors=1/' 's/samples_completed=8000/samples_completed=7999/' \
 's/schema=1/schema=2/' 's/schema=1/schema=1 unknown=0/' 's/schema=1/schema=1 schema=1/'; do
 sed "$expr" "$work_dir/calibration" > "$work_dir/bad"
 if bash "$repo_dir/scripts/summarize-cache-calibration.sh" "$work_dir/bad" >/dev/null 2>&1; then
  echo "Accepted invalid calibration: $expr" >&2; exit 1
 fi
done
echo 'Calibration modes/accounting/overhead/negative checks passed'
