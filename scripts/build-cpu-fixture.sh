#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output=${1:-"$repo_dir/build/generated/cpu-fixture/robowindows-cpu-v3.bin"}
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

image_bytes=1474560
sector_bytes=512
stage_sectors=8
result_sector=18

assembler_version=$(as --version | sed -n '1p')
case "$assembler_version" in
  *'GNU Binutils for Debian) 2.40') ;;
  *) echo "CPU fixture requires GNU assembler 2.40; found: $assembler_version" >&2; exit 2 ;;
esac

as --32 -o "$work_dir/boot.o" "$repo_dir/tests/cpu/robowindows_cpu_boot.S"
ld -m elf_i386 -Ttext 0x7c00 --oformat binary -e start \
  -o "$work_dir/boot-sector.bin" "$work_dir/boot.o"
test "$(wc -c <"$work_dir/boot-sector.bin")" -eq 512

as --32 -o "$work_dir/stage.o" "$repo_dir/tests/cpu/robowindows_cpu_fixture.S"
ld -m elf_i386 -Ttext 0x8000 --oformat binary -e start \
  -o "$work_dir/stage.bin" "$work_dir/stage.o"
stage_bytes=$(wc -c <"$work_dir/stage.bin")
stage_capacity=$((stage_sectors * sector_bytes))
if ((stage_bytes > stage_capacity)); then
  echo "CPU fixture stage exceeds its declared sector range: $stage_bytes" >&2
  exit 3
fi
if ((result_sector <= stage_sectors + 1)); then
  echo "CPU fixture result sector overlaps its stage" >&2
  exit 3
fi
truncate -s "$stage_capacity" "$work_dir/stage.bin"

boot_sha=$(sha256sum "$work_dir/boot-sector.bin" | cut -d' ' -f1)
stage_sha=$(sha256sum "$work_dir/stage.bin" | cut -d' ' -f1)
expected_boot_sha='9c3d8fd4fb64a209020cb729320b25661b56e7301e214db6d173304b62937771'
expected_stage_sha='b4a4d0715318cbcd03904b82056e9c05d0c606a27d5950a61cfabd8ce378e009'
if [[ "$boot_sha" != "$expected_boot_sha" ]]; then
  echo "CPU fixture boot-sector SHA-256 mismatch: $boot_sha" >&2
  exit 3
fi
if [[ "$stage_sha" != "$expected_stage_sha" ]]; then
  echo "CPU fixture stage SHA-256 mismatch: $stage_sha" >&2
  exit 3
fi

mkdir -p -- "$(dirname -- "$output")"
cp -- "$work_dir/boot-sector.bin" "$output"
truncate -s "$image_bytes" "$output"
dd if="$work_dir/stage.bin" of="$output" bs="$sector_bytes" seek=1 \
  conv=notrunc status=none

image_sha=$(sha256sum "$output" | cut -d' ' -f1)
expected_image_sha='a439417c22576a86266349b9e8f943448e30a37ef90753fcbcb99354876156c9'
if [[ "$image_sha" != "$expected_image_sha" ]]; then
  echo "CPU fixture image SHA-256 mismatch: $image_sha" >&2
  exit 3
fi
printf 'fixture=robowindows-cpu-v3 boot_sha256=%s stage_sha256=%s image_sha256=%s stage_bytes=%d stage_sectors=%d result_sector=%d image_bytes=%d\n' \
  "$boot_sha" "$stage_sha" "$image_sha" "$stage_bytes" "$stage_sectors" \
  "$result_sector" "$image_bytes"
