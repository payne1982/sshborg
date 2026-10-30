package com.sshborg.terminal

/**
 * A 2D grid of cells representing the terminal screen, plus a scrollback history.
 * Thread-safety: this class is NOT thread-safe; synchronize externally.
 */
class TerminalBuffer(var columns: Int, var rows: Int, wantedScrollback: Int = DEFAULT_SCROLLBACK) {

    /**
     * Clamped here and not only where it is typed, because this is where it is *spent*: the
     * deques below reserve this many slots before a single line has been printed. A value from
     * outside the range can only have come from a file, and a file is not a reason to fail to
     * open a terminal — see [saneScrollback].
     *
     * The parameter has a different name on purpose. A constructor parameter is in scope for
     * every property initializer in the class, so a property that merely shadows it leaves the
     * initializers below reading the raw number — which is how the first attempt at this fix
     * still ran out of memory.
     */
    val maxScrollback: Int = saneScrollback(wantedScrollback)

    /**
     * A single screen cell. [code] is a full Unicode codepoint (Int), so supplementary
     * characters (emoji) fit in one cell instead of being split across two surrogate cells.
     *
     * A wide character (CJK, emoji: [CharWidth] == 2) occupies two cells: the left "lead"
     * cell carries the codepoint with [wide] = true, and the right cell is a [trailing]
     * placeholder (code 0) that is never drawn and is skipped when reading text out.
     */
    data class Cell(
        val code: Int = ' '.code,
        val style: TextStyle = TextStyle.DEFAULT,
        val wide: Boolean = false,
        val trailing: Boolean = false,
    ) {
        /** BMP convenience view; supplementary codepoints (emoji) read back as a space. */
        val char: Char get() = if (Character.isBmpCodePoint(code)) code.toChar() else ' '
    }

    companion object {
        /**
         * The range of scrollback a terminal may be asked for, and the range the settings field
         * offers. It is a range and not a maximum because the number decides an allocation: the
         * deques below reserve that many slots the moment a terminal opens.
         */
        const val MIN_SCROLLBACK = 100
        const val MAX_SCROLLBACK = 50_000
        const val DEFAULT_SCROLLBACK = 2000
    }

    // Scrollback: index 0 = oldest line
    private val scrollback = ArrayDeque<Array<Cell>>(maxScrollback)
    // Visible screen lines
    private var screen = Array(rows) { Array(columns) { Cell() } }

    // Wrap flags, parallel to scrollback/screen: true = the line continues onto the
    // next one because of auto-wrap, i.e. the line break is not a real newline.
    private val scrollbackWrapped = ArrayDeque<Boolean>(maxScrollback)
    private var screenWrapped = BooleanArray(rows)

    // --- Cursor ---
    var cursorRow = 0
    var cursorCol = 0
    var cursorVisible = true

    // Saved cursor (ESC 7 / ESC 8)
    private var savedRow = 0
    private var savedCol = 0
    private var savedStyle = TextStyle.DEFAULT

    // Current drawing style
    var currentStyle = TextStyle.DEFAULT

    // Scroll region (inclusive, 0-based)
    var scrollTop = 0
    var scrollBottom = rows - 1

    /**
     * True while the alternate screen is showing (vim, less, tmux…). The alternate screen
     * has no history: lines that scroll off it are discarded instead of being pushed into
     * [scrollback], which would otherwise interleave a full-screen app's redraws with the
     * shell history sitting underneath it.
     */
    var altScreen = false

    /**
     * How many more lines leaving the screen are the far end repainting rather than history.
     *
     * Only a synchronised screen sets this, and only when its size has just changed under it.
     * The far end paints for the size it was last told, so for one round trip after a resize its
     * frames are drawn for the old shape — and a full repaint draws every row, which on a screen
     * that has since become shorter means the surplus rows scroll off the bottom. Measured on a
     * phone: the session was opened before the keyboard came up, so the far end was told 43 rows
     * and painted 43 onto the 27 that were left, and sixteen lines of somebody else's repaint
     * became the first sixteen lines of the history.
     *
     * Counted rather than held by a flag so that it can only ever swallow as much as a repaint
     * of one screen: if the expected repaint never comes, the history resumes by itself.
     */
    var repaintLinesToIgnore = 0

