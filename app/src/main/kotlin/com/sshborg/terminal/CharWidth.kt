package com.sshborg.terminal

/**
 * Display width of a Unicode codepoint in terminal cells, à la POSIX `wcwidth()`:
 *  - 0 for zero-width (combining marks, ZWJ/ZWNJ, variation selectors),
 *  - 2 for East-Asian wide / fullwidth characters and emoji-presentation codepoints,
 *  - 1 for everything else (including "ambiguous width" symbols).
 *
 * This MUST agree with the wcwidth() the remote host uses to place the cursor, otherwise our
 * column count drifts from the host's and redraws (e.g. tmux, TUIs) shift and corrupt. So the
 * "wide" set is deliberately narrow: only East_Asian_Width W/F plus the exact Emoji_Presentation
 * codepoints — NOT whole symbol blocks. Ambiguous-width symbols (arrows, ⏵, ☑, ★, ☺, box
 * drawing, dingbats without emoji presentation) are width 1, matching glibc and tmux defaults.
 */
object CharWidth {

    fun of(code: Int): Int = when {
        code == 0 -> 0
        code < 0x20 -> 1              // control chars: caller handles, treat as 1 here
        isZeroWidth(code) -> 0
        isWide(code) -> 2
        else -> 1
    }

    private fun isZeroWidth(c: Int): Boolean =
        c in 0x0300..0x036F ||       // Combining Diacritical Marks
        c in 0x0483..0x0489 ||       // Cyrillic combining
        c in 0x0591..0x05BD ||       // Hebrew combining
        c in 0x0610..0x061A ||       // Arabic combining
        c in 0x064B..0x065F ||       // Arabic marks
        c == 0x0670 ||
        c in 0x06D6..0x06DC ||
        c in 0x06DF..0x06E4 ||
        c in 0x0E31..0x0E3A ||       // Thai combining
        c in 0x0E47..0x0E4E ||
        c == 0x200B ||               // zero-width space
        c in 0x200C..0x200F ||       // ZWNJ, ZWJ, LRM, RLM
        c in 0x202A..0x202E ||       // bidi controls
        c in 0x2060..0x2064 ||       // word joiner etc.
        c in 0x20D0..0x20F0 ||       // Combining Diacritical Marks for Symbols
        c in 0x1AB0..0x1AFF ||
        c in 0x1DC0..0x1DFF ||
        c in 0xFE00..0xFE0F ||       // variation selectors
        c in 0xFE20..0xFE2F ||       // Combining Half Marks
        c in 0xE0100..0xE01EF        // variation selectors supplement

    private fun isWide(c: Int): Boolean =
        // --- East Asian Wide / Fullwidth (UAX #11 W and F) ---
        c in 0x1100..0x115F ||       // Hangul Jamo
        c in 0x2329..0x232A ||       // angle brackets 〈 〉
        c in 0x2E80..0x303E ||       // CJK radicals, Kangxi, CJK symbols/punct (incl. 0x3000)
        c in 0x3041..0x33FF ||       // Hiragana, Katakana, CJK symbols, enclosed, compat
        c in 0x3400..0x4DBF ||       // CJK Ext A
        c in 0x4E00..0x9FFF ||       // CJK Unified Ideographs
        c in 0xA000..0xA4CF ||       // Yi
        c in 0xA960..0xA97F ||       // Hangul Jamo Extended-A
        c in 0xAC00..0xD7A3 ||       // Hangul Syllables
        c in 0xF900..0xFAFF ||       // CJK Compatibility Ideographs
        c in 0xFE10..0xFE19 ||       // vertical forms
        c in 0xFE30..0xFE6F ||       // CJK compat forms, small forms
        c in 0xFF00..0xFF60 ||       // Fullwidth Forms
        c in 0xFFE0..0xFFE6 ||       // Fullwidth signs
        // --- Emoji with default emoji presentation in the BMP (exact list) ---
        c in 0x231A..0x231B ||       // ⌚ ⌛
        c in 0x23E9..0x23EC || c == 0x23F0 || c == 0x23F3 ||
        c in 0x25FD..0x25FE ||
        c in 0x2614..0x2615 ||
        c in 0x2648..0x2653 ||       // zodiac
        c == 0x267F ||
        c == 0x2693 || c == 0x26A1 ||
        c in 0x26AA..0x26AB ||
        c in 0x26BD..0x26BE ||
        c in 0x26C4..0x26C5 ||
        c == 0x26CE || c == 0x26D4 || c == 0x26EA ||
        c in 0x26F2..0x26F3 || c == 0x26F5 || c == 0x26FA || c == 0x26FD ||
        c == 0x2705 ||
        c in 0x270A..0x270B ||
        c == 0x2728 || c == 0x274C || c == 0x274E ||
        c in 0x2753..0x2755 || c == 0x2757 ||
        c in 0x2795..0x2797 ||
        c == 0x27B0 || c == 0x27BF ||
        c in 0x2B1B..0x2B1C || c == 0x2B50 || c == 0x2B55 ||
        // --- Emoji / wide in the supplementary planes ---
        c == 0x1F004 || c == 0x1F0CF ||
        c == 0x1F18E ||
        c in 0x1F191..0x1F19A ||
        c in 0x1F200..0x1F2FF ||     // enclosed ideographic supplement
        c in 0x1F300..0x1F64F ||     // Misc symbols & pictographs, emoticons
        c in 0x1F680..0x1F6FF ||     // transport & map symbols
        c in 0x1F900..0x1F9FF ||     // supplemental symbols & pictographs
        c in 0x1FA70..0x1FAFF ||     // symbols & pictographs extended-A
        c in 0x1F1E6..0x1F1FF ||     // regional indicators (flags)
        c in 0x20000..0x3FFFD        // CJK Ext B..F and supplement
}
