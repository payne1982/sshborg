package com.sshborg.ui.editor

/**
 * A small syntax highlighter for the files people actually edit over SFTP.
 *
 * It is written by hand rather than taken from a grammar collection, and deliberately stays
 * small: the families below cover configuration files, which is what this editor is for, and
 * everything else is left in plain text rather than coloured by guesswork. Highlighting is
 * appearance only — the bytes go back exactly as they came whatever colour they were shown in
 * — so the worst a mistake here can do is look wrong.
 *
 * It reads character by character instead of matching regular expressions, which is what keeps
 * it honest on the cases that catch naive highlighters: a `#` inside a quoted string is not a
 * comment, an apostrophe in a comment does not open a string, and the `//` in a URL is not the
 * start of anything.
 */
object ConfigSyntax {

    /** What a run of characters is. The editor maps these onto the theme's colours. */
    enum class Token { PLAIN, COMMENT, STRING, NUMBER, KEYWORD, KEY, TAG, VARIABLE }

    /**
     * The kinds of file the highlighter knows. [PLAIN] is not a failure: it is the answer for
     * prose, logs and anything whose shape we do not recognise, where colour would be noise.
     */
    enum class Family { PLAIN, SHELL, CONF, INI, YAML, JSON, XML }

    /** One coloured run on a line: [start] until [end], exclusive. */
    data class Run(val start: Int, val end: Int, val token: Token)

    private val SHELL_WORDS = setOf(
        "if", "then", "else", "elif", "fi", "for", "while", "until", "do", "done", "case",
        "esac", "in", "function", "return", "local", "export", "readonly", "declare", "shift",
        "set", "unset", "source", "exit", "break", "continue", "echo", "printf", "cd", "trap",
    )

    private val CONSTANTS = setOf(
        "true", "false", "null", "yes", "no", "on", "off", "none", "None", "True", "False",
    )

    /** Extensions that name a family outright. Everything else goes to [sniff]. */
    private val BY_EXTENSION = mapOf(
        "sh" to Family.SHELL, "bash" to Family.SHELL, "zsh" to Family.SHELL,
        "profile" to Family.SHELL, "bashrc" to Family.SHELL, "env" to Family.SHELL,
        "conf" to Family.CONF, "config" to Family.CONF,
        "ini" to Family.INI, "cfg" to Family.INI, "toml" to Family.INI,
        "properties" to Family.INI, "service" to Family.INI, "desktop" to Family.INI,
        "yml" to Family.YAML, "yaml" to Family.YAML,
        "json" to Family.JSON, "jsonc" to Family.JSON,
        "xml" to Family.XML, "html" to Family.XML, "htm" to Family.XML, "svg" to Family.XML,
        "plist" to Family.XML, "xhtml" to Family.XML, "pom" to Family.XML,
        "txt" to Family.PLAIN, "log" to Family.PLAIN, "md" to Family.PLAIN,
    )

    /** Names with no extension that are worth knowing about. */
    private val BY_NAME = mapOf(
        "sshd_config" to Family.CONF, "ssh_config" to Family.CONF, "nginx.conf" to Family.CONF,
        "fstab" to Family.CONF, "hosts" to Family.CONF, "crontab" to Family.CONF,
        "dockerfile" to Family.CONF, "makefile" to Family.SHELL,
        "gitconfig" to Family.INI, "editorconfig" to Family.INI,
    )

    /**
     * Which family [name] belongs to, falling back to the shape of [text] when the name says
     * nothing — a file called `config` could be anything, and a shebang or an opening brace
     * settles it better than a guess.
     */
    fun familyOf(name: String, text: String): Family {
        val lower = name.lowercase()
        BY_NAME[lower]?.let { return it }
        BY_NAME[lower.removePrefix(".")]?.let { return it }
        val extension = lower.substringAfterLast('.', "")
        BY_EXTENSION[extension]?.let { return it }
        BY_EXTENSION[lower.removePrefix(".")]?.let { return it }
        return sniff(text)
    }

