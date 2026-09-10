#!/usr/bin/env bash
set -euo pipefail
[[ $# == 1 && -f $1 ]] || { echo 'Usage: summarize-cache-attribution.sh NORMALIZED_LOG' >&2; exit 2; }
awk '
function fail(s) { print s > "/dev/stderr"; bad=1 }
function parse(line) {
 delete v
 n=split(line,t,/ +/)
 for(i=1;i<=n;i++) {
  if(split(t[i],p,"=")!=2 || p[2]!~/^[0-9]+$/ || p[1] in v) { fail("Invalid cache field"); continue }
  v[p[1]]=p[2]
 }
}
/RoboWindowsTelemetry:/ { intervals++ }
/RoboWindowsWorker:/ {
 worker++
 line=$0; sub(/^.*RoboWindowsWorker:[[:space:]]*/,"",line); parse(line)
 ms=v["interval_ms"]; attempts=v["translations"]; slices=v["worker_slices"]
}
/RoboWindowsTiming:/ { timing++ }
/RoboWindowsCache:/ {
 records++
 line=$0; sub(/^.*RoboWindowsCache:[[:space:]]*/,"",line); parse(line)
 fields="schema interval_ms sample_stride sample_cap publication_cap lookup_hits lookup_misses translation_attempts nested_attempts stride_skipped cap_skipped samples_started samples_completed samples_discarded translation_wall_sample_us translation_wall_sample_max_us translation_cpu_sample_us translation_cpu_sample_max_us publication_scopes publication_skipped publication_wall_sample_us publication_wall_sample_max_us publication_cpu_sample_us publication_cpu_sample_max_us invalidate_calls clear_calls clear_code_write clear_cache_reclaim clear_code_size clear_page_pressure clear_page_release clear_other clock_errors scope_errors"
 count=split(fields,keys," ")
 if(length(v)!=count) fail("Incomplete/unknown cache fields")
 for(j=1;j<=count;j++) if(!(keys[j] in v)) fail("Missing cache field")
 if(v["schema"]!=1 || v["sample_stride"]!=64 || v["sample_cap"]!=4 || v["publication_cap"]!=4) fail("Wrong cache policy/schema")
 if(records!=intervals || records!=worker || records!=timing || v["interval_ms"]!=ms ||
    v["translation_attempts"]!=attempts) fail("Unmatched cache snapshot")
 if(v["translation_attempts"]!=v["nested_attempts"]+v["stride_skipped"]+v["cap_skipped"]+v["samples_started"] ||
    v["samples_started"]!=v["samples_completed"]+v["samples_discarded"] ||
    v["samples_started"]>4*slices || v["publication_scopes"]>4*v["samples_completed"]) fail("Inconsistent sample coverage")
 reasons=v["clear_code_write"]+v["clear_cache_reclaim"]+v["clear_code_size"]
 reasons+=v["clear_page_pressure"]+v["clear_page_release"]+v["clear_other"]
 if(v["clear_calls"]!=reasons) fail("Inconsistent clear reasons")
 if(v["clock_errors"] || v["scope_errors"]) fail("Cache diagnostic error")
 durations="translation_wall_sample translation_cpu_sample publication_wall_sample publication_cpu_sample"
 nd=split(durations,d," ")
 for(j=1;j<=nd;j++) {
  if(v[d[j]"_max_us"]>v[d[j]"_us"]) fail("Invalid cache sample maximum")
  if(!v["samples_completed"] && v[d[j]"_us"]) fail("Duration without samples")
 }
 if(v["publication_wall_sample_us"]>v["translation_wall_sample_us"] ||
    v["publication_cpu_sample_us"]>v["translation_cpu_sample_us"] ||
    (!v["samples_completed"] && (v["publication_scopes"] || v["publication_skipped"])) ||
    (!v["publication_scopes"] && (v["publication_wall_sample_us"] || v["publication_cpu_sample_us"]))) fail("Invalid publication subset")
 for(j=6;j<=count;j++) {
  k=keys[j]
  if(!slices && v[k]) fail("Cache events without completed worker")
  if(k~/_max_us$/) { if(v[k]>maximum[k]) maximum[k]=v[k] }
  else total[k]+=v[k]
 }
}
END {
 if(records && (records!=intervals || records!=worker || records!=timing)) fail("Missing cache coverage")
 if(bad) exit 1
 if(!records) { print "translation_attribution=unavailable"; exit }
 print "translation_attribution=sampled"
 print "translation_sample_timing=" (total["samples_completed"] ? "available" : "unavailable")
 print "cache_reason_scope=clear_operations_not_miss_history"
 print "cache_selection=deterministic_stride64_cap4_per_slice"
 printf "cache_intervals=%d\n",records
 for(j=6;j<=count;j++) { k=keys[j]; printf "cache_%s=%.0f\n",k,(k~/_max_us$/ ? maximum[k] : total[k]) }
 if(total["translation_attempts"]) printf "cache_sample_coverage_pct=%.3f\n",100*total["samples_completed"]/total["translation_attempts"]
 else print "cache_sample_coverage_pct=unavailable"
}' "$1"
