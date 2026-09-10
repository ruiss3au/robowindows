#!/usr/bin/env bash
set -euo pipefail
[[ $# -ge 3 && $# -le 4 && -f $3 ]] || { echo "Usage: summarize-presentation.sh normal|dynamic 0|1 LOG [tone|stress]" >&2; exit 2; }
workload=${4:-tone}
[[ $workload == tone || $workload == stress ]] || exit 2
case "$1" in normal) decoder=Normal ;; dynamic) decoder=DynRec ;; *) exit 2 ;; esac
[[ $2 == 0 || $2 == 1 ]] || exit 2
diagnostics=$(bash "$(dirname "$0")/summarize-runtime-timing.sh" "$3")
awk -v decoder="$decoder" -v core="$1" -v policy="$2" -v workload="$workload" '
function fail(message) { print message > "/dev/stderr"; bad=1 }
/RoboWindowsTelemetry:/ {
  delete v
  for (i=1;i<=NF;i++) { split($i,p,"="); if (length(p[2])) v[p[1]]=p[2] }
  if (v["interval_ms"] !~ /^[0-9]+$/ || v["interval_ms"] <= 0) { fail("Invalid interval"); next }
  elapsed+=v["interval_ms"]
  # Exclude full initial ten seconds and the ending interval (tone turns off).
  if (elapsed <= 11000 || elapsed > 119000) next
  numeric="presented presenter_cpu_us underruns missing dropped resync stream_errors post_failures graphics_errors graphics_fallbacks presenter_clock_errors audio_produced audio_consumed"
  split(numeric,fields," ")
  for (j in fields) if (v[fields[j]] !~ /^[0-9]+$/) fail("Missing/invalid " fields[j])
  if (v["schema"] != 4 || v["state"] != "foreground" || v["audio_state"] != "playing" ||
      v["decoder"] != decoder || v["timing"] != "balanced_100ms" ||
      v["presentation_requested"] != policy || v["presentation_active"] != policy) fail("Invalid runtime state/policy")
  if (v["underruns"] || v["missing"] || v["dropped"] || v["resync"] || v["stream_errors"] ||
      v["post_failures"] || v["graphics_errors"] || v["graphics_fallbacks"] || v["presenter_clock_errors"]) fail("Settled runtime error")
  ms+=v["interval_ms"]; posts+=v["presented"]; cpu+=v["presenter_cpu_us"]
  produced+=v["audio_produced"]; consumed+=v["audio_consumed"]; intervals++
}
/RoboWindowsPresentation: PASS timer/ {
  ++timer
  delete result
  for (i=1;i<=NF;i++) { split($i,p,"="); if (length(p[2])) result[p[1]]=p[2] }
  # Historical light fixtures omitted workload; stress must be explicit.
  actual=("workload" in result ? result["workload"] : "tone")
  if (actual != workload || result["core"] != core || result["presentation"] != policy ||
      (workload == "stress" && result["phase_mask"] != 15)) fail("Wrong workload or incomplete phases")
}
/RoboWindowsPresentation: FAIL|Fatal signal|FATAL EXCEPTION/ { fail("Fixture failed") }
END {
  if (timer != 1 || ms < 100000 || !posts || !produced || !consumed) fail("Incomplete timer/audio capture")
  fps=ms ? posts*1000/ms : 0
  if (fps < (policy == 1 ? 28 : 14.5)) fail("Insufficient presentation rate")
  printf "core=%s\npresentation=%d\nintervals=%d\nelapsed_ms=%d\npresented_fps=%.2f\npresenter_cpu_us_per_frame=%.2f\nquality=%s\n", decoder,policy,intervals,ms,fps,posts ? cpu/posts : 0,bad ? "fail" : "pass"
  printf "workload=%s\n", workload
  exit bad ? 1 : 0
}' "$3"
printf '%s\n' "$diagnostics"
