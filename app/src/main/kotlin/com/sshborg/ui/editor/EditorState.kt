package com.sshborg.ui.editor

import com.sshborg.service.FileFailure

/**
 * The largest file the editor will open. Provisional: the ceiling is Compose's text layout,
 * which remeasures the whole content on every keystroke, so it has to be measured on a real
 * phone before it is settled. Configuration files, the case this is for, are far below it.
 */
const val EDITOR_MAX_BYTES = 512L * 1024

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
    ) : EditorState

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
