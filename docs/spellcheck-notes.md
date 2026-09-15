# Spell-check (word) mode — IME behaviour notes

Reference for the terminal's **word / spell-check input mode** and the per-device
quirks of soft keyboards (mostly Gboard) we have observed while debugging it.

Commit messages explain *why a single change was made*; this file is the
*synthesised cross-device picture* — the part that is expensive to reconstruct
because it requires the physical phone plus a logcat session. Keep it up to date
whenever a new device shows a behaviour we had not seen before.

> Scope: this documents **observed IME behaviour and the invariants our code
> relies on**, not a line-by-line walkthrough of the code. The per-mechanism
> rationale lives in comments next to the code (it updates with the code); this
> file is the device matrix and the bug history.

Code under discussion: `TerminalView.kt`, inner class `TerminalInputConnection`.

---

## 1. Why this mode is hard

A normal SSH terminal wants a **raw byte stream**: each keystroke is a byte sent
over the channel (`0x7F` = backspace/DEL, `0x0D` = CR/Enter, UTF-8 for the rest).

A soft keyboard in text mode does **not** give us raw keystrokes. It speaks the
Android `InputConnection` protocol against an `Editable` buffer: it inserts
*composing* text, replaces ranges, commits words, deletes surrounding text, runs
autocorrect, adds "smart" spaces and punctuation. To make spell-check / next-word
suggestions work we run a `BaseInputConnection(view, fullEditor = true)` so the
IME has a real `Editable` to read back.

Our job is to translate that rich editing protocol into the **minimal byte delta**
to send to the remote shell, while keeping our mirror of the composing state in
sync with what the IME thinks the buffer contains. Every bug in this area is a
**desync** between three things:

1. what the remote terminal has actually received,
2. what the IME's `Editable` believes is on screen,
3. our local mirror (`composingText`, `composingDeletedByCommit`).

### Side effect: voice typing only works in word mode

Outside word mode `onCreateInputConnection` declares `inputType = TYPE_NULL` —
"I am not a text editor, send me raw key events". Gboard's mic key then has
nothing to commit into: the voice panel may open, but no text ever reaches the
terminal. Turning word mode on switches the field to `TYPE_CLASS_TEXT` and
dictation starts working. **Observed on device 2026-09-10**, user-confirmed.

This is inherent to raw mode, not a bug to fix: `TYPE_NULL` is exactly what gets
us per-keystroke bytes instead of an editing protocol. The answer to a user
report is "enable word mode (the spell-check icon in the extra-key bar) while
dictating".

Do not confuse it with a **red herring that looks identical from the user's
side**: Gboard's own *"No permission to enable: Voice typing"* error. That is
Gboard missing `RECORD_AUDIO` (typically denied once with "don't ask again"), or
the Android 12+ global microphone toggle being off — fixed in Settings › Apps ›
Gboard › Permissions. SSHBorg declares no audio permission and needs none: a host
app never does for keyboard dictation. Users will still report it as our bug.

---

## 2. The key state we track

- **`composingText`** — our mirror of the text currently held as an IME
  *composing* span (the underlined, not-yet-committed word). Used to compute the
  byte delta when the composing text changes or is committed.
- **`composingDeletedByCommit` (cdc)** — backspaces already emitted by a
  `commitText` that replaced composing text, so a *redundant* in-batch
  `deleteSurroundingText` can subtract them and not double-delete. Scoped to a
  batch: cleared when the outermost batch closes (`batchDepth == 0`), so it can
  never bleed into a later, independent user backspace.
- **`batchDepth`** — nesting counter for `beginBatchEdit`/`endBatchEdit`. The
  batch boundary is the signal that separates an atomic IME correction
  (commit + delete in one batch) from an independent user edit (its own batch).

---

## 3. Two keyboard models

The single most important distinction. Gboard behaves differently per device /
ROM, and the two families need opposite handling after a correction.

### Phantom-space (a.k.a. "smart space" / waiting)

The keyboard **does not commit the trailing space immediately** after a word. It
keeps the word composing and *waits* to decide whether the next thing is a space,
another word, or punctuation. When you then type `.`, it commits the word and the
`.` with no space, and adds the space later, before the next word.

