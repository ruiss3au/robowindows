#!/usr/bin/env bash
# Update the debug APK without clearing RoboWindows app data.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/.." && pwd)
# shellcheck source=lib/android-device.sh
source "$script_dir/lib/android-device.sh"

[[ $# -le 1 ]] || { echo "Usage: scripts/install-debug.sh [APK]" >&2; exit 2; }
if [[ ${1:-} == -h || ${1:-} == --help ]]; then
  echo "Usage: scripts/install-debug.sh [APK]"
  exit 0
fi
apk=${1:-$repo_root/artifacts/robowindows-debug.apk}
[[ -f "$apk" ]] || { echo "APK not found: $apk" >&2; exit 1; }
rw_select_device
rw_require_model
"${RW_ADB_TARGET[@]}" install -r "$apk"
echo "Updated RoboWindows on $RW_DEVICE_SERIAL without clearing app data."
