#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_dir/scripts/lib/android-device.sh"
rw_select_device
rw_require_model
core=${1:-}
policy=${2:-}
workload=${3:-tone}
cache_phase=${workload#cache-}
cache_workload=false
case "$workload" in cache-warm|cache-cold|cache-reuse|cache-data|cache-rewrite) cache_workload=true ;; esac
[[ $# -le 3 && ($core == normal || $core == dynamic) && ($policy == 0 || $policy == 1) &&
    ($workload == tone || $workload == stress || $cache_workload == true) ]] || {
  echo "Usage: bash scripts/test-sm-t500-presentation.sh normal|dynamic 0|1 [tone|stress|cache-warm|cache-cold|cache-reuse|cache-data|cache-rewrite]" >&2; exit 2;
}
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
output=$(mktemp -d "$repo_dir/artifacts/presentation-${core}-${policy}-${workload}-XXXXXX")
tag=RoboWindowsPresentation
[[ $cache_workload == false ]] || tag=RoboWindowsCacheFixture
sha256sum "$repo_dir/artifacts/robowindows-debug.apk" >"$output/apk.sha256"
installed_path=$("${RW_ADB_TARGET[@]}" shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p')
[[ $installed_path == /data/app/*/base.apk && $installed_path != *$'\n'* ]] || {
  echo "Installed APK location is not a single package" >&2; exit 1;
}
installed_hash=$("${RW_ADB_TARGET[@]}" exec-out cat "$installed_path" | sha256sum | cut -d' ' -f1)
[[ $installed_hash == "$(cut -d' ' -f1 "$output/apk.sha256")" ]] || {
  echo "Installed APK differs from the local validation build" >&2; exit 1;
}
"${RW_ADB_TARGET[@]}" shell am force-stop "$package"
"${RW_ADB_TARGET[@]}" logcat -c
echo "Starting disposable $core presentation=$policy workload=$workload; keep the tablet unlocked and foregrounded."
"${RW_ADB_TARGET[@]}" shell am start -W -n "$package/org.robowindows.app.MainActivity" \
  --ez robowindows.testPresentationWorkload true --es core "$core" --ei presentation "$policy" \
  --es workload "$workload" >/dev/null
for attempt in {1..150}; do
  "${RW_ADB_TARGET[@]}" logcat -d -s RoboWindowsPresentation:I RoboWindowsTelemetry:I \
    RoboWindowsTiming:I RoboWindowsWorker:I RoboWindowsCalibration:I RoboWindowsCacheFixture:I \
    AndroidRuntime:E libc:F >"$output/telemetry.log"
  if rg -q "$tag: (PASS|FAIL)" "$output/telemetry.log"; then
    rg "$tag:" "$output/telemetry.log"
    rg -q "$tag: PASS" "$output/telemetry.log"
    if [[ $cache_workload == true ]]; then
      bash "$repo_dir/scripts/summarize-cache-fixture.sh" "$core" "$policy" "$cache_phase" "$output/telemetry.log" | tee "$output/summary.txt"
    else
      bash "$repo_dir/scripts/summarize-presentation.sh" "$core" "$policy" "$output/telemetry.log" "$workload" | tee "$output/summary.txt"
    fi
    exit
  fi
  if rg -q 'FATAL EXCEPTION|Fatal signal' "$output/telemetry.log"; then
    echo "Presentation fixture crashed; capture: $output" >&2; exit 1
  fi
  sleep 1
done
echo "Presentation fixture timed out or CPU gate is not passed; capture: $output" >&2
exit 1
