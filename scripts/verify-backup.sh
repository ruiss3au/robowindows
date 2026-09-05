#!/usr/bin/env bash
set -euo pipefail

usage() { echo "Usage: scripts/verify-backup.sh BACKUP_DIRECTORY"; }
[[ $# -eq 1 ]] || { usage >&2; exit 2; }
backup_dir=$1
[[ -d "$backup_dir" ]] || { echo "Backup directory not found: $backup_dir" >&2; exit 1; }
backup_dir=$(cd -- "$backup_dir" && pwd)
manifest=$backup_dir/manifest.txt
[[ -f "$manifest" && -f "$backup_dir/SHA256SUMS" ]] || {
  echo "Backup manifest or SHA256SUMS is missing." >&2; exit 1;
}

manifest_value() {
  local key=$1
  awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; found=1} END {exit !found}' \
    "$manifest"
}

format=$(manifest_value format)
archive=$(manifest_value disk_archive)
expected_size=$(manifest_value disk_bytes)
expected_hash=$(manifest_value disk_sha256)
[[ "$format" == 1 ]] || { echo "Unsupported backup format: $format" >&2; exit 1; }
[[ "$archive" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Unsafe archive name." >&2; exit 1; }
[[ "$expected_size" =~ ^[0-9]+$ && "$expected_hash" =~ ^[0-9a-f]{64}$ ]] || {
  echo "Invalid raw disk provenance in manifest." >&2; exit 1;
}

(
  cd "$backup_dir"
  sha256sum -c SHA256SUMS
  gzip -t "$archive"
)
actual_size=$(gzip -cd "$backup_dir/$archive" | wc -c)
actual_hash=$(gzip -cd "$backup_dir/$archive" | sha256sum | awk '{print $1}')
[[ "$actual_size" == "$expected_size" ]] || {
  echo "Raw disk size mismatch: expected=$expected_size actual=$actual_size" >&2; exit 1;
}
[[ "$actual_hash" == "$expected_hash" ]] || {
  echo "Raw disk SHA-256 mismatch: expected=$expected_hash actual=$actual_hash" >&2; exit 1;
}
echo "Backup verified: $backup_dir"
echo "Raw disk: $actual_size bytes, SHA-256 $actual_hash"
