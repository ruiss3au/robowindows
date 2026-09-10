#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
phase=${1:-}
case "$phase" in
  warm) id=1 ;; cold) id=2 ;; reuse) id=3 ;; data) id=4 ;; rewrite) id=5 ;;
  *) echo 'Usage: build-cache-fixture.sh warm|cold|reuse|data|rewrite [OUTPUT]' >&2; exit 2 ;;
esac
[[ $# -le 2 ]] || exit 2
output=${2:-"$repo_dir/build/generated/cache-fixture/robowindows_cache_$phase.bin"}
case "$(as --version | sed -n '1p')" in
  *'GNU Binutils for Debian) 2.40') ;;
  *) echo 'Cache fixture requires GNU assembler 2.40' >&2; exit 2 ;;
esac
case "$(ld --version | sed -n '1p')" in
  *'GNU Binutils for Debian) 2.40') ;;
  *) echo 'Cache fixture requires GNU linker 2.40' >&2; exit 2 ;;
esac
work_dir=$(mktemp -d)
publish_dir=
trap 'rm -rf -- "$work_dir"; if [[ -n "$publish_dir" ]]; then rm -rf -- "$publish_dir"; fi' EXIT
as --32 -o "$work_dir/boot.o" "$repo_dir/tests/cpu/robowindows_cpu_boot.S"
ld -m elf_i386 -Ttext 0x7c00 --oformat binary -e start -o "$work_dir/boot.bin" "$work_dir/boot.o"
as --32 --defsym CASE_ID="$id" -o "$work_dir/stage.o" "$repo_dir/tests/cpu/cache_workload.S"
ld -m elf_i386 -Ttext 0x8000 --oformat binary -e start -o "$work_dir/stage.bin" "$work_dir/stage.o"
test "$(wc -c <"$work_dir/boot.bin")" -eq 512
test "$(wc -c <"$work_dir/stage.bin")" -le 4096
cp -- "$work_dir/boot.bin" "$work_dir/image.bin"
truncate -s 1474560 "$work_dir/image.bin"
dd if="$work_dir/stage.bin" of="$work_dir/image.bin" bs=512 seek=1 conv=notrunc status=none
sha=$(sha256sum "$work_dir/image.bin" | cut -d' ' -f1)
case "$phase" in
  warm) expected=d37a11864fcc864defa77356972f8feac07353c25d6990e9a439bd7ed0025fd4 ;;
  cold) expected=a8557469b19a09c682fd5efda571c79941edc349d3a7a26e5351a6c66cba1acb ;;
  reuse) expected=d38a22b957dcf211ce484015c47ac25bb7302e3a6d037ca0fbbb1735e973e3b5 ;;
  data) expected=9a02c290d260e6724f7e17981c4a4fa2e274214c13a11ae42a2990226bb561f4 ;;
  rewrite) expected=da528da07344ce96ff9e664234c90e9477db3feade1828169fe2f329d29fbe8a ;;
esac
[[ $sha == "$expected" ]] || { echo "Cache fixture source/hash mismatch: $sha" >&2; exit 1; }
if [[ -e "$output" || -L "$output" ]]; then
  [[ -f "$output" && ! -L "$output" ]] && cmp -s "$work_dir/image.bin" "$output" || {
    echo 'Refusing to overwrite a differing or non-regular cache fixture output' >&2; exit 1;
  }
else
  mkdir -p -- "$(dirname -- "$output")"
  publish_dir=$(mktemp -d "$(dirname -- "$output")/.cache-fixture-XXXXXX")
  cp -- "$work_dir/image.bin" "$publish_dir/image.bin"
  # Same-filesystem hard link publishes a complete image and fails on a race.
  ln -- "$publish_dir/image.bin" "$output"
fi
printf 'fixture=cache-v1 phase=%s image_sha256=%s\n' "$phase" "$sha"
