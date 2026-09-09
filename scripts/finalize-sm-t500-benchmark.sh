#!/usr/bin/env bash
set -euo pipefail

capture_dir=
guest_result=
audio=
input=
gdi=
shutdown=
usage() {
  echo "Usage: scripts/finalize-sm-t500-benchmark.sh --capture-dir DIR --guest-result FILE --audio pass|fail|not_tested --input pass|fail|not_tested --gdi pass|fail|not_tested --shutdown clean|unclean" >&2
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
[[ $audio == pass || $audio == fail || $audio == not_tested ]] || { usage; exit 2; }
[[ $input == pass || $input == fail || $input == not_tested ]] || { usage; exit 2; }
[[ $gdi == pass || $gdi == fail || $gdi == not_tested ]] || { usage; exit 2; }
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
profile="$(sed -n 's/^profile=//p' "$capture_dir/telemetry-summary.txt")"
configured_core="$(sed -n 's/^configured_core=//p' "$capture_dir/telemetry-summary.txt")"
observed_decoders="$(sed -n 's/^observed_current_decoders=//p' "$capture_dir/telemetry-summary.txt")"
presented_fps="$(sed -n 's/^presented_fps=//p' "$capture_dir/telemetry-summary.txt")"
case "$profile" in
  dynrec-*)
    [[ $configured_core == DynRec && ,$observed_decoders, == *,DynRec,* &&
       ,$observed_decoders, != *,Normal,* ]] || short_quality=fail
    ;;
  normal-*)
    [[ $configured_core == Normal && ,$observed_decoders, == *,Normal,* &&
       ,$observed_decoders, != *,DynRec,* ]] || short_quality=fail
    ;;
  *) short_quality=fail ;;
esac
awk -v fps="$presented_fps" 'BEGIN { exit !(fps ~ /^[0-9]+([.][0-9]+)?$/ && fps >= 15) }' || \
  short_quality=fail
for phase in cpu memory gdi; do
  elapsed="$(sed -n "s/^${phase}_elapsed_ms=//p" <<<"$guest")"
  (( elapsed >= 9500 && elapsed <= 10500 )) || short_quality=fail
done
grep -q '^lifecycle_valid=1$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
grep -q '^audio_underruns=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
grep -q '^audio_missing_frames=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
grep -q '^audio_dropped_frames=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
grep -q '^scheduler_deadline_resyncs=0$' "$capture_dir/telemetry-summary.txt" || short_quality=fail
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
