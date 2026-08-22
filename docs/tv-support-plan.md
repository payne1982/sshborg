# Android TV support — action plan

Goal: make SSHBorg installable and listable from the Android TV Play Store by
opting into the Android TV form factor. Tracked in issue #3.

Work happens on the `V1_DEV_TV` branch and is merged back in small steps.

## Current state (already done)

- **Manifest is TV-ready**: `android.hardware.touchscreen`, `faketouch` and
  `android.software.leanback` are all `required="false"`; a `LEANBACK_LAUNCHER`
  launcher entry and an `android:banner` (`tv_banner.png`) are declared.
- **App-lock device-credential fallback** exists (`LOCK_DEVICE` mode accepts the
  device PIN/pattern/password), but it only helps on TVs that actually have a
  system screen lock set — many do not.
- **SFTP: show/hide dotfiles per host** — done in versionCode 27 (part of #3
  feedback).

## 1. Blocking — required before the Play opt-in

The store's TV review, and basic safety, gate on these. They must land first.

**Status: implemented on `V1_DEV_TV`, verified on a phone.** AppLockManager
(PBKDF2 + throttling), the adaptive AppLockScreen, MainActivity integration, the
Settings option with set/change flow and TV filtering are all in and working.
Refinements from hands-on testing: a show/hide reveal toggle on entry, PIN dots
that grow instead of exposing the max (now 12), a button-styled "Change" action,
a no-recovery disclaimer before enabling, the current secret required to change
**or** disable the lock, and an open-padlock unlock button. See
`tv-pin-lock-design.md`.

The first-run TV nudge was folded into the existing security reminder (one
dialog, one dismissed flag, "remind me later" / "don't show again" on every
device; only the wording differs on a TV) rather than kept as a second stacked
dialog. Also fixed during TV testing on the emulator: the privacy cover no
longer flashes on every foreground when no lock is configured (it's only drawn
when a lock is actually active).

**Remaining here:** a D-pad pass on a TV emulator (the lock UI was built
D-pad-friendly but not yet exercised on a TV).

### 1a. In-app PIN / passphrase lock (app-lock phase 2)

On a TV none of the current lock modes is usable *and* secure:

- `LOCK_BIOMETRIC` — no biometric hardware; the helper falls back to a device
  credential, and if the TV has no system lock the prompt errors and the app
  closes.
- `LOCK_DEVICE` — works only when a system screen lock is set (often absent).
- `LOCK_NONE` — the only mode that reliably "works", i.e. no protection at all:
  anyone with the remote reaches saved keys and passwords.

Introduce a lock that does not depend on device biometric/credential hardware:

- New lock mode (e.g. `LOCK_PIN`) with the secret stored **hashed** (salt +
  PBKDF2/argon2, never plaintext), ideally behind the Android Keystore.
- A **Compose lock screen** for PIN/passphrase entry, fully usable with the
  D-pad, shown by `MainActivity` instead of the system `BiometricPrompt` when
  the mode is PIN.
- Settings UI to **set / change** the PIN, with confirmation.
- Enabling the lock prompts to choose the PIN (first-run flow).
- Consider attempt rate-limiting / back-off.

### 1b. TV detection → prefer the in-app PIN

Add TV detection (`UiModeManager.currentModeType == UI_MODE_TYPE_TELEVISION`, or
`FEATURE_LEANBACK`) and, on TV, steer the first-run/lock flow to the in-app PIN
instead of the system biometric prompt.

## 2. D-pad quality (recommended for a clean review, not strictly blocking)

- **Terminal**: `KEYCODE_BACK` is not consumed, so the remote's Back leaves the
  terminal; D-pad keys are consumed as shell cursor keys, so navigating *inside*
  the terminal needs an external keyboard. Acceptable; at most add a visible
  hint. Low priority.
- **Context menus** — **done**. A focusable overflow ("⋮") button now surfaces
  each row's menu. Host rows already had one; added it to SFTP rows and Hosts
  group headers, gated on `isTouchless(context)` (no touchscreen, or a TV) so
  touch devices keep the long-press and are visually unchanged.
- **Text entry**: on a TV, focusing a text field opens the leanback IME, which
  is a separate window that captures the D-pad — custom in-app focus interception
  can't override it (an experimental `dpadFieldNav` modifier was tried and
  reverted). We keep the standard flow instead: `imeAction Next` chains the form
  fields (advancing in traversal order) so the on-screen or a Bluetooth keyboard
  fills the form. SSHBorg is text-heavy, so **a Bluetooth keyboard is the
  realistic input on a TV**; the D-pad handles the non-text controls (lists,
  menus, dropdowns, switches) via Compose's default focus.
- **Focus pass**: verify focus order and a visible focus indicator for the
  non-text controls across all six screens on a TV emulator. ← in progress.

## 3. Minor listing improvements (from #3, non-blocking)

- **SFTP**: surface *where* a downloaded file was saved — **done** (the download
  result shows "Saved: <path>").
- **SFTP**: optional setting to not sort folders before files — **done**. A
  "Folders before files" switch in a new Settings **SFTP** section, default on;
  when off the browser sorts by name only at display time (`listDir` still
  returns dirs-first, the screen re-sorts).
- **SFTP**: hide dotfiles — **done** (versionCode 27).

## 4. Play Console process (after section 1)

- **TV screenshots** (16:9) and a **store TV banner** (1280×720; distinct from
  the launcher banner already in the manifest).
- **Opt into the Android TV form factor** and complete the TV quality
  declaration → submit for Google's manual TV review.

## 5. Testing without physical hardware

- Use an **Android TV emulator** (AVD with an Android TV system image) and drive
  it with the keyboard arrow keys (they emulate the D-pad). This is the only
  practical way to validate section 2 here.

## Suggested order

1. ~~1a — in-app PIN lock (the bulk of the work).~~ **Done.**
2. ~~1b — TV detection and first-run steering.~~ **Done.**
3. ~~2 — focusable overflow ("⋮") menus on touchless devices.~~ **Done.**
4. ~~3 — optional folder/file sort setting.~~ **Done.**
5. 2 — D-pad focus pass on the emulator (order/focus indicator). ← next, needs an emulator/device.
6. 4 — Play Console assets + opt-in + review.
