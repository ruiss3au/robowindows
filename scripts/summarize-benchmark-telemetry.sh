#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 || ! -f $2 ]]; then
  echo "Usage: scripts/summarize-benchmark-telemetry.sh PROFILE TELEMETRY.LOG" >&2
  exit 2
fi
profile=$1
log_file=$2
# Validate the optional extension before emitting any accepted summary.
timing_summary="$(bash "$(dirname "$0")/summarize-runtime-timing.sh" "$log_file")"
case "$profile" in
  dynrec-fixed-20k) core=DynRec; policy=fixed; cycles=20000 ;;
  normal-fixed-12k) core=Normal; policy=fixed; cycles=12000 ;;
  normal-fixed-14k) core=Normal; policy=fixed; cycles=14000 ;;
  normal-fixed-20k) core=Normal; policy=fixed; cycles=20000 ;;
  normal-fixed-30k) core=Normal; policy=fixed; cycles=30000 ;;
  *) echo "Unsupported benchmark profile: $profile" >&2; exit 2 ;;
esac

printf 'profile=%s\nconfigured_core=%s\ncycle_policy=%s\ncycle_value=%s\n' \
  "$profile" "$core" "$policy" "$cycles"

awk -v expected_core="$core" '
function fail(message) { print message > "/dev/stderr"; bad=1 }
function number(value) { return value ~ /^[0-9]+$/ }
index($0, "RoboWindowsTelemetry:") {
  line=$0
  sub(/^.*RoboWindowsTelemetry:[[:space:]]*/, "", line)
  delete value
  count=split(line, token, /[[:space:]]+/)
  for (i=1; i<=count; ++i) {
    split(token[i], pair, "=")
    if (length(pair[1]) && length(pair[2])) value[pair[1]]=pair[2]
  }
  required="schema interval_ms state audio_state decoder current timing pacing run retro_max_us retro_over producer_gap_max_us scheduler_late_max_us catchup resync audio_produced audio_consumed queue_current queue_min queue_max underruns missing dropped saturated stream_errors submitted published presented coalesced post_failures"
  split(required, names, " ")
  for (i in names) if (!(names[i] in value)) { fail("Missing telemetry field: " names[i]); next }
  if (value["schema"] != "3") { fail("Unsupported telemetry schema"); next }
  if (value["state"] != "foreground") { fail("Invalid lifecycle interval: " value["state"]); next }
  if (value["decoder"] != expected_core) { fail("Observed decoder does not match profile"); next }
  if (value["timing"] != "balanced_100ms") { fail("Unexpected runtime timing policy"); next }
  if (value["pacing"] != "faster_1pct" && value["pacing"] != "nominal" && value["pacing"] != "slower_1pct") { fail("Unknown pacing state"); next }
  numeric="interval_ms run retro_max_us retro_over producer_gap_max_us scheduler_late_max_us catchup resync audio_produced audio_consumed queue_current queue_min queue_max underruns missing dropped saturated stream_errors submitted published presented coalesced post_failures"
  split(numeric, names, " ")
  for (i in names) if (!number(value[names[i]])) { fail("Invalid numeric telemetry field"); next }
  ms += value["interval_ms"]
  runs += value["run"]
  if (value["retro_max_us"] > retro_max_us) retro_max_us=value["retro_max_us"]
  retro_over += value["retro_over"]
  if (value["producer_gap_max_us"] > producer_gap_max_us) producer_gap_max_us=value["producer_gap_max_us"]
  if (value["scheduler_late_max_us"] > scheduler_late_max_us) scheduler_late_max_us=value["scheduler_late_max_us"]
  catchup += value["catchup"]
  resync += value["resync"]
  produced += value["audio_produced"]
  consumed += value["audio_consumed"]
  underruns += value["underruns"]
  missing += value["missing"]
  dropped += value["dropped"]
  saturated += value["saturated"]
  stream_errors += value["stream_errors"]
  submitted += value["submitted"]
  published += value["published"]
  presented += value["presented"]
  coalesced += value["coalesced"]
  post_failures += value["post_failures"]
  queue_current=value["queue_current"]
  if (!queue_seen || value["queue_min"] < queue_min) queue_min=value["queue_min"]
  if (!queue_seen || value["queue_max"] > queue_max) queue_max=value["queue_max"]
  queue_seen=1
  interval_fps=value["presented"] * 1000 / value["interval_ms"]
  if (interval_fps > peak_fps) peak_fps=interval_fps
  intervals++
  observed_current[value["current"]]=1
}
END {
  if (bad) exit 1
  if (intervals < 25) { print "Too few foreground telemetry intervals" > "/dev/stderr"; exit 1 }
  if (ms < 25000 || ms > 60000) { print "Telemetry duration is outside benchmark bounds" > "/dev/stderr"; exit 1 }
  printf "telemetry_schema=3\nintervals=%u\nhost_elapsed_ms=%u\nruntime_timing_policy=balanced_100ms\n", intervals, ms
  printf "emulator_calls_per_s=%.2f\n", runs * 1000 / ms
  printf "retro_run_max_us=%u\nretro_run_over_budget_calls=%u\naudio_producer_gap_max_us=%u\nscheduler_lateness_max_us=%u\nscheduler_catchup_calls=%u\nscheduler_deadline_resyncs=%u\n", retro_max_us, retro_over, producer_gap_max_us, scheduler_late_max_us, catchup, resync
  printf "submitted_fps=%.2f\npublished_fps=%.2f\npresented_fps=%.2f\npresented_fps_peak=%.2f\n", submitted * 1000 / ms, published * 1000 / ms, presented * 1000 / ms, peak_fps
  printf "frames_coalesced=%u\nsurface_post_failures=%u\n", coalesced, post_failures
  printf "audio_produced_hz=%.2f\naudio_consumed_hz=%.2f\n", produced * 1000 / ms, consumed * 1000 / ms
  printf "audio_queue_frames_current=%u\naudio_queue_frames_min=%u\naudio_queue_frames_max=%u\n", queue_current, queue_min, queue_max
  printf "audio_underruns=%u\naudio_missing_frames=%u\naudio_dropped_frames=%u\naudio_saturated_samples=%u\naudio_stream_errors=%u\n", underruns, missing, dropped, saturated, stream_errors
  current=""
  for (name in observed_current) current=current (current ? "," : "") name
  printf "observed_current_decoders=%s\nlifecycle_valid=1\n", current
}
' "$log_file"
printf '%s\n' "$timing_summary"
