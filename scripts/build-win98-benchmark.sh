#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source_dir="$repo_dir/tools/win98-benchmark"
output_dir="$repo_dir/artifacts/win98-benchmark"
work_dir="$(mktemp -d)"
trap 'rm -rf -- "$work_dir"' EXIT

if [[ ${1:-} == --output-dir ]]; then
  [[ $# -eq 2 && -n ${2:-} ]] || {
    echo "Usage: scripts/build-win98-benchmark.sh [--output-dir DIRECTORY]" >&2
    exit 2
  }
  output_dir=$2
elif [[ $# -ne 0 ]]; then
  echo "Usage: scripts/build-win98-benchmark.sh [--output-dir DIRECTORY]" >&2
  exit 2
fi

for tool in as ld objcopy objdump sha256sum xorriso; do
  command -v "$tool" >/dev/null || {
    echo "Missing required GNU binutils tool: $tool" >&2
    exit 1
  }
done

as_version="$(as --version | sed -n '1p')"
[[ "$as_version" == "GNU assembler (GNU Binutils for Debian) 2.40" ]] || {
  echo "Unsupported assembler: $as_version (expected GNU binutils 2.40)" >&2
  exit 1
}
xorriso_version="$(xorriso -version 2>&1 | sed -n '1p')"
[[ "$xorriso_version" == "xorriso 1.5.4 : RockRidge filesystem manipulator, libburnia project." ]] || {
  echo "Unsupported ISO builder: $xorriso_version (expected xorriso 1.5.4)" >&2
  exit 1
}

make_import_library() {
  local dll_name=$1
  as --32 "$source_dir/$dll_name-imports.s" -o "$work_dir/$dll_name.o"
  ld -mi386pe --dll --no-insert-timestamp --export-all-symbols \
    --out-implib "$work_dir/lib$dll_name.a" \
    -o "$work_dir/$dll_name.dll" "$work_dir/$dll_name.o"
}

make_import_library kernel32
make_import_library user32
make_import_library gdi32

as --32 --gdwarf-2 "$source_dir/benchmark.s" -o "$work_dir/benchmark.o"
ld -mi386pe --no-insert-timestamp --subsystem windows:4.0 \
  --major-os-version 4 --minor-os-version 0 \
  --major-subsystem-version 4 --minor-subsystem-version 0 --entry _start \
  -Map "$work_dir/RWBENCH.MAP" \
  -o "$work_dir/RW98BENCH.EXE" "$work_dir/benchmark.o" \
  "$work_dir/libkernel32.a" "$work_dir/libuser32.a" \
  "$work_dir/libgdi32.a"

# Drain each producer: grep -q can give objdump SIGPIPE under pipefail.
objdump -f "$work_dir/RW98BENCH.EXE" | grep 'file format pei-i386' >/dev/null
for dll in KERNEL32.dll USER32.dll GDI32.dll; do
  objdump -p "$work_dir/RW98BENCH.EXE" | grep -i "DLL Name: $dll" >/dev/null
done
if objdump -p "$work_dir/RW98BENCH.EXE" | grep -i 'DLL Name: WINMM.dll' >/dev/null; then
  echo "Benchmark workload must not import WINMM or play a test tone" >&2
  exit 1
fi

objdump -d "$work_dir/RW98BENCH.EXE" >"$work_dir/RWBENCH.DISASM"

media_dir="$work_dir/media"
mkdir -p "$media_dir"
install -m 0644 "$work_dir/RW98BENCH.EXE" "$media_dir/RWBENCH.EXE"
install -m 0644 "$source_dir/README.TXT" "$media_dir/README.TXT"
touch -d '2026-09-09 00:00:00 UTC' \
  "$media_dir/RWBENCH.EXE" "$media_dir/README.TXT" "$media_dir"
xorriso -as mkisofs -quiet -iso-level 1 -V RWBENCH \
  --modification-date=2026090900000000 \
  --set_all_file_dates 2026090900000000 \
  -o "$work_dir/RWBENCH.ISO" "$media_dir"

mkdir -p "$output_dir"
install -m 0644 "$work_dir/RW98BENCH.EXE" "$output_dir/RWBENCH.EXE"
install -m 0644 "$work_dir/RWBENCH.ISO" "$output_dir/RWBENCH.ISO"
install -m 0644 "$work_dir/RWBENCH.MAP" "$output_dir/RWBENCH.MAP"
install -m 0644 "$work_dir/RWBENCH.DISASM" "$output_dir/RWBENCH.DISASM"
(
  cd "$output_dir"
  sha256sum RWBENCH.EXE RWBENCH.ISO >SHA256SUMS
)
printf 'Built Windows 98 benchmark under %s\n' "$output_dir"
cat "$output_dir/SHA256SUMS"
