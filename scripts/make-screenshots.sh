#!/usr/bin/env bash
# Takes the store screenshots on the emulator and brings them back here.
#
# They come out of a UI test that arranges a made-up set of hosts and keys and photographs the
# screens, so they are identical every time and can be retaken after any change to the interface —
# unlike the ones in fastlane/, taken by hand on real phones over a year, which is why they are
# five different sizes.
#
# Nothing is published: the pictures land in app/build/outputs/screenshots for you to look at and
# copy over the listing's own if you like them. The emulator's language decides theirs.
set -euo pipefail

cd "$(dirname "$0")/.."
config=test.properties
[ -f "$config" ] || { echo "no $config; see test.properties.example" >&2; exit 2; }
setting() { sed -n "s/^$1=//p" "$config" | tail -1; }
host=$(setting emuHost); user=$(setting emuUser)
adb=$(setting emuAdb); adb=${adb:-adb}
remote="$user@$host"
ssh_opts=(-o BatchMode=yes -o ConnectTimeout=10)

package=$(sed -n 's/.*applicationId = "\(.*\)".*/\1/p' app/build.gradle.kts)
suffix=$(sed -n 's/.*applicationIdSuffix = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)
target="${package}${suffix}"
on_device="/sdcard/Android/data/$target/files/screenshots"
here=app/build/outputs/screenshots

ssh "${ssh_opts[@]}" "$remote" "$adb shell rm -rf '$on_device'" || true

# The terminal picture needs a real server, reachable from the emulator — which is on another
# machine, so "127.0.0.1" is not it. Set emuSshHost to an address that machine can dial; without
# it every other screenshot is taken and the terminal one is skipped.
demo_host=$(setting emuSshHost)
demo_user=$(setting sshUser)
demo_key="$(setting sshKeyDir)/i_plain"
files="/sdcard/Android/data/$target/files"
ssh "${ssh_opts[@]}" "$remote" "$adb shell rm -f '$files/demo.properties' '$files/demo.key'" || true
if [ -n "$demo_host" ] && [ -f "$demo_key" ]; then
    printf 'host=%s\nuser=%s\n' "$demo_host" "$demo_user" > /tmp/demo.properties
    scp "${ssh_opts[@]}" -q /tmp/demo.properties "$demo_key" "$remote:/tmp/"
    ssh "${ssh_opts[@]}" "$remote" "$adb shell mkdir -p '$files' && $adb push /tmp/demo.properties '$files/demo.properties' && $adb push /tmp/$(basename "$demo_key") '$files/demo.key' && rm -f /tmp/demo.properties /tmp/$(basename "$demo_key")" > /dev/null
    rm -f /tmp/demo.properties
    echo "terminale: si collega a $demo_host come $demo_user"
else
    echo "terminale: saltato (emuSshHost non impostato)"
fi
./scripts/run-instrumented.sh com.sshborg.ui.StoreScreenshots

rm -rf "$here"; mkdir -p "$here"
ssh "${ssh_opts[@]}" "$remote" "rm -rf /tmp/sshborg-screenshots && $adb pull -a '$on_device' /tmp/sshborg-screenshots" > /dev/null
scp "${ssh_opts[@]}" -q "$remote:/tmp/sshborg-screenshots/*" "$here/"
ssh "${ssh_opts[@]}" "$remote" "rm -rf /tmp/sshborg-screenshots"

echo
for f in "$here"/*.png; do
    read -r w h < <(python3 - "$f" <<'PY'
import struct, sys
data = open(sys.argv[1], 'rb').read(33)
print(*struct.unpack('>II', data[16:24]))
PY
)
    printf "  %-28s %sx%s\n" "$(basename "$f")" "$w" "$h"
done
echo "in $here — non ne viene pubblicata nessuna"
