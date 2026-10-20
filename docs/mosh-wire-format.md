# The mosh wire format, as we need it to implement a client

Written 2026-10-20 on `V1_DEV_MOSH`, from the mosh 1.4.0 source, to close the four
verification points left open by [mosh-plan.md](mosh-plan.md). It is the document to
implement against, so that nobody has to reopen the C++ for every field.

Reference source kept **outside this repository**, deliberately, at
`~/Documents/claude-generico/sshborg/mosh-ref/mosh` (shallow clone of tag
`mosh-1.4.0`, commit `bc73a26`). Nothing of it is vendored here: this file is our own
description of an interoperable wire format, which is what we are allowed to depend on
and what keeps the tree and the F-Droid build clean. Both projects are GPL v3, so
should we ever transliterate an algorithm rather than describe it, a file header with
attribution is enough; copying the `.proto` files into our tree is not needed and is
not done.

Line references below are to the mosh 1.4.0 tree, for whoever needs the original.

## 1. The datagram

```
UDP payload:  nonce[8] || OCB-AES-128( plaintext )           (ciphertext includes the 16-byte tag)
plaintext:    timestamp[2] || timestamp_reply[2] || fragment
fragment:     id[8] || (final<<15 | fragment_num)[2] || zlib( instruction )
instruction:  protobuf, TransportBuffers.Instruction
```

Everything multi-byte is big-endian.

**Crypto** (`src/crypto/crypto.{h,cc}`). AES-128 in **OCB version 3**, which is RFC
7253: mosh carries Krovetz's OCB v3 reference code (`ocb_internal.cc`) and its other
build path simply calls OpenSSL's `aes-128-ocb` (`ocb_openssl.cc`). BouncyCastle's
`OCBBlockCipher` implements the same RFC, so **no new dependency is needed** — this was
the single biggest risk in the whole plan and it is closed.

- key: the 16 bytes decoded from the base64 key printed by `mosh-server` (`Base64Key`);
- nonce: 12 bytes = four zero bytes followed by the 8 bytes on the wire;
- associated data: **empty**;
- tag: 16 bytes, appended to the ciphertext (`ADDED_BYTES = 16`);
- a datagram shorter than 24 bytes is malformed by definition.

The 8 wire bytes of the nonce are a 64-bit value: **bit 63 is the direction** (1 =
to-client, 0 = to-server) and the remaining 63 bits are the sequence number
(`DIRECTION_MASK`/`SEQUENCE_MASK`, `src/network/network.cc:66`). The sequence number is
a counter that **must never repeat for a given key**: OCB with a reused nonce is not
merely weakened, it is forgeable. Mosh enforces it with `Crypto::unique()`; we must
make it an invariant of our own type, and it must survive the local socket being
recreated after a network change. Mosh also refuses to encrypt more than 2^47 blocks
under one key and kills the session instead — worth copying, as a cheap safety net.

**The sequence number is also a replay filter, and the gate roaming passes through.**
Each side keeps an `expected_receiver_seq`: a datagram whose sequence number is below it
is still handed to the transport layer, but it does **not** update the peer address and
does not count as having heard from the other side (`network.cc:515-518`, flagged in the
source as security-sensitive). Our receiving side has to keep the same counter. In the
test rig this is the first thing to get wrong: a second run of a probe that restarts its
counter at 1 is authenticated and parsed, and then answered to the *previous* run's
address — which looks exactly like broken crypto and is not.

**Timestamps** (`Packet`, `src/network/network.{h,cc}`). Two 16-bit fields in front of
the payload: our own millisecond clock modulo 65536, and the last timestamp we received
from the other side (with the time we held it subtracted), which is how both ends
estimate the round trip. They are inside the encryption, not in the clear.

**Fragments** (`src/network/transportfragment.{h,cc}`). Ten bytes of header: a 64-bit
instruction id, then 16 bits holding a "final" flag in the top bit and the fragment
number in the low 15. Reassembly is keyed on the id; a fragment of a different id
starts a new assembly. The payload of the complete instruction is **zlib-compressed**
(`compress`/`uncompress`, i.e. RFC 1950 with the zlib header — on the JVM that is
`Deflater`/`Inflater` with default settings, *not* `nowrap`). Sending MTU starts at
1280 for both IPv4 and IPv6 and falls back to 500 if `sendto` reports `EMSGSIZE`.

## 2. The transport instruction

`TransportBuffers.Instruction`, all fields optional on the wire:

