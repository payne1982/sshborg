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

## 7. Still to settle empirically

These are questions the test rig of phase 1 answers in minutes and reading answers
badly, so they are deliberately left here rather than guessed:

1. the exact initial state on both sides — state 0 is a blank terminal, but of which
   size, and what the server expects before the first resize arrives;
2. whether anything in `Display`'s repertoire upsets our parser in practice (run
   `vim`, `htop`, `less`, a 256-colour and a true-colour test, and a mouse-driven
   program);
3. how the server behaves at the first packet: what it accepts as an opening
   instruction from a client that has only just been told the key.
