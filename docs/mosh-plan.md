# Mosh — what it would take, and what it would give

Status: **analysis only, nothing implemented.** Written 2026-10-20 on `V1_DEV_MOSH`,
which exists to hold this file and, if the feature is ever scheduled, the work.

This file exists because a user asked why SSHBorg does not support mosh. The answer
is longer than an issue reply, and most of it is reasoning that would be expensive to
reconstruct from scratch: what the protocol forces on us, which of its famous
limitations are real and which ones only apply to the stock client, and the order in
which the work would be worth doing. Conclusions are marked where they rest on
something still to be verified against the mosh source — nobody should start coding
from this file without closing those first.

## 1. What mosh actually is

Mosh is not an alternative transport for SSH. It is a second protocol — the State
Synchronisation Protocol, SSP, over UDP, encrypted with AES-128-OCB — and SSH is used
only to start it:

1. connect over SSH as usual;
2. run `mosh-server new -s -c 256 -l LANG=…`;
3. read back the line `MOSH CONNECT <port> <key>`;
4. from then on talk UDP to `<host>:<port>` with that key.

The decisive detail is what travels on that UDP channel. SSP does not carry a byte
stream: **the terminal emulator runs on the server**, and what is synchronised is
*screen state*. The server computes the difference between the screen the client last
acknowledged and the current one, and expresses that difference as escape sequences.
Because the protocol is state-based rather than stream-based, it can skip every
intermediate state — which is exactly why a session that was asleep for an hour
repaints instantly instead of replaying an hour of output.

Everything below follows from that one design choice.

## 2. Hard prerequisites

- **`mosh-server` installed on the host.** There is no way around this and no
  fallback: no mosh-server, no mosh. That rules out routers, appliances, NAS boxes
  without a package manager, shared hosting, and managed bastions where the user is
  not root. People who ask for mosh usually run their own servers; a large part of
  our users do not.
- **Inbound UDP on the host**, by default in the 60000–61000 range. Firewalls and NAT
  block it often, and every block becomes a "mosh does not work" report that the app
  cannot do anything about.
- **A UTF-8 locale on the server.** Mosh is strict about this.

## 3. The shape we settled on

A **per-host switch**, in the host editor, defaulting to off:

- on first connection to a host with the switch on, detect with `command -v
  mosh-server`, cache the outcome on the host record;
- if it is missing, fall back to plain SSH silently (log it, do not nag);
- a host with the switch off behaves exactly as it does today, byte for byte.

Architecturally this costs little, because the emulator already sits behind a
byte-stream interface: what changes is who feeds it. The real cost is not the code,
it is the **test matrix** — everything that touches the terminal (extra-key bar,
mouse, selection, resize, TV/D-pad, rotation) has to be exercised in two modes, for
as long as the feature exists.

### 3.1 We do not have to behave like the stock client

The stock client closes the SSH connection once the bootstrap is done. We are writing
the client, so we can keep it open, and that single decision takes back most of what
mosh is said to cost:

- **SFTP, local port forwarding and agent forwarding keep working**, because they live
  on the SSH session, not on mosh. Only the terminal rides SSP. When the network
  changes the SSH session dies and reconnects the way it already does today, while the
  terminal does not notice.
- The orphaned-`mosh-server` problem (processes left behind on the host, which users
  report as "it never exits") is ours to avoid, because we write the environment the
  server is launched in: mosh has an environment variable for the server's network
  timeout. **Verify the exact name and semantics in the source before relying on it.**

## 4. What is genuinely unavailable inside a mosh session

**Scrollback.** Structural, not a limitation of our code: the server synchronises the
visible screen, so what scrolled away was never transmitted. The mosh project's own
position is to use `tmux` or `screen` for history. In mosh mode our scrollback would
be local-only, partial and unreliable. This is the one honest renunciation and it
belongs in the guide.

**Mouse reporting — nothing is lost, against the first draft of this file.** The claim
here used to be that mosh synchronises a screen and not the terminal's mode set, so the
client would never learn that the application had asked for the mouse. Reading
`terminaldisplay.cc` settled it the other way: the mouse reporting and encoding modes
are part of the synchronised draw state, and the frame carries `?1000/?1002/?1003`,
`?1006/?1015/?1005`, `?1004` and `?2004` in both their on and off forms. Our existing
`?1000/?1006` support therefore works by itself, provided the reports we generate are
sent as keystrokes. Details and the four modes our parser does not yet know in
[mosh-wire-format.md](mosh-wire-format.md) §4.

