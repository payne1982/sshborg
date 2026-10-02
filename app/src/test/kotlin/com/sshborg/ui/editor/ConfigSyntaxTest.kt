package com.sshborg.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Highlighting is appearance only, so a wrong colour cannot corrupt a file — but it can make the
 * app look broken, and the cases that fool a naive highlighter are exactly the ones a server
 * config is full of. The tokeniser goes character by character for one reason, checked here: a
 * `#` inside a string is not a comment.
 */
class ConfigSyntaxTest {

    private fun runsOf(line: String, family: ConfigSyntax.Family, inComment: Boolean = false) =
        ConfigSyntax.highlight(line, family, inComment).first

    private fun tokensAt(line: String, family: ConfigSyntax.Family, index: Int) =
        runsOf(line, family).filter { index >= it.start && index < it.end }.map { it.token }

    @Test fun `a known name settles the family`() {
        assertEquals(ConfigSyntax.Family.CONF, ConfigSyntax.familyOf("nginx.conf", ""))
        assertEquals(ConfigSyntax.Family.CONF, ConfigSyntax.familyOf("sshd_config", ""))
        assertEquals(ConfigSyntax.Family.YAML, ConfigSyntax.familyOf("docker-compose.yml", ""))
        assertEquals(ConfigSyntax.Family.JSON, ConfigSyntax.familyOf("package.json", ""))
        assertEquals(ConfigSyntax.Family.XML, ConfigSyntax.familyOf("pom.xml", ""))
        assertEquals(ConfigSyntax.Family.SHELL, ConfigSyntax.familyOf("deploy.sh", ""))
        assertEquals(ConfigSyntax.Family.INI, ConfigSyntax.familyOf("app.ini", ""))
    }

    @Test fun `prose and logs are left alone`() {
        assertEquals(ConfigSyntax.Family.PLAIN, ConfigSyntax.familyOf("note.txt", "just words\n"))
        assertEquals(ConfigSyntax.Family.PLAIN, ConfigSyntax.familyOf("syslog.log", "Sep 26 11:00:00 host thing\n"))
        assertTrue("PLAIN must produce no runs at all", runsOf("anything # here", ConfigSyntax.Family.PLAIN).isEmpty())
    }

    @Test fun `a file whose name says nothing is settled by its shape`() {
        assertEquals(ConfigSyntax.Family.SHELL, ConfigSyntax.familyOf("data", "#!/bin/sh\necho hi\n"))
        assertEquals(ConfigSyntax.Family.JSON, ConfigSyntax.familyOf("data", "{\n  \"a\": 1\n}\n"))
        assertEquals(ConfigSyntax.Family.XML, ConfigSyntax.familyOf("data", "<?xml version=\"1.0\"?>\n"))
        assertEquals(ConfigSyntax.Family.INI, ConfigSyntax.familyOf("data", "[section]\nkey = 1\n"))
        assertEquals(ConfigSyntax.Family.YAML, ConfigSyntax.familyOf("data", "name: thing\nother: 2\n"))
    }

    /** A name that does say something wins over the shape, which is how `.bashrc` and
     *  `.gitconfig` are recognised — and why a shell script called `config` is coloured as a
     *  config file. Cosmetic, and the name is the better guess far more often. */
    @Test fun `a known name beats the shape`() {
        assertEquals(ConfigSyntax.Family.CONF, ConfigSyntax.familyOf("config", "#!/bin/sh\necho hi\n"))
        assertEquals(ConfigSyntax.Family.SHELL, ConfigSyntax.familyOf(".bashrc", "alias x=y\n"))
        assertEquals(ConfigSyntax.Family.INI, ConfigSyntax.familyOf(".gitconfig", "[user]\n"))
    }

    @Test fun `a hash inside a string is not a comment`() {
        val line = """server_name "a#b"; # the real one"""
        val firstHash = line.indexOf('#')
        val realHash = line.lastIndexOf('#')
        assertTrue("the # inside the quotes must not start a comment",
            ConfigSyntax.Token.COMMENT !in tokensAt(line, ConfigSyntax.Family.CONF, firstHash))
        assertTrue("it belongs to the string instead",
            ConfigSyntax.Token.STRING in tokensAt(line, ConfigSyntax.Family.CONF, firstHash))
        assertTrue("the # outside the quotes starts the comment",
            ConfigSyntax.Token.COMMENT in tokensAt(line, ConfigSyntax.Family.CONF, realHash))
    }

    @Test fun `a comment swallows the rest of the line`() {
        val line = "listen 80; # port \"quoted\" and more"
        val comment = runsOf(line, ConfigSyntax.Family.CONF).single { it.token == ConfigSyntax.Token.COMMENT }
        assertEquals(line.indexOf('#'), comment.start)
        assertEquals(line.length, comment.end)
    }

    @Test fun `an xml comment carries over to the next line`() {
        val (_, open) = ConfigSyntax.highlight("<!-- opened here", ConfigSyntax.Family.XML, false)
        assertTrue("an unterminated XML comment has to be carried", open)
        val (runs, stillOpen) = ConfigSyntax.highlight("still inside --> <tag/>", ConfigSyntax.Family.XML, true)
        assertTrue("and closed when it ends", !stillOpen)
        assertTrue(runs.any { it.token == ConfigSyntax.Token.COMMENT })
    }

    @Test fun `runs never overlap and stay inside the line`() {
        val lines = listOf(
            """key = "value" # note""" to ConfigSyntax.Family.INI,
            """{"a": 1, "b": "x#y"}""" to ConfigSyntax.Family.JSON,
            "name: value # note" to ConfigSyntax.Family.YAML,
            "export A=1 # note" to ConfigSyntax.Family.SHELL,
            "<tag a=\"1\">text</tag>" to ConfigSyntax.Family.XML,
        )
        lines.forEach { (line, family) ->
            val runs = runsOf(line, family).sortedBy { it.start }
            runs.forEach {
                assertTrue("$line: run out of bounds ${it.start}..${it.end}",
                    it.start >= 0 && it.end <= line.length && it.start < it.end)
            }
            runs.zipWithNext().forEach { (a, b) ->
                assertTrue("$line: runs overlap ${a.start}..${a.end} and ${b.start}..${b.end}", a.end <= b.start)
            }
        }
    }
}
