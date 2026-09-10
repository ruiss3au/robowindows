#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 && -f $1 ]] || { echo 'Usage: summarize-cache-calibration.sh LOG' >&2; exit 2; }
awk '
function fail(s) { print s > "/dev/stderr"; bad=1 }
/RoboWindowsCacheCalibration:/ {
 line=$0; sub(/^.*RoboWindowsCacheCalibration:[[:space:]]*/,"",line)
 delete v; n=split(line,t," ")
 for(i=1;i<=n;i++) {
  if(split(t[i],p,"=")!=2 || p[2]!~/^[0-9]+$/ || p[1] in v) { fail("Invalid calibration field"); continue }
  v[p[1]]=p[2]
 }
 fields="schema mode iterations attempts_per_slice publication_per_sample mean_ns cache_clock_reads samples_completed stride_skipped cap_skipped cache_events clock_errors accounting_errors"
 count=split(fields,keys," ")
 if(length(v)!=count) fail("Incomplete calibration fields")
 for(i=1;i<=count;i++) if(!(keys[i] in v)) fail("Missing calibration field")
 mode=v["mode"]
 if(v["schema"]!=1 || mode!=records++ || mode>3 || v["iterations"]!=2000 ||
    v["attempts_per_slice"]!=512 || v["publication_per_sample"]!=4 || !v["mean_ns"] ||
    v["clock_errors"] || v["accounting_errors"]) fail("Invalid calibration mode/identity/errors")
 if(v["cache_clock_reads"]!=(mode==2 ? 160000 : 0) ||
    v["samples_completed"]!=(mode==2 ? 8000 : 0) ||
    v["stride_skipped"]!=(mode==2 ? 378000 : 0) ||
    v["cap_skipped"]!=(mode==2 ? 638000 : 0) ||
    v["cache_events"]!=(mode>=2 ? 3072000 : 0)) fail("Invalid calibration accounting")
 if(mode==2 && v["mean_ns"]>=142680) fail("Cache calibration exceeds fixed 1% boot-frame budget")
 mean[mode]=v["mean_ns"]
}
END {
 if(records!=4) fail("Missing/duplicate calibration group")
 if(bad) exit 1
 print "cache_calibration=pass\ncache_calibration_budget_ns=142680"
 for(i=0;i<4;i++) printf "cache_calibration_mode%d_ns=%.0f\n",i,mean[i]
}' "$1"
