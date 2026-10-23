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
work a peer could ask for at will. That shortcut is only safe while the sender never
reuses a fragment id for different bytes, so `MoshFragmentAssembly` counts the fragments
that contradict one it already holds, and the integration test asserts that counter is
zero against a real server rather than taking mosh's chaff on trust.

The receiving half was closed on the same day by resizing the window to 240×70 and
painting it with `base64` of `/dev/urandom`: **nine fragments in one instruction**, back
together, and the zlib checksum inside is what proves the order was right. It also found
a defect reading had missed — we were treating mosh's 1280 as a UDP payload when it is an
*IP datagram*, so our packets were 28 bytes over on IPv4 and 48 over on IPv6, where 1280
is the minimum every path must carry. See §5 of the wire-format document.

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

### Phase 5 — input and output. Gate met, 2026-10-21

The user-input diff towards the server, and the host diff applied to the existing
`TerminalEmulator` — including routing the emulator's own query replies back into the
input stream, which is easy to forget and hard to debug later.

*Gate, in three steps: a keystroke reaches `cat`; `vim`, `htop` and `less` repaint with
no dirt; a resize propagates both ways.* **All three met**, by `scripts/mosh-gate.sh`.
This was the unknown that could still have cost real work, and it did not: the answer is
that our emulator already agrees with mosh.

#### The oracle, because an opinion was not going to be enough

"Repaints with no dirt" cannot be asserted by the code that produced the repaint. So the
gate records the host byte stream of a scripted session, replays it into our emulator,
and replays **the identical bytes** through a second, independent emulator — a detached
`tmux` pane of the same size — then diffs the two renderings cell by cell. Two emulators
disagreeing about the same bytes is a defect in one of them; two agreeing is the only
evidence worth having here. The result, at 100×30:

| checkpoint | what it is for | result |
|---|---|---|
| `wide-margin` | 日本語 against the right margin at four offsets, where our `CharWidth` and the server's `wcwidth` have to agree and where mosh leans on the terminal's own deferred wrap | identical |
| `colours` | the 256-colour cube and a true-colour ramp, as cell *placement* | identical |
| `colour-cells` | one cell per palette entry at a known row — the attribute half, asserted directly | 256 cells, all correct, palette and 24-bit |
| `partial` | a scroll region, absolute positioning and `ECH`: the moves mosh picks when they are cheaper, which a program would rarely make by itself | identical |
| `less` | a pager: reverse-video status line, screen repainted by scrolling | identical |
| `vim` | a full-screen editor: alternate screen, status line, repaint driven by cursor movement | identical |
| `htop` | the densest partial-update program to hand; recorded but **not** diffed, because its screen is never the same twice | identical anyway, including the sort arrow |

Seventeen checkpoints compared, none disagreeing, and the resize step proved separately
by asking the far end's shell what size it thinks it is (`tput cols`) and reading 120×40
back.

Two things are worth being plain about. The text diff **cannot see an attribute** — a
cell in the wrong colour holds the right character and the oracle shrugs — which is why
`colour-cells` exists and asserts the colours itself rather than comparing them with
anything. And `tmux` is one mature emulator, not the truth; agreement means we are not
alone in a misreading, not that neither of us has one.

#### What the gate found, which was not what it was looking for

The first run reported fifteen checkpoints and no disagreements, and it had tested
**nothing**: every recording was the same 259 bytes of untouched prompt. `awaitQuiet`
waited for the screen to go quiet, and the screen is always already quiet at the moment
keystrokes are handed to the transport — a tick schedules them 8 ms out so that a burst
becomes one packet — so it returned at once, every time, and the keys piled up unsent.
Only the resize assertion at the end noticed. It now waits for the host to answer first
and for silence second, which is the difference between a gate and a formality.

That is the second time on this branch that a passing check turned out to be a vacuous
one, and both times what caught it was an assertion about something *else* in the same
test. It is an argument for a gate always ending on a question whose answer cannot be
"nothing happened".

