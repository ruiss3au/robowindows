#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
patch_file="$repo_dir/patches/dosbox-pure/0007-bounded-pagefault-core-slice.patch"

mapfile -t touched < <(sed -n 's|^diff --git a/[^ ]* b/||p' "$patch_file")
expected=(include/paging.h src/cpu/cpu.cpp src/cpu/paging.cpp)
[[ "${touched[*]}" == "${expected[*]}" ]]

rg -q '^\+static const Bit32s robowindows_pf_core_slice_cycles = 64;$' "$patch_file"
rg -q '^\+void PAGING_PageFaultCoreIret\(void\)' "$patch_file"
rg -q '^\+\tPAGING_PageFaultCoreIret\(\);$' "$patch_file"
rg -q '^\+\tif \(parent_slice\) parent_slice->entered_nested_slice = true;$' "$patch_file"
rg -q '^\+\tCPU_CycleLeft-=\(Bit32s\)executed;$' "$patch_file"
rg -q '^\+\tif \(pf_queue.used >= 2\) DBP_PageFaultCycles \+= executed;$' "$patch_file"
if rg -q '^\+.*DBP_PageFaultCycles\+\+' "$patch_file"; then
  echo "Bounded PageFaultCore patch still advances its watchdog per host call" >&2
  exit 1
fi

echo "Bounded PageFaultCore slice checks passed"
