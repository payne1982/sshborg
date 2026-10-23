#!/bin/sh
# Phase 5's repaint gate: does our terminal draw what mosh's optimiser meant?
#
# It records the host byte stream of a scripted mosh session (MoshRepaintGateTest writes one file
# per checkpoint), then replays the identical bytes through a second, independent terminal emulator
# — a detached tmux pane of the same size — and diffs the two renderings. Two emulators disagreeing
# about the same bytes is a defect in one of them; two agreeing is the only evidence worth having.
#
# What it compares is cell placement and content, not attributes: colour and bold would need
# `capture-pane -e` and a parser for what it prints, and geometry is where the risk is. A colour
# fault that moves nothing is invisible here and belongs to the manual pass, with the scenarios
# that are not deterministic enough to diff — `htop` above all.
#
# See docs/mosh-plan.md §8 phase 5.
set -e

PORT="${MOSH_LAB_PORT:-60110}"
TIMEOUT="${MOSH_LAB_TIMEOUT:-900}"
OUT="${MOSH_GATE_OUT:-$PWD/app/build/mosh-gate}"
SOCKET=moshgate                 # a tmux server of our own: never touch the one you are sitting in
LOG="$(mktemp -t mosh-gate-XXXXXX.log)"
PID=""

cleanup() {
    [ -n "$PID" ] && kill "$PID" 2>/dev/null || true
    tmux -L "$SOCKET" kill-server 2>/dev/null || true
    rm -f "$LOG"
}
trap cleanup EXIT INT TERM

for tool in mosh-server tmux; do
    command -v "$tool" >/dev/null 2>&1 || { echo "$tool is not installed" >&2; exit 1; }
done

mkdir -p "$OUT"
MOSH_SERVER_NETWORK_TMOUT="$TIMEOUT" mosh-server new -v -c 256 -p "$PORT" >"$LOG" 2>&1
PID="$(sed -n 's/.*pid = \([0-9]*\).*/\1/p' "$LOG" | head -1)"
KEY="$(sed -n 's/^MOSH CONNECT [0-9]* //p' "$LOG" | head -1)"
[ -n "$KEY" ] || { echo "mosh-server did not print a key:" >&2; grep -v '^MOSH CONNECT' "$LOG" >&2; exit 1; }
echo "mosh-server on port $PORT, pid ${PID:-unknown}"

./gradlew :app:testDebugUnitTest --tests 'com.sshborg.mosh.MoshRepaintGateTest' \
    -PmoshPort="$PORT" -PmoshKey="$KEY" -PmoshGateOut="$OUT" "$@"

# ── the oracle ───────────────────────────────────────────────────────────────────────────────────
# `stty raw` before cat is not optional. Mosh's bytes are made in user space and never cross a pty,
# and mosh-client puts the real terminal in raw mode, so a bare LF means "down one row" and nothing
# else. Replayed through a pty with the default onlcr, every LF would silently gain a carriage
# return and the oracle would disagree with us about every wrapped line — the rig's fault, not ours.
normalise() { sed -e 's/[[:space:]]*$//' "$1" | awk '{ lines[NR] = $0 } END { last = 0; for (i = 1; i <= NR; i++) if (lines[i] != "") last = i; for (i = 1; i <= last; i++) print lines[i] }'; }

failed=0
checked=0
for bin in "$OUT"/*.bin; do
    [ -e "$bin" ] || { echo "no recordings in $OUT — did the test skip itself?" >&2; exit 1; }
    name="$(basename "$bin" .bin)"
    read -r cols rows < "$OUT/$name.size"

    tmux -L "$SOCKET" kill-server 2>/dev/null || true
    tmux -u -L "$SOCKET" new-session -d -x "$cols" -y "$rows" \
        "stty raw -echo; cat '$bin'; sleep 600"
    # The pane needs a moment to swallow the stream; it is all local and already in a file.
    i=0
    while [ "$i" -lt 40 ]; do
        tmux -L "$SOCKET" capture-pane -p -t 0 > "$OUT/$name.tmux.raw" 2>/dev/null || true
        [ -s "$OUT/$name.tmux.raw" ] && break
        i=$((i + 1))
    done
    tmux -L "$SOCKET" capture-pane -p -t 0 > "$OUT/$name.tmux.raw"
    tmux -L "$SOCKET" kill-server 2>/dev/null || true

    normalise "$OUT/$name.tmux.raw" > "$OUT/$name.tmux.txt"
    normalise "$OUT/$name.ours.txt" > "$OUT/$name.ours.norm.txt"
    # A scenario with a clock or a process list in it draws a different screen every time, so
    # there is nothing to diff. Both renderings are kept side by side for a person to read.
    case "$name" in
        *manual*)
            diff -u "$OUT/$name.tmux.txt" "$OUT/$name.ours.norm.txt" > "$OUT/$name.diff" || true
            echo "  man   $name (${cols}x${rows}) -> $OUT/$name.{ours.norm,tmux}.txt"
            continue
            ;;
    esac

    checked=$((checked + 1))
    if diff -u "$OUT/$name.tmux.txt" "$OUT/$name.ours.norm.txt" > "$OUT/$name.diff"; then
        rm -f "$OUT/$name.diff"
        echo "  ok    $name (${cols}x${rows})"
    else
        failed=$((failed + 1))
        echo "  DIFF  $name (${cols}x${rows}) -> $OUT/$name.diff"
    fi
done

echo "--- $checked checkpoints compared, $failed disagreeing ---"
[ "$failed" -eq 0 ] || exit 1
