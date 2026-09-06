# Customizable extra-key bar — plan

Goal: let the user pick the extra-key bar layout from a set of presets, and
build fully custom bars (keys, order, rows, size) from an editor that works
both with touch and with a D-pad (Android TV). Tracked in issue #12.

Work happens on the `V1_DEV_EXTRABAR` branch (from `V1_DEV`).

## What #12 asks for

From the reporter's write-up and follow-up:

- larger key font
- a two-row layout
- a different key order (arrows and page keys grouped "the natural way":
  `ESC / - HOME ↑ END PGUP` over `TAB CTRL ALT ← ↓ → PGDN`)
- more keys that terminals need often (`/`, `-`, …)
- ~~bar stays open while the keyboard is hidden~~ — **done** in vc25
  (`extra_keys_bar_pinned` + on-bar pin)
- "Configure the extra keys would be nice but choosing from different layouts
  is also ok."

The two-row arrangement above is the one two other terminal apps share; it is
the de-facto layout people expect. It must be one of the presets. (Do not name
the other apps anywhere in code, strings, notes or commits.)

## Current state

`ExtraKeyRow` in `ui/terminal/TerminalScreen.kt` is a hardcoded single
`Row`, horizontally scrollable, 12sp, in this order:

```
Ctrl Alt [word-mode] | ESC Tab ↑ ↓ ← → Home End PgUp PgDn Del [paste] [pin] | F1 … F12
```

- `Ctrl`/`Alt` are sticky one-shot modifiers applied in `sendInput` (Ctrl maps
  the next single byte to its control code, Alt prefixes ESC).
- Arrows go through `vm.cursorKeyBytes()` so they honour application-cursor
  mode; the rest are constant escape sequences.
- `↑ ↓ ← → PgUp PgDn` auto-repeat while held (`repeatOnHold`), timings from
  `ViewConfiguration`.
- Arrows use JetBrains Mono, other labels Roboto Condensed.
- Pinned state: settings default + cluster-scoped override in `SessionManager`.
- On a TV the bar hides while the soft keyboard is shown (2189af5). Keys are
  plain `clickable` boxes, so they are D-pad focusable already.
- Nothing about the bar is stored except the pin flag; settings backup is
  JSON format v5.

## 1. Data model

One small, serialisable description of a bar. Presets are Kotlin constants;
custom bars are the same type persisted as JSON.

```kotlin
sealed class ExtraKeyDef {
    /** Fixed terminal key: ESC, TAB, ENTER, BKSP, DEL, INS, HOME, END, PGUP,
     *  PGDN, UP/DOWN/LEFT/RIGHT (via cursorKeyBytes), F1…F12. */
    data class Special(val key: SpecialKey, val label: String? = null) : ExtraKeyDef()
    /** Sticky one-shot modifier: CTRL, ALT. */
    data class Modifier(val mod: ModKey) : ExtraKeyDef()
    /** Literal text sent as-is. `\n \t \e \\` escapes allowed. Label optional
     *  (defaults to the text, ellipsised). */
    data class Text(val text: String, val label: String? = null) : ExtraKeyDef()
    /** Bar/app actions: PASTE, PIN, WORD_MODE, SWITCH_BAR, TOGGLE_KEYBOARD. */
    data class Action(val action: BarAction) : ExtraKeyDef()
}

data class ExtraBar(
    val id: String,              // "preset:<name>" or "custom:<uuid>"
    val name: String,            // presets: resolved via string resource
    val rows: List<List<ExtraKeyDef>>,   // 1..3 rows
    val fontScale: FontScale = MEDIUM,   // SMALL 12sp / MEDIUM 14sp / LARGE 16sp
    val fitToWidth: Boolean = false,     // false = natural width + scroll (today)
                                         // true  = keys share the row width (weight)
)
```

- Auto-repeat is a property of the key kind (arrows, PgUp/PgDn, BKSP, DEL),
  not something the user sets.