**Predictive local echo — deliberately out of a first version.** It is the reason
people love mosh, and it is also ~1000 lines of subtle logic (epochs, validation of
predictions against arriving frames, an RTT trigger). A half-finished prediction
engine is worse than none: it shows wrong characters and then corrects them, and that
reads as a bug in the app. Version one would give roaming and persistence but no
faster typing.

## 5. Jump hosts

### 5.1 Tunnelling mosh's UDP over SSH: possible, elegant, and self-defeating

SSH has no native UDP forwarding. The only thing close is `-w` (a tun device), which
needs `PermitTunnel=yes` and privileges on both ends: on Android without root that is
dead, and going through `VpnService` for this would be out of all proportion.

The socat-style relay does work, and in a nicer form than the classic recipe, because
**the client side needs no helper at all — we are the client**. Instead of opening a
`DatagramSocket` we open an exec channel on the (already proxied) JSch session, run a
ten-line relay on the target, and frame the datagrams ourselves with a length prefix.
No listening TCP port, no `-L`, no firewall hole: one channel. Instead of socat a
`python3`/`perl` one-liner we ship ourselves is likelier to be present on a machine
that has `mosh-server` than socat is.

The problem is not feasibility, it is that it **defeats its own purpose**: SSP put on
top of a reliable, ordered transport gets head-of-line blocking, and the tunnel dies
at the very Wi-Fi→cellular handoff mosh exists to survive. What is left is not
nothing — the session *survives on the server* and resynchronises instantly on
re-dial instead of starting over, and local echo would still help — but transparent
roaming is gone by construction.

**Therefore: in version one, mosh is unavailable when a jump chain is configured**, and
such a host connects over SSH as today. Promising roaming and not delivering it is
worse than not offering it. The exec-channel relay stays as a possible second phase,
described for what it is: *persistent session, not roaming*. For the genuine case the
real answer is the user's own VPN (WireGuard and friends), which carries the UDP to
its destination and is none of our business.

### 5.2 The right way round: mosh outward, SSH inward

The useful arrangement is the reverse of the above, and it is the one to aim for:
reach the host that *is* reachable with mosh, and hop inward from inside that session
with ordinary SSH and agent forwarding.

```
app ──SSH (kept alive: SFTP + agent) ──┐
  └──mosh/UDP (the terminal) ──────────┤  bastion   ──ssh──>  internal host
                                       └─ signature requested of the app
```

The property that makes this better than anything SSH-plus-jump can do today: the
inner `ssh` runs **on the bastion**, so it is a TCP connection between two machines in
a datacentre and the phone's network is irrelevant to it. The only fragile hop,
phone→bastion, is precisely the one mosh covers. The whole chain becomes
handoff-proof.

## 6. Agent forwarding — mosh has nothing to do with it

The agent does not travel over mosh. It travels over the SSH connection, as an
`auth-agent@openssh.com` channel request. The reason "mosh breaks agent forwarding" in
the wild is only that the stock client closes the SSH connection after the bootstrap:
sshd then tears the forwarded socket down, and `SSH_AUTH_SOCK` inside the mosh session
points at a dead file. Since we keep the SSH session alive for SFTP anyway, the same
channel keeps the agent alive.

What is already in the tree, and needs no new work:

- `SshManager.kt` sets `channel.setAgentForwarding(params.agentForwarding)` — the
  switch exists per host and is off by default;
- the reflection workaround for the JSch bug where `setAgentForwarding(true)` sets the
  channel flag but never `Session.agent_forwarding` (see the comment in the same file);
- the forwarded agent is served by JSch out of the session's identities, and a session
  gets a fresh `JSch()` with exactly one identity, the host's own key. **Signing
  happens inside the app; no private key ever leaves the device.**

Two things to solve:

1. **`SSH_AUTH_SOCK` has to reach the shell `mosh-server` starts.** We write the
   bootstrap command, so it can be prefixed; what needs checking is whether
   `mosh-server` scrubs its environment and whether `-l` accepts variables that are
   not locale-related (the manual page describes it as "locale-related"). First item
   on the verification list.
