package com.sshborg.data.ssh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The names a listing may contain, and the ones that are a path wearing a name's clothes.
 *
 * This is the only way to check it: a hostile SFTP server is not something anyone can set up on
 * the way to testing a download, and the behaviour being refused — a download writing outside the
 * folder it was given — leaves no trace on the screen when it works.
 */
class SftpNamesTest {

    @Test fun `ordinary names are names`() {
        for (name in listOf(
            "notes.txt", ".bashrc", "a file with spaces", "già", "日本語", "-rf", "a\\b", "~", "...",
            "..file", "file..", "a:b", "*", "C:", "name\twith\ttabs",
        )) {
            assertTrue("$name is a perfectly good filename", SftpNames.isPlain(name))
        }
    }

    @Test fun `a path is not a name`() {
        for (name in listOf(
            "..", ".", "", "../../somewhere.txt", "../..", "sub/file", "/etc/passwd",
            "/", "a/b/c", "name\u0000.txt",
        )) {
            assertFalse("[$name] is not a single name and must not be used as one", SftpNames.isPlain(name))
        }
    }
}