#### Where it plugs in: a byte source under the terminal, and nothing above it

The rule, decided before any of this is wired up: **the terminal must not learn that
mosh exists.** It is not only a matter of taste. Everything above the byte stream — the
scrollback, the selection, the extra-key bar, the IME's word mode, the TV remote, the
title bar, the history suggestions — has been settled by use and by bug reports, and
none of it has any business being touched by a transport.

That is affordable because the seam is already narrow. Today the bytes reach the
emulator through exactly three places:

| direction | today |
|---|---|
| output in | `session.inputStream.read(buf)` → `em.process(buf, 0, n)`, the reader loop in `TerminalViewModel` |
| input out | `shellSession.write(data)` — from `sendData`, and from `em.onSendResponse` for the emulator's own query replies |
| resize | `shellSession.resize(cols, rows)` |

So the layer is a small interface over those three, with two implementations: the SSH
one, which is what exists, and the mosh one, which is `MoshTransport` plus a socket and
the tick loop. The output direction becomes a push rather than a blocking read, because
mosh's bytes come out of a tick and not out of a stream — which is the only shape change
the reader loop needs. `MoshTransport.Listener.onHostBytes` was written to be exactly
that, and the transport deliberately owns no socket and no thread so that it can sit
under either.

What that buys: the per-host switch, detection and silent fallback, and the ability to
take the whole feature out again by not constructing the mosh implementation.

**What it does not buy, and no architecture could.** If the repaint gate finds that we
mis-render a sequence mosh leans on, the fix lands in `TerminalEmulator`, which SSH
shares. That is acceptable on one condition: the fix has to be *"we now behave like
xterm"*, which is a bug fix for SSH as well and is guarded by the existing suite. What
must not happen is an `if (mosh)` branch inside the emulator. Two interpretations of the
same escape sequence is precisely how the work done so far would get damaged, and it
would double the terminal's test matrix for good.

The gate above has now taken the sting out of this: the one fix the repaint comparison
asked for in the terminal was none. Should a later one ask, the rule still holds.

The one place that was worth checking before saying any of this: mosh relies on the
client's own autowrap and compensates for terminals that snap the cursor to the true last
column instead of leaving it past the margin (`terminaldisplay.cc:425-432`, and a note at
469 about making the terminal wrap where it wrapped for mosh). Our emulator defers the
wrap for narrow characters (`TerminalEmulator.kt:366-369`), which is the xterm behaviour
mosh is written for. That one looks already right, which is a good sign for the gate
without being a substitute for it.

### Phase 6 — the Android integration

Bootstrap over a JSch exec channel, the per-host switch, the SSH session kept alive
underneath, and the same foreground service and Wi-Fi lock the SSH sessions already use.

*Gate: the Wi-Fi is switched off on a real phone and the session carries on.*

#### Decisions taken 2026-10-21, which change what was written above

- **The switch is a checkbox in the host form**, beside the colour row where there is
  room, labelled `Mosh` with `(experimental)` under it, and **off by default**.
- **No silent fallback, and no autodetect worth the name.** The plan said detection and a
  quiet fall back to SSH. That was wrong for an explicit per-host switch: somebody who
  asked for mosh and did not get it is owed the reason. A failure raises a dialog saying
  what went wrong, with *carry on over SSH* and *leave* as the two answers.
  [MoshUnavailable.Reason] is therefore a real enumeration — not installed, locale not
  UTF-8, an unreadable answer, no datagram ever came back — because each has a different
  cure and three of the four are on the server.
- **mosh and a jump chain exclude each other** in the configuration, but a host reached
  over mosh is still a perfectly good bastion: the inward hop is SSH, runs server-side,
  and is immune to the phone's network. That is the arrangement in §5.2, and it is the
  right way round. What is excluded is SSH → jump → mosh.
- **The radio cost goes in the guide** rather than being engineered around: a heartbeat
  every three seconds for as long as the session is open.
