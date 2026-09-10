#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
classes_dir="$(mktemp -d)"
trap 'rm -rf -- "$classes_dir"' EXIT

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/WindowsInstallMedia.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/DynamicCyclePolicy.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/LaunchConfig.java" \
  "$repo_dir/tests/java/org/robowindows/app/LaunchConfigTest.java"
java -cp "$classes_dir" org.robowindows.app.LaunchConfigTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/RuntimeTimingPolicy.java" \
  "$repo_dir/tests/java/org/robowindows/app/RuntimeTimingPolicyTest.java"
java -cp "$classes_dir" org.robowindows.app.RuntimeTimingPolicyTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/BuildIdentity.java" \
  "$repo_dir/tests/java/org/robowindows/app/BuildIdentityTest.java"
java -cp "$classes_dir" org.robowindows.app.BuildIdentityTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/SessionUiState.java" \
  "$repo_dir/tests/java/org/robowindows/app/SessionUiStateTest.java"
java -cp "$classes_dir" org.robowindows.app.SessionUiStateTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/DynamicAttemptState.java" \
  "$repo_dir/tests/java/org/robowindows/app/DynamicAttemptStateTest.java"
java -cp "$classes_dir" org.robowindows.app.DynamicAttemptStateTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/DynamicLiveness.java" \
  "$repo_dir/tests/java/org/robowindows/app/DynamicLivenessTest.java"
java -cp "$classes_dir" org.robowindows.app.DynamicLivenessTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/DynamicProgressWatchdog.java" \
  "$repo_dir/tests/java/org/robowindows/app/DynamicProgressWatchdogTest.java"
java -cp "$classes_dir" org.robowindows.app.DynamicProgressWatchdogTest

"$repo_dir/tests/scripts/test-win98-benchmark.sh"
"$repo_dir/tests/scripts/test-benchmark-telemetry.sh"
bash "$repo_dir/tests/scripts/test-runtime-timing.sh"
bash "$repo_dir/tests/scripts/test-worker-timing.sh"
bash "$repo_dir/tests/scripts/test-cache-summary.sh"
bash "$repo_dir/tests/scripts/test-presentation.sh"
javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/PresentationWorkload.java" \
  "$repo_dir/tests/java/org/robowindows/app/PresentationWorkloadTest.java"
java -cp "$classes_dir" org.robowindows.app.PresentationWorkloadTest
bash "$repo_dir/scripts/test-stress-reference.sh"
bash "$repo_dir/scripts/test-cache-reference.sh"

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/DynamicReadiness.java" \
  "$repo_dir/tests/java/org/robowindows/app/DynamicReadinessTest.java"
java -cp "$classes_dir" org.robowindows.app.DynamicReadinessTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/SettingsDraft.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/PresentationPolicy.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/DisposableCoreConfig.java" \
  "$repo_dir/tests/java/org/robowindows/app/SettingsDraftTest.java"
java -cp "$classes_dir" org.robowindows.app.SettingsDraftTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/CpuFixtureResult.java" \
  "$repo_dir/tests/java/org/robowindows/app/CpuFixtureResultTest.java"
java -cp "$classes_dir" org.robowindows.app.CpuFixtureResultTest

javac -d "$classes_dir" \
  "$repo_dir/android/src/main/java/org/robowindows/app/ExpandedCpuSuite.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/ExpandedCpuResult.java" \
  "$repo_dir/android/src/main/java/org/robowindows/app/ExpandedCpuReport.java" \
  "$repo_dir/tests/java/org/robowindows/app/ExpandedCpuResultTest.java"
java -cp "$classes_dir" org.robowindows.app.ExpandedCpuResultTest

"$repo_dir/scripts/test-expanded-cpu-reference.sh"

fixture_image="$classes_dir/robowindows-cpu-v3.bin"
"$repo_dir/scripts/build-cpu-fixture.sh" "$fixture_image"
test "$(wc -c <"$fixture_image")" -eq 1474560
test "$(od -An -tx1 -j510 -N2 "$fixture_image" | tr -d ' ')" = "55aa"
test "$(od -An -tx1 -j512 -N4 "$fixture_image" | tr -d ' ')" != "00000000"
test "$(od -An -tx1 -j8704 -N22 "$fixture_image" | tr -d ' \n')" = \
  "00000000000000000000000000000000000000000000"

"$repo_dir/tests/scripts/check-experimental-fat-test.sh"
bash "$repo_dir/tests/scripts/check-stosd-helper-test.sh"
bash "$repo_dir/tests/scripts/check-string-helpers-test.sh"
bash "$repo_dir/tests/scripts/check-pagefault-core-slice-test.sh"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/input/input_event_test.cpp" -o "$classes_dir/input_event_test"
"$classes_dir/input_event_test"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/session_state_test.cpp" -o "$classes_dir/session_state_test"
"$classes_dir/session_state_test"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  -I"$repo_dir/third_party/dosbox-pure/libretro-common/include" \
  "$repo_dir/tests/native/av_environment_test.cpp" -o "$classes_dir/av_environment_test"
"$classes_dir/av_environment_test"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/run_diagnostics_test.cpp" -o "$classes_dir/run_diagnostics_test"
"$classes_dir/run_diagnostics_test"
g++ -std=c++11 -O2 -Wall -Wextra -Werror -fsanitize=undefined -fno-sanitize-recover=undefined -pthread \
  -I"$repo_dir/third_party/dosbox-pure/include" \
  "$repo_dir/tests/native/worker_timing_test.cpp" -o "$classes_dir/worker_timing_test"
"$classes_dir/worker_timing_test"

g++ -std=c++17 -Wall -Wextra -Werror -pthread \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/runtime_telemetry_test.cpp" \
  "$repo_dir/android/src/main/cpp/runtime_telemetry.cpp" \
  -o "$classes_dir/runtime_telemetry_test"
"$classes_dir/runtime_telemetry_test"

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/realtime_scheduler_test.cpp" \
  "$repo_dir/android/src/main/cpp/realtime_scheduler.cpp" \
  -o "$classes_dir/realtime_scheduler_test"
"$classes_dir/realtime_scheduler_test"

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

g++ -std=c++17 -Wall -Wextra -Werror \
  -I"$repo_dir/android/src/main/cpp" \
  "$repo_dir/tests/native/presentation_policy_test.cpp" -o "$classes_dir/presentation_policy_test"
"$classes_dir/presentation_policy_test"
echo "Presentation policy checks passed"
