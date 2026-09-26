package com.sshborg.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * The bytes the hex editor is working on, changed in place.
 *
 * Only values change, never the length: this is overwrite mode, the default of every hex editor
 * there is. It covers what people actually do to a binary — flip a flag, fix a number, correct
 * a string — and it keeps every offset in the file where it was, which is what makes the change
 * safe to write back. Inserting would move everything after it, and in most binary formats that
 * breaks the file whatever we do.
 *
 * [revision] is what the screen watches: it is bumped on every change so the rows redraw. With
 * the rows in a lazy list only the visible ones exist, so redrawing all of them is redrawing
 * about thirty, whatever the file's size.
 */
class HexBuffer(original: ByteArray) {
    val bytes: ByteArray = original.copyOf()

    var revision by mutableIntStateOf(0)
        private set

    /** Offset and the value that was there, newest last. */
    private val history = ArrayDeque<Pair<Int, Byte>>()

    val dirty: Boolean get() = revision > 0 && history.isNotEmpty()

    val size: Int get() = bytes.size

    operator fun get(index: Int): Byte = bytes[index]

    fun set(index: Int, value: Byte) {
        if (bytes[index] == value) return
        history.addLast(index to bytes[index])
        bytes[index] = value
        revision++
    }

    /** Undoes the last change and returns the offset it was at, or null when there is none. */
    fun undo(): Int? {
        val (index, previous) = history.removeLastOrNull() ?: return null
        bytes[index] = previous
        revision++
        return index
    }

    /** Called once a save has landed: what is on the server now is what is here. */
    fun markSaved() {
        history.clear()
        revision++
    }
}
