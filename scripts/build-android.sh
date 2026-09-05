#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo_root=$(CDPATH= cd -- "$script_dir/.." && pwd)
sdk_root=${ANDROID_SDK_ROOT:-${XDG_DATA_HOME:-$HOME/.local/share}/robowindows/android-sdk}

case "${1:---debug}" in
  --debug) ;;
  *) echo "Usage: $0 [--debug]" >&2; exit 2 ;;
esac

[ -x "$repo_root/gradlew" ] || {
  echo "Missing Gradle wrapper. Review and run scripts/bootstrap-gradle-wrapper.sh first." >&2
  exit 1
}
[ -d "$sdk_root/platforms/android-36" ] || {
  echo "Pinned Android SDK not found at $sdk_root" >&2
  exit 1
}

export ANDROID_SDK_ROOT=$sdk_root
export ANDROID_HOME=$sdk_root
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-$repo_root/build/gradle-home}
export JAVA_OPTS=${JAVA_OPTS:--Xmx2048m -Dfile.encoding=UTF-8}
mkdir -p "$GRADLE_USER_HOME"

"$repo_root/scripts/fetch-sources.sh" --verify-only
cd "$repo_root"
./gradlew --no-daemon :android:assembleDebug

apk="$repo_root/android/build/outputs/apk/debug/android-debug.apk"
[ -f "$apk" ] || { echo "Expected APK missing: $apk" >&2; exit 1; }
mkdir -p "$repo_root/artifacts"
cp "$apk" "$repo_root/artifacts/robowindows-debug.apk"
sha256sum "$repo_root/artifacts/robowindows-debug.apk" \
  > "$repo_root/artifacts/robowindows-debug.apk.sha256"
echo "Built $repo_root/artifacts/robowindows-debug.apk"
