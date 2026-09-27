#!/usr/bin/env bash
# Runs the instrumented tests on the emulator, which lives on another machine.
#
# Instrumented tests need a real Android device, and the emulator runs on a Linux box on the LAN
# rather than here. Nothing of the build has to exist there: this builds both APKs locally, copies
# them over, and drives the test run through that machine's adb.
#
# Where that machine is comes from test.properties (not in the repo — see test.properties.example):
#
#     emuHost=the-machine.lan
#     emuUser=the-login-that-runs-the-emulator
#     emuAdb=adb                  # optional, a full path if adb is not in its PATH
#     emuDir=/some/where          # optional, where to leave the APKs (default /tmp/…)
#
# Usage:  scripts/run-instrumented.sh [test class or method]
#     scripts/run-instrumented.sh
#     scripts/run-instrumented.sh com.sshborg.data.db.MigrationTest
set -euo pipefail

cd "$(dirname "$0")/.."
config=test.properties
[ -f "$config" ] || { echo "no $config; copy test.properties.example and fill in emuHost/emuUser" >&2; exit 2; }

setting() { sed -n "s/^$1=//p" "$config" | tail -1; }
host=$(setting emuHost)
user=$(setting emuUser)
adb=$(setting emuAdb); adb=${adb:-adb}
dir=$(setting emuDir); dir=${dir:-/tmp/sshborg-instrumented}
[ -n "$host" ] && [ -n "$user" ] || { echo "emuHost and emuUser must be set in $config" >&2; exit 2; }

remote="$user@$host"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=10)
run() { ssh "${ssh_opts[@]}" "$remote" "$@"; }

echo "== building"
JAVA_HOME=${JAVA_HOME:-/home/payne/jdk21} ./gradlew -q assembleDebug assembleDebugAndroidTest

app=app/build/outputs/apk/debug/app-debug.apk
test_app=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
package=$(sed -n 's/.*applicationId = "\(.*\)".*/\1/p' app/build.gradle.kts)
suffix=$(sed -n 's/.*applicationIdSuffix = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)
target="${package}${suffix}"

echo "== waiting for a device on $host"
run "$adb wait-for-device" || { echo "no device: is the emulator running there?" >&2; exit 1; }

echo "== copying and installing"
run "mkdir -p '$dir'"
scp "${ssh_opts[@]}" -q "$app" "$test_app" "$remote:$dir/"
run "$adb install -r -t '$dir/$(basename "$app")'"
run "$adb install -r -t '$dir/$(basename "$test_app")'"

filter=""
[ $# -gt 0 ] && filter="-e class $1"

echo "== running"
# -r for the machine-readable stream, so a failure can be found in the output; the runner's own
# exit status is not enough, it reports OK even when a test fails.
output=$(run "$adb shell am instrument -w -r $filter $target.test/androidx.test.runner.AndroidJUnitRunner")
echo "$output" | sed -n 's/^INSTRUMENTATION_STATUS: //p;s/^INSTRUMENTATION_RESULT: //p;/^Time:/p;/^OK (/p;/^FAILURES/p' | grep -vE "^(numtests|stream|id|current|class|test)=" || true

if echo "$output" | grep -qE "^(FAILURES|INSTRUMENTATION_RESULT: shortMsg)"; then
    echo; echo "== failures, in full"
    echo "$output" | sed -n '/^INSTRUMENTATION_STATUS: stack=/,/^INSTRUMENTATION_STATUS_CODE/p'
    exit 1
fi
echo "== green"
