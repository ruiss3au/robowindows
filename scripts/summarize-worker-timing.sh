#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 && -f $1 ]] || { echo "Usage: summarize-worker-timing.sh LOG" >&2; exit 2; }
awk '
function fail(s) { print s > "/dev/stderr"; bad=1 }
/RoboWindowsTelemetry:/ {
 ++intervals
 for(i=1;i<=NF;i++) { split($i,p,"="); if(p[1]=="interval_ms") ms[intervals]=p[2] }
}
/RoboWindowsWorker:/ {
 ++records
 line=$0; sub(/^.*RoboWindowsWorker:[[:space:]]*/,"",line)
 delete v
 count=split(line,tokens,/[[:space:]]+/)
 for(i=1;i<=count;i++) {
  if(split(tokens[i],p,"=")!=2 || p[2]!~/^[0-9]+$/ || p[1] in v) { fail("Malformed worker field"); next }
  v[p[1]]=p[2]
 }
 fields="schema interval_ms worker_slices worker_wall_total_us worker_wall_max_us worker_cpu_total_us worker_cpu_max_us worker_clock_errors wait_calls wait_total_us wait_max_us mix_calls mix_total_us mix_max_us frontend_clock_errors translations fallback_special fallback_invalidated fallback_opcode fallback_smc fallback_trap discarded_slices"
 n=split(fields,names," ")
 if(length(v)!=n) fail("Incomplete or unknown worker fields")
 for(i=1;i<=n;i++) if(!(names[i] in v)) fail("Missing worker field")
 if(v["schema"]!=1 || records!=intervals || v["interval_ms"]!=ms[records]) fail("Unmatched worker record")
 if(v["worker_wall_max_us"]>v["worker_wall_total_us"] || v["worker_cpu_max_us"]>v["worker_cpu_total_us"] ||
    v["wait_max_us"]>v["wait_total_us"] || v["mix_max_us"]>v["mix_total_us"]) fail("Inconsistent worker maximum")
 if(v["worker_clock_errors"] || v["frontend_clock_errors"]) fail("Worker diagnostic clock failure")
 if(!v["worker_slices"] && (v["worker_wall_total_us"] || v["worker_cpu_total_us"] || v["translations"] ||
    v["fallback_special"] || v["fallback_invalidated"] || v["fallback_opcode"] || v["fallback_smc"] || v["fallback_trap"]))
    fail("Worker totals without completed slices")
 if((!v["wait_calls"] && v["wait_total_us"]) || (!v["mix_calls"] && v["mix_total_us"])) fail("Frontend totals without calls")
 for(i=3;i<=n;i++) {
  k=names[i]
  if(k~/_max_us$/) { if(v[k]>maximum[k]) maximum[k]=v[k] }
  else total[k]+=v[k]
 }
}
END {
 if(records && records!=intervals) fail("Missing worker interval")
 if(bad) exit 1
 if(!records) { print "core_timing=unavailable"; exit }
 print "core_timing=1"
 printf "core_timing_intervals=%d\n",records
 for(i=3;i<=n;i++) {
  k=names[i]; printf "core_%s=%.0f\n",k,(k~/_max_us$/ ? maximum[k] : total[k])
 }
}' "$1"
