#!/bin/sh
# Review and run manually. Downloads a pinned Gradle distribution only to
# generate the repository wrapper; subsequent builds use ./gradlew.
set -eu

GRADLE_VERSION=9.1.0
GRADLE_SHA256=a17ddd85a26b6a7f5ddb71ff8b05fc5104c0202c6e64782429790c933686c806

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this script as your normal user." >&2
  exit 1
fi

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
tool_root="${XDG_DATA_HOME:-$HOME/.local/share}/robowindows/gradle"
archive="/tmp/gradle-${GRADLE_VERSION}-bin.zip"
distribution="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

echo "This downloads verified Gradle $GRADLE_VERSION under:"
echo "  $tool_root"
echo "and generates Gradle wrapper files in:"
echo "  $repo_root"
printf "Continue? [y/N] "
read answer
case "$answer" in y|Y|yes|YES) ;; *) echo "Cancelled."; exit 0 ;; esac

mkdir -p "$tool_root"
if [ ! -x "$tool_root/gradle-${GRADLE_VERSION}/bin/gradle" ]; then
  curl --fail --location --proto '=https' --tlsv1.2 \
    --output "$archive" "$distribution"
  printf '%s  %s\n' "$GRADLE_SHA256" "$archive" | sha256sum --check --strict
  unzip -q "$archive" -d "$tool_root"
fi

cd "$repo_root"
"$tool_root/gradle-${GRADLE_VERSION}/bin/gradle" wrapper \
  --gradle-version "$GRADLE_VERSION" --distribution-type bin

wrapper_properties="$repo_root/gradle/wrapper/gradle-wrapper.properties"
if ! grep -q '^distributionSha256Sum=' "$wrapper_properties"; then
  printf '\ndistributionSha256Sum=%s\n' "$GRADLE_SHA256" >> "$wrapper_properties"
fi

echo "Gradle wrapper generated and distribution checksum pinned."

