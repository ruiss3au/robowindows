#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
case "$(qemu-system-i386 --version | sed -n '1p')" in
  'QEMU emulator version 7.2.22 '*) ;;
  *) echo "Stress reference requires QEMU 7.2.22" >&2; exit 2 ;;
esac
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
bash "$repo_dir/scripts/build-presentation-fixture.sh" "$work_dir/stress.bin" stress >/dev/null
timeout 35 qemu-system-i386 -display none -serial none -monitor none -no-reboot -no-shutdown \
  -drive "file=$work_dir/stress.bin,format=raw,if=floppy" >/dev/null 2>&1 || test "$?" -eq 124
test "$(od -An -tx1 -j8704 -N8 "$work_dir/stress.bin" | tr -d ' \n')" = 5257535452303031
test "$(od -An -tu2 -j8716 -N2 "$work_dir/stress.bin" | tr -d ' \n')" = 1
test "$(od -An -tu2 -j8718 -N2 "$work_dir/stress.bin" | tr -d ' \n')" = 15
ticks=$(od -An -tu4 -j8712 -N4 "$work_dir/stress.bin" | tr -d ' \n')
test "$ticks" -ge 576
test "$ticks" -le 728
read -r idle integer ram vga <<<"$(od -An -tu4 -j8720 -N16 "$work_dir/stress.bin")"
test "$idle" -gt 0 && test "$integer" -gt 0 && test "$ram" -gt 0 && test "$vga" -gt 0
echo "Stress QEMU reference passed: ticks=$ticks phase_mask=15 all phase counters positive"
