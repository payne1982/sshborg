package com.sshborg.ui.common

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager

/**
 * Puts the soft keyboard away. [view] only has to be in the right window.
 *
 * Not Compose's own keyboard controller, and the reason is the terminal: it takes its input
 * connection back by hand (`TerminalView.reattachIme`), so Compose does not know that keyboard
 * exists and cannot dismiss it. Asking the window's input method manager works whoever opened it,
 * which is what both callers need — the terminal putting its own keyboard away, and the lock gate
 * putting away somebody else's.
 */
fun hideKeyboard(view: View) {
    val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
}
