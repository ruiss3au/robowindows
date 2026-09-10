#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/android-device.sh
source "$repo_dir/scripts/lib/android-device.sh"
package=org.robowindows.app.debug
profile=
seconds=40
output_dir=

usage() {
  cat <<'EOF'
Usage: scripts/capture-sm-t500-benchmark.sh --profile PROFILE [--seconds 35..60] [--output-dir DIRECTORY]

Profiles: dynrec-fixed-20k, normal-fixed-12k, normal-fixed-14k,
          normal-fixed-20k, normal-fixed-30k

Start this only with the clean experimental copy already at the Windows desktop.
Launch RWBENCH.EXE immediately after capture begins. The script never starts,
stops, or changes a guest and writes only ignored host-side evidence.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --profile) profile=${2:-}; shift 2 ;;
    --seconds) seconds=${2:-}; shift 2 ;;
    --output-dir) output_dir=${2:-}; shift 2 ;;
    *) usage >&2; exit 2 ;;
  esac
done
case "$profile" in
  dynrec-fixed-20k|normal-fixed-12k|normal-fixed-14k|normal-fixed-20k|normal-fixed-30k) ;;
  *) usage >&2; exit 2 ;;
esac
[[ $seconds =~ ^[0-9]+$ ]] && (( seconds >= 35 && seconds <= 60 )) || {
  echo "Capture duration must be 35 through 60 seconds" >&2
  exit 2
}
if [[ -z $output_dir ]]; then
  output_dir="$repo_dir/artifacts/benchmark-captures/$(date -u +%Y%m%dT%H%M%SZ)-$profile"
fi
[[ ! -e $output_dir ]] || {
  echo "Capture output already exists: $output_dir" >&2
  exit 1
}

rw_select_device
rw_require_model
interactive="$("${RW_ADB_TARGET[@]}" shell dumpsys power | sed -n 's/.*mWakefulness=//p' | head -1 | tr -d '\r')"
focus="$("${RW_ADB_TARGET[@]}" shell dumpsys window | sed -n '/mCurrentFocus=/{p;q}' | tr -d '\r')"
[[ $interactive == Awake ]] || { echo "SM-T500 is not awake" >&2; exit 1; }
[[ $focus == *"$package"* ]] || { echo "RoboWindows is not foreground" >&2; exit 1; }

processes="$("${RW_ADB_TARGET[@]}" shell ps -A | awk '/org\.robowindows\.app\.debug/ {print $NF}' | tr -d '\r')"
if [[ $profile == dynrec-* ]]; then
  grep -q '^org\.robowindows\.app\.debug:dynrec$' <<<"$processes" || {
    echo "The isolated DynRec runner is not active" >&2
    exit 1
  }
else
  if grep -q '^org\.robowindows\.app\.debug:dynrec$' <<<"$processes"; then
    echo "A Normal capture cannot run while the DynRec runner exists" >&2
    exit 1
  fi
fi

mkdir -p "$output_dir"

thermal_snapshot() {
  local destination=$1 battery_tenths status
  battery_tenths="$("${RW_ADB_TARGET[@]}" shell dumpsys battery | sed -n 's/^[[:space:]]*temperature: //p' | head -1 | tr -d '\r')"
  status="$("${RW_ADB_TARGET[@]}" shell dumpsys thermalservice | sed -n 's/.*Thermal Status: //p' | head -1 | tr -d '\r')"
  {
    printf 'battery_tenths_c=%s\n' "${battery_tenths:-unknown}"
    printf 'android_thermal_status=%s\n' "${status:-unknown}"
    "${RW_ADB_TARGET[@]}" shell 'count=0; for zone in /sys/class/thermal/thermal_zone*; do [ "$count" -lt 64 ] || break; type=$(cat "$zone/type" 2>/dev/null) || continue; temp=$(cat "$zone/temp" 2>/dev/null) || continue; printf "zone_%s_type=%s\nzone_%s_millic=%s\n" "$count" "$type" "$count" "$temp"; count=$((count + 1)); done' | tr -d '\r'
  } >"$destination"
}

apk_path="$("${RW_ADB_TARGET[@]}" shell pm path "$package" | sed -n 's/^package://p' | head -1 | tr -d '\r')"
apk_hash="$("${RW_ADB_TARGET[@]}" shell sha256sum "$apk_path" | awk '{print $1}' | tr -d '\r')"
version_name="$("${RW_ADB_TARGET[@]}" shell dumpsys package "$package" | sed -n 's/^[[:space:]]*versionName=//p' | head -1 | tr -d '\r')"
{
  printf 'capture_schema=1\nprofile=%s\nduration_s=%s\n' "$profile" "$seconds"
  printf 'device_model=SM-T500\napp_version=%s\ninstalled_apk_sha256=%s\n' \
    "$version_name" "$apk_hash"
} >"$output_dir/metadata.txt"

thermal_snapshot "$output_dir/thermal-start.txt"
since="$("${RW_ADB_TARGET[@]}" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
echo "Capture started. Launch RWBENCH.EXE now; it runs for 30 seconds."
set +e
timeout "${seconds}s" "${RW_ADB_TARGET[@]}" logcat -v threadtime -T "$since" \
  RoboWindowsTelemetry:I RoboWindowsTiming:I RoboWindowsWorker:I RoboWindowsCalibration:I RoboWindowsCore:I RoboWindowsDynrec:I '*:S' \
  >"$output_dir/telemetry.log"
logcat_status=$?
set -e
[[ $logcat_status -eq 0 || $logcat_status -eq 124 ]] || {
  echo "ADB telemetry capture failed" >&2
  exit 1
}
thermal_snapshot "$output_dir/thermal-end.txt"
"${RW_ADB_TARGET[@]}" exec-out screencap -p >"$output_dir/result-screen.png"
"$repo_dir/scripts/summarize-benchmark-telemetry.sh" "$profile" \
  "$output_dir/telemetry.log" >"$output_dir/telemetry-summary.txt"
{
  cat "$output_dir/metadata.txt"
  cat "$output_dir/telemetry-summary.txt"
  sed 's/^/thermal_start_/' "$output_dir/thermal-start.txt"
  sed 's/^/thermal_end_/' "$output_dir/thermal-end.txt"
  printf 'guest_result=pending_strict_parse\n'
  printf 'human_audio_quality=pending\nphysical_input=pending\nclean_shutdown=pending\n'
} >"$output_dir/report.txt"
echo "Captured benchmark evidence: $output_dir/report.txt"
