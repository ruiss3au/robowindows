#!/bin/sh
# Review and run manually. Codex must not execute this script.
set -eu

ANDROID_CMDLINE_TOOLS_REVISION=13114758
ANDROID_CMDLINE_TOOLS_SHA256=7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee
ANDROID_BUILD_TOOLS_VERSION=36.0.0
ANDROID_PLATFORM_VERSION=36
ANDROID_NDK_VERSION=28.2.13676358

if [ "$(id -u)" -eq 0 ]; then
  echo "Run this script as your normal user; it invokes sudo only for Debian packages." >&2
  exit 1
fi

echo "This installs Debian build packages and a pinned Android SDK under:"
echo "  ${XDG_DATA_HOME:-$HOME/.local/share}/robowindows/android-sdk"
echo "It does not clone emulator sources or modify the connected tablet."
printf "Continue? [y/N] "
read answer
case "$answer" in y|Y|yes|YES) ;; *) echo "Cancelled."; exit 0 ;; esac

sudo apt-get update
sudo apt-get install --yes \
  binutils ca-certificates curl git make ninja-build unzip xorriso zip \
  openjdk-17-jdk-headless

sdk_root="${XDG_DATA_HOME:-$HOME/.local/share}/robowindows/android-sdk"
archive="/tmp/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REVISION}_latest.zip"
download_url="https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_REVISION}_latest.zip"

mkdir -p "$sdk_root/cmdline-tools"
if [ ! -x "$sdk_root/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl --fail --location --proto '=https' --tlsv1.2 \
    --output "$archive" "$download_url"
  printf '%s  %s\n' "$ANDROID_CMDLINE_TOOLS_SHA256" "$archive" | sha256sum --check --strict
  archive_dir="$(mktemp -d /tmp/robowindows-sdk.XXXXXX)"
  unzip -q "$archive" -d "$archive_dir"
  mv "$archive_dir/cmdline-tools" "$sdk_root/cmdline-tools/latest"
fi

sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
yes | "$sdkmanager" --sdk_root="$sdk_root" --licenses
"$sdkmanager" --sdk_root="$sdk_root" \
  "platform-tools" \
  "platforms;android-${ANDROID_PLATFORM_VERSION}" \
  "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" \
  "ndk;${ANDROID_NDK_VERSION}"

echo
echo "Installed. For the current shell run:"
echo "  export ANDROID_SDK_ROOT='$sdk_root'"
echo "  export PATH=\"\$ANDROID_SDK_ROOT/platform-tools:\$PATH\""
echo "Gradle will be supplied by the repository's pinned wrapper."
