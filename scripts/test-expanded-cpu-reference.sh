#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT

qemu_version=$(qemu-system-i386 --version | sed -n '1p')
case "$qemu_version" in
  'QEMU emulator version 7.2.22 '*) ;;
  *) echo "Expanded CPU reference requires QEMU 7.2.22; found: $qemu_version" >&2; exit 2 ;;
esac

images="$work_dir/images"
records="$work_dir/records"
classes="$work_dir/classes"
mkdir -p -- "$images" "$records" "$classes"
"$repo_dir/scripts/build-expanded-cpu-fixtures.sh" "$images" >/dev/null

pids=()
names=(strings fault_retry pagefault_progress integer_flags stack_control paging_smc x87 mixed_seeds)
for name in "${names[@]}"; do
  cp -- "$images/robowindows_x86_gate_${name}.bin" "$work_dir/${name}.run.bin"
  timeout 2 qemu-system-i386 -display none -serial none -monitor none \
    -no-reboot -no-shutdown \
    -drive "file=$work_dir/${name}.run.bin,format=raw,if=floppy" \
    >/dev/null 2>&1 &
  pids+=("$!")
done
for pid in "${pids[@]}"; do
  wait "$pid" || test "$?" -eq 124
done
for name in "${names[@]}"; do
  dd if="$work_dir/${name}.run.bin" of="$records/${name}.record" \
    bs=1 skip=20480 count=52 status=none
done

javac -d "$classes" \
  "$repo_dir/android/src/main/java/org/robowindows/app/ExpandedCpuSuite.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/ExpandedCpuResult.java" \
  "$repo_dir/tests/java/org/robowindows/app/ExpandedCpuReferenceCheck.java"
java -cp "$classes" org.robowindows.app.ExpandedCpuReferenceCheck \
  "$records/strings.record" "$records/fault_retry.record" \
  "$records/pagefault_progress.record" \
  "$records/integer_flags.record" "$records/stack_control.record" \
  "$records/paging_smc.record" "$records/x87.record" \
  "$records/mixed_seeds.record"
printf 'reference=%s suites=%d status=pass\n' "$qemu_version" "${#names[@]}"