    /** The family the first few lines look like, or [Family.PLAIN] when nothing stands out. */
    fun sniff(text: String): Family {
        val head = text.take(2048).trimStart()
        if (head.startsWith("#!")) return Family.SHELL
        if (head.startsWith("<?xml") || head.startsWith("<!DOCTYPE") || head.startsWith("<")) return Family.XML
        if (head.startsWith("{") || head.startsWith("[\"") || head.startsWith("[{")) return Family.JSON
        val lines = head.lineSequence().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }.take(12).toList()
        if (lines.isEmpty()) return Family.PLAIN
        if (lines.any { it.trimStart().startsWith("[") && it.trimEnd().endsWith("]") }) return Family.INI
        // A colon-separated key at the start of a line is YAML's shape; an equals sign is INI's.
        val yaml = lines.count { Regex("""^\s*[\w.-]+:(\s|$)""").containsMatchIn(it) }
        val ini = lines.count { Regex("""^\s*[\w.-]+\s*=""").containsMatchIn(it) }
        if (yaml > lines.size / 2) return Family.YAML
        if (ini > lines.size / 2) return Family.INI
        return Family.PLAIN
    }

    /**
     * Colours one line, given [family] and whether the previous line left an XML comment open.
     * Returns the runs and the state to carry into the next line.
     */
    fun highlight(line: String, family: Family, inComment: Boolean): Pair<List<Run>, Boolean> {
        if (family == Family.PLAIN) return emptyList<Run>() to false
        val runs = mutableListOf<Run>()
        var open = inComment
        var i = 0

        if (family == Family.XML) return xml(line, inComment)

        // A line comment swallows the rest of the line — but only outside a string, which is
        // why this cannot be done by looking for the marker with indexOf.
        val commentStarts = when (family) {
            Family.SHELL, Family.CONF, Family.YAML -> "#"
            Family.INI -> "#;"
            else -> ""
        }
        val firstWord = family == Family.CONF || family == Family.SHELL

        while (i < line.length) {
            val c = line[i]
            when {
                c in commentStarts -> {
                    runs += Run(i, line.length, Token.COMMENT)
                    return runs to false
                }
                c == '"' || c == '\'' -> {
                    val end = closingQuote(line, i, c)
                    runs += Run(i, end, Token.STRING)
                    i = end
                }
                c == '$' && (family == Family.SHELL || family == Family.CONF) -> {
                    val end = variableEnd(line, i)
                    runs += Run(i, end, Token.VARIABLE)
                    i = end
                }
                c == '&' || c == '*' -> {
                    // YAML anchors and references; elsewhere just an ampersand.
                    val end = if (family == Family.YAML) wordEnd(line, i + 1) else i + 1
                    if (end > i + 1) runs += Run(i, end, Token.VARIABLE)
                    i = end
                }
                c.isDigit() && (i == 0 || !line[i - 1].isLetterOrDigit() && line[i - 1] != '_') -> {
                    val end = numberEnd(line, i)
                    runs += Run(i, end, Token.NUMBER)
                    i = end
                }
                c == '[' && family == Family.INI && line.take(i).isBlank() -> {
                    val end = (line.indexOf(']', i) + 1).takeIf { it > 0 } ?: line.length
                    runs += Run(i, end, Token.KEYWORD)
                    i = end
                }
                c.isLetter() || c == '_' || c == '/' -> {
                    val end = wordEnd(line, i)
                    val word = line.substring(i, end)
                    val before = line.take(i)
                    val token = when {
                        word in CONSTANTS -> Token.NUMBER
                        family == Family.SHELL && word in SHELL_WORDS -> Token.KEYWORD
                        // The first word of a line is the directive in nginx, sshd_config and
                        // their kin; that one rule is what makes those files readable.
                        firstWord && before.isBlank() -> Token.KEYWORD
                        keyFollows(line, end, family) -> Token.KEY
                        else -> Token.PLAIN
                    }
                    if (token != Token.PLAIN) runs += Run(i, end, token)
                    i = end
                }
                else -> i++
            }
        }
        return runs to open
    }

    /** Whether the word ending at [end] is a key: followed by `=` in INI, by `:` in YAML/JSON. */
    private fun keyFollows(line: String, end: Int, family: Family): Boolean {
        var j = end
        while (j < line.length && line[j] == ' ') j++
        if (j >= line.length) return false
        return when (family) {
            Family.INI, Family.CONF -> line[j] == '='
            Family.YAML, Family.JSON -> line[j] == ':'
            else -> false
        }
    }

    /** The index just past the closing quote, or the end of the line if it never closes. */
    private fun closingQuote(line: String, start: Int, quote: Char): Int {
        var j = start + 1
        while (j < line.length) {
            if (line[j] == '\\' && quote == '"') { j += 2; continue }
            if (line[j] == quote) return j + 1
            j++
        }
        return line.length
    }

    private fun variableEnd(line: String, start: Int): Int {
        var j = start + 1
        if (j < line.length && line[j] == '{') {
            val close = line.indexOf('}', j)
            return if (close < 0) line.length else close + 1
        }
        while (j < line.length && (line[j].isLetterOrDigit() || line[j] == '_')) j++
        return if (j == start + 1) start + 1 else j
    }

    private fun wordEnd(line: String, start: Int): Int {
        var j = start
        while (j < line.length && (line[j].isLetterOrDigit() || line[j] in "_-./")) j++
        return maxOf(j, start + 1)
    }

    private fun numberEnd(line: String, start: Int): Int {
        var j = start
        while (j < line.length && (line[j].isDigit() || line[j] == '.')) j++
        // A trailing unit is part of the number to the eye: 30d, 20M, 443/tcp stays a number.
        while (j < line.length && line[j].isLetter() && j - start <= 12) j++
        return j
    }

    /** XML is its own shape: tags, attributes, entities, and comments that cross lines. */
    private fun xml(line: String, inComment: Boolean): Pair<List<Run>, Boolean> {
        val runs = mutableListOf<Run>()
        var i = 0
        var open = inComment
        while (i < line.length) {
            if (open) {
                val close = line.indexOf("-->", i)
                val end = if (close < 0) line.length else close + 3
                runs += Run(i, end, Token.COMMENT)
                open = close < 0
                i = end
                continue
            }
            when {
                line.startsWith("<!--", i) -> { open = true }
                line[i] == '<' -> {
                    val end = wordEnd(line, i + 1 + (if (line.getOrNull(i + 1) == '/') 1 else 0))
                    runs += Run(i, end, Token.TAG)
                    i = end
                    continue
                }
                line[i] == '"' || line[i] == '\'' -> {
                    val end = closingQuote(line, i, line[i])
                    runs += Run(i, end, Token.STRING)
                    i = end
                    continue
                }
                line[i] == '&' -> {
                    val close = line.indexOf(';', i)
                    if (close in i + 1..i + 10) { runs += Run(i, close + 1, Token.NUMBER); i = close + 1; continue }
                }
                line[i].isLetter() && keyFollowsEquals(line, i) -> {
                    val end = wordEnd(line, i)
                    runs += Run(i, end, Token.KEY)
                    i = end
                    continue
                }
            }
            i++
        }
        return runs to open
    }

    private fun keyFollowsEquals(line: String, start: Int): Boolean {
        var j = start
        while (j < line.length && (line[j].isLetterOrDigit() || line[j] in "_-:")) j++
        return j < line.length && line[j] == '='
    }
}