- **The scrollback stays as it comes.** Lines that scroll away do enter it, because mosh
  really does scroll the terminal, but a mosh session starts with an empty one and
  anything painted without scrolling leaves nothing behind. The gesture is not disabled
  and the difference is written in the guide.

#### The bootstrap, done and gated 2026-10-21

`ShellSession.startMoshServer` opens one exec channel, runs one command, reads one line.
`MoshBootstrapIntegrationTest` drives it against a real server from the JVM — no device
needed, which is why it is the part of phase 6 that could be finished first: it logs in
over SSH, starts the server, holds a session with the handed-over key, types into it, and
then checks that **SFTP still works on the SSH session underneath**, which is the whole
reason for not closing it.

Three things that are not obvious are in that function's KDoc: no pty, `env` rather than
`VAR=value cmd` because fish has no such syntax, and the network timeout raised far above
the default because Doze would otherwise end the session in exactly the situation mosh
exists for.

The handover line carries the session key, so `MoshBootstrap.redact` takes it out of
anything that can reach a log or the error dialog's copy button, and that is unit-tested
rather than reasoned about. The first version of that test was wrong in an instructive
way: it fed the parser a *fake* handover line and expected it to be rejected. But a line
that looks like a handover **is** one — SSH has already authenticated whoever printed it,
so there is nothing there to distrust. Redaction is a pure function and is tested as one.

#### The socket loop, done and gated 2026-10-21

`MoshTerminalTransport` is the other half of the protocol: `MoshTransport` owns no socket
and no thread so that a test can drive it with a clock it controls, and this is the loop
that asks it what to send, puts it on the wire, waits for an answer or for the next
deadline, and hands back what arrives. It has no Android in it either, which is what lets
it be gated from the JVM against a real server through the same `TerminalTransport` the
SSH side uses.

**The loop does not live in the screen's ViewModel, and that is the one structural thing
mosh forces on the app.** `SshTerminalTransport` reads while somebody is reading, and that
is enough: nothing is lost while the terminal is away because TCP holds what arrives. Mosh
has nowhere to put anything — the session exists only for as long as we keep answering,
three seconds of heartbeat at a time — so the loop has to outlive the ViewModel, which a
switch between sibling tabs recreates. It therefore runs in a scope the transport owns,
started by `start()` and ended by `close()`: for exactly as long as the session is in the
`SessionManager`. Which is also exactly as long as the foreground service lives, so
**nothing new is asked of the service or of its Wi-Fi lock** — the process stays alive and
the radio stays out of power-save for the same reason the SSH sessions do.

That has a consequence worth stating. Because the loop runs whether or not anybody is
reading, the host's bytes have to go somewhere when nobody is, and they are *applied to
the session's emulator* rather than queued. A queue is the obvious answer and the wrong
one: a program left running with the terminal in the background would fill it without
limit, where the emulator has a bounded scrollback and is the thing that will be on screen
when the user comes back.

The client's half of roaming is the local port, and there are three things that move it:
nothing having made it there and back for ten seconds, a send failing outright — which is
what a Wi-Fi interface going away looks like from here, and is cured by a new socket on
whatever network the phone has now rather than by ending the session — and being *told*,
which is what `changeLocalPort()` is for and what the `ConnectivityManager` callback will
use, ten seconds earlier than noticing. The port we have left is kept open and readable
for a minute, because what the server is sending at that moment is still addressed to it;
nine sockets is the ceiling, as in mosh. EMSGSIZE, the one thing that can prove the MTU
guess wrong, drops it to 500.

`close()` says goodbye and gives the server a second to acknowledge it. Without that it
detaches and sits there until its own network timeout — minutes of a process, and of a
terminal, for a session the user has closed. The session is given up on after the server's
own network timeout plus fifteen seconds, since that is the longest the far end can still
be there; there is no summary line for it, only the generic one the SSH side already uses,
and the detail carries the round-trip estimate, the number of hops, the refused sends and
the undecipherable datagrams — no key, no address, no host name.