| field | no. | type | meaning |
|---|---|---|---|
| `protocol_version` | 1 | uint32 | must be **2**; anything else is a hard error |
| `old_num` | 2 | uint64 | the state this diff is computed *from* |
| `new_num` | 3 | uint64 | the state it produces |
| `ack_num` | 4 | uint64 | the highest state of *ours* the sender has applied |
| `throwaway_num` | 5 | uint64 | states the receiver may forget |
| `diff` | 6 | bytes | the state diff (see §3); empty for a pure ack |
| `chaff` | 7 | bytes | padding, ignored |

The receive algorithm (`networktransport-impl.h`) is the part to copy exactly, because
its idempotency is also its security:

1. version mismatch → drop the connection;
2. tell the sender side that everything up to `ack_num` is acknowledged;
3. if we already hold `new_num`, **return** — nothing to do;
4. find the state numbered `old_num` among the ones we hold; if we do not have it,
   **return** (an out-of-order or replayed packet, and the comment in the source flags
   this as security-sensitive);
5. discard states below `throwaway_num`;
6. apply `diff` to a copy of the reference state, number it `new_num`, insert it in
   order, and acknowledge the newest state we now hold;
7. a queue above 1024 states is a sign of a malicious or long-unidirectional peer:
   mosh then refuses to grow it for 15 s.

## 3. The two state types

Both diffs are protobuf messages carrying a repeated `Instruction`, where each
instruction holds exactly one proto2 **extension** — on the wire simply a nested
message under its own field number, which is why a hand-written parser of a few
hundred lines is enough and no code generation is required.

**Client to server** — `ClientBuffers.UserMessage`, field 1 repeated:

| extension | field | contents |
|---|---|---|
| keystroke | 2 | `keys` = bytes, field 4 |
| resize | 3 | `width` field 5, `height` field 6 (int32) |

**Server to client** — `HostBuffers.HostMessage`, field 1 repeated:

| extension | field | contents |
|---|---|---|
| hostbytes | 2 | `hoststring` = bytes, field 4 — terminal output to apply |
| resize | 3 | `width` field 5, `height` field 6 |
| echoack | 7 | `echo_ack_num` = uint64, field 8 — for predictive echo only |

Two details that are easy to miss and expensive to discover later
(`src/statesync/completeterminal.cc`):

- applying `hoststring` to the terminal **can produce a reply** (device-status and
  identification answers). Mosh routes it back as user input. Our emulator's replies
  must go into the mosh input stream, not into a discarded buffer.
- `echo_ack_num` only matters once there is a prediction engine; until then it is read
  and ignored.

## 4. What the host diff actually contains

The bytes in `hoststring` are produced by `Display::new_frame`
(`src/terminal/terminaldisplay.cc`, 537 lines), which repaints the difference between
two framebuffers. The repertoire is small and conventional, and our emulator already
handles all of the drawing part:

- `CUP` (`ESC [ r ; c H`), `ED` (`ESC [ 2 J`), `EL` (`ESC [ K`), `ECH` (`ESC [ n X`),
  `SGR`, `DECSTBM` (`ESC [ t ; b r` and `ESC [ r` to reset);
- cursor visibility `?25h/l`, reverse video `?5h/l`, application cursor keys `?1h`;
- window title as `OSC 0/1/2`, and **clipboard writes as `OSC 52`** — our emulator
  should keep ignoring that one unless we decide otherwise on purpose;
- the client's `smcup` is emitted from the *client's own* terminfo and is local to
  mosh-client: we have no terminfo and we do not need one.

**Correction to the plan's first draft: the mouse reporting mode is synchronised.**
`mouse_reporting_mode` and `mouse_encoding_mode` are part of the synced draw state, so
`Display` emits `?1000/?1002/?1003h`, `?1006/?1015/?1005h` and their `l` forms as part
of the frame (`terminaldisplay.cc:302-334`), together with `?1004` focus events and
`?2004` bracketed paste. The client therefore *does* learn that the remote application
asked for the mouse, and no "mouse always on" switch is needed: our existing
`?1000/?1006` support lights up by itself, as long as the generated reports are sent as
keystrokes. Of these modes our emulator today understands 1000, 1002, 1003, 1006 and
`?25`; it does not know 1015, 1005, 1004 and 2004, which in the ordinary case arrive
only in their "off" form and must at minimum be ignored without disturbing the parser.

## 5. Sizes, ports and timing

