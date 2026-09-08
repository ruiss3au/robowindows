#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
classes_dir="$(mktemp -d)"
trap 'rm -rf -- "$classes_dir"' EXIT

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/WindowsInstallMedia.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/LaunchConfig.java" \
  "$repo_dir/tests/java/org/robowindows/app/LaunchConfigTest.java"
java -cp "$classes_dir" org.robowindows.app.LaunchConfigTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/BuildIdentity.java" \
  "$repo_dir/tests/java/org/robowindows/app/BuildIdentityTest.java"
java -cp "$classes_dir" org.robowindows.app.BuildIdentityTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/SessionUiState.java" \
  "$repo_dir/tests/java/org/robowindows/app/SessionUiStateTest.java"
java -cp "$classes_dir" org.robowindows.app.SessionUiStateTest

"$repo_dir/tests/scripts/check-experimental-fat-test.sh"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/input/input_event_test.cpp" -o "$classes_dir/input_event_test"
"$classes_dir/input_event_test"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/session_state_test.cpp" -o "$classes_dir/session_state_test"
"$classes_dir/session_state_test"

g++ -std=c++17 -Wall -Wextra -Werror -pthread \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/runtime_telemetry_test.cpp" \
  "$repo_dir/android/src/main/cpp/runtime_telemetry.cpp" \
  -o "$classes_dir/runtime_telemetry_test"
"$classes_dir/runtime_telemetry_test"

g++ -std=c++17 -Wall -Wextra -Werror -pthread \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/frame_mailbox_test.cpp" \
  "$repo_dir/android/src/main/cpp/frame_mailbox.cpp" \
  -o "$classes_dir/frame_mailbox_test"
"$classes_dir/frame_mailbox_test"

g++ -std=c++17 -O2 -Wall -Wextra -Werror -pthread \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/frame_handoff_benchmark.cpp" \
  "$repo_dir/android/src/main/cpp/frame_mailbox.cpp" \
  -o "$classes_dir/frame_handoff_benchmark"
"$classes_dir/frame_handoff_benchmark"

g++ -std=c++17 -O2 -Wall -Wextra -Werror -pthread \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/audio_ring_test.cpp" \
  "$repo_dir/android/src/main/cpp/audio_ring.cpp" \
  -o "$classes_dir/audio_ring_test"
"$classes_dir/audio_ring_test"

g++ -std=c++17 -O2 -Wall -Wextra -Werror -pthread \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/audio_state_test.cpp" \
  "$repo_dir/android/src/main/cpp/audio_output.cpp" \
  -o "$classes_dir/audio_state_test"
"$classes_dir/audio_state_test"

if rg -n 'PUREMENU|DBP_StartOSD|dosbox_pure_osd' "$repo_dir/android/src/main"; then
  echo "Prohibited upstream UI reference found in product sources" >&2
  exit 1
fi

echo "Host checks passed"