One limit is inherited and cannot be fixed from here: `mosh-server -s` binds to the
address the SSH connection arrived on, and the handover says nothing about which that was,
so we resolve the host name again and send there. A host with several addresses whose
first is not the one SSH ended up using leaves us talking to a port nobody is listening
on. It looks exactly like a blocked path, which is reported, and the cure is to put the
address in the host instead of the name. The stock client avoids this by resolving the
name itself and then ssh-ing to the address; we cannot, because by the time the question
arises the SSH session is not ours to re-dial.

*Gate: `MoshTerminalTransportTest`, which ends on four questions whose answer cannot be
"nothing happened".* A command typed through `write` appears **on our own screen** rather
than merely in a byte stream, so the frames, the emulator and both sizes agree. With the
reader detached, as a tab switch leaves it, the screen keeps up. After moving to a new
local port the session carries on, because the server followed us — the roaming the whole
feature is for. And `exit` ends it as `CleanExit`, the way an SSH shell does, so the
screen above closes itself without ever learning which transport it had.

This time the gate's honesty was checked on purpose rather than by accident: the
unattended sink was broken deliberately, and the run failed on exactly the assertion that
claims it. What the gate does *not* cover: the minute-long grace on a retired port and the
nine-socket ceiling, which are four lines of arithmetic, and a real change of network —
that one is the phone gate, and it is what the switch in the host form exists to reach.

#### The switch and the dialog, 2026-10-21

The checkbox sits beside the colour row, which had left the right half of its line empty,
labelled `Mosh` with `(experimental)` under it. Database 14 adds one column defaulting to
0 and the migration test asserts that a host which predates mosh comes out with it off;
backup format 8 carries it for every host rather than only when it is on, so restoring
over a host that has it on turns it off again — a backup is what the hosts were, not a
list of things to add.

The exclusion with jump chains is resolved in the direction that loses nothing: while a
chain is configured the whole switch row stops responding and a line underneath says why,
so the cure is to remove the chain first and nothing is ever silently thrown away. When
mosh is on, the jump-host section is one line in place of its fields, and that line says
the thing worth saying — a host reached over mosh is still a perfectly good bastion for
others. The rule is then applied twice more, where nothing can get round it: in `save`, so
no row ever leaves with both, and in the connect path, because an imported backup could
have been edited by hand and a jump chain is the one thing mosh cannot carry.

The failure dialog came with the wiring rather than after it, because writing a failure
path that was known to be wrong and then replacing it would have been the longer way
round. Four reasons, four sentences, and the two answers the user asked for: *carry on
over SSH* and *leave*. Carrying on costs nothing at all — the SSH session is already open
and healthy, since it is what started the server — so it is simply the transport that
would have been made anyway. The server's own words are shown only for the case where they
are the diagnosis, selectable and in a monospace box, and the handover line has already
had the session key taken out of it before the text could reach anything.

One hazard found while wiring: the SSH session reaches the `SessionManager` only once the
transport has been chosen, so during the window in which the dialog is waiting for an
answer it belongs to nobody. Leaving the screen then — the back gesture, a rotation —
cancelled the connect coroutine and left a live login, and a `mosh-server`, with nothing
holding either. `onCleared` now closes it, which was also the one thing standing between
the password prompt and the same leak.

**Two timeouts, and why they are the way round they are.** The client gives up after half
an hour of silence (`MoshTerminalTransport.SILENCE_LIMIT`) and the server is told an hour.
The client is therefore always the first of the two to give up, so the server's figure
never decides when a session ends — it decides only how long a server the client has
already written off keeps a shell on the host, which matters because a phone abandons
sessions far more readily than a laptop does. Half an hour is not a technical limit but a
compromise with something missing: mosh's own client shows a "contact lost" line and goes
on trying for ever, and until the terminal has somewhere to say that, a screen that cannot
be reached is a photograph with nothing to explain it. Both numbers grow together the day
that line exists. This also corrects what §6 of [mosh-wire-format.md](mosh-wire-format.md)
used to say: `MOSH_SERVER_NETWORK_TMOUT` unset means *wait indefinitely*, not forty
seconds — the forty is the server forgetting the client's address, which is roaming, not
exiting.

