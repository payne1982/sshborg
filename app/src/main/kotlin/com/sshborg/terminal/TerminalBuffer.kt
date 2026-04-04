package com.sshborg.terminal

/**
 * A 2D grid of cells representing the terminal screen, plus a scrollback history.
 * Thread-safety: this class is NOT thread-safe; synchronize externally.
 */
class TerminalBuffer(var columns: Int, var rows: Int, val maxScrollback: Int = 2000) {

    data class Cell(val char: Char = ' ', val style: TextStyle = TextStyle.DEFAULT)

    // Scrollback: index 0 = oldest line
    private val scrollback = ArrayDeque<Array<Cell>>(maxScrollback)
    // Visible screen lines
    private var screen = Array(rows) { Array(columns) { Cell() } }

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

    // --- Read access ---

    /**
     * Returns the text of [row] from column 0 up to (exclusive) [upToCol], trailing spaces stripped.
     * Pass [upToCol] = -1 (default) to read the full row width.
     */
    fun getRowText(row: Int, upToCol: Int = -1): String {
        if (row < 0 || row >= rows) return ""
        val end = if (upToCol < 0) columns else upToCol.coerceIn(0, columns)
        return buildString { for (c in 0 until end) append(screen[row][c].char) }.trimEnd()
    }

    fun getCell(row: Int, col: Int): Cell {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return Cell()
        return screen[row][col]
    }

    /** Returns a line from scrollback (0 = oldest). */
    fun getScrollbackLine(index: Int): Array<Cell>? = scrollback.getOrNull(index)

    val scrollbackSize: Int get() = scrollback.size

    // --- Write access ---

    fun setChar(row: Int, col: Int, char: Char, style: TextStyle = currentStyle) {
        if (row < 0 || row >= rows || col < 0 || col >= columns) return
        screen[row][col] = Cell(char, style)
    }

    fun saveCursor() {
        savedRow = cursorRow; savedCol = cursorCol; savedStyle = currentStyle
    }

    fun restoreCursor() {
        cursorRow = savedRow.coerceIn(0, rows - 1)
        cursorCol = savedCol.coerceIn(0, columns - 1)
        currentStyle = savedStyle
    }

    /** Scrolls lines scrollTop..scrollBottom up by count, adding blank lines at bottom. */
    fun scrollUp(count: Int = 1) {
        repeat(count) {
            // Push top line into scrollback
            val evicted = screen[scrollTop].copyOf()
            if (scrollback.size >= maxScrollback) scrollback.removeAt(0)
            scrollback.addLast(evicted)
            // Shift lines up within scroll region
            for (r in scrollTop until scrollBottom) {
                screen[r] = screen[r + 1]
            }
            screen[scrollBottom] = Array(columns) { Cell() }
        }
    }

    /** Scrolls lines scrollTop..scrollBottom down by count, adding blank lines at top. */
    fun scrollDown(count: Int = 1) {
        repeat(count) {
            for (r in scrollBottom downTo scrollTop + 1) {
                screen[r] = screen[r - 1]
            }
            screen[scrollTop] = Array(columns) { Cell() }
        }
    }

    /** Inserts [count] blank lines at [row], pushing lines down (bottom lines disappear). */
    fun insertLines(row: Int, count: Int) {
        val n = count.coerceAtMost(scrollBottom - row + 1)
        for (r in scrollBottom downTo row + n) {
            screen[r] = screen[r - n]
        }
        for (r in row until row + n) {
            screen[r] = Array(columns) { Cell() }
        }
    }

    /** Deletes [count] lines starting at [row], pulling lines up (blank lines added at bottom). */
    fun deleteLines(row: Int, count: Int) {
        val n = count.coerceAtMost(scrollBottom - row + 1)
        for (r in row..scrollBottom - n) {
            screen[r] = screen[r + n]
        }
        for (r in scrollBottom - n + 1..scrollBottom) {
            screen[r] = Array(columns) { Cell() }
        }
    }

    /** Returns a snapshot of the current visible screen (for alt-screen save/restore). */
    fun copyScreen(): Array<Array<Cell>> = Array(rows) { r -> screen[r].copyOf() }

    /** Restores a previously saved screen snapshot. No-op if dimensions don't match. */
    fun restoreScreen(snapshot: Array<Array<Cell>>) {
        if (snapshot.size != rows || snapshot.any { it.size != columns }) return
        for (r in 0 until rows) screen[r] = snapshot[r].copyOf()
    }

    fun eraseInDisplay(mode: Int) {
        when (mode) {
            0 -> { // cursor to end
                eraseInLine(0)
                for (r in cursorRow + 1 until rows) screen[r] = Array(columns) { Cell() }
            }
            1 -> { // beginning to cursor
                for (r in 0 until cursorRow) screen[r] = Array(columns) { Cell() }
                eraseInLine(1)
            }
            2, 3 -> { // whole screen (3 also clears scrollback)
                for (r in 0 until rows) screen[r] = Array(columns) { Cell() }
                if (mode == 3) scrollback.clear()
            }
        }
    }

    fun eraseInLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until columns) screen[cursorRow][c] = Cell()
            1 -> for (c in 0..cursorCol) screen[cursorRow][c] = Cell()
            2 -> screen[cursorRow] = Array(columns) { Cell() }
        }
    }

    fun eraseChars(count: Int) {
        for (c in cursorCol until (cursorCol + count).coerceAtMost(columns)) {
            screen[cursorRow][c] = Cell()
        }
    }

    fun insertChars(count: Int) {
        val row = screen[cursorRow]
        val n = count.coerceAtMost(columns - cursorCol)
        for (c in columns - 1 downTo cursorCol + n) {
            row[c] = row[c - n]
        }
        for (c in cursorCol until cursorCol + n) row[c] = Cell()
    }

    fun deleteChars(count: Int) {
        val row = screen[cursorRow]
        val n = count.coerceAtMost(columns - cursorCol)
        for (c in cursorCol until columns - n) row[c] = row[c + n]
        for (c in columns - n until columns) row[c] = Cell()
    }

    /** Resizes the buffer, preserving content as much as possible. */
    fun resize(newCols: Int, newRows: Int) {
        val oldRows = rows
        val oldCols = columns
        screen = Array(newRows) { r ->
            Array(newCols) { c ->
                if (r < oldRows && c < oldCols) screen[r][c] else Cell()
            }
        }
        columns = newCols
        rows = newRows
        scrollTop = 0
        scrollBottom = newRows - 1
        cursorRow = cursorRow.coerceAtMost(newRows - 1)
        cursorCol = cursorCol.coerceAtMost(newCols - 1)
    }
}
