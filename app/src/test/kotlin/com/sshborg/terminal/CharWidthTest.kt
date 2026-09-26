package com.sshborg.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The width table has to agree with the wcwidth() the remote host uses, or our column count
 * drifts from the host's and a redraw in tmux or a TUI shifts and corrupts. So what is checked
 * here is not "does it look right" but the exact decisions that were argued over: emoji are
 * two cells, ambiguous-width symbols are one, and combining marks are none.
 */
class CharWidthTest {

    @Test fun `plain text is one cell`() {
        listOf('A'.code, 'z'.code, '0'.code, ' '.code, 0x00E8 /* è */, 0x0416 /* Ж */)
            .forEach { assertEquals("U+%04X".format(it), 1, CharWidth.of(it)) }
    }

    @Test fun `east asian is two cells`() {
        listOf(
            0x4E00,  // 一 CJK
            0x3042,  // あ Hiragana
            0x30AB,  // カ Katakana
            0xAC00,  // 가 Hangul syllable
            0x3000,  // ideographic space
            0xFF21,  // Ａ fullwidth
            0x20000, // CJK Ext B
        ).forEach { assertEquals("U+%04X".format(it), 2, CharWidth.of(it)) }
    }

    @Test fun `emoji are two cells`() {
        listOf(
            0x1F600, // 😀
            0x1F680, // 🚀
            0x1F9E1, // 🧡
            0x1F1EE, // regional indicator, a flag half
            0x231A,  // ⌚ emoji presentation in the BMP
            0x2705,  // ✅
        ).forEach { assertEquals("U+%04X".format(it), 2, CharWidth.of(it)) }
    }

    /**
     * The set that must NOT be wide. These are the "ambiguous width" symbols: glibc and tmux
     * give them one cell, and a Nerd Font in a prompt is full of them. Widening any of these
     * is what shifts a Starship prompt by a column.
     */
    @Test fun `ambiguous symbols stay one cell`() {
        listOf(
            0x2192,  // → arrow
            0x2500,  // ─ box drawing
            0x2605,  // ★ star
            0x263A,  // ☺
            0x2611,  // ☑
            0x23F1,  // ⏱ stopwatch: no emoji presentation, unlike U+23F0
            0x2714,  // ✔ heavy check mark, unlike U+2705
            0xE0B0,  // private use: a Powerline separator
        ).forEach { assertEquals("U+%04X".format(it), 1, CharWidth.of(it)) }
    }

    @Test fun `marks and joiners take no cell`() {
        listOf(
            0x0301,  // combining acute
            0x0300,  // combining grave
            0x200D,  // zero-width joiner
            0x200B,  // zero-width space
            0xFE0F,  // variation selector 16, the emoji one
            0x0,     // NUL
        ).forEach { assertEquals("U+%04X".format(it), 0, CharWidth.of(it)) }
    }

    @Test fun `control characters count as one here`() {
        // The caller handles them; this only has to be defined and never 2.
        listOf(0x07, 0x09, 0x0A, 0x1B).forEach { assertEquals(1, CharWidth.of(it)) }
    }
}