#### Why the login cannot be pushed down below the title bar, 2026-10-22

Under SSH the terminal's first screenful is kept clear of the retracting title bar by
writing `rowsUnderTitleBar` line feeds into **our own** emulator before a single byte has
arrived (`TerminalViewModel.connect`). The server's output is a stream appended at the
cursor, so the motd simply begins further down and nothing else in the app has to know.

The first run on a real device reported that this does not happen under mosh, and the
question was whether the same trick could not just be applied there. It cannot, and the
reason is worth keeping because it is the whole character of the protocol. These are the
first bytes of a real session, recorded with three line feeds already in our emulator:

```
ESC[r ESC[0m ESC[H ESC[2J ESC[?25l  ESC[K LF (×29)  ESC[2;1H ESC[?25h …
```

Home, **clear the screen**, clear every line, then an absolute cursor position. Measured:
the cursor was on row 3 before the first frame and the prompt ended up on row 1 — exactly
where it would have been with no trick at all. The line feeds existed for a fraction of a
second and were erased.

And in general it is not merely ineffective but unsound. After that first frame mosh sends
*differences against the screen it believes we hold*, which is the screen its own terminal
had at the state we last acknowledged. Anything we write into our emulator ourselves makes
that belief false, and every later frame is then computed against a screen we do not have
— permanently, since there is no second full repaint except on a resize. The screen, under
mosh, is the server's. The client may not edit it.

The obvious next question, and it was asked: why not send three Enters to the *shell* once
mosh is up, which is real input and therefore cannot put us out of step with the server?
Tried, and the screen says why not:

```
before:  row 1: sshborg@fatberry:~ $            (cursor row 1)
after:   row 1: sshborg@fatberry:~ $
         row 2: sshborg@fatberry:~ $
         row 3: sshborg@fatberry:~ $
         row 4: sshborg@fatberry:~ $            (cursor row 4)
```

Adding lines below does not move what is above them. The first prompt — and on a host that
has one, the whole motd — stays exactly where it was, under the bar; what moves down is
only the line being typed into. The cost is three duplicated prompts, which on a two-line
prompt looks like a fault in the app, and on a long motd the Enters would scroll it *up*
and away instead. There is also a safety side to it that has nothing to do with looks: a
login shell may be an interactive program — a menu, an attach that asks for confirmation,
a `read` — and three Enters it did not ask for are three answers it did not expect. Input
the user did not type is not ours to send.

**Decided, 2026-10-22: the bar starts out of the way instead.**
`TerminalViewModel.barWouldHideOutput` says whether a bar lying over the top of this
terminal would cover the host's own output rather than rows left blank for it, and the
screen keeps the overlay retracted from the start when it would. It is asked of the
*host's* switch and not of the transport, because the bar's countdown is shorter than a
login: by the time there is a transport to ask, the bar would already have covered the
first screenful. It is then corrected from the transport, so a session that fell back to
SSH is not left with a stale answer. Only the arrival changes — a tap brings the bar back
as always, and the countdown takes it away as always. What is given up is the host's name
being shown at the start of a mosh session, and with it the lesson that a tap brings the
bar back; what is gained is that nothing is ever hidden. The user chose this over the
alternative below.

What is left is to move the *drawing* rather than the content: offset the drawn grid
downward by the height the bar covers while the bar is there, and let the offset follow
the bar as it retracts. No injection, no protocol involvement, nothing the transport has
to know, and it would work the same for both — which would let the line-feed trick be
deleted rather than branched. The cost is a change to settled behaviour: today the content
under SSH sits still while the bar slides away, and with a following offset it would slide
up with it. That is the user's call, not a decision for this document.

#### What the first field test cost and taught, 2026-10-22

Three reports from a real device, and the order they were answered in is the lesson.

