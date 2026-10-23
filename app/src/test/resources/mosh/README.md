# Captured mosh vectors

Three artefacts of a real conversation with `mosh-server` 1.4.0 on 2026-10-20, kept so the
protocol tests check interoperability against mosh itself rather than against our own
assumptions. How they were taken is in `docs/mosh-wire-format.md` §7 and in the lab's own
README outside this repository.

| file | what it is |
|---|---|
| `server-datagram.bin` | an encrypted datagram as it arrived from the server |
| `server-plaintext.bin` | the same datagram's plaintext: two timestamps and one fragment |
| `host-diff.bin` | a real host diff, with the escape sequences of a redraw in it |

The key that opens the datagram is in `MoshVectors.kt`, with the reason it is not a secret.
