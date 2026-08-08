package com.sshborg.terminal

/**
 * VT100/VT220/xterm terminal emulator.
 * Feed incoming bytes via [process]; the state is reflected in [buffer].
 */
class TerminalEmulator(columns: Int, rows: Int, maxScrollback: Int = 2000) {

    val buffer = TerminalBuffer(columns, rows, maxScrollback)

    // Parser state machine
    private var state = State.NORMAL
    private val params = mutableListOf<Int>()
    private val oscBuf = java.io.ByteArrayOutputStream()
    private var csiIntermediate = ""
    private var privMode = false   // '?' was seen after CSI

    // Alternate screen
    private var onAltScreen = false
    private var savedMainScreen: Array<Array<TerminalBuffer.Cell>>? = null
    private var savedMainWrapFlags: BooleanArray? = null

    // Application cursor key mode (DECCKM, set by ESC[?1h / cleared by ESC[?1l)
    var applicationCursorKeys = false

    // Pending title / callback
    var onTitleChanged: ((String) -> Unit)? = null
    var onBell: (() -> Unit)? = null
    // Called when the terminal must send a response back to the remote (e.g. CPR, DA replies)
    var onSendResponse: ((ByteArray) -> Unit)? = null

    private enum class State { NORMAL, ESC, CSI, OSC, SS3, CHARSET }

    // UTF-8 multi-byte decoder state
    private var utf8Remaining  = 0
    private var utf8Codepoint  = 0

    // --- Public API ---

    fun process(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        val end = offset + length
        var i = offset
        while (i < end) {
            val b = data[i].toInt() and 0xFF
            processByte(b)
            i++
        }
    }

    fun process(text: String) = process(text.toByteArray(Charsets.UTF_8))

    fun resize(cols: Int, rows: Int) {
        buffer.resize(cols, rows)
    }

    // --- Private processing ---

    private fun processByte(b: Int) {
        when (state) {
            State.NORMAL  -> processNormal(b)
            State.ESC     -> processEsc(b)
            State.CSI     -> processCsi(b)
            State.OSC     -> processOsc(b)
            State.SS3     -> processSs3(b)
            State.CHARSET -> state = State.NORMAL // consume designator byte (B, 0, A, …) and ignore
        }
    }

    private fun processNormal(b: Int) {
        // UTF-8 continuation byte (10xxxxxx) — accumulate into current codepoint
        if (b in 0x80..0xBF) {
            if (utf8Remaining > 0) {
                utf8Codepoint = (utf8Codepoint shl 6) or (b and 0x3F)
                utf8Remaining--
                if (utf8Remaining == 0) printChar(utf8Codepoint)
            }
            return
        }
        // Any non-continuation byte resets a partial sequence (handles invalid UTF-8 gracefully)
        utf8Remaining = 0
        utf8Codepoint = 0

        when {
            b <= 0x06 -> {}                          // NUL..ACK ignored
            b == 0x07 -> onBell?.invoke()            // BEL
            b == 0x08 -> { if (buffer.cursorCol > 0) buffer.cursorCol-- } // BS
            b == 0x09 -> {                           // HT (tab)
                val next = ((buffer.cursorCol / 8) + 1) * 8
                buffer.cursorCol = next.coerceAtMost(buffer.columns - 1)
            }
            b in 0x0A..0x0C -> lineFeed()            // LF, VT, FF
            b == 0x0D -> buffer.cursorCol = 0        // CR
            b == 0x0E || b == 0x0F -> {}             // SO/SI charset (ignored)
            b == 0x1B -> state = State.ESC           // ESC
            b == 0x9B -> {                           // 8-bit CSI
                state = State.CSI; params.clear(); csiIntermediate = ""; privMode = false
            }
            b in 0x20..0x7F -> printChar(b)          // printable ASCII
            b in 0xC0..0xDF -> { utf8Remaining = 1; utf8Codepoint = b and 0x1F } // 2-byte start
            b in 0xE0..0xEF -> { utf8Remaining = 2; utf8Codepoint = b and 0x0F } // 3-byte start
            b in 0xF0..0xF7 -> { utf8Remaining = 3; utf8Codepoint = b and 0x07 } // 4-byte start
        }
    }

