#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
work_dir=$(mktemp -d)
trap 'rm -rf -- "$work_dir"' EXIT
image="$work_dir/volume.fat"
truncate -s 4096 "$image"
checker="$repo_dir/scripts/check-experimental-fat.sh"
base_env=(ROBOWINDOWS_FSCK_FAT="$repo_dir/tests/scripts/fake-fsck-fat.sh" ROBOWINDOWS_MTYPE="$repo_dir/tests/scripts/fake-mtype.sh")

env "${base_env[@]}" "$checker" --image "$image" --expected WINDOWS/WIN.INI | rg -q '^checker=robowindows-fat-check-v1 status=pass image_sha256=[0-9a-f]{64} fixtures=1$'
if env "${base_env[@]}" ROBOWINDOWS_FAKE_FSCK_EXIT=1 "$checker" --image "$image" --expected WINDOWS/WIN.INI; then
  echo "filesystem rejection unexpectedly passed" >&2; exit 1
fi
if env "${base_env[@]}" ROBOWINDOWS_FAKE_MTYPE_FAIL=1 "$checker" --image "$image" --expected WINDOWS/WIN.INI; then
  echo "fixture rejection unexpectedly passed" >&2; exit 1
fi
truncate -s 4096 "$work_dir/volume.vhd"
if env "${base_env[@]}" "$checker" --image "$work_dir/volume.vhd" --expected WINDOWS/WIN.INI >/dev/null 2>&1; then
  echo "container rejection unexpectedly passed" >&2; exit 1
fi
if env "${base_env[@]}" "$checker" --image "$image" --expected ../unsafe >/dev/null 2>&1; then
  echo "unsafe fixture path unexpectedly passed" >&2; exit 1
fi
echo "Experimental FAT checker checks passed"
