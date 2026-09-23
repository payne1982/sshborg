package com.sshborg.ui.editor

import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * sora-editor's widget, in place of the Compose text field — an experiment.
 *
 * The difference that matters is that it keeps its own text model and draws only the lines on
 * screen, instead of laying out the whole file on every keystroke. That is the one thing our
 * own editor cannot do, and the reason it has a ceiling at all.
 *
 * It is a View, so everything Compose gave for free has to be handed to it: the font, the
 * colours of the current theme, and its own disposal.
 */
@Composable
fun SoraEditor(
    text: String,
    /** Changes when [text] is a different file, or the same one read another way. */
    textKey: Any,
    readOnly: Boolean,
    onDirty: () -> Unit,
    onEditor: (CodeEditor?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val typeface = remember(context) {
        Typeface.createFromAsset(context.assets, "fonts/JetBrainsMonoNerdFontMono-Regular.ttf")
    }

    // The widget outlives single recompositions and has to be told to let go of its threads.
    val editor = remember(context) { CodeEditor(context) }
    DisposableEffect(editor) {
        onEditor(editor)
        onDispose {
            onEditor(null)
            editor.release()
        }
    }

    // Only when the file changes: comparing the widget's content against the string would be a
    // pass over the whole file on every recomposition, which is the cost we are here to avoid.
    LaunchedEffect(editor, textKey) { editor.setText(text) }

    AndroidView(
        factory = {
            editor.apply {
                typefaceText = typeface
                typefaceLineNumber = typeface
                setTextSize(13f)
                isWordwrap = true
                setLineNumberEnabled(true)
                subscribeEvent(
                    ContentChangeEvent::class.java,
                    EventReceiver { event, _ ->
                        // Setting the text counts as a change to the widget; it is not an edit.
                        if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) onDirty()
                    },
                )
            }
        },
        modifier = modifier,
        update = { view ->
            view.isEditable = !readOnly
            view.colorScheme = EditorColorScheme().apply {
                setColor(EditorColorScheme.WHOLE_BACKGROUND, colors.surface.toArgb())
                setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, colors.surface.toArgb())
                setColor(EditorColorScheme.TEXT_NORMAL, colors.onSurface.toArgb())
                setColor(EditorColorScheme.LINE_NUMBER, colors.onSurfaceVariant.toArgb())
                setColor(EditorColorScheme.LINE_NUMBER_CURRENT, colors.onSurface.toArgb())
                setColor(EditorColorScheme.CURRENT_LINE, colors.surfaceVariant.toArgb())
                setColor(EditorColorScheme.SELECTION_INSERT, colors.primary.toArgb())
                setColor(EditorColorScheme.SELECTION_HANDLE, colors.primary.toArgb())
                setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, colors.primaryContainer.toArgb())
                setColor(EditorColorScheme.LINE_DIVIDER, colors.outlineVariant.toArgb())
                setColor(EditorColorScheme.BLOCK_LINE, colors.outlineVariant.toArgb())
                setColor(EditorColorScheme.SCROLL_BAR_THUMB, colors.outline.toArgb())
            }
        },
    )
}