- `SWITCH_BAR` opens the quick-switch popup (see §3); `TOGGLE_KEYBOARD`
  shows/hides the soft keyboard — useful when the bar is pinned and the user
  is scrolling through output (the reporter's scenario).
- Custom text keys cover `/`, `-`, `|`, `~`, `sudo `, `ls -la\n`, … Multi-char
  text with `\n` makes them command macros for free.

### Presets (code, localised names) — as shipped in Phase 1

| id | rows | fit | font | notes |
|----|------|-----|------|-------|
| `standard` | today's row + `[⇄]` after F12 | no | S | unchanged for existing users |
| `natural` | `ESC Tab Ctrl Alt [spell] / - \| ~ ← ↑ ↓ → Home End PgUp PgDn Del [paste] [pin] F1…F12 [⇄]` | no | M | one row ordered by frequency of use; arrows in physical-keyboard order |
| `natural_2` | `ESC / - Home ↑ End PgUp [paste] [pin]` / `Tab Ctrl Alt ← ↓ → PgDn [spell] [⇄]` | yes | M | the two-row arrangement of #12, 9 aligned columns |
| `natural_3` | as `natural_2` + third row `F1…F12` scrolling | mixed | M | |
| `minimal` | `ESC Tab Ctrl ↑ ↓ ← → [⇄]` | yes | M | phones in portrait |

`fit` is per row (`ExtraBarRow.fit`); stretched keys use 2dp horizontal padding
so nine columns fit a 360dp phone.

## 2. Storage

**Proposal A (recommended): DataStore JSON.** Two keys in `AppPreferences`:

- `extra_bar_selected` — string id, default `preset:standard`
- `extra_bar_custom` — JSON array of custom `ExtraBar`s

org.json is already used for settings backup, so no new dependency. Custom bars
are a handful of small objects with no query needs, so a Room table (proposal
B) adds a migration (v12→13) and a DAO for nothing. Backup: bump the settings
JSON to format v6 and add `extra_bars` (custom list + selected id); import
falls back to `preset:standard` when the selected id no longer exists.

Presets are never stored, so they can change between versions without a
migration. Custom bars keep a `formatVersion` field for future key kinds;
unknown key kinds are dropped on load, never crash.

## 3. Switching bars quickly — done in Phase 1

Custom bars first, then presets, active one highlighted. Selection is
**global and permanent** from both entry points (unlike the pin, which has a
per-cluster override): people switch layout to stay there.

1. **On the bar** — the `Action(SWITCH_BAR)` key (`⇄`). It does *not* open a
   popup: a `DropdownMenu` is a separate window that steals focus, closes the
   soft keyboard and leaves the menu floating. Instead the bar swaps its keys
   in place for a scrolling row of bar-name keys (plus ✕), same height, so
   the keyboard and the terminal don't move; Back closes it; D-pad works
   because the names are ordinary keys.
2. **Settings → Terminal → "Extra key bar layout"** — one row "In use: <bar>"
   with a *Customise* button (a button, not a clickable row, so the D-pad
   reaches it) that opens the bar list. The dropdown was dropped as redundant
   with the list's radio buttons.

A per-host override (`HostEntity.extraBarId`) stays a possible follow-up.

## 4. Editor — one interaction model for touch and D-pad

### Bar list screen (`Screen.ExtraBars`, route `extrabars`)

- Section "Your bars" (custom) then "Presets". Row: name, small preview strip
  of row 1, radio/checkmark for the active one. Tap = select.
- Row menu (`onMenuKey` / overflow, same pattern as hosts): custom → Edit,
  Duplicate, Rename, Delete; preset → Duplicate (creates `custom:<uuid>` named
  "<preset> copy") — this is the intended way to start customising.
- FAB "New bar" → empty custom bar with one row.

### Bar editor screen (`Screen.ExtraBarEditor`, route `extrabar/{id}`)

WYSIWYG: the editor *is* the bar. The top of the screen renders the real
`ExtraKeyBar` composable in `editing = true` mode: keys don't send anything,
tapping (or OK on a D-pad) **selects** the key. Under the preview a toolbar
acts on the selection:

```
[ ◀ ] [ ▶ ]  [ ⇅ row ]  [ ✎ edit ]  [ + insert ]  [ 🗑 remove ]
```

- ◀ ▶ move within the row; ⇅ moves to the previous/next row (menu when there
  are more than two rows); + inserts after the selection (or appends when
  nothing is selected).
- Row controls: "Add row" / "Remove row" (max 3); per row a *Scroll | Fill*
  segmented choice (a switch plus a sentence explaining it was unclear).
- Bar options: name (`TvTapField` on touchless), font size (Small/Medium/Large
  via `SettingSelect`).
- **Why this instead of drag & drop:** it's one code path that works
  identically with a finger and with a remote (keys are focusable, the toolbar
  is a row of buttons), needs no reordering library, and the preview shows the
  real result at real size. Drag & drop on touch is an additive polish for
  later (§7).

### Key catalogue dialog ("+ insert" / "✎ edit")

Grouped list, scrollable, D-pad safe (same dialog style as `TvSelectField`):

- Navigation: ↑ ↓ ← → Home End PgUp PgDn
- Editing: ESC Tab Enter Backspace Del Ins
- Function keys: F1 … F12
- Modifiers: Ctrl Alt
- Actions: Paste, Pin, Word mode, Switch bar, Keyboard
- Custom text: text + optional label (`TvTapField` on touchless). Preview of
  the resulting key in the dialog.

Editing an existing key opens the same dialog pre-filled.

### Save semantics

Edits are buffered in the editor's state and written on Save (back with
unsaved changes asks). A custom bar that is currently active re-renders in the
terminal on save.