**The server's initial terminal size is the size of the pty on the SSH channel that
launched it** — `TIOCGWINSZ` on its stdin, falling back to 80×24
(`mosh-server.cc:411-423`). So the bootstrap channel should request a pty of the real
size, and a resize should be sent immediately afterwards anyway.

`mosh-server` binds a UDP port in **60001–60999** (`network.h:136`).

Port behaviour on our side is the opposite of what one would guess: **only the server
roams** (it adopts the source address of an authenticated packet, `network.cc:558`),
while the client keeps the server's address fixed and instead **hops its own local
port** when nothing has round-tripped for 10 s, which is how it escapes a stale NAT
binding (`network.cc:424`, `PORT_HOP_INTERVAL`). Old sockets stay open for up to 60 s
(`MAX_OLD_SOCKET_AGE`) and every one of them has to be read, not just the newest.

**The 1280 is an IP datagram, not a payload.** This is worth stating plainly because we
got it wrong and the rig caught it. Mosh aims at a 1280-byte packet *on the wire* and
subtracts the IP and UDP headers before anything else (`network.h:104-130`,
`network.cc:200`): 20 + 8 guessed for IPv4, and for IPv6 a deliberately pessimistic
40 + 16 (two extension headers) + 8. Its own comment gives the reason — tunnels and
mobile networks drop well below Ethernet's 1500, and it names a network that dropped
anything from 1320 bytes up. So:

| family | UDP payload | less nonce+timestamps (12) and tag (16) | less fragment header (10) |
|---|---|---|---|
| IPv4 | 1280 − 28 = **1252** | 1224 | **1214** per fragment |
| IPv6 | 1280 − 64 = **1216** | 1188 | **1178** per fragment |

The 1214 is confirmed by the server's own log, which reports exactly that length for
every non-final fragment of a repaint on a loopback IPv4 socket. Treating 1280 as the
payload, as we did at first, puts 1308 bytes on an IPv4 path and 1328 on an IPv6 one —
and 1280 is the minimum MTU every IPv6 path is *required* to carry, so the second is not
a matter of efficiency. After the kernel refuses a datagram (`EMSGSIZE`) mosh drops to a
payload MTU of 500 and stays there (`network.cc:413`).

| constant | value | what it governs |
|---|---|---|
| `SEND_INTERVAL_MIN` / `MAX` | 20 ms / 250 ms | minimum and maximum gap between frames |
| `ACK_INTERVAL` | 3000 ms | empty acks when there is nothing to say |
| `ACK_DELAY` | 100 ms | delay before acknowledging |
| `ACTIVE_RETRY_TIMEOUT` | 10000 ms | resend at frame rate while waiting |
| `SHUTDOWN_RETRIES` | 16 | closing packets before giving up |
| `SERVER_ASSOCIATION_TIMEOUT` | 40000 ms | server-side only: forget the client's address |

The 3 s ack interval is the figure behind the battery note in the plan: it is a radio
wake-up every three seconds for as long as the session is up.

## 6. The launch environment

- `MOSH_SERVER_NETWORK_TMOUT` and `MOSH_SERVER_SIGNAL_TMOUT` (seconds) are read from
  **mosh-server's own environment** (`mosh-server.cc:384,398`), so we set them as a
  prefix on the command we run over SSH. The first is what prevents a forgotten server
  from living forever on the host.
- **`-l` is not a general-purpose environment setter.** The variables it collects are
  applied only when the server's native locale is *not* UTF-8, and only after
  `clear_locale_variables()` (`mosh-server.cc:330-348`). Do not try to pass
  `SSH_AUTH_SOCK` through it.
- `mosh-server` never calls `clearenv`: it sets `TERM`, `NCURSES_NO_UTF8_ACS` and
  `PWD`, and unsets `STY`. **Everything else inherited from the SSH command survives
  into the login shell**, which is exactly what makes the agent-forwarding arrangement
  in §6 of the plan work — `SSH_AUTH_SOCK=… mosh-server new …`, as a prefix.

So the bootstrap command has the shape

```
MOSH_SERVER_NETWORK_TMOUT=<s> SSH_AUTH_SOCK=<stable path> mosh-server new -s -c 256 -l LANG=…
```

run on a pty-backed channel of the right size, from which we read `MOSH CONNECT <port>
<key>`.

## 7. What the test rig proved, 2026-10-20

A `mosh-server` 1.4.0 launched by hand on the development host and a throwaway Java
probe — `~/Documents/claude-generico/sshborg/mosh-lab/`, outside this repository,
with the recipe in its `README.md` — closed the first gates of phase 1 in an afternoon,
and everything in §§1–4 above is now measured rather than read:

