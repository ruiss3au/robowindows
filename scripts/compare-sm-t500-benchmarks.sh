#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 6 ]]; then
  echo "Usage: scripts/compare-sm-t500-benchmarks.sh DYNREC1 DYNREC2 DYNREC3 NORMAL1 NORMAL2 NORMAL3" >&2
  exit 2
fi

field() {
  local name=$1 file=$2 values
  values="$(sed -n "s/^${name}=//p" "$file")"
  [[ $(wc -l <<<"$values") -eq 1 && -n $values ]] || {
    echo "Missing or duplicate $name in $file" >&2
    return 1
  }
  printf '%s\n' "$values"
}

for index in 1 2 3 4 5 6; do
  file=${!index}
  [[ -f $file ]] || { echo "Missing benchmark report: $file" >&2; exit 1; }
  expected=dynrec-fixed-20k
  (( index > 3 )) && expected=normal-fixed-20k
  [[ $(field profile "$file") == "$expected" ]] || {
    echo "Unexpected profile in report $index" >&2
    exit 1
  }
  [[ $(field complete "$file") == 1 && $(field short_run_quality "$file") == pass ]] || {
    echo "Incomplete or failed-quality report $index" >&2
    exit 1
  }
done

build_hash=$(field installed_apk_sha256 "$1")
for index in 2 3 4 5 6; do
  [[ $(field installed_apk_sha256 "${!index}") == "$build_hash" ]] || {
    echo "Benchmark reports use different app builds" >&2
    exit 1
  }
done

median_three() {
  printf '%s\n%s\n%s\n' "$1" "$2" "$3" | sort -n | sed -n '2p'
}

printf 'comparison_schema=1\napp_sha256=%s\ndynrec_profile=dynrec-fixed-20k\nnormal_profile=normal-fixed-20k\nruns_per_profile=3\n' "$build_hash"
for metric in cpu_throughput memory_throughput gdi_throughput presented_fps; do
  dynrec=$(median_three "$(field "$metric" "$1")" "$(field "$metric" "$2")" "$(field "$metric" "$3")")
  normal=$(median_three "$(field "$metric" "$4")" "$(field "$metric" "$5")" "$(field "$metric" "$6")")
  delta=$(awk -v dynamic="$dynrec" -v baseline="$normal" 'BEGIN { if (baseline == 0) print "undefined"; else printf "%.2f", (dynamic - baseline) * 100 / baseline }')
  printf '%s_dynrec_median=%s\n%s_normal_median=%s\n%s_delta_percent=%s\n' \
    "$metric" "$dynrec" "$metric" "$normal" "$metric" "$delta"
done
printf 'promotion_eligible=0\n'
printf 'promotion_blockers=ten_cycle_gate,thirty_minute_thermal_gate,aoe2_gate\n'
