#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
backup_dir="$repo_dir/backups/win98-installed-20260810"
# shellcheck source=lib/android-device.sh
source "$repo_dir/scripts/lib/android-device.sh"
package="org.robowindows.app.debug"

init_device() {
  [[ -n "${adb_bin:-}" ]] && return
  rw_select_device
  rw_require_model
  adb_bin=$RW_ADB
  device_serial=$RW_DEVICE_SERIAL
}

usage() {
  cat <<'EOF'
Usage:
  scripts/test-sm-t500-realtime.sh --verify-backup
  scripts/test-sm-t500-realtime.sh --create-host-clone PATH
  scripts/test-sm-t500-realtime.sh --check-environment
  scripts/test-sm-t500-realtime.sh --create-device-clone

Verification is read-only. Clone creation refuses an existing target and never changes the
compressed installed-state backup. Device acceptance and writable-clone modes are added only
after the protected baseline is complete.
EOF
}

verify_backup() {
  (
    cd "$backup_dir"
    sha256sum -c SHA256SUMS
    gzip -t disk.img.gz
  )
  echo "Verified compressed Windows test backup: $backup_dir"
}

create_host_clone() {
  local target=$1
  case "$target" in
    ""|/|.|..|"$repo_dir"|"$backup_dir")
      echo "Unsafe or ambiguous clone target: $target" >&2
      exit 2
      ;;
  esac
  if [[ -e "$target" ]]; then
    echo "Clone target already exists; refusing to overwrite: $target" >&2
    exit 1
  fi
  mkdir -p -- "$(dirname -- "$target")"
  gzip -cd -- "$backup_dir/disk.img.gz" >"$target"
  sha256sum -- "$target" >"$target.sha256"
  echo "Created writable host clone: $target"
  echo "The clone and checksum are local proprietary test artifacts; do not commit them."
}

check_environment() {
  local model fingerprint abi focus interactive
  init_device
  "$adb_bin" -s "$device_serial" get-state >/dev/null
  model="$($adb_bin -s "$device_serial" shell getprop ro.product.model | tr -d '\r')"
  fingerprint="$($adb_bin -s "$device_serial" shell getprop ro.build.fingerprint | tr -d '\r')"
  abi="$($adb_bin -s "$device_serial" shell getprop ro.product.cpu.abi | tr -d '\r')"
  interactive="$($adb_bin -s "$device_serial" shell dumpsys power | sed -n 's/.*mWakefulness=//p' | head -1 | tr -d '\r')"
  focus="$($adb_bin -s "$device_serial" shell dumpsys window | sed -n '/mCurrentFocus=/p' | head -1 | tr -d '\r')"

  [[ "$model" == "SM-T500" ]] || { echo "Unexpected target model: $model" >&2; exit 1; }
  [[ "$abi" == "arm64-v8a" ]] || { echo "Unexpected primary ABI: $abi" >&2; exit 1; }
  [[ "$interactive" == "Awake" ]] || { echo "Device is not awake: $interactive" >&2; exit 1; }
  [[ "$focus" == *"$package"* ]] || { echo "RoboWindows is not focused: $focus" >&2; exit 1; }

  printf 'model=%s\nabi=%s\nbuild_fingerprint_sha256=' "$model" "$abi"
  printf '%s' "$fingerprint" | sha256sum | cut -d' ' -f1
  printf 'focus=robowindows\npower=awake\n'
}

create_device_clone() {
  local source_path="files/machines/39d6f1be-d435-4bb3-a39d-2f1a930020ba/disk.img"
  local clone_dir="files/realtime-tests/win98-baseline"
  local clone_path="$clone_dir/disk.img"
  local source_hash clone_hash source_size available_kb required_kb

  init_device
  "$adb_bin" -s "$device_serial" get-state >/dev/null
  if "$adb_bin" -s "$device_serial" shell run-as "$package" ls "$clone_path" \
      >/dev/null 2>&1; then
    echo "Device clone already exists; refusing to overwrite: $clone_path" >&2
    exit 1
  fi

  source_size="$($adb_bin -s "$device_serial" shell run-as "$package" \
    stat -c %s "$source_path" | tr -d '\r')"
  available_kb="$($adb_bin -s "$device_serial" shell df -k /data/user/0 | \
    tail -1 | awk '{print $4}' | tr -d '\r')"
  required_kb=$((source_size / 1024 + 262144))
  if (( available_kb < required_kb )); then
    echo "Insufficient device space: need ${required_kb} KiB, have ${available_kb} KiB" >&2
    exit 1
  fi

  source_hash="$($adb_bin -s "$device_serial" shell run-as "$package" \
    sha256sum "$source_path" | awk '{print $1}' | tr -d '\r')"
  "$adb_bin" -s "$device_serial" shell run-as "$package" mkdir -p "$clone_dir"
  if ! "$adb_bin" -s "$device_serial" shell run-as "$package" \
      cp "$source_path" "$clone_path"; then
    "$adb_bin" -s "$device_serial" shell run-as "$package" rm -f "$clone_path"
    echo "Device clone copy failed; incomplete target removed" >&2
    exit 1
  fi
  clone_hash="$($adb_bin -s "$device_serial" shell run-as "$package" \
    sha256sum "$clone_path" | awk '{print $1}' | tr -d '\r')"
  if [[ "$source_hash" != "$clone_hash" ]]; then
    "$adb_bin" -s "$device_serial" shell run-as "$package" rm -f "$clone_path"
    echo "Device clone checksum mismatch; incomplete target removed" >&2
    exit 1
  fi

  printf 'device_clone=%s\nsize_bytes=%s\nsha256=%s\n' \
    "$clone_path" "$source_size" "$clone_hash"
}

case "${1:-}" in
  --verify-backup)
    [[ $# -eq 1 ]] || { usage >&2; exit 2; }
    verify_backup
    ;;
  --create-host-clone)
    [[ $# -eq 2 ]] || { usage >&2; exit 2; }
    verify_backup
    create_host_clone "$2"
    ;;
  --check-environment)
    [[ $# -eq 1 ]] || { usage >&2; exit 2; }
    check_environment
    ;;
  --create-device-clone)
    [[ $# -eq 1 ]] || { usage >&2; exit 2; }
    create_device_clone
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac
