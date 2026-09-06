#!/usr/bin/env bash
# Remove only the two confirmed disposable SM-T500 debug-storage areas.
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=lib/android-device.sh
source "$repo_dir/scripts/lib/android-device.sh"

package=org.robowindows.app.debug
clone_dir=files/realtime-tests/win98-baseline
metadata=shared_prefs/machine_store.xml
mode=${1:-}

usage() {
  cat <<'EOF'
Usage: scripts/cleanup-tablet-storage.sh --preflight | --delete | --verify-cleanup

--preflight performs no tablet writes. --delete permanently removes only the
verified real-time clone and unreferenced debug Windows-import directory.
--verify-cleanup confirms the completed cleanup without changing tablet data.
EOF
}

[[ "$mode" == --preflight || "$mode" == --delete || "$mode" == --verify-cleanup ]] || {
  usage >&2
  exit 2
}

rw_select_device
rw_require_model
adb_target=("$RW_ADB" -s "$RW_DEVICE_SERIAL")

remote() {
  local command encoded
  command="$*"
  encoded="$(printf '%s' "$command" | base64 | tr -d '\n')"
  "${adb_target[@]}" shell "run-as $package sh -c 'echo $encoded | base64 -d | sh'"
}

require_machines_screen() {
  local focus dump
  focus="$("${adb_target[@]}" shell dumpsys window | sed -n '/mCurrentFocus=/p' | head -1 | tr -d '\r')"
  [[ "$focus" == *"$package"* ]] || {
    echo "RoboWindows must be foregrounded at the Machines screen." >&2
    return 1
  }
  dump="$(mktemp)"
  trap 'rm -f -- "${dump:-}"' RETURN
  "${adb_target[@]}" exec-out uiautomator dump /dev/tty >"$dump" 2>/dev/null || {
    echo "Unable to inspect the RoboWindows screen." >&2
    return 1
  }
  grep -q 'text="Machines"' "$dump" || {
    echo "RoboWindows is not at the Machines screen." >&2
    return 1
  }
}

preflight() {
  require_machines_screen
  remote '
    set -eu
    test -f "'$metadata'" || { echo "metadata=missing"; exit 10; }
    grep -q active_session "'$metadata'" && { echo "active_session=present"; exit 11; }
    test -d files/machines || { echo "machine_library=missing"; exit 12; }
    test -d "'$clone_dir'" || { echo "realtime_clone=missing"; exit 13; }
    clone_files="$(find "'$clone_dir'" -type f -printf "%f\\n" | sort)"
    test "$clone_files" = "disk.img
launch.conf
normal-12000.conf
normal-20000.conf" || {
      echo "realtime_clone_layout=unexpected"
      find "'$clone_dir'" -type f -printf "realtime_clone_file=%f\\n" | sort
      exit 14
    }
    test -f "'$clone_dir'/disk.img" || { echo "realtime_clone_layout=unexpected"; exit 14; }

    # The import target is deliberately outside the machine library. It is the
    # sole large (0.5--0.8 GB) debug-only directory outside protected areas.
    candidate_count=0
    candidate=
    for directory in files/* cache/*; do
      test -d "$directory" || continue
      case "$directory" in
        files/machines|files/realtime-tests|files/system|files/saves) continue ;;
      esac
      bytes=$(du -sk "$directory" | awk "{print \$1 * 1024}")
      if [ "$bytes" -ge 500000000 ] && [ "$bytes" -le 800000000 ]; then
        candidate_count=$((candidate_count + 1))
        candidate=$directory
      fi
    done
    test "$candidate_count" -eq 1 || {
      echo "debug_windows_import_candidates=$candidate_count"; exit 15; }
    candidate_id=${candidate##*/}
    grep -Fq "$candidate_id" "'$metadata'" && {
      echo "debug_windows_import_metadata_reference=present"; exit 16; }
    candidate_files="$(find "$candidate" -type f -printf "%f\\n" | sort)"
    test "$candidate_files" = "incoming.iso
