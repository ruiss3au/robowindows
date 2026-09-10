#!/usr/bin/env bash
set -euo pipefail
[[ $# == 4 && -f $4 ]] || { echo 'Usage: summarize-cache-fixture.sh normal|dynamic 0|1 warm|cold|reuse|data|rewrite LOG' >&2; exit 2; }
case "$1" in normal) decoder=Normal ;; dynamic) decoder=DynRec ;; *) exit 2 ;; esac
[[ $2 == 0 || $2 == 1 ]] || exit 2
case "$3" in warm|cold|reuse|data|rewrite) ;; *) exit 2 ;; esac
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
bash "$(dirname "$0")/normalize-session-timing.sh" "$4" >"$work_dir/session.log"
diagnostics=$(bash "$(dirname "$0")/summarize-runtime-timing.sh" "$work_dir/session.log")
awk -v core="$1" -v decoder="$decoder" -v policy="$2" -v phase="$3" '
function fail(s) { print s > "/dev/stderr"; bad=1 }
function parse(line) {
 delete v
 n=split(line,tokens,/[[:space:]]+/)
 for(i=1;i<=n;i++) {
  if(split(tokens[i],p,"=")!=2 || p[1] in v) { fail("Malformed/duplicate field"); continue }
  v[p[1]]=p[2]
 }
}
/RoboWindowsTerminal:/ {
 line=$0; sub(/^.*RoboWindowsTerminal:[[:space:]]*/,"",line); parse(line)
 terminal=1; terminal_us=v["interval_us"]; reason=v["reason"]
}
/RoboWindowsTelemetry:/ {
 line=$0; sub(/^.*RoboWindowsTelemetry:[[:space:]]*/,"",line); parse(line)
 if(v["schema"]!=4 || v["state"]!=(terminal ? "stopping" : "foreground") || v["decoder"]!=decoder ||
    v["timing"]!="balanced_100ms" || v["presentation_requested"]!=policy) fail("Wrong runtime policy/state")
 fields="interval_ms run presented underruns missing dropped saturated stream_errors resync graphics_errors graphics_fallbacks post_failures presenter_clock_errors"
 count=split(fields,keys," ")
 for(j=1;j<=count;j++) {
  k=keys[j]; if(v[k]!~/^[0-9]+$/) fail("Missing/invalid runtime field")
  total[k]+=v[k]
 }
 if(!terminal && v["interval_ms"]<=0) fail("Invalid interval")
 if(v["presentation_active"]!~/^[01]$/ || v["current"]!~/^(Normal|DynRec|PageFault|Halt|Other|Unknown)$/) fail("Missing/invalid residency")
 if(v["presentation_active"]!=policy) fallback=1
 if(v["current"]!=decoder) other_decoder++
 if(terminal) terminal_records++
 else { intervals++; periodic_ms+=v["interval_ms"] }
}
/RoboWindowsCacheFixture:/ {
 if($0 !~ /RoboWindowsCacheFixture: PASS correctness /) { fail("Fixture failed"); next }
 results++
 line=$0; sub(/^.*PASS correctness /,"",line); parse(line)
 if(n!=12 || length(v)!=12 || v["core"]!=core || v["decoder"]!=decoder ||
    v["presentation"]!=policy || v["workload"]!="cache-"phase) fail("Wrong fixture identity")
 fields="host_ms calls checksum setup_ticks warmup_ticks measured_ticks total_ticks pit_divisor"
 count=split(fields,keys," ")
 for(j=1;j<=count;j++) if(v[keys[j]]!~/^[0-9]+$/) fail("Invalid fixture result")
 if(v["calls"]!=4096 || v["pit_divisor"]!=1193 ||
    v["checksum"]!=(phase=="rewrite" ? 3540428800 : 3540426752) ||
    v["host_ms"]<=0 || v["host_ms"]>60000 || v["measured_ticks"]<=0 ||
    v["total_ticks"]>30000 ||
    v["total_ticks"]!=v["setup_ticks"]+v["warmup_ticks"]+v["measured_ticks"]) fail("Invalid correctness/timer result")
 for(j=1;j<=count;j++) result[keys[j]]=v[keys[j]]
}
/Fatal signal|FATAL EXCEPTION/ { fail("Native/Java failure") }
END {
 if(results!=1) fail("Missing/duplicate fixture result")
 if(terminal && reason!="guest_shutdown") fail("Cache fixture did not terminate by guest shutdown")
 if(bad) exit 1
 printf "fixture=cache-v1\nphase=%s\ncore=%s\npresentation=%s\ncorrectness=pass\n",phase,core,policy
 fields="host_ms calls checksum setup_ticks warmup_ticks measured_ticks total_ticks pit_divisor"
 count=split(fields,keys," ")
 for(j=1;j<=count;j++) printf "%s=%s\n",keys[j],result[keys[j]]
 printf "measured_guest_ms=%.3f\n",result["measured_ticks"]*1193000/1193182
 elapsed_us=periodic_ms*1000+terminal_us
 printf "complete_intervals=%d\nterminal_records=%d\ntelemetry_elapsed_ms=%.3f\n",intervals,terminal_records,elapsed_us/1000
 if(elapsed_us) printf "whole_session_presented_fps=%.2f\n",total["presented"]*1000000/elapsed_us
 else print "whole_session_presented_fps=unavailable"
 fields="underruns missing dropped saturated stream_errors resync graphics_errors graphics_fallbacks post_failures presenter_clock_errors"
 count=split(fields,keys," ")
 for(j=1;j<=count;j++) {
  if(intervals || terminal_records) printf "%s=%.0f\n",keys[j],total[keys[j]]
  else printf "%s=unavailable\n",keys[j]
 }
 printf "other_decoder_intervals=%d\npresentation_fallback_observed=%d\n",other_decoder,fallback
 print "timing_scope=" (terminal ? "observed_intervals_plus_terminal_teardown" : "whole_session_complete_intervals_only")
 print "terminal_coverage=" (terminal ? "available" : "unavailable")
 if(terminal) print "terminal_reason=" reason
 print "quality=not_assessed"
 print "cache_mechanism=unverified"
}' "$work_dir/session.log"
printf '%s\n' "$diagnostics"