**The wheel in tmux did nothing.** mosh carries `?1000h` and `?1006h` but never `?1049h` —
it keeps the terminal on its own side and sends what it looks like, so it switches to the
alternate screen itself and says nothing. The gate for "the wheel belongs to the program"
was *alternate screen and mouse reporting*, and the first half can never be true there.
The policy moved into `TerminalEmulator.wheelBelongsToApplication`, which reads
`hasAlternateScreen`, set from `TerminalTransport.screenIsSynchronised`. Both facts about
mosh are now held by a test against a real server rather than by a reading of its source.

**Three blank rows on arrival, sometimes, and then the typed line jumping to the top.**
One bug, and the one looked at the day before and called harmless: the title-bar line
feeds were still being written under mosh. The first frame erases them, which had been
measured — but the pump is already running by then, so whether they land before that clear
or after it decides whether the session is merely odd or corrupt. Landing after, our
screen sits three rows below the one the server is computing differences against, and the
jump is the server positioning the cursor for its own layout. **A race left open by a
conclusion that was right about the common case.** The fact now has one name on the
transport and three places take from it only what they need.

**"Half a second a character, and plain SSH is fine on the same tablet."** This one took
three wrong explanations before a number, and every wrong one was a guess about a machine
that was not in front of me:

1. *the draw holds the emulator's lock and blocks the protocol loop.* Plausible, and the
   lab even reproduced a 240 ms every other keystroke with a simulated slow draw — but
   that 240 ms turned out to be the measurement itself queueing for that lock behind its
   own painter, Java's monitors not being fair. The real number, taken from the device,
   was `screen-wait<=10ms`: innocent. Delivery was moved off the loop anyway and that
   stands on its own merits, but it was not the cure;
2. *the radio is losing datagrams.* `missed=0` out of 55, counted from the gaps the far
   end leaves in its own sequence numbers: innocent;
3. *the cost per datagram of OCB and inflate on a 2017 ARM.* Never got as far as being
   tested, because by then the counter that mattered was already on the screen.

What it actually is: `stale=21` against `applied=19` — **more frames thrown away than
applied, with nothing lost in transit.** That is the shortcut of phase 4, where this
document says the cost of keeping one terminal instead of mosh's queue of them is "an
occasional extra retransmission". On a LAN with no round trip to speak of that is true,
which is exactly why the lab measures 17 ms a keystroke and cannot see this at all. At
71-110 ms of round trip, with the server's frame interval at half of that, about half of
what it sends can never be used by a client that keeps one screen.

Acknowledging faster took the tail off: every state we accept is acknowledged within
`ACK_DELAY` instead of only the ones carrying something to show, and a frame we threw away
is acknowledged immediately, because until the far end hears which state we hold it goes
on sending frames we cannot use. Median 115 ms and a worst of 316 over 125 samples, where
before there were 438s and 770s. SSH on the same device is a median of 72 ms, so mosh is
now within about one round trip of it rather than ten.

**What is left, and now with a measurement behind it:** keep a few screens of history, as
mosh does, so that a frame diffed against a state we have passed is applied from the older
screen instead of dropped. The log prints the three numbers that say so — a thrown-away
frame reads `old=233 new=236 held=235`, the far end diffing from the state it has heard us
acknowledge rather than the one we hold — and no amount of faster acknowledging can close
that, because the acknowledgement still takes half a round trip. It wants a snapshot of
the visible screen per state, which our emulator has no way to make yet.

**And a rule, for the third time on this branch.** A gate that ends on a question whose
answer cannot be "nothing happened" is not enough on its own: `MoshTerminalTransportTest`
typed, saw the right text come back, and would have passed just as happily at two seconds
a character, because it waits fifteen seconds for the text to appear. A gate has to end on
a question whose answer is a **number**, where a wrong one is a different number rather
than a missing event. Hence `MoshLatencyTest`, and hence the one line a debug build logs
every two seconds on a real device: the lab cannot answer for somebody's tablet, and three
of my explanations for that tablet were wrong before one measurement from it settled the
matter.