2. **The socket path changes at every SSH reconnect; the mosh session does not.** This
   is the real snag, and it only exists *because* mosh works: the network changes, the
   SSH session is re-dialled and sshd creates a new socket, while the shell inside the
   surviving mosh session still holds the old `SSH_AUTH_SOCK`. Cure: a stable symlink
   on the host (`~/.sshborg/agent.sock`), refreshed on every reconnect, with
   `SSH_AUTH_SOCK` pointed at the symlink — the trick people use with tmux, except we
   would do it automatically, since we own both the bootstrap and the reconnect. Dark
   window: between the drop and the re-dial a *new* inner authentication fails; an
   inner `ssh` already established does not notice, because it does not pass through us.

**Warning that belongs in the guide:** agent forwarding to a bastion lets anyone with
root on that machine have your key sign things *while you are connected*, and in mosh
mode you are connected far longer and behind a socket with a predictable name. The
exposure is limited to that one key, not to the whole key store. Keep it as it is —
off by default, per host — and say so plainly where the two features meet.

## 7. Two implementation routes

### A. Wrap `mosh-client` through the NDK — rejected

Cross-compile mosh plus protobuf plus a crypto library for four ABIs, run it on a pty,
give its stdout to our emulator. Double emulation, but that is how the existing
Android ports work and it does work; a terminfo file has to be shipped too.

Licensing is **not** an obstacle: SSHBorg is GPL v3 and so is mosh.

What rejects it is the rest: it introduces a native toolchain into a project that has
none (no `cpp/`, no CMake), adds 3–5 MB (split per ABI on Play, not on F-Droid), and
the F-Droid recipe would have to rebuild the native parts reproducibly. That pipeline
is today the most trouble-free thing we own (`gradle=yes`, `AutoUpdateMode=Version`)
and is not worth risking for this.

### B. Reimplement SSP in Kotlin — the one to pick

- AES-128-OCB is already available: BouncyCastle 1.86 is already a dependency
  (`OCBBlockCipher`).
- The three protobuf messages are small enough to parse by hand in a few hundred
  lines; no codegen, no new dependency.
- UDP is trivial, and the host diffs go straight into the `TerminalEmulator` we
  already have.
- Nothing is added to the APK, nothing native, and the whole transport is testable on
  the JVM suite that exists.
- The protocol is effectively frozen (1.3.2 in 2017, 1.4.0 in 2022), so a
  reimplementation is not chasing a moving target. The 2012 USENIX ATC paper plus the
  source are the specification; with both projects under GPL v3, reading it and even
  transliterating parts with proper attribution is legitimate.

### Work breakdown for B

Layer by layer, with the proof each one owes, in §8 below. In short: crypto and packet,
fragmentation and protobuf, the transport state machine, the two state directions, the
Android integration, and — separately and later — prediction.

### The reading is done

All four points that used to be listed here as "verify before writing any of it" are
closed, and the answers are in **[mosh-wire-format.md](mosh-wire-format.md)**, written
from the mosh 1.4.0 source on the same day as this file. The two that mattered:

- the cipher is **OCB version 3 = RFC 7253**, which BouncyCastle already implements, so
  the crypto layer needs no new dependency and no native code — this was the largest
  single risk in the plan;
- `mosh-server` does not scrub its environment, so `SSH_AUTH_SOCK` passed as a prefix
  on the SSH command reaches the login shell, which is what makes §6 work. The `-l`
  option, which the earlier draft of this file suggested for the purpose, is **not** a
  general environment setter and must not be used for it.

Three questions are left deliberately for the test rig of phase 1 rather than for
another reading, and they are listed in §7 of that file.

## 8. How the protocol gets written: phases, gates and traps

The order below is chosen so that **every phase ends with a fact rather than with an
opinion**. Each one has a gate: something that either happens or does not. A phase
whose gate has not been met is not finished, however much code it has produced.

### Phase 0 — get the specification. Done, 2026-10-20

A shallow clone of mosh 1.4.0 kept outside this repository, read, and turned into
[mosh-wire-format.md](mosh-wire-format.md) in our own words. No code was written in
this phase and none should have been.

*Gate: the four open questions answered or consciously deferred to phase 1.* Met.

### Phase 1 — the test rig. Nothing else matters as much. Done, 2026-10-20

`mosh-server new -s -c 256` launched **by hand** on the development host, which prints
its port and key, driven by a small JVM harness that talks to it — no Android, no
emulator, no APK, no app lifecycle.