    // --- Read access ---

    /**
     * Returns the text of [row] from column 0 up to (exclusive) [upToCol], trailing spaces stripped.
     * Pass [upToCol] = -1 (default) to read the full row width.
     */
    fun getRowText(row: Int, upToCol: Int = -1): String {
        if (row < 0 || row >= rows) return ""
        val end = if (upToCol < 0) columns else upToCol.coerceIn(0, columns)
        return buildString {
            for (c in 0 until end) {
                val cell = screen[row][c]
                if (!cell.trailing) appendCodePoint(cell.code)
            }
        }.trimEnd()
    }

    fun getCell(row: Int, col: Int): Cell {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return Cell()
        return screen[row][col]
    }

    /** Returns a line from scrollback (0 = oldest). */
    fun getScrollbackLine(index: Int): Array<Cell>? = scrollback.getOrNull(index)

    val scrollbackSize: Int get() = scrollback.size

    fun isLineWrapped(row: Int): Boolean = row in 0 until rows && screenWrapped[row]

    fun setLineWrapped(row: Int, wrapped: Boolean) {
        if (row in 0 until rows) screenWrapped[row] = wrapped
    }

    fun isScrollbackLineWrapped(index: Int): Boolean = scrollbackWrapped.getOrNull(index) == true

    // --- Write access ---

