#!/usr/bin/env bash
set -euo pipefail
repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
case "$(qemu-system-i386 --version | sed -n '1p')" in
  'QEMU emulator version 7.2.22 '*) ;;
  *) echo 'Cache reference requires QEMU 7.2.22' >&2; exit 2 ;;
esac
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
phases=(warm cold reuse data rewrite)
for phase in "${phases[@]}"; do
  bash "$repo_dir/scripts/build-cache-fixture.sh" "$phase" "$work_dir/$phase.bin"
  # Identical generated output is reusable, without overwriting.
  bash "$repo_dir/scripts/build-cache-fixture.sh" "$phase" "$work_dir/$phase.bin" >/dev/null
done
if bash "$repo_dir/scripts/build-cache-fixture.sh" bad "$work_dir/bad.bin" >/dev/null 2>&1; then exit 1; fi
ln -s "$work_dir/warm.bin" "$work_dir/link.bin"
if bash "$repo_dir/scripts/build-cache-fixture.sh" warm "$work_dir/link.bin" >/dev/null 2>&1; then exit 1; fi
cp "$work_dir/warm.bin" "$work_dir/failure.bin"
as --32 --defsym CASE_ID=1 --defsym TEST_BAD_VALUE=1 -o "$work_dir/failure.o" "$repo_dir/tests/cpu/cache_workload.S"
ld -m elf_i386 -Ttext 0x8000 --oformat binary -e start -o "$work_dir/failure.stage" "$work_dir/failure.o"
dd if="$work_dir/failure.stage" of="$work_dir/failure.bin" bs=512 seek=1 conv=notrunc status=none
pids=()
for phase in "${phases[@]}" failure; do
  # Each QEMU instance opens only its generated floppy, never a host/user disk.
  timeout 20 qemu-system-i386 -display none -serial none -monitor none -no-reboot \
    -device isa-debug-exit,iobase=0xf4,iosize=0x04 \
    -drive "file=$work_dir/$phase.bin,format=raw,if=floppy" >"$work_dir/$phase.log" 2>&1 &
  pids+=("$!")
done
failed=0
for pid in "${pids[@]}"; do
  status=0; wait "$pid" || status=$?
  [[ $status == 33 ]] || { echo "Cache QEMU reference failed/timeout: status=$status" >&2; failed=1; }
done
[[ $failed == 0 ]] || exit 1
records=()
for phase in "${phases[@]}" failure; do
  dd if="$work_dir/$phase.bin" of="$work_dir/$phase.record" bs=1 skip=8704 count=64 status=none
  records+=("$work_dir/$phase.record")
done
before=$(sha256sum "$work_dir/warm.bin")
if bash "$repo_dir/scripts/build-cache-fixture.sh" warm "$work_dir/warm.bin" >/dev/null 2>&1; then exit 1; fi
[[ $(sha256sum "$work_dir/warm.bin") == "$before" ]]
javac -d "$work_dir/classes" "$repo_dir/android/src/main/java/org/robowindows/app/CacheWorkload.java" \
  "$repo_dir/tests/java/org/robowindows/app/CacheWorkloadTest.java"
java -cp "$work_dir/classes" org.robowindows.app.CacheWorkloadTest "${records[@]}"
# Without debug-exit, the normal fixture must also complete BIOS APM shutdown.
bash "$repo_dir/scripts/build-cache-fixture.sh" warm "$work_dir/apm.bin" >/dev/null
timeout 20 qemu-system-i386 -display none -serial none -monitor none -no-reboot \
  -drive "file=$work_dir/apm.bin,format=raw,if=floppy" >"$work_dir/apm.log" 2>&1
echo 'Cache fixture BIOS APM shutdown passed'