    private fun processEsc(b: Int) {
        utf8Remaining = 0; utf8Codepoint = 0
        state = State.NORMAL
        when (b) {
            '['.code -> { state = State.CSI; params.clear(); csiIntermediate = ""; privMode = false }
            ']'.code -> { state = State.OSC; oscBuf.reset() }
            'O'.code -> { state = State.SS3 }
            '7'.code -> buffer.saveCursor()
            '8'.code -> buffer.restoreCursor()
            'M'.code -> reverseLineFeed()
            'c'.code -> resetTerminal()
            'D'.code -> lineFeed()
            'E'.code -> { buffer.cursorCol = 0; lineFeed() }
            'H'.code -> {} // Tab set (ignored)
            // Character set designation: ESC ( X or ESC ) X — consume the designator byte
            '('.code, ')'.code, '*'.code, '+'.code -> state = State.CHARSET
            else -> {} // unknown escape
        }
    }

    private fun processCsi(b: Int) {
        when {
            b in 0x3C..0x3F && params.isEmpty() && csiIntermediate.isEmpty() -> privMode = true // ?, >, <, = private markers
            b in 0x30..0x39 -> { // digit
                if (params.isEmpty()) params.add(0)
                val last = params.last()
                params[params.size - 1] = last * 10 + (b - 0x30)
            }
            b == ';'.code || b == ':'.code -> params.add(0) // parameter / sub-parameter separator
            b in 0x20..0x2F -> csiIntermediate += b.toChar() // intermediate bytes
            b in 0x40..0x7E -> { // final byte
                executeCsi(b.toChar())
                state = State.NORMAL
                privMode = false
            }
            else -> state = State.NORMAL
        }
    }

    private fun processOsc(b: Int) {
        when (b) {
            // BEL (0x07) terminates OSC. 0x9C (8-bit ST) is intentionally NOT treated as a
            // terminator because in UTF-8 mode 0x9C is a valid continuation byte (e.g. U+2726
            // ✦ encodes as E2 9C A6), so treating it as ST would corrupt multi-byte titles.
            0x07 -> { handleOsc(oscBuf.toByteArray().toString(Charsets.UTF_8)); oscBuf.reset(); state = State.NORMAL }
            0x1B -> { /* ESC \ terminator – wait for next byte */ }
            '\\'.code -> { handleOsc(oscBuf.toByteArray().toString(Charsets.UTF_8)); oscBuf.reset(); state = State.NORMAL }
            else -> oscBuf.write(b)
        }
    }

    private fun processSs3(b: Int) {
        state = State.NORMAL
        // Application keypad/cursor keys – ignore for now
    }

    // --- CSI dispatch ---

    private fun param(index: Int, default: Int = 0): Int {
        val v = params.getOrElse(index) { default }
        return if (v == 0) default else v
    }