- Punctuation arrives as plain `commitText(".")` then `commitText(" ")` with an
  **empty composing span** → each is a clean append, no deletion. Nothing to fix.

### Eager-space (no waiting)

The keyboard **commits the word and a real space immediately**. When you then type
`.`, it must retroactively *delete the already-committed space* and insert the
punctuation (smart-punctuation). It does this with `replaceText` over committed
text. This is the family that exposes our desync bugs, because the deletion
happens **out of band** from the composing channel.

---

## 4. Device matrix

Observed via `adb logcat` of `InputConnection` calls. "Per-key" = one call per
physical keystroke; "buffered" = the keyboard sometimes batches several letters
into one call (e.g. `commitText("cchio ")`).

| Device (model / OS)              | Keyboard            | Space model    | Normal typing                               | Punctuation / autocorrect                          | Uses `replaceText`? |
|----------------------------------|---------------------|----------------|---------------------------------------------|----------------------------------------------------|---------------------|
| Motorola moto g86 — Android 16   | Gboard (stock)      | **Eager**      | `commitText` per-key/buffered, **no composing span at all** | smart-punctuation + autocorrect via `replaceText` (e.g. replace the auto-space with `"."`) | **Yes** |
| Poco F2 Pro — **custom ROM**: LineageOS 23.2 (Android 16), not stock MIUI | Gboard | **Phantom** | `setComposingText` per-key, finalised with `commitText` | plain `commitText(".")` then `commitText(" ")`, empty composing | **No** |

Take-aways:

- **Eager + `replaceText`** is the dangerous combination. The phantom-space device
  never calls `replaceText`, so changes to `replaceText` cannot regress it.
- Do **not** assume the keyboard composes. The eager device commits every letter
  directly; `composingText` stays `""` for the whole word. Logic must work when
  there is no composing span.
- Letter delivery can be buffered, so never assume `commitText` length == 1.

> Add a row here whenever a new phone shows a different pattern. The cheap moment
> to record it is right after the logcat session, while the phone is in hand.

---

## 5. Invariants the code relies on

1. **All bytes for one logical IME op are sent in a single `onInput` call**
   (one `ByteArray`). The byte stream is `0x7F` for delete, UTF-8 for inserts,
   `0x0D` for Enter. Batching avoids a write race on the SSH channel.

2. **`composingText` mirrors only a *live composing span*.** It must be `""`
   whenever the IME's buffer has no composing region. In particular:
   - `commitText` finalises → reset to `""`.
   - `finishComposingText` → reset to `""`.
   - **`replaceText` commits its text (framework uses `composing = false`) and
     leaves no live composing span → reset to `""`.** Carrying the replacement
     forward as composing is the bug in §6 "Smart-punctuation eats the period".

3. **`cdc` is batch-scoped.** Set it only when a `commitText` replaced non-empty
   composing text with a correction; clear it when the outermost batch closes.
   A user backspace always arrives in its own separate batch, so it must never
   see a stale `cdc`.

4. **When composing restarts from empty, reconcile against the real `Editable`,
   not the mirror** (`readoptWordBytes`). The IME may recompose a word that is
   already before the cursor (undo-correction, backspacing into a word, or
   continuing a word right after a `replaceText`). Emit only the prefix-diff
   against the word actually before the cursor, guarded so a brand-new letter
   never erases an unrelated committed word.

5. **While a word is composing, swallow only `KEYCODE_DEL` in `sendKeyEvent`.**
   Other keys (Enter, arrows) end the word — finalise composing and let the key
   through, otherwise Enter gets swallowed while a composing span is alive.

---

## 6. Bug history (with the decisive evidence)

Each entry: symptom → root cause → the log line that proved it → fix.

### Deleted chars resurrected while erasing a word
- **Symptom:** backspacing through a word made pieces of *previous* words reappear.
- **Cause:** the undo-correction path rewrote the whole last word wholesale, so
  when terminal and `Editable` had drifted it resurrected already-deleted chars.
- **Fix:** prefix-diff against the `Editable`'s word; re-adopting an unchanged
  word becomes a zero-byte no-op (`readoptWordBytes`).

