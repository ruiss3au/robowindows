#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir=${1:-"$repo_dir/build/generated/cpuFixture/res/raw"}
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

image_bytes=1474560
sector_bytes=512
stage_sectors=32
stage_capacity=$((stage_sectors * sector_bytes))
result_offset=$((40 * sector_bytes))
record_bytes=52

assembler_version=$(as --version | sed -n '1p')
case "$assembler_version" in
  *'GNU Binutils for Debian) 2.40') ;;
  *) echo "Expanded CPU fixtures require GNU assembler 2.40; found: $assembler_version" >&2; exit 2 ;;
esac

as --32 -o "$work_dir/boot.o" "$repo_dir/tests/cpu/robowindows_x86_gate_boot.S"
ld -m elf_i386 -Ttext 0x7c00 --oformat binary -e start \
  -o "$work_dir/boot.bin" "$work_dir/boot.o"
test "$(wc -c <"$work_dir/boot.bin")" -eq 512
boot_sha=$(sha256sum "$work_dir/boot.bin" | cut -d' ' -f1)
expected_boot_sha=fde0c406e971d9a5bac9e15fee70a0d3a14dffc9866ccad4b4e86070f64a31af
if [[ "$boot_sha" != "$expected_boot_sha" ]]; then
  echo "Expanded CPU boot SHA-256 mismatch: $boot_sha" >&2
  exit 3
fi

mkdir -p -- "$output_dir"
while read -r name suite_id case_count expected_stage_sha expected_image_sha; do
  as --32 --defsym "SUITE_ID=$suite_id" --defsym "CASE_COUNT=$case_count" \
    -o "$work_dir/stage.o" "$repo_dir/tests/cpu/robowindows_x86_gate.S"
  ld -m elf_i386 -Ttext 0x8000 --oformat binary -e start \
    -o "$work_dir/stage.bin" "$work_dir/stage.o"
  stage_bytes=$(wc -c <"$work_dir/stage.bin")
  if ((stage_bytes > stage_capacity)); then
    echo "Expanded CPU $name stage exceeds $stage_sectors sectors: $stage_bytes" >&2
    exit 3
  fi
  truncate -s "$stage_capacity" "$work_dir/stage.bin"
  stage_sha=$(sha256sum "$work_dir/stage.bin" | cut -d' ' -f1)
  if [[ "$stage_sha" != "$expected_stage_sha" ]]; then
    echo "Expanded CPU $name stage SHA-256 mismatch: $stage_sha" >&2
    exit 3
  fi

  output="$output_dir/robowindows_x86_gate_${name}.bin"
  cp -- "$work_dir/boot.bin" "$output"
  truncate -s "$image_bytes" "$output"
  dd if="$work_dir/stage.bin" of="$output" bs="$sector_bytes" seek=1 \
    conv=notrunc status=none
  image_sha=$(sha256sum "$output" | cut -d' ' -f1)
  if [[ "$image_sha" != "$expected_image_sha" ]]; then
    echo "Expanded CPU $name image SHA-256 mismatch: $image_sha" >&2
    exit 3
  fi
  if [[ "$(od -v -An -tx1 -j"$result_offset" -N"$record_bytes" "$output" | tr -d ' \n')" != \
        "$(printf '%0*d' $((record_bytes * 2)) 0)" ]]; then
    echo "Expanded CPU $name result area is not blank" >&2
    exit 3
  fi
  printf 'suite=%s id=%s cases=%s stage_sha256=%s image_sha256=%s stage_bytes=%s\n' \
    "$name" "$suite_id" "$case_count" "$stage_sha" "$image_sha" "$stage_bytes"
done <<'EOF'
strings 0x0101 30 7a5325aacea15186df8b2f31144f6aff70f60357de491936de4aacf0a976e47c d3a53d368c94a4edd09c2ee549ba7ef034d87afa1ba2a6ea000a1038e34e084c
fault_retry 0x0102 8 66b56248661a76109b11d3f10a9e30a58888bf33b5c594cba6ebf657e657eaf6 c03f59e5e13f45428e691818fd1cba0be320635f7e53f0fb79ecd856591dcc8b
pagefault_progress 0x0103 3 1070a9af59e747cec03a12f7225f5e71892d07eb8375ccdb37f6622354bec043 745f2005c5649cfa9d53fd84f919a8251c91f4ef5f8ef277c30580e28e8387bc
integer_flags 0x0201 10 c6ae7595bf6af8dc3d88d95c10c4b86a9e014c220c393f25a135b526d5cf26d7 7dbfd085d5c711648816a21909497b1d2655ec63c7ee2823096984b9d8e33249
stack_control 0x0202 8 222bb40d914e806f4f205d4cd49e94acfbcdb2645952e57b354d4b6ebb4d3fa5 d4a180f646e43fd89d9933bc16d06e02c6c6bd8e0252b53f820ca3b25b2d4138
paging_smc 0x0203 8 0ee69f907d533a0b73a2f052962115225549954809636673ecdcdf8db44710ff afcff546a554a00780b90544f12e87a6f74d4a80c5db2ab61d5f326b38012c9c
x87 0x0301 8 b0abdfd1115271f99f3aaf7ac89c0301a80ce84bf4f1e4f5a167c1226ad9a492 5fda5a40e84d6e75293030815bd9423c8b61366adf325c5073e877bf2641c4a8
mixed_seeds 0x0302 32 592c2fd0a3a8543d56c63e43c1a27284b84b555843ad9d29ce4607ad43360c0b 44e8daae81ae76f79522af05bf3b1c5ac006f72eb7fed7b5abca0cc682b05e04
EOF
