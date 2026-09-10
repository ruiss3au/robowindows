#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
bash "$repo_dir/scripts/build-presentation-fixture.sh" "$work_dir/fixture.bin" >/dev/null
test "$(wc -c <"$work_dir/fixture.bin")" -eq 1474560
bash "$repo_dir/scripts/build-presentation-fixture.sh" "$work_dir/stress.bin" stress >/dev/null
if cmp -s "$work_dir/fixture.bin" "$work_dir/stress.bin"; then exit 1; fi
for iteration in {1..120}; do
  printf 'RoboWindowsTelemetry: schema=4 interval_ms=1000 state=foreground audio_state=playing decoder=Normal timing=balanced_100ms presentation_requested=1 presentation_active=1 presented=30 presenter_cpu_us=60000 underruns=0 missing=0 dropped=0 resync=0 stream_errors=0 post_failures=0 graphics_errors=0 graphics_fallbacks=0 presenter_clock_errors=0 audio_produced=48000 audio_consumed=48000\n'
done >"$work_dir/valid.log"
printf 'RoboWindowsPresentation: PASS timer core=normal presentation=1 host_ms=120000 guest_ms=120014\n' >>"$work_dir/valid.log"
summary=$(bash "$repo_dir/scripts/summarize-presentation.sh" normal 1 "$work_dir/valid.log")
grep -q 'quality=pass' <<<"$summary"
for mutation in 's/graphics_fallbacks=0/graphics_fallbacks=1/' 's/presented=30/presented=15/' \
  's/ missing=0//' 's/audio_state=playing/audio_state=prebuffering/' 's/PASS timer/FAIL timer/' \
  's/decoder=Normal/decoder=DynRec/'; do
  sed "$mutation" "$work_dir/valid.log" >"$work_dir/invalid.log"
  if bash "$repo_dir/scripts/summarize-presentation.sh" normal 1 "$work_dir/invalid.log" >/dev/null 2>&1; then
    echo "Invalid presentation capture accepted" >&2; exit 1
  fi
done
sed 's/PASS timer/PASS timer workload=stress phase_mask=15/' "$work_dir/valid.log" >"$work_dir/stress.log"
summary=$(bash "$repo_dir/scripts/summarize-presentation.sh" normal 1 "$work_dir/stress.log" stress)
grep -q 'quality=pass' <<<"$summary"
if bash "$repo_dir/scripts/summarize-presentation.sh" normal 1 "$work_dir/stress.log" >/dev/null 2>&1; then exit 1; fi
if bash "$repo_dir/scripts/summarize-presentation.sh" normal 1 "$work_dir/valid.log" stress >/dev/null 2>&1; then exit 1; fi
for mutation in 's/phase_mask=15/phase_mask=7/' 's/workload=stress/workload=unknown/' \
  's/core=normal/core=dynamic/' 's/ phase_mask=15//'; do
  sed "$mutation" "$work_dir/stress.log" >"$work_dir/invalid.log"
  if bash "$repo_dir/scripts/summarize-presentation.sh" normal 1 "$work_dir/invalid.log" stress >/dev/null 2>&1; then
    echo "Invalid stress identity accepted" >&2; exit 1
  fi
done
echo "Presentation fixture build and strict capture checks passed"
