#!/usr/bin/env bash
# Shared Android SDK and single-device discovery for RoboWindows tools.

if [[ -n "${ROBOWINDOWS_ANDROID_DEVICE_SH_LOADED:-}" ]]; then
  return 0
fi
ROBOWINDOWS_ANDROID_DEVICE_SH_LOADED=1

rw_find_adb() {
  local candidate
  if [[ -n "${ADB:-}" ]]; then
    candidate=$ADB
  elif [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    candidate=$ANDROID_SDK_ROOT/platform-tools/adb
  elif [[ -n "${ANDROID_HOME:-}" ]]; then
    candidate=$ANDROID_HOME/platform-tools/adb
  elif command -v adb >/dev/null 2>&1; then
    candidate=$(command -v adb)
  else
    candidate=${XDG_DATA_HOME:-$HOME/.local/share}/robowindows/android-sdk/platform-tools/adb
  fi
  [[ -x "$candidate" ]] || {
    echo "ADB was not found. Set ADB or ANDROID_SDK_ROOT." >&2
    return 1
  }
  RW_ADB=$candidate
}

rw_select_device() {
  local -a devices=()
  local serial state remainder
  rw_find_adb
  if [[ -n "${ROBOWINDOWS_DEVICE_SERIAL:-}" ]]; then
    RW_DEVICE_SERIAL=$ROBOWINDOWS_DEVICE_SERIAL
    state=$("$RW_ADB" -s "$RW_DEVICE_SERIAL" get-state 2>/dev/null || true)
    [[ "$state" == device ]] || {
      echo "ADB device is unavailable: $RW_DEVICE_SERIAL" >&2
      return 1
    }
  else
    while read -r serial state remainder; do
      [[ "$state" == device ]] && devices+=("$serial")
    done < <("$RW_ADB" devices)
    case ${#devices[@]} in
      1) RW_DEVICE_SERIAL=${devices[0]} ;;
      0) echo "No authorized ADB device is connected." >&2; return 1 ;;
      *) echo "Multiple ADB devices are connected; set ROBOWINDOWS_DEVICE_SERIAL." >&2
         return 1 ;;
    esac
  fi
  RW_ADB_TARGET=("$RW_ADB" -s "$RW_DEVICE_SERIAL")
}

rw_require_model() {
  local expected=${ROBOWINDOWS_EXPECTED_MODEL:-SM-T500}
  local actual
  actual=$("${RW_ADB_TARGET[@]}" shell getprop ro.product.model | tr -d '\r')
  [[ "$actual" == "$expected" ]] || {
    echo "Unexpected Android model: $actual (expected $expected)" >&2
    return 1
  }
}
