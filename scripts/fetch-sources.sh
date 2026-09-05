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
  if [ -n "$(git -C "$target" status --porcelain --untracked-files=normal)" ]; then
    if [ ! -f "$patch_file" ] ||
       [ "$(git -C "$target" diff --name-only)" != "dosbox_pure_libretro.cpp" ] ||
       ! git -C "$target" apply --reverse --check "$patch_file"; then
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
    git -C "$target" apply "$patch_file"
  fi
  echo "OK $name $current"
done < "$lock_file"

[ "$count" -gt 0 ] || { echo "No source records found in $lock_file" >&2; exit 1; }
[ "$failed" -eq 0 ] || exit 1