    private fun executeCsi(final: Char) {
        if (privMode) { executePrivateCsi(final); return }
        when (final) {
            // Cursor movement
            'A' -> moveCursor(-param(0, 1), 0)
            'B' -> moveCursor(param(0, 1), 0)
            'C' -> moveCursorCol(param(0, 1))
            'D' -> moveCursorCol(-param(0, 1))
            'E' -> { buffer.cursorRow = (buffer.cursorRow + param(0, 1)).coerceAtMost(buffer.rows - 1); buffer.cursorCol = 0 }
            'F' -> { buffer.cursorRow = (buffer.cursorRow - param(0, 1)).coerceAtLeast(0); buffer.cursorCol = 0 }
            'G' -> buffer.cursorCol = (param(0, 1) - 1).coerceIn(0, buffer.columns - 1)
            'H', 'f' -> {
                buffer.cursorRow = (param(0, 1) - 1).coerceIn(0, buffer.rows - 1)
                buffer.cursorCol = (param(1, 1) - 1).coerceIn(0, buffer.columns - 1)
            }
            // Erase
            'J' -> buffer.eraseInDisplay(param(0, 0))
            'K' -> buffer.eraseInLine(param(0, 0))
            'X' -> buffer.eraseChars(param(0, 1))
            // Insert/delete
            'L' -> buffer.insertLines(buffer.cursorRow, param(0, 1))
            'M' -> buffer.deleteLines(buffer.cursorRow, param(0, 1))
            'P' -> buffer.deleteChars(param(0, 1))
            '@' -> buffer.insertChars(param(0, 1))
            // Scroll
            'S' -> buffer.scrollUp(param(0, 1))
            'T' -> buffer.scrollDown(param(0, 1))
            // SGR (colors/attributes)
            'm' -> handleSgr()
            // Device Status Report: ESC[5n → OK, ESC[6n → cursor position
            'n' -> when (param(0, 0)) {
                5 -> onSendResponse?.invoke("\u001b[0n".toByteArray())
                6 -> onSendResponse?.invoke(
                    "\u001b[${buffer.cursorRow + 1};${buffer.cursorCol + 1}R".toByteArray()
                )
            }
            // Primary Device Attributes: ESC[c → report as VT100 with AVO
            'c' -> if (param(0, 0) == 0) onSendResponse?.invoke("\u001b[?1;2c".toByteArray())
            // Scroll region
            'r' -> {
                buffer.scrollTop = (param(0, 1) - 1).coerceIn(0, buffer.rows - 1)
                buffer.scrollBottom = (param(1, buffer.rows) - 1).coerceIn(buffer.scrollTop, buffer.rows - 1)
                buffer.cursorRow = 0; buffer.cursorCol = 0
            }
            // Save/restore cursor (ANSI.SYS)
            's' -> buffer.saveCursor()
            'u' -> buffer.restoreCursor()
            // Cursor visibility
            'd' -> buffer.cursorRow = (param(0, 1) - 1).coerceIn(0, buffer.rows - 1)
            // Repeat
            'b' -> {
                val cp = lastPrintedCodePoint
                if (cp >= 0) repeat(param(0, 1)) { printChar(cp) }
            }
            else -> {} // unhandled
        }
    }

    private fun executePrivateCsi(final: Char) {
        val p = param(0, 0)
        when (final) {
            'h' -> when (p) {
                1    -> applicationCursorKeys = true
                25   -> buffer.cursorVisible = true
                47, 1047 -> switchToAltScreen()
                1049 -> { buffer.saveCursor(); switchToAltScreen() }
                else -> {}
            }
            'l' -> when (p) {
                1    -> applicationCursorKeys = false
                25   -> buffer.cursorVisible = false
                47, 1047 -> switchToMainScreen()
                1049 -> { switchToMainScreen(); buffer.restoreCursor() }
                else -> {}
            }
            else -> {}
        }
    }

    // --- SGR (Select Graphic Rendition) ---

