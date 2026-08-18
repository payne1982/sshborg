# In-app app lock (PIN or passphrase) — design

Part of the Android TV work (see `tv-support-plan.md`, section 1). Tracked in #3.

This is the blocking prerequisite for the Play TV opt-in: a lock that does not
depend on biometric or device-credential hardware, so a TV is protected too.

## Scope / threat model

Same class of protection as the existing biometric lock: it **gates the UI** on
launch/resume so someone with physical access (e.g. a TV remote) cannot browse
hosts or use saved credentials. It is **not** data-at-rest encryption — saved
passwords keep their own Keystore encryption (`encryptedPassword`); the local DB
is not re-encrypted by the secret. This keeps parity with the biometric mode and
avoids a false promise.

## Availability

The PIN/passphrase lock is a **general, device-agnostic mode** (`LOCK_SECRET`),
offered on every device type, not just TVs — on a phone it is simply an extra
choice next to Biometric and Device credential, useful for anyone who prefers an
app-specific PIN or has no enrolled biometric. The TV-specific behaviour is only
additive: on a TV the unusable Biometric/Device options are hidden and a
first-run nudge is shown. Phones keep all options, PIN/passphrase included.

## Secret kinds

The user picks one when enabling the lock:

- **Numeric PIN** — 4–8 digits. On-screen numeric keypad, ideal for the D-pad.
- **Alphanumeric passphrase** — letters/digits/symbols, min ~4 chars. Uses the
  system keyboard (awkward on a TV, but offered by choice).

A stored flag `lock_secret_kind` (`pin` | `passphrase`) tells the unlock screen
which input to render. Both kinds are hashed identically.

## Storage & verification

The secret is never stored. On set:

- Random 16-byte salt + **PBKDF2WithHmacSHA256**, ≥200k iterations, 256-bit.
- Persist base64(salt) + base64(hash) + iteration count + `lock_secret_kind` in
  DataStore under new keys.

On unlock: recompute and compare in constant time. A low-entropy PIN cannot be
made brute-force-proof if the hash leaks, so the KDF is paired with attempt
throttling for the realistic threat (guessing at the device).

## Attempt throttling

Track failed attempts + last-attempt timestamp in DataStore. After ~5 failures,
apply an escalating back-off (e.g. 30s, then longer). No data wipe.

## Lock screen (Compose)

A full-screen `AppLockScreen`, rendered above the app content by `MainActivity`
when `locked && mode == LOCK_SECRET`. It adapts to `lock_secret_kind`:

- **PIN**: a row of dots reflecting entered length + a keypad grid (1–9, 0,
  backspace, ✓). Every key is a focusable composable with a clear focus ring;
  default focus on a key so the D-pad has an anchor.
- **Passphrase**: a masked text field with a reveal toggle and a submit action.

Both variants also accept hardware input (remote/keyboard number keys, DEL to
erase, ENTER to submit) and work by touch on phones. Success records
`lastAuthTime` and clears `locked`; failure resets the input and applies
throttling.

## MainActivity integration

Keep the existing plain-`View` privacy overlay for the "cover on
background/recents" case (unchanged). Add a `locked` state that the root of
`setContent` observes:

- `onStart`: decide whether auth is needed (reuse `lockMode` + `lockTimeout` +
  `lastAuthTime`). If needed and `mode == LOCK_SECRET` → `locked = true` (show
  `AppLockScreen`). If `mode` is biometric/device → keep the current
  `BiometricPrompt` path.

This lets phone (biometric) and TV (PIN/passphrase) coexist without removing the
existing path.

## Settings — set / change

Extend the lock-mode options: None / Biometric / Device credential / **PIN or
passphrase**. Choosing the last first shows a **disclaimer** — a forgotten
secret cannot be recovered; the only way back in is clearing app data or
reinstalling, which loses saved servers and keys without a backup — then opens a
set-up flow: pick kind (PIN/passphrase), enter, then confirm (mismatch → retry).
"Change secret" **requires entering the current one first** (checked via a
non-throttling `AppLockManager.checkSecret`, so it never trips the unlock
screen's lockout), preventing an accidental replacement the user won't remember.
Switching away clears the stored hash. On a TV, hide the Biometric and Device
options (they don't work there) so the in-app secret is the obvious pick.

## First-run nudge on TV

On first launch, if `isTelevision` and no lock is configured and the nudge has
not been shown before (a `tv_lock_nudge_shown` flag), show a one-time dialog
suggesting to set a PIN/passphrase, with "Set up" (→ set-up flow) and "Not now".
Only on TV; phones are unaffected.

## TV detection

Helper `isTelevision(context)` using `UiModeManager.currentModeType ==
UI_MODE_TYPE_TELEVISION` (fallback `FEATURE_LEANBACK`). Used to (a) filter the
lock options in Settings and (b) gate the first-run nudge.

## New strings (×10 locales)

Lock screen title/subtitle; "Enter PIN" / "Enter passphrase"; "Set PIN" /
"Set passphrase"; "Confirm"; "They don't match"; "Wrong PIN/passphrase";
"Too many attempts, try again in %d s"; "Change"; the mode label; the first-run
nudge title/body/buttons; keypad key content descriptions.

## Files to touch

- New `ui/lock/AppLockScreen.kt` (+ `PinKeypad`).
- New `data/AppLockManager.kt` — KDF hash/verify + throttling.
- `AppPreferences.kt` — `LOCK_SECRET` constant + keys: hash, salt, iterations,
  `lock_secret_kind`, failed-attempts, last-attempt, `tv_lock_nudge_shown`.
- `MainActivity.kt` — `locked` state, render `AppLockScreen`, branch by mode.
- `ui/settings/SettingsScreen.kt` + `SettingsViewModel.kt` — new option, set/
  change flow, TV option filtering.
- `TvUtils.kt` — `isTelevision`.
- strings ×10.

## Refinements from hands-on testing

- **Reveal toggle** on every secret field (set/change dialog and confirm-current)
  and on the PIN unlock screen — because a forgotten secret can't be recovered,
  the user must be able to check what they typed.
- **PIN dots grow** with the entered digits rather than showing a fixed row that
  exposes the maximum; the max is 12.
- **Change or disable requires the current secret**, mirroring the OS screen
  lock: while active, re-selecting the same mode is a no-op, and switching to
  None/Biometric/Device prompts for the current secret first (ConfirmSecretDialog).
- The unlock button uses an **open padlock**; the header keeps a closed one as
  the "locked" indicator.

## Implementation order (small, independently testable steps)

1. `AppLockManager` + `AppPreferences` keys — hash/verify/throttle, no UI. Unit-
   check hashing round-trips.
2. `AppLockScreen` (PIN keypad + passphrase field) as a standalone composable.
3. `MainActivity` integration — show/clear the lock, branch by mode.
4. Settings — add the option and the set/change flow; TV option filtering.
5. `isTelevision` + first-run TV nudge.
6. Strings ×10 and a full pass on a TV emulator with arrow-key navigation.
