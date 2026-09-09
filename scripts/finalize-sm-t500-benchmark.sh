#!/usr/bin/env bash
set -euo pipefail

capture_dir=
guest_result=
audio=
input=
gdi=
shutdown=
usage() {
  echo "Usage: scripts/finalize-sm-t500-benchmark.sh --capture-dir DIR --guest-result FILE --audio pass|fail --input pass|fail --gdi pass|fail --shutdown clean|unclean" >&2
}
while [[ $# -gt 0 ]]; do
  case "$1" in
    --capture-dir) capture_dir=${2:-}; shift 2 ;;
    --guest-result) guest_result=${2:-}; shift 2 ;;
    --audio) audio=${2:-}; shift 2 ;;
    --input) input=${2:-}; shift 2 ;;
    --gdi) gdi=${2:-}; shift 2 ;;
    --shutdown) shutdown=${2:-}; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done
[[ -d $capture_dir && -f $capture_dir/report.txt && -f $guest_result ]] || {
  usage; exit 2
}
[[ $audio == pass || $audio == fail ]] || { usage; exit 2; }
[[ $input == pass || $input == fail ]] || { usage; exit 2; }
[[ $gdi == pass || $gdi == fail ]] || { usage; exit 2; }
[[ $shutdown == clean || $shutdown == unclean ]] || { usage; exit 2; }
[[ ! -e $capture_dir/final-report.txt ]] || {
  echo "Final report already exists; refusing to overwrite" >&2
  exit 1
}

guest="$($(dirname "$0")/parse-win98-benchmark.sh "$guest_result")"
gdi_rate="$(sed -n 's/^gdi_throughput=//p' <<<"$guest")"
gdi_mpix="$(awk -v rects="$gdi_rate" 'BEGIN { printf "%.3f", rects * 0.004096 }')"
short_quality=pass
[[ $audio == pass && $input == pass && $gdi == pass && $shutdown == clean ]] || short_quality=fail
grep -q '^audio_underruns=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
grep -q '^audio_stream_errors=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
grep -q '^surface_post_failures=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail

sed '/^guest_result=/,$d' "$capture_dir/report.txt" >"$capture_dir/final-report.txt"
{
  printf '%s\n' "$guest"
  printf 'gdi_megapixels_per_s=%s\n' "$gdi_mpix"
  printf 'human_audio_quality=%s\nphysical_input=%s\ngdi_responsiveness=%s\nclean_shutdown=%s\n' \
    "$audio" "$input" "$gdi" "$shutdown"
  printf 'short_run_quality=%s\n' "$short_quality"
  printf 'ten_cycle_gate=pending\nthirty_minute_thermal_gate=pending\naoe2_gate=pending\n'
} >>"$capture_dir/final-report.txt"
echo "Finalized benchmark report: $capture_dir/final-report.txt"
