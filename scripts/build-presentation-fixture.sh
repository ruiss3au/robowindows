#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output=${1:-"$repo_dir/build/generated/presentation-fixture/robowindows_gpu_tone.bin"}
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
case "$(as --version | sed -n '1p')" in
  *'GNU Binutils for Debian) 2.40') ;;
  *) echo "Presentation fixture requires GNU assembler 2.40" >&2; exit 2 ;;
esac
as --32 -o "$work_dir/boot.o" "$repo_dir/tests/cpu/robowindows_cpu_boot.S"
ld -m elf_i386 -Ttext 0x7c00 --oformat binary -e start -o "$work_dir/boot.bin" "$work_dir/boot.o"
as --32 -o "$work_dir/stage.o" "$repo_dir/tests/realtime/presentation_tone.S"
ld -m elf_i386 -Ttext 0x8000 --oformat binary -e start -o "$work_dir/stage.bin" "$work_dir/stage.o"
test "$(wc -c <"$work_dir/boot.bin")" -eq 512
test "$(wc -c <"$work_dir/stage.bin")" -le 4096
mkdir -p -- "$(dirname -- "$output")"
cp -- "$work_dir/boot.bin" "$output"
truncate -s 1474560 "$output"
dd if="$work_dir/stage.bin" of="$output" bs=512 seek=1 conv=notrunc status=none
test "$(sha256sum "$output" | cut -d' ' -f1)" = \
  '8643f9d97386905e71f74e9aee74c3a936701dd3471dfdb48423715abac4b76a'
sha256sum "$output"
