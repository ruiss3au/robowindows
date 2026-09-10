#!/usr/bin/env bash
# Guard the common toolbar and process boundary in addition to executable state tests.
set -euo pipefail
repo=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
main="$repo/android/src/main/java/org/robowindows/app/MainActivity.java"
service="$repo/android/src/main/java/org/robowindows/app/DynamicTrialService.java"
[[ $(rg -c 'LinearLayout controls = createSessionControls' "$main") == 2 ]]
rg -q 'showDynamicSession\(p, DynamicCyclePolicy.FIXED_20K, false\)' "$main"
rg -q 'if \(diagnostic\) page.addView\(createReadinessControls' "$main"
hide=$(sed -n '/private void scheduleControlsHide()/,/private void syncSessionControls()/p' "$main")
! rg -q 'dynamicTrial' <<<"$hide"
rg -q 'if \(diagnosticSession\) return;' <<<"$hide"
rg -q 'mediaRequest.consume\(sessionGeneration, sessionActive\)' "$main"
rg -q 'dynamicTrial.changeMedia\(media.getAbsolutePath\(\)\)' "$main"
rg -q 'case DynamicTrialProtocol.CHANGE_MEDIA:' "$service"
rg -q 'validateDynamicMediaInChild' "$service"
rg -q 'sendStatus\(null, "The selected media could not be mounted' "$service"
rg -q 'versionCode = 2' "$repo/android/build.gradle.kts"
rg -q 'versionName = "1.0-alpha"' "$repo/android/build.gradle.kts"
echo 'Shared toolbar, diagnostic-only readiness, isolated media routing and alpha version contracts passed'
