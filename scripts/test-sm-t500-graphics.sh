#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_dir/scripts/lib/android-device.sh"
rw_select_device
rw_require_model
package=org.robowindows.app.debug
for process in "$package:dynrec" "$package:cpu_normal" "$package:cpu_dynamic" "$package:presentation_fixture"; do
  [[ -z $("${RW_ADB_TARGET[@]}" shell pidof "$process" || true) ]] || {
    echo "Stop the guest or fixture first" >&2; exit 1;
  }
done
preferences=$("${RW_ADB_TARGET[@]}" shell run-as "$package" cat shared_prefs/machine_store.xml)
if rg -q 'name="active_session"' <<<"$preferences"; then
  echo "Shut down the guest normally first" >&2; exit 1
fi
journals=$("${RW_ADB_TARGET[@]}" shell run-as "$package" find files -name dynamic-attempt.json)
[[ -z "$journals" ]] || { echo "Resolve interrupted trials first" >&2; exit 1; }
echo "Tablet must be unlocked and foregrounded; this probe opens no guest media."
"${RW_ADB_TARGET[@]}" shell am force-stop "$package"
"${RW_ADB_TARGET[@]}" logcat -c
"${RW_ADB_TARGET[@]}" shell am start -W -n "$package/org.robowindows.app.MainActivity" \
  --ez robowindows.testGraphics true >/dev/null
for attempt in {1..20}; do
  logs=$("${RW_ADB_TARGET[@]}" logcat -d -s RoboWindowsGraphics:I AndroidRuntime:E libc:F)
  if rg -q 'RoboWindowsGraphics: (PASS|FAIL)' <<<"$logs"; then
    printf '%s\n' "$logs"
    rg -q 'RoboWindowsGraphics: PASS .* surface fallback' <<<"$logs"
    exit
  fi
  if rg -q 'FATAL EXCEPTION|Fatal signal' <<<"$logs"; then
    echo "Graphics probe crashed" >&2; exit 1
  fi
  sleep 1
done
echo "Graphics probe timed out; inspect the disposable surface test" >&2
exit 1
