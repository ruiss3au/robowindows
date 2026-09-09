#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/android-device.sh
source "$repo_dir/scripts/lib/android-device.sh"
rw_select_device
rw_require_model
adb_bin=$RW_ADB
export ANDROID_SERIAL=$RW_DEVICE_SERIAL
package="org.robowindows.app.debug"
if [[ -n $("$adb_bin" shell pm path "$package" 2>/dev/null) ]]; then
  for process in "$package:dynrec" "$package:cpu_normal" "$package:cpu_dynamic" "$package:presentation_fixture"; do
    [[ -z $("$adb_bin" shell pidof "$process" || true) ]] || {
      echo "Stop the guest or CPU fixture before installing" >&2; exit 1;
    }
  done
  if "$adb_bin" shell run-as "$package" test -f shared_prefs/machine_store.xml; then
    preferences=$("$adb_bin" shell run-as "$package" cat shared_prefs/machine_store.xml)
    if rg -q 'name="active_session"' <<<"$preferences"; then
      echo "Shut down the guest normally before installing" >&2; exit 1
    fi
  fi
  journals=$("$adb_bin" shell run-as "$package" find files -name dynamic-attempt.json | tr -d '\r')
  [[ -z "$journals" ]] || { echo "Resolve the interrupted trial before testing" >&2; exit 1; }
fi
fixture_dir="$(mktemp -d)"
device_stage_image="/data/local/tmp/robowindows-core-test.img"
device_stage_config="/data/local/tmp/robowindows-core-test.conf"
device_stage_media="/data/local/tmp/robowindows-core-test-media.img"
device_private_config="/data/user/0/$package/files/debug-core-test/launch.conf"
device_private_media="/data/user/0/$package/files/debug-core-test/media.img"
original_stay_awake="$($adb_bin shell settings get global stay_on_while_plugged_in | tr -d '\r')"

cleanup() {
  if [[ "${KEEP_SESSION:-0}" != "1" ]]; then
    "$adb_bin" shell am force-stop "$package" >/dev/null 2>&1 || true
    "$adb_bin" shell run-as "$package" rm -r files/debug-core-test >/dev/null 2>&1 || true
  fi
  "$adb_bin" shell rm "$device_stage_image" "$device_stage_config" "$device_stage_media" >/dev/null 2>&1 || true
  if [[ "$original_stay_awake" == "null" || -z "$original_stay_awake" ]]; then
    "$adb_bin" shell settings delete global stay_on_while_plugged_in >/dev/null 2>&1 || true
  else
    "$adb_bin" shell settings put global stay_on_while_plugged_in "$original_stay_awake" >/dev/null 2>&1 || true
  fi
  rm -rf -- "$fixture_dir"
}
trap cleanup EXIT

truncate -s 1474560 "$fixture_dir/test.img"
truncate -s 1474560 "$fixture_dir/media.img"
cat >"$fixture_dir/launch.conf" <<'EOF'
[dosbox]
memsize=16
[cpu]
core=normal
[mixer]
nosound=true
[autoexec]
@echo off
boot "/data/user/0/org.robowindows.app.debug/files/debug-core-test/test.img"
EOF

"$adb_bin" install -r "$repo_dir/artifacts/robowindows-debug.apk" >/dev/null
"$adb_bin" push "$fixture_dir/test.img" "$device_stage_image" >/dev/null
"$adb_bin" push "$fixture_dir/launch.conf" "$device_stage_config" >/dev/null
"$adb_bin" push "$fixture_dir/media.img" "$device_stage_media" >/dev/null
"$adb_bin" shell run-as "$package" mkdir -p files/debug-core-test
"$adb_bin" shell run-as "$package" cp "$device_stage_image" files/debug-core-test/test.img
"$adb_bin" shell run-as "$package" cp "$device_stage_config" files/debug-core-test/launch.conf
"$adb_bin" shell run-as "$package" cp "$device_stage_media" files/debug-core-test/media.img
"$adb_bin" logcat -c
"$adb_bin" shell am force-stop "$package"
"$adb_bin" shell svc power stayon usb
"$adb_bin" shell input keyevent KEYCODE_WAKEUP
"$adb_bin" shell wm dismiss-keyguard
"$adb_bin" shell cmd statusbar collapse
launch_args=(--es robowindows.testConfig "$device_private_config")
if [[ "${KEEP_SESSION:-0}" != "1" ]]; then
  launch_args+=(--ez robowindows.testPersistence true --ez robowindows.testLifecycle true
    --es robowindows.testMedia "$device_private_media")
fi
"$adb_bin" shell am start -W -n "$package/org.robowindows.app.MainActivity" \
  "${launch_args[@]}" >/dev/null
if [[ "${KEEP_SESSION:-0}" == "1" ]]; then
  echo "Disposable SM-T500 guest session left running"
  exit 0
fi
"$adb_bin" shell sleep 7
logs="$($adb_bin logcat -d -s RoboWindowsCore:V RoboWindowsTelemetry:I RoboWindowsTest:I AndroidRuntime:E DEBUG:E libc:F)"
printf '%s\n' "$logs"
grep -q 'guest started' <<<"$logs"
grep -q 'persistence and input bridge probes passed' <<<"$logs"
grep -q 'first guest frame 640x400' <<<"$logs"
grep -q 'audio stream open requested_rate=48000 actual_rate=48000' <<<"$logs"
# nosound=true has no producer frames; an opened stream is not an audio quality pass.
grep -Eq 'schema=[34] .*audio_state=prebuffering .*audio_produced=0' <<<"$logs"
grep -q 'guest paused' <<<"$logs"
grep -q 'guest resumed' <<<"$logs"
grep -q 'media change queued' <<<"$logs"
grep -q 'media change completed' <<<"$logs"
grep -q 'guest stopped cleanly' <<<"$logs"
grep -q 'guest restart completed' <<<"$logs"
if [[ "${REQUIRE_SURFACE:-0}" == "1" ]]; then
  grep -Eq 'schema=[34] .*presented=[1-9][0-9]* .*post_failures=0' <<<"$logs"
elif grep -Eq 'schema=[34] .*presented=[1-9][0-9]* .*post_failures=0' <<<"$logs"; then
  echo "Guest frame was rendered to the Android surface"
else
  echo "Guest surface was unavailable; rerun unlocked with REQUIRE_SURFACE=1" >&2
fi
if grep -Eq 'FATAL EXCEPTION|Fatal signal|guest load failed' <<<"$logs"; then
  echo "Core test reported a fatal error" >&2
  exit 1
fi

echo "SM-T500 direct-boot core test passed"