The reason this comes before any protocol code: with a real counterparty, the gate of
the first layer is binary. If the server answers our first datagram, the nonce, the
cipher and the framing are right; if it does not, they are wrong, and we know in
seconds instead of after a week of code written blind. Every later layer gets its
evidence from the same rig, and a captured datagram becomes a test vector for the JVM
suite that already exists.

*Gate: a `MOSH CONNECT` line parsed and a server process we can talk to on loopback.* Met
— and it went further than its own gate, see [mosh-wire-format.md](mosh-wire-format.md) §7.
The rig lives in `~/Documents/claude-generico/sshborg/mosh-lab/`, with its recipe and
its two traps in the `README.md` there.

### Phase 2 — crypto and packet. Written, 2026-10-20

Nonce with its direction bit, OCB through BouncyCastle, the two timestamp fields.

*Gate: a datagram captured from the real server decrypts, and re-encrypting the
plaintext reproduces the captured bytes exactly.* Real vectors, not invented ones.

Proved in Java against a live server before any Kotlin was written, then written as
`com.sshborg.mosh.MoshCrypto`: the server authenticated our datagram, its answers
decrypt, and **re-sealing its plaintext reproduces its own ciphertext byte for byte**,
which is the interoperability claim made into a test rather than a sentence. The
sequence counter and the replay filter (direction bit included) came with it.

### Phase 3 — fragments, zlib and protobuf. Written, 2026-10-20

Reassembly keyed on the instruction id, `Inflater` on the payload, and a hand-written
varint reader for the three messages.

*Gate: a frame from the real server parses, and re-serialising it yields the same
bytes.* Met in both directions, including a hand-built `UserMessage` the server
accepted, and now in `MoshFraming`, `ProtoWire`, `MoshInstruction` and `MoshDiffs` with
30 unit tests over mosh's own captured bytes.

One deliberate departure from mosh: a completed assembly is handed over once, where mosh
re-delivers it for every repeat of the final fragment and leans on idempotency. Ours is
idempotent too, but decompressing a full-screen repaint again on each retransmission is
work a peer could ask for at will.

### Phase 4 — the transport state machine. Written, 2026-10-20

Sequence numbers, acknowledgement, the idempotency rule on `old_num`/`new_num`,
retransmission driven by the round-trip estimate, the 3 s empty ack, local port
hopping after 10 s without a round trip while reading every socket still open, and the
closing handshake.

*Gate: a session stays up for ten minutes with neither side giving up, survives the
local port being changed underneath it, and resynchronises after the socket is
recreated.* Met, in `MoshTransport` and `MoshTiming`, with `scripts/mosh-lab.sh` as the
one command that starts a throwaway server, runs the session against it and takes it
down again. The session types into a real shell, reads its output back, idles through
several heartbeats, has its **local port replaced underneath it** mid-session — the
server's own log says `Server now attached to client at …` and the session carries on,
which is the mobile case the whole feature exists for — and then closes with the
handshake acknowledged.

The state machine owns no socket and starts no thread: every entry point takes the time
and `tick` returns the datagrams to send, so the timing is driven by a test with a clock
it controls. That is the only honest way to check a heartbeat and a retransmission, and
it is why there is no `Thread.sleep` and no call to the clock anywhere in the package.

Two things the lab found that reading could not. The sequence counter has to survive the
socket being replaced, or the server ignores us as a replay — the first probe wasted two
runs on exactly that. And a frame whose reference state we do not hold must be dropped
*and acknowledged at once*: before that, the server spent the first 1.3 seconds of every
session resending the prompt while we threw it away, because it had no way to learn which
state we held until our next heartbeat. The fix cut it to one frame interval.

### Phase 5 — input and output

The user-input diff towards the server, and the host diff applied to the existing
`TerminalEmulator` — including routing the emulator's own query replies back into the
input stream, which is easy to forget and hard to debug later.

*Gate, in three steps: a keystroke reaches `cat`; `vim`, `htop` and `less` repaint with
no dirt; a resize propagates both ways.* The first step is done — the rig ran
`echo mosh-lab-ok` in a real session — and the repaint step is the one unknown left that
could still cost real work.

### Phase 6 — the Android integration

Bootstrap over a JSch exec channel with a pty of the right size, the per-host switch,
detection and silent fallback, the SSH session kept alive underneath, and the same
foreground service and Wi-Fi lock the SSH sessions already use.