- **the cipher is interoperable in both directions.** Our datagram was authenticated by
  the server (`Server now attached to client at …`), and its answers decrypt. BouncyCastle
  OCB against mosh's own OCB v3, no new dependency, nothing native. This was the plan's
  biggest risk and it is gone;
- **framing, zlib and the hand-written protobuf are right in both directions.** Fragment
  header, `Deflater`/`Inflater` with default (zlib) settings, and the five varint fields
  of the transport instruction;
- **the opening packet can be minimal**: `protocol_version=2`, `old_num=new_num=0`,
  `ack_num=throwaway_num=0`, no diff. The server adopts the sender's address on it and
  starts sending state;
- **acknowledgement, state numbering and throwaway work as described.** With our acks
  flowing, the server's instructions walked `1=>2, 2=>3, …` and its `throwaway_num`
  followed one behind, which is the queue being pruned;
- **input reaches the shell.** A `ClientBuffers.UserMessage` carrying one keystroke
  instruction, sent as our state `0 => 1` and retransmitted until the server's `ack_num`
  reached 1, ran `echo mosh-lab-ok` in the session. The reply came back as two host
  diffs, the second of which carries `OSC 0` for the title, `SGR` colour, `?25l/h`
  around the redraw and `?2004h` from the shell's own prompt — i.e. real material for
  the emulator, now kept as a test vector;
- **`echo_ack_num` really does arrive** (as an `echoack` extension), unused until there
  is a prediction engine.

Two behaviours of the server that only a running one teaches, both useful:

- **a session nobody talks to cleans itself up.** With no client it exits after sixty
  seconds; after a client that stops answering it detaches at forty seconds
  (`SERVER_ASSOCIATION_TIMEOUT`) and then exits. The orphan-process worry in the plan is
  smaller than it looked, and `MOSH_SERVER_NETWORK_TMOUT` covers the rest;
- **a client that attaches late does not get the screen it missed.** The server diffs
  from the state it assumes the client shares, which is the terminal as it was when the
  transport was created; output produced before the first datagram arrived is never
  retransmitted. It cannot bite in the app, because there the client launches the server
  and attaches immediately, but in the rig it means: attach promptly, or type something
  to force a repaint.

### What was left after phase 4, and when it was closed

Phase 4 closed the timing half of this list the same day: the transport state machine
holds a real session, idles through heartbeats and survives its local port being
replaced mid-session, which the server announces in its own log. What is left:

1. ~~the escape repertoire against real programs~~ — **closed 2026-10-21**. See the
   repaint gate in §8 phase 5 of the plan: `vim`, `less`, `htop`, a scroll region, `ECH`,
   256-colour and true-colour output and wide characters against the right margin, all
   fed into our emulator and into a `tmux` pane of the same size from the identical
   bytes, with no disagreement. The only thing the gate still cannot see is an attribute,
   which is asserted separately against known coordinates.

   Mouse mode is half-answered by the same recordings. `htop` turning reporting on and
   off appears in the host stream as `?1000`/`?1002`/`?1003`/`?1006` and the rest, which
   confirms §4's correction — the modes really are part of the synchronised state and
   reach us by themselves. Whether we then *send* the reports back is the terminal view's
   wiring, not the protocol's, and belongs to phase 6.

### Multi-fragment reassembly from the server, closed 2026-10-20

The second leftover is done. The integration test now resizes the window to 240×70 and
paints it with `base64` of `/dev/urandom`, which deflate cannot squeeze below about three
quarters of its size; the server answered with **one instruction in nine fragments** and
it went back together. Three things came out of it:

- **the assertion is weaker than the check.** The reassembled payload is a zlib stream,
  so its Adler-32 is verified when `MoshInstruction.decode` inflates it. Fragments put
  together in the wrong order, or one counted twice, do not inflate at all. Arriving at a
  frame we can parse *is* the proof that the assembly was right, which is why the test
  needs no knowledge of the random content it asked for;
- **the id-reuse assumption is now checked, not trusted.** Our assembly hands a completed
  instruction over once, which is only safe if the sender never reuses a fragment id for
  different bytes — mosh's random chaff is what makes that true. `MoshFragmentAssembly`
  counts the fragments that arrive with an id and number it already holds but different
  contents, and the test asserts that counter is zero against a real server;
- **it found the MTU defect above**, which reading had not. The server's fragment lengths
  did not match our budget, and the 28 bytes of difference were the IPv4 header we were
  not subtracting.
