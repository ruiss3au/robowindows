#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 && -f $1 ]] || { echo 'Usage: normalize-session-timing.sh LOG' >&2; exit 2; }
# Opt-in only. Keep the terminal header so consumers can distinguish shutdown
# from foreground even after mapping the residual triplet to existing schemas.
awk '
function fail(s) { print s > "/dev/stderr"; bad=1 }
function parse(line) {
 delete v
 n=split(line,tokens,/[[:space:]]+/)
 for(i=1;i<=n;i++) {
  if(split(tokens[i],p,"=")!=2 || p[1] in v) { fail("Malformed/duplicate terminal field"); continue }
  v[p[1]]=p[2]
 }
}
/RoboWindows(Telemetry|Worker|Timing):/ {
 if(stage) fail("Periodic diagnostic after terminal header")
 if(/RoboWindowsTelemetry:/) periodic++
 print; next
}
/RoboWindowsTerminal:/ {
 if(stage) fail("Duplicate terminal group")
 stage=1
 line=$0; sub(/^.*RoboWindowsTerminal:[[:space:]]*/,"",line); parse(line)
 if(n!=5 || length(v)!=5 || v["schema"]!=1 || v["interval_us"]!~/^[0-9]+$/ ||
    v["periodic_records"]!~/^[0-9]+$/ || v["periodic_records"]!=periodic ||
    v["clock_errors"]!~/^[0-9]+$/ || v["clock_errors"]!=0 ||
    v["reason"]!~/^(guest_shutdown|host_stop|runtime_failure)$/) fail("Invalid terminal header/coverage")
 ms=int(v["interval_us"]/1000)
 print; next
}
/RoboWindowsTerminal(Telemetry|Worker|Timing):/ {
 expected=(/RoboWindowsTerminalTelemetry:/ ? 1 : (/RoboWindowsTerminalWorker:/ ? 2 : 3))
 if(stage!=expected) fail("Missing/reordered terminal record")
 stage=expected+1
 line=$0; sub(/^.*RoboWindowsTerminal[A-Za-z]+:[[:space:]]*/,"",line); parse(line)
 if(v["interval_ms"]!~/^[0-9]+$/ || v["interval_ms"]!=ms ||
    v["schema"]!=(expected==1 ? 4 : 1)) fail("Mismatched terminal interval/schema")
 if(expected==1 && (v["state"]!="stopping" || v["audio_state"]!="stopped" ||
    v["timing"]!="balanced_100ms")) fail("Invalid terminal lifecycle")
 if(expected==1 && v["presenter_clock_errors"]!~/^0$/) fail("Terminal presenter clock failure")
 if(expected==3 && v["cpu_clock_errors"]!~/^0$/) fail("Terminal frontend clock failure")
 sub(/RoboWindowsTerminal/,"RoboWindows"); print; next
}
/RoboWindowsTerminal[^ :]*:/ { fail("Unknown terminal record"); next }
{ print }
END { if(stage && stage!=4) fail("Incomplete terminal group"); if(bad) exit 1 }
' "$1"
