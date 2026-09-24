package com.sshborg.ui.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.analysis.AnalyzeManager
import io.github.rosemoe.sora.lang.analysis.SimpleAnalyzeManager
import io.github.rosemoe.sora.lang.styling.MappedSpans
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.lang.styling.TextStyle
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Hands [ConfigSyntax] to the editor. Everything else is inherited: no completion, no
 * formatter, no bracket handling — the one thing this adds is colour.
 */
class ConfigLanguage(private val family: ConfigSyntax.Family) : EmptyLanguage() {

    private val analyzer = ConfigAnalyzer(family)

    override fun getAnalyzeManager(): AnalyzeManager = analyzer

    override fun destroy() {
        analyzer.destroy()
    }
}

/**
 * Colours the file a line at a time, carrying only whether an XML comment is still open.
 *
 * The work is repeated from the top after every edit, which sounds wasteful and is not: it is
 * one pass of character comparisons over a file of at most a few megabytes, on a background
 * thread, and sora only asks for the spans of the lines it is about to draw.
 */
private class ConfigAnalyzer(private val family: ConfigSyntax.Family) : SimpleAnalyzeManager<Unit>() {

    override fun analyze(text: StringBuilder, delegate: Delegate<Unit>): Styles {
        val builder = MappedSpans.Builder()
        var line = 0
        var start = 0
        var inComment = false
        val content = text.toString()

        while (start <= content.length) {
            if (!delegate.isCancelled) {
                val breakAt = content.indexOf('\n', start).let { if (it < 0) content.length else it }
                val (runs, carry) = ConfigSyntax.highlight(content.substring(start, breakAt), family, inComment)
                inComment = carry
                // A line always opens in plain text; each run then claims its own stretch and
                // hands the rest back, because a span reaches to the start of the next one.
                builder.addIfNeeded(line, 0, NORMAL)
                for (run in runs) {
                    builder.addIfNeeded(line, run.start, styleOf(run.token))
                    builder.addIfNeeded(line, run.end, NORMAL)
                }
                start = breakAt + 1
                line++
            } else {
                break
            }
        }
        builder.determine(line)
        return Styles(builder.build())
    }

    private fun styleOf(token: ConfigSyntax.Token) = when (token) {
        ConfigSyntax.Token.COMMENT -> COMMENT
        ConfigSyntax.Token.STRING -> STRING
        ConfigSyntax.Token.NUMBER -> NUMBER
        ConfigSyntax.Token.KEYWORD -> KEYWORD
        ConfigSyntax.Token.KEY -> KEY
        ConfigSyntax.Token.TAG -> TAG
        ConfigSyntax.Token.VARIABLE -> VARIABLE
        ConfigSyntax.Token.PLAIN -> NORMAL
    }

    private companion object {
        val NORMAL = TextStyle.makeStyle(EditorColorScheme.TEXT_NORMAL)
        val COMMENT = TextStyle.makeStyle(EditorColorScheme.COMMENT)
        val STRING = TextStyle.makeStyle(EditorColorScheme.LITERAL)
        val NUMBER = TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_VALUE)
        val KEYWORD = TextStyle.makeStyle(EditorColorScheme.KEYWORD)
        val KEY = TextStyle.makeStyle(EditorColorScheme.ATTRIBUTE_NAME)
        val TAG = TextStyle.makeStyle(EditorColorScheme.HTML_TAG)
        val VARIABLE = TextStyle.makeStyle(EditorColorScheme.IDENTIFIER_VAR)
    }
}