## 5. Renderer changes

`ExtraKeyRow` becomes `ExtraKeyBar(bar: ExtraBar, state, callbacks)`:

- `Column` of rows; each row `Row` with `horizontalScroll` when not fit,
  `weight(1f)` per key when fit.
- `ExtraKey` gets `fontSize` from `bar.fontScale`; label font stays
  JetBrains Mono for single arrows, Roboto Condensed otherwise.
- One dispatcher `ExtraKeyDef → onPress`: Special → bytes (arrows through
  `cursorKeys`), Modifier → toggle, Text → unescape + send (modifiers apply
  to a single byte as today), Action → callback.
- Unchanged: pinned logic, TV hides bar while IME visible, repeat-on-hold,
  modifier highlight.
- `editing` mode (see §4): presses select instead of sending; selected key
  drawn with the `primaryContainer` highlight.

Terminal height: a two-row bar takes ~40dp more; that's the user's choice and
`TerminalView` already resizes on bar changes (pin toggle does it today).

## 6. Work breakdown

**Phase 1 — presets — DONE (`54122bb`), verified on a phone**
1. Model + presets + JSON (de)serialiser + unit tests for round-trip and
   escapes.
2. `AppPreferences`: `extra_bar_selected`, `extra_bar_custom`, backup v6.
3. Data-driven `ExtraKeyBar` renderer with fit/scroll rows, font scale,
   `SWITCH_BAR` (inline chooser) + `KEYBOARD` show/hide actions.
4. Settings row (select) + on-bar switch menu.
5. Strings ×10 locales, phone pass done; TV emulator pass (hp450) still to do.

**Phase 2 — custom bars — DONE (`c7f77f4`), verified on a phone**
6. Bar list screen (custom first, duplicate preset → custom).
7. Editor screen with WYSIWYG preview + selection toolbar, row controls, bar
   options.
8. Key catalogue dialog incl. custom text keys.
9. Backup import/export of custom bars — done in Phase 1 (format v6).
   Site docs chapter + release notes: still to do.
10. D-pad pass on every new screen (hp450 emulator): still to do; touch pass done.

**Phase 3 — optional polish**
- Drag & drop reordering on touch (keep the toolbar as the D-pad path).
- Per-host bar override.
- Share/import a single bar as text (paste a JSON line).

## 7. Decisions to take before coding

1. Storage: DataStore JSON (recommended) vs Room table.
2. Editor: WYSIWYG tap-to-select + toolbar for both inputs (recommended) vs
   list editor vs drag & drop first.
3. Preset set and whether `two_rows` carries `[switch]`/`[pin]`/`[paste]`.
4. Custom text keys with `\n \t \e` escapes (recommended yes; documented in
   the dialog's helper text).
5. Global selection only (recommended) vs per-host from the start.
6. `fitToWidth` per row (recommended) vs per bar.
