#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
fixture_dir="$repo_dir/tests/realtime/fixtures"
first_dir="$(mktemp -d)"
second_dir="$(mktemp -d)"
trap 'rm -rf -- "$first_dir" "$second_dir"' EXIT

valid="$($repo_dir/scripts/parse-win98-benchmark.sh "$fixture_dir/benchmark-valid.txt")"
grep -q '^cpu_throughput=100$' <<<"$valid"
grep -q '^memory_throughput=50$' <<<"$valid"
grep -q '^gdi_throughput=60$' <<<"$valid"
grep -q '^gdi_frames=99$' <<<"$valid"
grep -q '^gdi_fps=9$' <<<"$valid"
grep -q '^complete=1$' <<<"$valid"

for invalid in benchmark-incomplete.txt benchmark-inconsistent.txt \
  benchmark-overactive-preview.txt; do
  if "$repo_dir/scripts/parse-win98-benchmark.sh" "$fixture_dir/$invalid" >/dev/null 2>&1; then
    echo "Parser accepted invalid fixture: $invalid" >&2
    exit 1
  fi
done

"$repo_dir/scripts/build-win98-benchmark.sh" --output-dir "$first_dir" >/dev/null
"$repo_dir/scripts/build-win98-benchmark.sh" --output-dir "$second_dir" >/dev/null
cmp "$first_dir/RWBENCH.EXE" "$second_dir/RWBENCH.EXE"
cmp "$first_dir/RWBENCH.ISO" "$second_dir/RWBENCH.ISO"
(
  cd "$first_dir"
  sha256sum -c SHA256SUMS >/dev/null
)
file "$first_dir/RWBENCH.EXE" | grep 'PE32 executable (GUI) Intel 80386' >/dev/null
for dll in KERNEL32.dll USER32.dll GDI32.dll; do
  objdump -p "$first_dir/RWBENCH.EXE" | grep -i "DLL Name: $dll" >/dev/null
done
if objdump -p "$first_dir/RWBENCH.EXE" | grep -i 'DLL Name: WINMM.dll' >/dev/null; then
  echo "Benchmark unexpectedly imports WINMM" >&2
  exit 1
fi
if strings "$first_dir/RWBENCH.EXE" | grep 'PlaySound' >/dev/null; then
  echo "Benchmark unexpectedly contains a sound path" >&2
  exit 1
fi
echo "Windows 98 benchmark build/parser checks passed"