patch9x.img" || {
      echo "debug_windows_import_layout=unexpected"; exit 17; }
    # A protected stable profile must remain represented by distinct metadata.
    grep -q profiles "'$metadata'" || { echo "stable_machine_metadata=missing"; exit 18; }

    du -sk files/machines | awk "{printf \"machine_library_bytes=%d\\n\", \$1 * 1024}"
    du -sk "'$clone_dir'" | awk "{printf \"realtime_clone_bytes=%d\\n\", \$1 * 1024}"
    sha256sum "'$clone_dir'/disk.img" | awk "{print \"realtime_clone_sha256=\" \$1}"
    du -sk "$candidate" | awk "{printf \"debug_windows_import_bytes=%d\\n\", \$1 * 1024}"
    find "$candidate" -type f -printf "%f\\n" | sort | while read -r name; do
      sha256sum "$candidate/$name" | awk -v name="$name" "{print \"debug_windows_import_sha256[\" name \"]=\" \$1}"
    done
    echo "active_session=absent"
    echo "machine_library=present"
    echo "debug_windows_import_metadata_reference=absent"
  '
}

delete_verified_targets() {
  remote '
    set -eu
    # Re-run all target resolution in this destructive transaction; never take
    # a target path from the host or from prior command output.
    test -f "'$metadata'" && ! grep -q active_session "'$metadata'"
    test -d files/machines
    test -d "'$clone_dir'" && test -f "'$clone_dir'/disk.img"
    clone_files="$(find "'$clone_dir'" -type f -printf "%f\\n" | sort)"
    test "$clone_files" = "disk.img
launch.conf
normal-12000.conf
normal-20000.conf"
    candidate_count=0
    candidate=
    for directory in files/* cache/*; do
      test -d "$directory" || continue
      case "$directory" in files/machines|files/realtime-tests|files/system|files/saves) continue ;; esac
      bytes=$(du -sk "$directory" | awk "{print \$1 * 1024}")
      if [ "$bytes" -ge 500000000 ] && [ "$bytes" -le 800000000 ]; then
        candidate_count=$((candidate_count + 1)); candidate=$directory
      fi
    done
    test "$candidate_count" -eq 1
    ! grep -Fq "${candidate##*/}" "'$metadata'"
    candidate_files="$(find "$candidate" -type f -printf "%f\\n" | sort)"
    test "$candidate_files" = "incoming.iso
patch9x.img"
    before_library=$(du -sk files/machines | awk "{print \$1 * 1024}")
    before_total=$(du -sk "'$clone_dir'" "$candidate" | awk "{sum += \$1 * 1024} END {print sum}")
    rm -rf -- "'$clone_dir'"
    test ! -e "'$clone_dir'"
    after_first_library=$(du -sk files/machines | awk "{print \$1 * 1024}")
    test "$before_library" -eq "$after_first_library"
    rm -rf -- "$candidate"
    test ! -e "$candidate"
    after_library=$(du -sk files/machines | awk "{print \$1 * 1024}")
    test "$before_library" -eq "$after_library"
    echo "machine_library_bytes=$after_library"
    echo "reclaimed_target_bytes=$before_total"
    echo "app_storage_reset=absent"
    echo "machine_library_removed=absent"
  '
}

verify_cleanup() {
  require_machines_screen
  remote '
    set -eu
    test ! -e files/realtime-tests/win98-baseline
    candidates=0
    for directory in files/* cache/*; do
      test -d "$directory" || continue
      case "$directory" in files/machines|files/realtime-tests|files/system|files/saves) continue ;; esac
      bytes=$(du -sk "$directory" | awk "{print \$1 * 1024}")
      if [ "$bytes" -ge 500000000 ] && [ "$bytes" -le 800000000 ]; then candidates=$((candidates + 1)); fi
    done
    test "$candidates" -eq 0
    test -d files/machines
    ! grep -q active_session shared_prefs/machine_store.xml
    du -sk files/machines | awk "{printf \"machine_library_bytes=%d\\n\", \$1 * 1024}"
    echo "realtime_clone=absent"
    echo "debug_windows_import=absent"
    echo "active_session=absent"
    echo "machines_screen=present"
  '
}

case "$mode" in
  --preflight) preflight ;;
  --delete) preflight; delete_verified_targets ;;
  --verify-cleanup) verify_cleanup ;;
esac
