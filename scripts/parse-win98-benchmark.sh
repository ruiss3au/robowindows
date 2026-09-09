#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 || ! -f $1 ]]; then
  echo "Usage: scripts/parse-win98-benchmark.sh RWBENCH.TXT" >&2
  exit 2
fi
input=$1
size=$(stat -c %s "$input")
(( size > 0 && size <= 4096 )) || {
  echo "Invalid benchmark result size" >&2
  exit 1
}
if LC_ALL=C grep -Pq '[^\x0A\x0D\x20-\x7E]' "$input"; then
  echo "Benchmark result is not bounded ASCII" >&2
  exit 1
fi

mapfile -t lines < <(tr -d '\r' <"$input")
[[ ${#lines[@]} -eq 5 ]] || {
  echo "Benchmark result must contain exactly five lines" >&2
  exit 1
}
[[ ${lines[0]} == 'RW98BENCH schema=1 workload=1 duration_ms=30000' ]] || {
  echo "Unsupported benchmark result header" >&2
  exit 1
}

parse_phase() {
  local line=$1 phase=$2 unit=$3 extra=$4
  local pattern
  pattern="^phase=${phase} unit=${unit}${extra} elapsed_ms=([0-9]+) work=([0-9]+) throughput=([0-9]+) integrity=([0-9A-F]{8})$"
  [[ $line =~ $pattern ]] || {
    echo "Invalid or missing $phase result" >&2
    return 1
  }
  local elapsed=${BASH_REMATCH[1]}
  local work=${BASH_REMATCH[2]}
  local throughput=${BASH_REMATCH[3]}
  local integrity=${BASH_REMATCH[4]}
  (( elapsed >= 10000 && elapsed <= 15000 && work > 0 )) || {
    echo "Out-of-range $phase result" >&2
    return 1
  }
  local expected
  if [[ $phase == gdi ]]; then
    expected=$((work * 1000 / elapsed))
  else
    expected=$((work / elapsed))
  fi
  (( throughput == expected )) || {
    echo "Inconsistent $phase throughput" >&2
    return 1
  }
  printf '%s_elapsed_ms=%s\n%s_work=%s\n%s_throughput=%s\n%s_integrity=%s\n' \
    "$phase" "$elapsed" "$phase" "$work" "$phase" "$throughput" \
    "$phase" "$integrity"
}

parse_phase "${lines[1]}" cpu ops_per_ms ''
parse_phase "${lines[2]}" memory kib_per_ms ''
parse_phase "${lines[3]}" gdi fills_per_s ' pixels_per_fill=256000'
[[ ${lines[4]} == 'complete=1' ]] || {
  echo "Benchmark result is incomplete" >&2
  exit 1
}
printf 'schema=1\nworkload=1\nnominal_duration_ms=30000\ncomplete=1\n'
