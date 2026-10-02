#!/usr/bin/env bash
# Films a session surviving the app going into the background, for Google Play's
# foreground-service declaration.
#
# Play asks for a video showing what the service is for, and the thing to show is not a feature on
# screen but the absence of a failure: the app leaves, the notification says the session is still
# open, and the session is still there on the way back. Shot on the phone AVD against a real
# server, so there is nothing staged in it.
#
# The film lands in app/build/outputs/video/ and nothing is published. A `gradlew clean` takes it
# with it, which is where a run's leftovers belong — it costs one command to make again.
#
# Note on the app's name: a debug build is labelled SSHBorgDebug, and that label is what the
# notification header shows — the one shot the declaration is about. Patch it locally before
# filming if that matters (manifestPlaceholders["appLabel"] in app/build.gradle.kts) and put it
# back afterwards. Never commit that patch, the same rule as the .r8test suffix.
set -euo pipefail

cd "$(dirname "$0")/.."
config=test.properties
[ -f "$config" ] || { echo "no $config; see test.properties.example" >&2; exit 2; }
setting() { sed -n "s/^$1=//p" "$config" | tail -1; }
host=$(setting emuHost); user=$(setting emuUser)
adb=$(setting emuAdb); adb=${adb:-adb}
remote="$user@$host"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=10)
run() { ssh "${ssh_opts[@]}" "$remote" "$@"; }

package=$(sed -n 's/.*applicationId = "\(.*\)".*/\1/p' app/build.gradle.kts)
suffix=$(sed -n 's/.*applicationIdSuffix = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)
target="${package}${suffix}"
files="/sdcard/Android/data/$target/files"
here=app/build/outputs/video


# The system bars belong to the device, not to the app: without this the app is dark and the bars
# around it are not.
run "$adb shell cmd uimode night yes" > /dev/null || true

# The emulator renders in software and sometimes cannot keep up with an app launch, a status bar
# and an SSH session at once; Android then puts an "isn't responding" dialog over everything, which
# is both untrue of the app and fatal to a film. Test devices turn those off.
run "$adb shell settings put global hide_error_dialogs 1" > /dev/null || true
trap 'run "$adb shell settings put global hide_error_dialogs 0; $adb shell cmd uimode night auto" > /dev/null 2>&1 || true' EXIT

# Everything heavy before the camera is on: pushing and installing 86 MB stops a recording dead.
./scripts/run-instrumented.sh --install-only

# A film has to start from an app with nothing in it: the demo refuses to run against a device
# that already holds hosts, because they would be in the picture — and the run before this one
# left its own demo host behind, which is how three takes came back three seconds long.
# `pm clear` also drops the notification permission and the app's external files, so the grant and
# the demo server's details come after it, not before.
run "$adb shell pm clear '$target'" > /dev/null
run "$adb shell pm grant '$target' android.permission.POST_NOTIFICATIONS" 2>/dev/null || true

# The server to connect to, reachable from the machine the emulator runs on — "127.0.0.1" is not
# it. Same setting the screenshots use.
demo_host=$(setting emuSshHost)
demo_user=$(setting sshUser)
demo_key="$(setting sshKeyDir)/i_plain"
[ -n "$demo_host" ] && [ -f "$demo_key" ] || {
    echo "emuSshHost or the key is missing: there is nothing to connect to, and a film of a" >&2
    echo "failed connection proves the opposite of what Play is asking." >&2
    exit 2
}
printf 'host=%s\nuser=%s\n' "$demo_host" "$demo_user" > /tmp/demo.properties
scp "${ssh_opts[@]}" -q /tmp/demo.properties "$demo_key" "$remote:/tmp/"
run "$adb shell mkdir -p '$files' && $adb push /tmp/demo.properties '$files/demo.properties' && $adb push /tmp/$(basename "$demo_key") '$files/demo.key' && rm -f /tmp/demo.properties /tmp/$(basename "$demo_key")" > /dev/null
rm -f /tmp/demo.properties
echo "si collega a $demo_host come $demo_user"

echo "== registro"
# The device's own recorder, detached so it outlives this ssh command. What breaks a recording here
# is pushing and installing 86 MB while it runs — both this recorder and the emulator's console one
# die within seconds of that — which is why the install is already done by the time we get here.
run "$adb shell rm -f /sdcard/fgs.mp4" || true
run "setsid nohup $adb shell screenrecord --bit-rate 6000000 --time-limit 180 /sdcard/fgs.mp4 > /dev/null 2>&1 < /dev/null &"
sleep 2

set +e
SSHBORG_SKIP_INSTALL=1 ./scripts/run-instrumented.sh com.sshborg.service.ForegroundServiceDemo
status=$?
set -e

# SIGINT is what finalises an mp4; never -9, which leaves a file without its index.
run "$adb shell pkill -INT screenrecord" || true
sleep 4

mkdir -p "$here"
run "rm -f /tmp/fgs.mp4 && $adb pull /sdcard/fgs.mp4 /tmp/fgs.mp4" > /dev/null
scp "${ssh_opts[@]}" -q "$remote:/tmp/fgs.mp4" "$here/foreground-service.mp4"
run "rm -f /tmp/fgs.mp4; $adb shell rm -f /sdcard/fgs.mp4" || true

echo
ls -lh "$here/foreground-service.mp4"
echo "in $(pwd)/$here — non viene pubblicato niente"
[ $status -eq 0 ] || echo "ATTENZIONE: il test strumentato è uscito con $status, guarda il video prima di usarlo" >&2
exit $status
