package com.sshborg.ui.editor

import com.sshborg.service.FileFailure

/**
 * The largest file the editor will open at all, and the size above which it asks first.
 *
 * Measured on the test phone, not chosen: a single Compose text field lays out the whole file
 * on every keystroke, so it stays comfortable to about 64 KB, drags noticeably at 128 KB and is
 * unusable by 512 KB. Configuration files, the case this is for, sit far below all of it; past
 * the ceiling the terminal is the honest answer, and it has no ceiling.
 */
const val EDITOR_MAX_BYTES = 256L * 1024
const val EDITOR_WARN_BYTES = 64L * 1024

/**
 * The ceiling for read-only viewing, which is far higher because none of the cost above
 * applies: with nothing to type into, the lines are an ordinary lazy list and only the ones on
 * screen exist. What limits it is holding the file in memory, not drawing it.
 */
const val EDITOR_VIEW_MAX_BYTES = 4L * 1024 * 1024

/**
 * The largest draft kept across a process death. Saved state travels in a Bundle, and a big one
 * takes the whole app down with a TransactionTooLargeException, so past this the text simply is
 * not kept — the file is small enough to edit, not small enough to carry around.
 */
const val EDITOR_MAX_DRAFT = 64 * 1024

/** What the editor is doing with one remote file. Null (no state) means the editor is closed. */
sealed interface EditorState {
    val path: String
    val name: String get() = path.substringAfterLast('/')

    /** Reading the file off the server. */
    data class Loading(override val path: String) : EditorState

    /** The file is open. [decoded] holds what is needed to write the bytes back unchanged. */
    data class Ready(
        override val path: String,
        val decoded: TextFile.Decoded,
        val saving: Boolean = false,
        /** When the last save landed, so the screen can say so and drop the unsaved mark. */
        val savedAt: Long = 0L,
        /**
         * A failed save, shown over the editor. It deliberately does not replace this state:
         * a file that could not be written is exactly when the user must keep their text.
         */
        val problem: FileFailure? = null,
        /** Shown as a lazy list of lines, with no field to type in — see [EDITOR_VIEW_MAX_BYTES]. */
        val readOnly: Boolean = false,
    ) : EditorState

    /**
     * The file is open in the hex editor. Its bytes live in the ViewModel, not here: they are
     * megabytes and they are mutated in place, neither of which belongs in a state object.
     */
    data class Hex(
        override val path: String,
        val size: Int,
        val saving: Boolean = false,
        val savedAt: Long = 0L,
        val problem: FileFailure? = null,
    ) : EditorState

    /** Big enough to be slow: the user is asked before it is read. */
    data class Confirm(override val path: String, val size: Long) : EditorState

    /** The file is readable but not editable as text — see [Reason]. */
    data class Unsupported(
        override val path: String,
        val reason: Reason,
        val size: Long = 0L,
    ) : EditorState

    /** Reading or writing failed; [failure] carries the message and the stack trace. */
    data class Failed(override val path: String, val failure: FileFailure) : EditorState

    enum class Reason { TOO_LARGE, BINARY }
}
