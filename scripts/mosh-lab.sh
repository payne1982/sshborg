#!/bin/sh
# The mosh test rig as one command: start a throwaway mosh-server, run the mosh integration
# test against it, take the server down again. See docs/mosh-wire-format.md.
#
# The session key is minted by the server and lives only as long as it does, so it is passed to
# Gradle on the command line. That makes it visible in the process list of this machine for the
# few seconds the run lasts, which is why this is for a loopback server you started yourself and
# not for a host anyone else can reach.
set -e

PORT="${MOSH_LAB_PORT:-60100}"
TIMEOUT="${MOSH_LAB_TIMEOUT:-600}"
LOG="$(mktemp -t mosh-lab-XXXXXX.log)"
PID=""

cleanup() {
    [ -n "$PID" ] && kill "$PID" 2>/dev/null || true
    rm -f "$LOG"
}
trap cleanup EXIT INT TERM

command -v mosh-server >/dev/null 2>&1 || {
    echo "mosh-server is not installed (Debian/Raspberry Pi OS: apt install mosh)" >&2
    exit 1
}

# The network timeout is also the safety net: if this script is killed before its trap runs, the
# server gives up by itself instead of sitting on the port for ever.
MOSH_SERVER_NETWORK_TMOUT="$TIMEOUT" mosh-server new -v -c 256 -p "$PORT" >"$LOG" 2>&1
PID="$(sed -n 's/.*pid = \([0-9]*\).*/\1/p' "$LOG" | head -1)"
KEY="$(sed -n 's/^MOSH CONNECT [0-9]* //p' "$LOG" | head -1)"

[ -n "$KEY" ] || { echo "mosh-server did not print a key:" >&2; grep -v '^MOSH CONNECT' "$LOG" >&2; exit 1; }
echo "mosh-server on port $PORT, pid ${PID:-unknown}"

./gradlew :app:testDebugUnitTest --tests 'com.sshborg.mosh.MoshSessionIntegrationTest' \
    -PmoshPort="$PORT" -PmoshKey="$KEY" "$@"
STATUS=$?

echo "--- the server's own account of it ---"
grep -v '^MOSH CONNECT\|^$\|Copyright\|WARRANTY\|free software\|^mosh-server (mosh\|^License' "$LOG" | tail -20
exit $STATUS