    fun setCodePoint(row: Int, col: Int, code: Int, style: TextStyle = currentStyle) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return
        screen[row][col] = Cell(code, style)
    }

    /** Writes a wide (2-cell) character: lead at [col], trailing placeholder at [col]+1. */
    fun setWide(row: Int, col: Int, code: Int, style: TextStyle = currentStyle) {
        if (row < 0 || row >= rows || col < 0 || col + 1 >= columns) return
        screen[row][col] = Cell(code, style, wide = true)
        screen[row][col + 1] = Cell(0, style, trailing = true)
    }

    /**
     * If ([row],[col]) is half of a wide character, blanks both of its cells so no orphaned
     * lead or trailing placeholder is left behind when a character is overwritten.
     */
    fun clearWidePairAt(row: Int, col: Int) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return
        val cell = screen[row][col]
        when {
            cell.trailing && col > 0 -> { screen[row][col - 1] = Cell(); screen[row][col] = Cell() }
            cell.wide && col + 1 < columns -> { screen[row][col] = Cell(); screen[row][col + 1] = Cell() }
        }
    }

    /** Blanks any orphaned wide lead / trailing placeholder in [row] (after shift/erase edits). */
    fun sanitizeWide(row: Int) {
        if (row < 0 || row >= rows) return
        val line = screen[row]
        for (c in 0 until columns) {
            val cell = line[c]
            if (cell.wide && (c + 1 >= columns || !line[c + 1].trailing)) line[c] = Cell()
            if (cell.trailing && (c == 0 || !line[c - 1].wide)) line[c] = Cell()
        }
    }

    fun saveCursor() {
        savedRow = cursorRow; savedCol = cursorCol; savedStyle = currentStyle
    }

    fun restoreCursor() {
        cursorRow = savedRow.coerceIn(0, rows - 1)
        cursorCol = savedCol.coerceIn(0, columns - 1)
        currentStyle = savedStyle
    }

    /**
     * Scrolls lines scrollTop..scrollBottom up by count, adding blank lines at bottom.
     *
     * More than the region holds is the whole region blanked, and is done as exactly that: the
     * count arrives from the far end and `CSI 2000000000 S` would otherwise be two billion rows
     * allocated one at a time with the screen's lock held.
     */
    fun scrollUp(count: Int = 1) {
        repeat(count.coerceIn(0, scrollBottom - scrollTop + 1)) {
            // Push top line into scrollback (never from the alternate screen, which has no
            // history, and never while a repaint drawn for another size is still landing)
            if (altScreen) {
                // nothing to keep
            } else if (repaintLinesToIgnore > 0) {
                repaintLinesToIgnore--
            } else {
                val evicted = screen[scrollTop].copyOf()
                if (scrollback.size >= maxScrollback) { scrollback.removeAt(0); scrollbackWrapped.removeAt(0) }
                scrollback.addLast(evicted)
                scrollbackWrapped.addLast(screenWrapped[scrollTop])
            }
            // Shift lines up within scroll region
            for (r in scrollTop until scrollBottom) {
                screen[r] = screen[r + 1]
                screenWrapped[r] = screenWrapped[r + 1]
            }
            screen[scrollBottom] = Array(columns) { Cell() }
            screenWrapped[scrollBottom] = false
        }
    }

    /** Scrolls lines scrollTop..scrollBottom down by count, adding blank lines at top. See [scrollUp]. */
    fun scrollDown(count: Int = 1) {
        repeat(count.coerceIn(0, scrollBottom - scrollTop + 1)) {
            for (r in scrollBottom downTo scrollTop + 1) {
                screen[r] = screen[r - 1]
                screenWrapped[r] = screenWrapped[r - 1]
            }
            screen[scrollTop] = Array(columns) { Cell() }
            screenWrapped[scrollTop] = false
        }
    }

    /** Inserts [count] blank lines at [row], pushing lines down (bottom lines disappear). */
    fun insertLines(row: Int, count: Int) {
        val n = count.coerceIn(0, scrollBottom - row + 1)
        for (r in scrollBottom downTo row + n) {
            screen[r] = screen[r - n]
            screenWrapped[r] = screenWrapped[r - n]
        }
        for (r in row until row + n) {
            screen[r] = Array(columns) { Cell() }
            screenWrapped[r] = false
        }
    }

    /** Deletes [count] lines starting at [row], pulling lines up (blank lines added at bottom). */
    fun deleteLines(row: Int, count: Int) {
        val n = count.coerceIn(0, scrollBottom - row + 1)
        for (r in row..scrollBottom - n) {
            screen[r] = screen[r + n]
            screenWrapped[r] = screenWrapped[r + n]
        }
        for (r in scrollBottom - n + 1..scrollBottom) {
            screen[r] = Array(columns) { Cell() }
            screenWrapped[r] = false
        }
    }

    /** Returns a snapshot of the current visible screen (for alt-screen save/restore). */
    fun copyScreen(): Array<Array<Cell>> = Array(rows) { r -> screen[r].copyOf() }

    /** Restores a previously saved screen snapshot. No-op if dimensions don't match. */
    fun restoreScreen(snapshot: Array<Array<Cell>>) {
        if (snapshot.size != rows || snapshot.any { it.size != columns }) return
        for (r in 0 until rows) screen[r] = snapshot[r].copyOf()
    }

    /**
     * Drops the newest [count] lines of scrollback.
     *
     * For putting the screen back as it was at an earlier moment: the lines that scrolled away
     * since then are about to be scrolled away again by whatever is replayed over the top, and
     * left here they would appear in the history twice. Nothing real is lost — it is the same
     * content, arriving again.
     */
    fun dropNewestScrollback(count: Int) {
        repeat(count.coerceIn(0, scrollback.size)) {
            scrollback.removeLast()
            if (scrollbackWrapped.isNotEmpty()) scrollbackWrapped.removeLast()
        }
    }

    /** Returns a snapshot of the screen wrap flags (for alt-screen save/restore). */
    fun copyWrapFlags(): BooleanArray = screenWrapped.copyOf()

    /** Restores previously saved wrap flags. No-op if dimensions don't match. */
    fun restoreWrapFlags(flags: BooleanArray) {
        if (flags.size != rows) return
        screenWrapped = flags.copyOf()
    }

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // cursor to end
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) {
                    screen[r] = Array(columns) { Cell() }
                    screenWrapped[r] = false
                }
            }
            1 -> { // beginning to cursor
                for (r in 0 until cursorRow) {
                    screen[r] = Array(columns) { Cell() }
                    screenWrapped[r] = false
                }
                eraseInLine(1)
            }
            2, 3 -> { // whole screen (3 also clears scrollback)
                for (r in 0 until rows) screen[r] = Array(columns) { Cell() }
                screenWrapped.fill(false)
                if (mode == 3) { scrollback.clear(); scrollbackWrapped.clear() }
            }
        }
    }

    fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> {
                for (c in cursorCol until columns) screen[cursorRow][c] = Cell()
                screenWrapped[cursorRow] = false
                sanitizeWide(cursorRow)
            }
            1 -> { for (c in 0..cursorCol) screen[cursorRow][c] = Cell(); sanitizeWide(cursorRow) }
            2 -> {
                screen[cursorRow] = Array(columns) { Cell() }
                screenWrapped[cursorRow] = false
            }
        }
    }

    fun eraseChars(count: Int) {
        for (c in cursorCol until (cursorCol + count.coerceAtLeast(0)).coerceAtMost(columns)) {
            screen[cursorRow][c] = Cell()
        }
        if (cursorCol + count >= columns) screenWrapped[cursorRow] = false
        sanitizeWide(cursorRow)
    }

    fun insertChars(count: Int) {
        val row = screen[cursorRow]
        val n = count.coerceIn(0, columns - cursorCol)
        for (c in columns - 1 downTo cursorCol + n) {
            row[c] = row[c - n]
        }
        for (c in cursorCol until cursorCol + n) row[c] = Cell()
        // The tail of the line changed, so any auto-wrap continuation is broken
        screenWrapped[cursorRow] = false
        sanitizeWide(cursorRow)
    }

    fun deleteChars(count: Int) {
        val row = screen[cursorRow]
        val n = count.coerceIn(0, columns - cursorCol)
        for (c in cursorCol until columns - n) row[c] = row[c + n]
        for (c in columns - n until columns) row[c] = Cell()
        screenWrapped[cursorRow] = false
        sanitizeWide(cursorRow)
    }

    /** Resizes the buffer, preserving content as much as possible.
     *
     * When shrinking rows, if the cursor would be clipped, the top lines are
     * scrolled into scrollback (xterm-style) rather than truncating the bottom,
     * so the cursor stays at the last row and no visible content is lost.
     */
    fun resize(newCols: Int, newRows: Int) {
        val oldRows = rows
        val oldCols = columns
        // How many top lines to push into scrollback to keep cursor in view
        val scrollNeeded = if (newRows < oldRows) (cursorRow - newRows + 1).coerceAtLeast(0) else 0
        if (scrollNeeded > 0) {
            if (!altScreen) {
                for (i in 0 until scrollNeeded) {
                    val evicted = screen[i].copyOf()
                    if (scrollback.size >= maxScrollback) { scrollback.removeAt(0); scrollbackWrapped.removeAt(0) }
                    scrollback.addLast(evicted)
                    scrollbackWrapped.addLast(screenWrapped[i])
                }
            }
            screen = Array(newRows) { r ->
                val oldR = r + scrollNeeded
                Array(newCols) { c ->
                    if (oldR < oldRows && c < oldCols) screen[oldR][c] else Cell()
                }
            }
            // No reflow: flags follow their line but become approximate if newCols != oldCols
            screenWrapped = BooleanArray(newRows) { r ->
                val oldR = r + scrollNeeded
                oldR < oldRows && screenWrapped[oldR]
            }
            cursorRow -= scrollNeeded
        } else {
            screen = Array(newRows) { r ->
                Array(newCols) { c ->
                    if (r < oldRows && c < oldCols) screen[r][c] else Cell()
                }
            }
            screenWrapped = BooleanArray(newRows) { r -> r < oldRows && screenWrapped[r] }
        }
        columns = newCols
        rows = newRows
        scrollTop = 0
        scrollBottom = newRows - 1
        cursorRow = cursorRow.coerceIn(0, newRows - 1)
        cursorCol = cursorCol.coerceAtMost(newCols - 1)
        for (r in 0 until rows) sanitizeWide(r)
    }
}

/**
 * A number of scrollback lines that can actually be allocated.
 *
 * The settings field clamps what is typed into it, but the same number also arrives in a **backup
 * file**, and a backup is not something anybody reads before applying it. From there it goes
 * straight into [TerminalBuffer], where it reserves that many slots the moment a terminal opens —
 * so two billion lines was not a slow terminal, it was an OutOfMemoryError on the way to the first
 * prompt. Asked here by everything that can supply the number, so no single one of them has to be
 * the one that remembers.
 */
fun saneScrollback(lines: Int): Int =
    lines.coerceIn(TerminalBuffer.MIN_SCROLLBACK, TerminalBuffer.MAX_SCROLLBACK)