### Enter swallowed while composing
- **Symptom:** after the fix above, pressing Enter after a word did nothing.
- **Cause:** keeping the composing span alive (for auto-space/suggestions) means
  Gboard sends Enter *during* composing; `sendKeyEvent` was swallowing all keys.
- **Fix:** swallow only `KEYCODE_DEL` while composing; other keys finalise the
  word and pass through.

### Autocomplete duplication after partial delete ("rieriesce"/"rriesce")
- **Symptom:** delete 3 letters of a word, then autocomplete → duplicated text;
  the first user backspace afterwards went to nothing.
- **Cause:** a leftover `cdc` from a correction's `commitText` was subtracted from
  a *later, independent* user backspace. Batch-edit logging proved the commit and
  the user backspace were in **separate batches**.
- **Fix:** track `batchDepth`; clear `cdc` when the outermost batch closes.

### Extra letter on a 2-letter word ("ho" + backspace → "hoh")
- **Symptom:** type "...casa grande", backspace to "ho", one more backspace wrote
  an extra "h".
- **Evidence:** `setComposingText("h") was="" before="ho"` — a length-1
  recomposition of a 2-letter word skipped the old length-based re-adoption branch
  and fell through to append.
- **Fix:** replace the length threshold with a **prefix guard** — re-adopt only
  when one of `newText`/`wordBefore` is a prefix of the other.

### Smart-punctuation eats the period (eager-space devices)
- **Symptom (eager device only):** type a word, `.`, then space → the period
  disappears and is replaced by a space ("finocchio." → "finocchio "). Phantom
  device was fine.
- **Evidence (eager device):**
  ```
  replaceText(9,10,".")  before="finocchio "   → "finocchio."   (we set composingText=".")
  commitText(" ") was="." before="finocchio."  → 1 BS + " "      → ate the "."
  ```
  The `was="."` shows the stale mirror: `replaceText` had set `composingText="."`,
  and the next `commitText(" ")` backspaced it, deleting the real period. The
  IME's own `Editable` still had the period (`before="finocchio. "` afterwards),
  so terminal and IME desynced from there (visible later as a double space).
- **Root cause:** `replaceText` commits (no live composing span), but we were
  carrying the replacement forward as `composingText`. On eager-space Gboards that
  drive smart-punctuation through `replaceText`, that stale value was consumed by
  the following `commitText`.
- **Fix:** reset `composingText = ""` after `replaceText`. A `setComposingText`
  that legitimately continues the word is reconciled against the real `Editable`
  by `readoptWordBytes`. Verified fixed on the eager device, no regression on the
  phantom device (which never calls `replaceText`).
- **Note:** an older comment feared that *not* keeping the replacement as
  composing would make Gboard "repeat the word" via a following
  `commitText("word ")`. That sequence was never reproduced in either device's
  log — it would duplicate inside the IME's own `Editable` too, so the IME does
  not do it. The fear was a misattribution.

---

## 7. How to capture an IME trace

The disciplined method that solved every bug above — instrument, reproduce on the
physical device, read the exact call sequence, then fix. Do **not** guess.

1. Add `android.util.Log.d("TIC", ...)` at the top of the relevant overrides
   (`setComposingText`, `commitText`, `deleteSurroundingText`, `replaceText`,
   `finishComposingText`, `sendKeyEvent`, `begin/endBatchEdit`). Log the argument,
   the current `composingText` (`was=`), the real buffer
   (`getTextBeforeCursor(...)` → `before=`), `cdc` and `batchDepth`.
2. Build the debug APK and install it on the device under test.
3. Clear the buffer: `adb logcat -c`.
4. Capture: `adb logcat -s TIC:D > /tmp/tic.log` while reproducing on-device.
5. Read the sequence. The `was=` vs `before=` mismatch is usually the smoking gun
   (mirror vs reality). Batch boundaries separate atomic corrections from user
   edits.
6. Remove the logging before committing the fix.

A good standard reproduction string: `finocchio` + `.` + space, then continue with
a short sentence and backspace through it (exercises auto-space, smart-punctuation,
autocorrect accept, word re-composition and per-key deletes in one pass).
