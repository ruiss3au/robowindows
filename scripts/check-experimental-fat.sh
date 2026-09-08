#!/usr/bin/env bash
# Read-only health inspection for a quarantined experimental FAT volume.
set -euo pipefail

CHECKER_VERSION="robowindows-fat-check-v1"

usage() {
  echo "Usage: $0 --image RAW_FAT_VOLUME --expected RELATIVE_PATH [--expected RELATIVE_PATH ...]" >&2
  exit 2
}

image=""
expected=()
while (($#)); do
  case "$1" in
    --image) (($# >= 2)) || usage; image=$2; shift 2 ;;
    --expected) (($# >= 2)) || usage; expected+=("$2"); shift 2 ;;
    --help) usage ;;
    *) usage ;;
  esac
done

[[ -n "$image" && -f "$image" && ${#expected[@]} -gt 0 ]] || usage
case ${image,,} in
  *.vhd|*.vhdx|*.qcow2|*.iso) echo "unsupported-volume-container" >&2; exit 3 ;;
esac
for path in "${expected[@]}"; do
  [[ -n "$path" && "$path" != /* && "$path" != *".."* && "$path" != *$'\n'* ]] || {
    echo "invalid-fixture-path" >&2; exit 2;
  }
done

find_tool() {
  local candidate=$1
  if [[ "$candidate" == */* ]]; then
    [[ -x "$candidate" ]] || return 1
    printf '%s\n' "$candidate"
  else
    command -v "$candidate"
  fi
}

fsck_tool="${ROBOWINDOWS_FSCK_FAT:-}"
if [[ -z "$fsck_tool" ]]; then
  fsck_tool=$(find_tool fsck.fat || find_tool dosfsck) || {
    echo "missing-read-only-fat-checker" >&2; exit 4;
  }
else
  fsck_tool=$(find_tool "$fsck_tool") || { echo "missing-read-only-fat-checker" >&2; exit 4; }
fi
mtype_tool=$(find_tool "${ROBOWINDOWS_MTYPE:-mtype}") || {
  echo "missing-read-only-fat-reader" >&2; exit 4;
}

before=$(sha256sum -- "$image" | awk '{print $1}')
"$fsck_tool" -n -- "$image" >/dev/null
for path in "${expected[@]}"; do
  "$mtype_tool" -i "$image" "::$path" >/dev/null
done
after=$(sha256sum -- "$image" | awk '{print $1}')
[[ "$before" == "$after" ]] || { echo "inspection-modified-image" >&2; exit 5; }
printf 'checker=%s status=pass image_sha256=%s fixtures=%d\n' \
  "$CHECKER_VERSION" "$after" "${#expected[@]}"