*Gate: the Wi-Fi is switched off on a real phone and the session carries on.*

### Phase 7 — prediction, separately or never

Only after everything above has been in use for a while, behind its own switch. A
half-finished prediction engine shows wrong characters and corrects them afterwards,
which reads as a bug in the app rather than as a feature in progress.

### Where the code lives

`app/src/main/kotlin/com/sshborg/mosh/`, **pure JVM, no Android API at all** — not even
`android.util.Log` (as of 2026-10-20: `MoshCrypto`, `MoshFraming`, `ProtoWire`,
`MoshInstruction`, `MoshDiffs`, `MoshTiming`, `MoshUserStream`, `MoshTransport`, with
their tests and vectors under `app/src/test/{kotlin,resources}/`). The one integration
test talks to a real server and skips itself when there is none, the way the SSH ones do. That is what lets `./gradlew test` cover the protocol and lets the
phase 1 harness run on it with no adapter in between. Exactly one class touches Android
and JSch: the glue that owns the session, and it lives outside that package.

### Rough shape of the effort

Phase 0 half a day (spent). Phase 1 a day. Phases 2 and 3 two or three days. **Phase 4
one to two weeks with its tests.** Phase 5 three to five days. Phase 6 a week plus
field testing on a real phone. Phase 7 is its own project.

### Three traps to hold in mind from the first line of code

**The nonce must never repeat.** OCB with a reused nonce is not weakened, it is
forgeable. The counter never goes backwards — not when the socket is recreated after a
network change, not after a reconnect, not ever. This belongs in the type as an
invariant, not in the goodwill of the caller.

**Roaming is a security question, not a convenience.** The peer address is adopted from
a packet that authenticates and whose sequence number is new; nothing else may move it,
and a regressed sequence number is never accepted. The receive algorithm in
[mosh-wire-format.md](mosh-wire-format.md) §2 is to be followed step by step, including
the two early returns, because that is where the idempotency lives.

**A gap in the escape repertoire does not look like an error.** It looks like a dirty
screen, and the user reads it as our bug. Hence the repaint gate in phase 5 against
real programs, with frames captured from the real server kept as test vectors.

## 9. Costs that are not code

- **Battery.** Mosh's heartbeat runs at about 3 s against our SSH keepalive at 15 s.
  With background sessions kept alive by the foreground service, that is a radio
  wake-up every three seconds on a locked phone. Visible cost, and it is the sort of
  thing that arrives as a one-star review rather than as a bug report.
- **Support load.** Blocked UDP on hotel and corporate networks, missing mosh-server,
  non-UTF-8 locales: three failure modes we cannot fix from the app, each of which
  looks like our fault.
- **Documentation in ten locales**, including the two caveats above.

## 10. What to do first instead

Almost nobody asks for mosh because of local echo; they ask because they want the
session not to die. Two things already within reach:

1. **Automatic re-attach to tmux/screen**: an optional per-host command on connect
   (`tmux new -A -s sshborg`). Works on *any* server, needs nothing installed, and the
   work survives everything.
2. **Immediate reconnection on network change**: listen to `ConnectivityManager`
   callbacks and re-dial at once instead of waiting for the TCP timeout, keeping the
   local scrollback and re-running the attach command.

Together these are days of work, not months, and they cover the Wi-Fi→cellular
handoff case that was diagnosed as unfixable client-side with SSH alone, for every
host rather than for mosh hosts only. What they do not give is typing latency: on LTE
that is 40–80 ms, which only really hurts on distant or satellite links.

## 11. Decision

Mosh is **not scheduled**. Route B with a per-host switch, the SSH session kept alive
and the scrollback caveat declared is a defensible feature and worth a release of its
own, but it comes after the reconnect-plus-reattach work above — and only if the
requests keep arriving once that is in people's hands. The queue in front of it is
the 1.20.0 review, Huawei, and the Android 7 branch.

## References

- mosh.org, and the 2012 USENIX ATC paper "Mosh: An Interactive Remote Shell for
  Mobile Clients" (Winstein and Balakrishnan).
- mosh source: `src/network/` (transport, fragmenter, crypto), `src/statesync/`
  (user input and terminal state), `src/terminal/display.cc` (how the diff becomes
  escape sequences), `src/frontend/mosh-server.cc` (the launch environment).