    private fun handleSgr() {
        if (params.isEmpty()) { buffer.currentStyle = TextStyle.DEFAULT; return }
        var s = buffer.currentStyle
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0  -> s = TextStyle.DEFAULT
                1  -> s = s.copy(bold = true)
                3  -> s = s.copy(italic = true)
                4  -> s = s.copy(underline = true)
                5, 6 -> s = s.copy(blink = true)
                7  -> s = s.copy(inverse = true)
                8  -> s = s.copy(invisible = true)
                9  -> s = s.copy(strikethrough = true)
                22 -> s = s.copy(bold = false)
                23 -> s = s.copy(italic = false)
                24 -> s = s.copy(underline = false)
                25 -> s = s.copy(blink = false)
                27 -> s = s.copy(inverse = false)
                28 -> s = s.copy(invisible = false)
                29 -> s = s.copy(strikethrough = false)
                in 30..37 -> s = s.copy(fg = p - 30)
                38 -> {
                    val color = parseSgrColor(i); if (color != null) { s = s.copy(fg = color.first); i += color.second }
                }
                39 -> s = s.copy(fg = TextStyle.COLOR_DEFAULT)
                in 40..47 -> s = s.copy(bg = p - 40)
                48 -> {
                    val color = parseSgrColor(i); if (color != null) { s = s.copy(bg = color.first); i += color.second }
                }
                49 -> s = s.copy(bg = TextStyle.COLOR_DEFAULT)
                in 90..97  -> s = s.copy(fg = p - 90 + 8)  // bright fg
                in 100..107 -> s = s.copy(bg = p - 100 + 8) // bright bg
                else -> {}
            }
            i++
        }
        buffer.currentStyle = s
    }

    /** Returns (color, paramsConsumed) for 38/48;2/5 sequences, or null. */
    private fun parseSgrColor(idx: Int): Pair<Int, Int>? {
        return when (params.getOrElse(idx + 1) { -1 }) {
            5 -> {
                val n = params.getOrElse(idx + 2) { 0 }
                Pair(n, 2) // 256-color
            }
            2 -> {
                val r = params.getOrElse(idx + 2) { 0 }
                val g = params.getOrElse(idx + 3) { 0 }
                val bv = params.getOrElse(idx + 4) { 0 }
                Pair(0x1000000 or (r shl 16) or (g shl 8) or bv, 4) // 24-bit RGB
            }
            else -> null
        }
    }

    // --- OSC ---

    private fun handleOsc(s: String) {
        val semi = s.indexOf(';')
        if (semi < 0) return
        val cmd = s.substring(0, semi).trim().toIntOrNull() ?: return
        val arg = s.substring(semi + 1)
        when (cmd) {
            0, 2 -> onTitleChanged?.invoke(arg) // set window title
            else -> {}
        }
    }

    // --- Helpers ---

    private var lastPrintedCodePoint = -1

    private fun printChar(codepoint: Int) {
        val w = CharWidth.of(codepoint)
        // Zero-width (combining marks, variation selectors, ZWJ): drop it so our column
        // count stays aligned with the host, which also counts it as 0. (Applying it to the
        // previous glyph is a later refinement.)
        if (w == 0) return
        lastPrintedCodePoint = codepoint
        // A wide character needs 2 columns; if it doesn't fit, wrap first (it can't straddle
        // the right margin). A narrow char defers the wrap until the next char, as before.
        if (buffer.cursorCol + w > buffer.columns) {
            // Auto-wrap: mark the full line as continuing onto the next one,
            // so copy/paste can join the two without inserting a fake newline
            buffer.setLineWrapped(buffer.cursorRow, true)
            buffer.cursorCol = 0
            lineFeed()
        }
        val row = buffer.cursorRow
        val col = buffer.cursorCol
        // Overwriting half of an existing wide char: clear its orphaned partner first.
        buffer.clearWidePairAt(row, col)
        if (w == 2) {
            buffer.clearWidePairAt(row, col + 1)
            buffer.setWide(row, col, codepoint)
        } else {
            buffer.setCodePoint(row, col, codepoint)
        }
        buffer.cursorCol += w
    }

    private fun lineFeed() {
        if (buffer.cursorRow == buffer.scrollBottom) {
            buffer.scrollUp()
        } else {
            buffer.cursorRow = (buffer.cursorRow + 1).coerceAtMost(buffer.rows - 1)
        }
    }

    private fun reverseLineFeed() {
        if (buffer.cursorRow == buffer.scrollTop) {
            buffer.scrollDown()
        } else {
            buffer.cursorRow = (buffer.cursorRow - 1).coerceAtLeast(0)
        }
    }

    private fun moveCursor(dRow: Int, dCol: Int) {
        buffer.cursorRow = (buffer.cursorRow + dRow).coerceIn(0, buffer.rows - 1)
        buffer.cursorCol = (buffer.cursorCol + dCol).coerceIn(0, buffer.columns - 1)
    }

    private fun moveCursorCol(d: Int) {
        buffer.cursorCol = (buffer.cursorCol + d).coerceIn(0, buffer.columns - 1)
    }

    private fun resetTerminal() {
        buffer.eraseInDisplay(2)
        buffer.cursorRow = 0; buffer.cursorCol = 0
        buffer.scrollTop = 0; buffer.scrollBottom = buffer.rows - 1
        buffer.currentStyle = TextStyle.DEFAULT
        buffer.cursorVisible = true
    }

    private fun switchToAltScreen() {
        if (!onAltScreen) {
            savedMainScreen = buffer.copyScreen()
            savedMainWrapFlags = buffer.copyWrapFlags()
            onAltScreen = true
            buffer.eraseInDisplay(2)
            buffer.cursorRow = 0; buffer.cursorCol = 0
        }
    }

    private fun switchToMainScreen() {
        if (onAltScreen) {
            onAltScreen = false
            val snapshot = savedMainScreen
            if (snapshot != null) {
                buffer.restoreScreen(snapshot)
                savedMainWrapFlags?.let { buffer.restoreWrapFlags(it) }
                savedMainScreen = null
                savedMainWrapFlags = null
            } else {
                buffer.eraseInDisplay(2)
                buffer.cursorRow = 0; buffer.cursorCol = 0
            }
        }
    }
}