#### The screen's recent pasts, and what typing then costs, 2026-10-22

`TerminalScreenHistory` keeps the last eight screens, so a frame the far end computed
against a state we have already passed is applied to the right screen instead of thrown
away. What it holds and what it deliberately does not is in its own KDoc; the one thing
worth repeating here is the scrollback length, because the lines that scrolled away since
are about to scroll away again, carried by the frame replayed over the top, and a history
that left them behind would quietly double every line of a long session's output.

Three things make it fit the architecture rather than sit on top of it. The emulator
gained a capability, not a branch: "give me the visible screen as a value, and put it
back", which nothing in the parsing consults and SSH never asks for. The protocol gained
one listener method that defaults to *no*, so a client without a history behaves exactly
as it did. And the ordering lives in the one queue that already existed: bytes reach the
screen off the protocol loop, so "put the screen back to 233, then draw this on top" and
"keep a copy of 236 once 236 is on the screen" travel in that same queue, while the loop
keeps its own tally of returnable states because it has to answer before the screen's
thread gets round to the work. A resize throws the lot away on both sides at once.

**What a keystroke costs on the tablet**, measured through four stages of the same
evening, with plain SSH on the same device and network as the ruler:

| | median | p90 | worst | frames dropped |
|---|---|---|---|---|
| as first built | 135 ms | ~440 ms | 770 ms | 21 of 40 |
| acknowledging at once | 115 ms | 240 ms | 316 ms | 140 of 253 |
| with the screen's pasts | **97 ms** | 168 ms | 282 ms | 28 of 160 |
| SSH, same device | 72 ms | — | 134 ms | — |

Seventy-eight frames rescued in one session of typing, nothing refused, nothing lost in
transit, and a floor of 2 ms when the far end happens to be in step.

**Why the last seventeen per cent cannot be rescued either.** The frames still dropped all
have the same shape — `old=99 new=101 held=100` — a difference computed against a state we
never passed through, so there is no screen of ours to put back. Mosh's own client cannot
do better: it keeps the states it *received* and ignores an instruction starting from one
it does not know. This is parity with the original, not a remaining shortcut.

**What the 25 ms between 97 and 72 is.** The server's frame rate, which is half its own
round-trip estimate. Mosh's answer to that is prediction — the client draws the character
locally at once and corrects it if the server disagrees — which is phase 7 and still the
same judgement as before: a half-finished prediction engine shows wrong characters and
takes them back, and reads as a bug in the app rather than a feature in progress. What has
changed is that the question is now a real one rather than theoretical: without
prediction, mosh is a touch slower to type on than SSH over the same network, and
everything it gives is in surviving the network changing underneath.

#### What phase 6 still owes

- **The guide, in ten locales**: the three-second heartbeat and what it costs in radio,
  the scrollback that starts empty, and the three failures that are on the server.
- **"Contact lost, still trying"**, as above. It is the difference between a patient
  client and a frozen screen, and it is what would let both timeouts be as long as mosh's
  own.
- ~~**The gate on a real phone**~~ — **met, 2026-10-23.** The Wi-Fi was switched off
  mid-session on a phone with a SIM, against a VPS, and the session carried on; the
  contact-lost line appeared and went by itself. It took one thing that was not in the plan:
  the per-host **IPv4 / IPv6** preference, because a mosh session is bound to the family it
  was born on and the first attempt had been born on IPv6 — which the carrier did not have.
  Phase 6 is therefore complete.

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
phase 1 harness run on it with no adapter in between.

Two things live outside that package and still have no Android in them, which is why both
are gated from the JVM: `ShellSession.startMoshServer`, which touches JSch because the
bootstrap is an SSH exec channel, and `data/terminal/MoshTerminalTransport`, which touches
neither — only `java.nio` and a clock. What is left for Android is the part that is
genuinely Android: the switch in the host form, the failure dialog, and the service that
was already there.

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
