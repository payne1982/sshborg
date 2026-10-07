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
#
# Two switches, for the screen recording in make-fgs-video.sh: --install-only builds, copies and
# installs without running anything, and SSHBORG_SKIP_INSTALL=1 runs the tests on what is already
# installed. Pushing 86 MB onto the emulator is heavy enough to kill a recording in progress, so
# that script installs first and only then turns the camera on.
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

install_only=""
if [ "${1:-}" = "--install-only" ]; then install_only=yes; shift; fi

# Which app we are talking to is needed whether or not anything is installed this time round.
app=app/build/outputs/apk/debug/app-debug.apk
test_app=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
package=$(sed -n 's/.*applicationId = "\(.*\)".*/\1/p' app/build.gradle.kts)
suffix=$(sed -n 's/.*applicationIdSuffix = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)
target="${package}${suffix}"

if [ -z "${SSHBORG_SKIP_INSTALL:-}" ]; then
echo "== building"
JAVA_HOME=${JAVA_HOME:-/home/payne/jdk21} ./gradlew -q assembleDebug assembleDebugAndroidTest

echo "== waiting for a device on $host"
# Through `timeout`, because wait-for-device does not fail when there is nothing to wait for —
# it blocks for ever, and the message below could never be reached. A run then looks slow rather
# than broken, which is how a stopped emulator cost an afternoon once.
run "timeout 180 $adb wait-for-device" || { echo "no device: is the emulator running there? (~/Documents/sshborg-emulator/start.sh <avd>)" >&2; exit 1; }

echo "== copying and installing"
run "mkdir -p '$dir'"
scp "${ssh_opts[@]}" -q "$app" "$test_app" "$remote:$dir/"
run "$adb install -r -t '$dir/$(basename "$app")'"
run "$adb install -r -t '$dir/$(basename "$test_app")'"

# Android 13+ asks for POST_NOTIFICATIONS the first time the app runs, and that system dialog
# sits in front of everything: the activity never reaches the foreground and a Compose test finds
# no hierarchy at all ("No compose hierarchies found in the app"). Granting it up front is what a
# test device is for.
run "$adb shell pm grant '$target' android.permission.POST_NOTIFICATIONS" 2>/dev/null || true
fi
[ -z "$install_only" ] || { echo "== installato"; exit 0; }

# With no argument, everything except the screenshot runs: those photograph the app instead of
# checking it, and they are slow. Naming a class asks for exactly that class, screenshots included.
if [ $# -gt 0 ]; then
    filter="-e class $1"
else
    filter="-e notAnnotation com.sshborg.Screenshots"
fi

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
