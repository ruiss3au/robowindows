#!/usr/bin/env bash
# Stream one stopped RoboWindows machine into a verified Debian-side backup.
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/.." && pwd)
# shellcheck source=lib/android-device.sh
source "$script_dir/lib/android-device.sh"

package=${ROBOWINDOWS_PACKAGE:-org.robowindows.app.debug}
backup_root=${ROBOWINDOWS_BACKUP_ROOT:-$repo_root/backups}
machine_id=
backup_name=

usage() {
  cat <<'EOF'
Usage: scripts/backup-device-profile.sh --machine-id ID --name NAME

Creates backups/NAME on the Debian host from a stopped RoboWindows profile.
Set ROBOWINDOWS_DEVICE_SERIAL when more than one ADB device is connected.
Set ROBOWINDOWS_BACKUP_ROOT to place the backup elsewhere on Debian.
EOF
}

while (($#)); do
  case $1 in
    --machine-id) [[ $# -ge 2 ]] || { usage >&2; exit 2; }; machine_id=$2; shift 2 ;;
    --name) [[ $# -ge 2 ]] || { usage >&2; exit 2; }; backup_name=$2; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

[[ "$machine_id" =~ ^[A-Za-z0-9._-]+$ ]] || {
  echo "Machine ID contains unsafe characters." >&2; exit 2;
}
[[ "$backup_name" =~ ^[A-Za-z0-9._-]+$ && "$backup_name" != . && "$backup_name" != .. ]] || {
  echo "Backup name contains unsafe characters." >&2; exit 2;
}

rw_select_device
rw_require_model
mkdir -p -- "$backup_root"
backup_root=$(cd -- "$backup_root" && pwd)
destination=$backup_root/$backup_name
[[ ! -e "$destination" ]] || {
  echo "Backup destination already exists: $destination" >&2; exit 1;
}

running=$("${RW_ADB_TARGET[@]}" shell pidof "$package" | tr -d '\r' || true)
[[ -z "$running" ]] || {
  echo "RoboWindows is running. Shut down Windows and stop the app before backup." >&2
  exit 1
}

private_root=files/machines/$machine_id
"${RW_ADB_TARGET[@]}" shell run-as "$package" test -f "$private_root/disk.img" || {
  echo "Machine disk is unavailable: $machine_id" >&2; exit 1;
}
"${RW_ADB_TARGET[@]}" shell run-as "$package" test -f "$private_root/launch.conf" || {
  echo "Machine launch configuration is unavailable: $machine_id" >&2; exit 1;
}

stage_dir=$(mktemp -d "$backup_root/.${backup_name}.part.XXXXXX")
cleanup() {
  if [[ -n "${stage_dir:-}" && -d "$stage_dir" ]]; then
    rm -rf -- "$stage_dir"
  fi
}
trap cleanup EXIT

source_size=$("${RW_ADB_TARGET[@]}" shell run-as "$package" \
  stat -c %s "$private_root/disk.img" | tr -d '\r')
source_hash=$("${RW_ADB_TARGET[@]}" shell run-as "$package" \
  sha256sum "$private_root/disk.img" | awk '{print $1}' | tr -d '\r')
[[ "$source_size" =~ ^[0-9]+$ && "$source_hash" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Tablet returned invalid disk provenance." >&2; exit 1;
}

echo "Backing up Android device $RW_DEVICE_SERIAL machine $machine_id"
echo "Debian destination: $destination"
echo "Source: $source_size bytes, SHA-256 $source_hash"

"${RW_ADB_TARGET[@]}" exec-out run-as "$package" \
  dd "if=$private_root/disk.img" bs=4M status=none | gzip -1 >"$stage_dir/disk.img.gz"
"${RW_ADB_TARGET[@]}" exec-out run-as "$package" \
  dd "if=$private_root/launch.conf" status=none >"$stage_dir/launch.conf"

copy_optional() {
  local private_path=$1 output_name=$2
  if "${RW_ADB_TARGET[@]}" shell run-as "$package" test -f "$private_path"; then
    "${RW_ADB_TARGET[@]}" exec-out run-as "$package" \
      dd "if=$private_path" status=none >"$stage_dir/$output_name"
  fi
}
copy_optional "$private_root/launch.auto.conf" launch.auto.conf
copy_optional "$private_root/cdboot.img" cdboot.img
copy_optional shared_prefs/machine_store.xml machine_store.xml

gzip -t "$stage_dir/disk.img.gz"
restored_size=$(gzip -cd "$stage_dir/disk.img.gz" | wc -c)
restored_hash=$(gzip -cd "$stage_dir/disk.img.gz" | sha256sum | awk '{print $1}')
[[ "$restored_size" == "$source_size" ]] || {
  echo "Backup size mismatch: source=$source_size restored=$restored_size" >&2; exit 1;
}
[[ "$restored_hash" == "$source_hash" ]] || {
  echo "Backup checksum mismatch." >&2; exit 1;
}

created_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
cat >"$stage_dir/manifest.txt" <<EOF
format=1
created_utc=$created_utc
android_package=$package
device_model=${ROBOWINDOWS_EXPECTED_MODEL:-SM-T500}
machine_id=$machine_id
disk_bytes=$source_size
disk_sha256=$source_hash
disk_archive=disk.img.gz
iso_files_included=false
EOF
cat >"$stage_dir/README.md" <<EOF
# RoboWindows machine backup

Created at $created_utc from a stopped RoboWindows profile on the Android tablet.
This directory is stored on the Debian host and is intentionally ignored by Git.

The backup contains the compressed guest disk, launch configuration, available
profile metadata, and checksums. User-owned ISO files are deliberately excluded.

Verify it from the repository with:

\`scripts/verify-backup.sh "$destination"\`

Restore only while RoboWindows is stopped. Verify first, decompress
\`disk.img.gz\` to a temporary file, and copy it into the intended app-private
machine directory. Absolute paths in the configuration and metadata must match
the destination package and machine ID.
EOF
(
  cd "$stage_dir"
  find . -maxdepth 1 -type f ! -name SHA256SUMS -printf '%f\n' | LC_ALL=C sort | \
    xargs sha256sum >SHA256SUMS
  sha256sum -c SHA256SUMS
)

mv -- "$stage_dir" "$destination"
stage_dir=
trap - EXIT
echo "Backup complete and verified: $destination"
