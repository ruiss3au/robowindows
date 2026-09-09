#!/bin/sh
# Fetch or verify exact upstream commits from lock/sources.lock.
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
lock_file=${ROBOWINDOWS_SOURCE_LOCK:-$repo_root/lock/sources.lock}
destination=${ROBOWINDOWS_THIRD_PARTY_DIR:-$repo_root/third_party}
mode=fetch

usage() {
  echo "Usage: $0 [--verify-only]"
  echo "Environment overrides for tests: ROBOWINDOWS_SOURCE_LOCK, ROBOWINDOWS_THIRD_PARTY_DIR"
}

case "${1:-}" in
  "") ;;
  --verify-only) mode=verify ;;
  -h|--help) usage; exit 0 ;;
  *) usage >&2; exit 2 ;;
esac

command -v git >/dev/null 2>&1 || { echo "git is required" >&2; exit 1; }
[ -f "$lock_file" ] || { echo "Missing source lock: $lock_file" >&2; exit 1; }
mkdir -p "$destination"

failed=0
count=0
while read -r name url commit extra; do
  case "$name" in ""|'#'*) continue ;; esac

  # Toolchain records have no URL and are handled by bootstrap-debian.sh.
  case "$url" in https://*.git) ;; *) continue ;; esac

  count=$((count + 1))
  if [ -n "${extra:-}" ]; then
    echo "Malformed lock record for $name" >&2
    failed=1
    continue
  fi
  case "$name" in *[!A-Za-z0-9._-]*|'')
    echo "Unsafe source name in lock: $name" >&2; failed=1; continue ;;
  esac
  case "$commit" in
    [0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f][0-9a-f]*) ;;
    *) echo "Invalid commit for $name: $commit" >&2; failed=1; continue ;;
  esac
  if [ "${#commit}" -ne 40 ]; then
    echo "Commit must be a full 40-character SHA for $name" >&2
    failed=1
    continue
  fi

  target=$destination/$name
  if [ ! -d "$target/.git" ]; then
    if [ "$mode" = verify ]; then
      echo "MISSING $name ($target)" >&2
      failed=1
      continue
    fi
    if [ -e "$target" ]; then
      echo "Refusing to replace non-Git path: $target" >&2
      failed=1
      continue
    fi
    echo "Initializing $name"
    git init -q "$target"
    git -C "$target" remote add origin "$url"
  fi

  actual_url=$(git -C "$target" remote get-url origin 2>/dev/null || true)
  if [ "$actual_url" != "$url" ]; then
    echo "Origin mismatch for $name: $actual_url" >&2
    failed=1
    continue
  fi
  patch_file=$repo_root/patches/$name/0001-preserve-conf-on-guest-reboot.patch
  set -- "$patch_file"
  if [ "$name" = dosbox-pure ]; then
    set -- "$@" "$repo_root/patches/$name/0002-dynrec-consume-invlpg-address.patch"
    set -- "$@" "$repo_root/patches/$name/0003-dynrec-stosd-precise-page-fault.patch"
    set -- "$@" "$repo_root/patches/$name/0004-dynrec-precise-string-page-faults.patch"
    set -- "$@" "$repo_root/patches/$name/0005-dynrec-honor-supervisor-write-protect.patch"
    set -- "$@" "$repo_root/patches/$name/0006-pagefault-core-timing-diagnostics.patch"
    set -- "$@" "$repo_root/patches/$name/0007-bounded-pagefault-core-slice.patch"
  fi
  if [ -n "$(git -C "$target" status --porcelain --untracked-files=normal)" ]; then
    expected_patch_files=dosbox_pure_libretro.cpp
    if [ "$name" = dosbox-pure ]; then
expected_patch_files='dosbox_pure_libretro.cpp
include/paging.h
src/cpu/core_dynrec/decoder_opcodes.h
src/cpu/core_dynrec/operators.h
src/cpu/cpu.cpp
src/cpu/paging.cpp
src/dosbox.cpp'
    fi
    audit_dir=$(mktemp -d)
    forward_ok=1
    # Reconstruct the expected tree from pristine HEAD and apply each patch in
    # order. This remains valid when a later patch intentionally overlaps an
    # earlier one, unlike one combined reverse-check transaction.
    if ! git -C "$target" archive HEAD $expected_patch_files | tar -x -C "$audit_dir"; then
      forward_ok=0
    else
      for expected_patch in "$@"; do
        patch -s -d "$audit_dir" -p1 < "$expected_patch" || forward_ok=0
      done
      for expected_file in $expected_patch_files; do
        cmp -s "$audit_dir/$expected_file" "$target/$expected_file" || forward_ok=0
      done
    fi
    rm -rf -- "$audit_dir"
    if [ ! -f "$patch_file" ] ||
       [ "$(git -C "$target" diff --name-only)" != "$expected_patch_files" ] ||
       [ "$forward_ok" -ne 1 ]; then
      echo "Refusing unexpected dirty source tree: $target" >&2
      failed=1
      continue
    fi
  fi

  if ! git -C "$target" cat-file -e "$commit^{commit}" 2>/dev/null; then
    if [ "$mode" = verify ]; then
      echo "MISSING COMMIT $name $commit" >&2
      failed=1
      continue
    fi
    echo "Fetching pinned commit for $name"
    git -C "$target" fetch --depth 1 origin "$commit"
  fi

  current=$(git -C "$target" rev-parse HEAD 2>/dev/null || true)
  if [ "$current" != "$commit" ]; then
    if [ "$mode" = verify ]; then
      echo "REVISION MISMATCH $name expected=$commit actual=${current:-none}" >&2
      failed=1
      continue
    fi
    git -C "$target" checkout --detach "$commit"
  fi
  current=$(git -C "$target" rev-parse HEAD)
  [ "$current" = "$commit" ] || { echo "Verification failed for $name" >&2; failed=1; continue; }
  if [ -f "$patch_file" ] && [ -z "$(git -C "$target" status --porcelain --untracked-files=normal)" ]; then
    git -C "$target" apply "$@"
  fi
  echo "OK $name $current"
done < "$lock_file"

[ "$count" -gt 0 ] || { echo "No source records found in $lock_file" >&2; exit 1; }
[ "$failed" -eq 0 ] || exit 1
