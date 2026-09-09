#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 && -f $1 ]] || { echo "Usage: scripts/summarize-runtime-timing.sh LOG" >&2; exit 2; }
awk '
function fail(message) { print message > "/dev/stderr"; bad=1 }
index($0, "RoboWindowsTelemetry:") {
  ++intervals
  for (i=1; i<=NF; ++i) {
    split($i, pair, "=")
    if (pair[1] == "interval_ms") expected_ms[intervals]=pair[2]
    if (pair[1] == "run") expected_calls[intervals]=pair[2]
  }
}
index($0, "RoboWindowsTiming:") {
  ++records
  line=$0; sub(/^.*RoboWindowsTiming:[[:space:]]*/, "", line)
  delete value
  count=split(line, tokens, /[[:space:]]+/)
  for (i=1; i<=count; ++i) {
    if (split(tokens[i], pair, "=") != 2 || pair[2] !~ /^[0-9]+$/ || pair[1] in value) {
      fail("Malformed timing diagnostic field"); next
    }
    value[pair[1]]=pair[2]
  }
  required="schema interval_ms calls wall_total_us process_cpu_total_us process_cpu_max_us cpu_clock_errors host_gap_max_us wake_late_max_us video_total_us video_max_us audio_total_us audio_max_us"
  n=split(required, names, " ")
  if (length(value) != n) fail("Incomplete or unknown timing diagnostic fields")
  for (i=1; i<=n; ++i) if (!(names[i] in value)) fail("Missing timing diagnostic field")
  if (value["schema"] != 1) fail("Unsupported timing diagnostic schema")
  if (records != intervals || value["interval_ms"] != expected_ms[records] ||
      value["calls"] > expected_calls[records]) fail("Unmatched timing diagnostic interval")
  if (value["process_cpu_max_us"] > value["process_cpu_total_us"] ||
      value["video_max_us"] > value["video_total_us"] ||
      value["audio_max_us"] > value["audio_total_us"] ||
      value["cpu_clock_errors"] > value["calls"]) fail("Inconsistent timing diagnostic totals")
  for (i=1; i<=n; ++i) {
    key=names[i]
    if (key ~ /_max_us$/) { if (value[key] > maximum[key]) maximum[key]=value[key] }
    else total[key]+=value[key]
  }
}
END {
  if (records && records != intervals) fail("Missing timing diagnostic interval")
  if (bad) exit 1
  if (!records) { print "timing_diagnostics=unavailable"; exit }
  print "timing_diagnostics=1"
  printf "timing_intervals=%d\ntiming_calls=%.0f\n", records, total["calls"]
  fields="wall_total_us process_cpu_total_us process_cpu_max_us cpu_clock_errors host_gap_max_us wake_late_max_us video_total_us video_max_us audio_total_us audio_max_us"
  n=split(fields, names, " ")
  for (i=1; i<=n; ++i) {
    key=names[i]; printf "timing_%s=%.0f\n", key, (key ~ /_max_us$/ ? maximum[key] : total[key])
  }
}' "$1"
