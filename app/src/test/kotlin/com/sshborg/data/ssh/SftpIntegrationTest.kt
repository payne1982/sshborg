package com.sshborg.data.ssh

import com.sshborg.TestConfig
import java.security.Security
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

/**
 * SFTP against a real server. Two things can only be checked here: that a file comes back
 * exactly as it went, over a real transfer rather than in memory, and that the channel survives
 * being used from two threads at once — the bug that cost 1.17.1 its first week.
 *
 * Skipped unless `test.properties` names a server; see `test.properties.example`.
 */
class SftpIntegrationTest {

    private val configured = TestConfig.sshServer()
    private var session: SftpSession? = null
    private var scratch: String = ""

    @Before fun openSession() {
        assumeTrue(TestConfig.NO_SERVER, configured != null)
        val s = configured!!
        val params = SshConnectionParams(
            hostname = s.host, port = s.port, username = s.user,
            auth = SshAuth.PublicKey(s.key("i_plain")),
        )
        val opened = runBlocking { SshManager.openSftp(params) { _, _, _ -> true } }
        session = opened
        scratch = "${opened.homePath.trimEnd('/')}/.sshborg-test-${System.nanoTime()}"
        opened.mkdir(scratch)
    }

    @After fun closeSession() {
        val open = session ?: return
        runCatching { open.listDir(scratch).forEach { open.deleteFile("$scratch/${it.name}") } }
        runCatching { open.deleteDir(scratch) }
        open.disconnect()
    }

    private fun sftp(): SftpSession = session!!

    @Test fun `a file goes up and comes back byte for byte`() {
        val path = "$scratch/round-trip.bin"
        // Bytes a text-safe path would mangle: NULs, high bytes, CR LF, a UTF-8 tail.
        val bytes = ByteArray(4096) { (it % 256).toByte() } + "\r\n\u0000fine è così\n".toByteArray()
        sftp().writeFile(path, bytes)
        assertEquals(bytes.size.toLong(), sftp().sizeOf(path))
        assertArrayEquals(bytes, sftp().readFile(path, limit = 1 shl 20))
    }

    @Test fun `an empty file is a file too`() {
        val path = "$scratch/empty"
        sftp().writeFile(path, ByteArray(0))
        assertEquals(0L, sftp().sizeOf(path))
        assertArrayEquals(ByteArray(0), sftp().readFile(path, limit = 1024))
    }

    @Test fun `a file bigger than the caller will hold is refused, not truncated`() {
        val path = "$scratch/too-big"
        sftp().writeFile(path, ByteArray(2048))
        try {
            sftp().readFile(path, limit = 1024)
            fail("reading past the limit should have been refused")
        } catch (e: FileTooLargeException) {
            assertEquals(2048L, e.size)
            assertEquals(1024L, e.limit)
        }
    }

    @Test fun `the listing follows what happens to the directory`() {
        val path = "$scratch/listed.txt"
        assertTrue("the scratch directory should start empty", sftp().listDir(scratch).isEmpty())
        sftp().writeFile(path, "hello\n".toByteArray())

        val entry = sftp().listDir(scratch).singleOrNull { it.name == "listed.txt" }
        assertNotNull("the file should be in the listing", entry)
        assertEquals(6L, entry!!.size)
        assertTrue(!entry.isDir && !entry.isLink)

        sftp().rename(path, "$scratch/renamed.txt")
        assertNull(sftp().listDir(scratch).find { it.name == "listed.txt" })
        assertNotNull(sftp().listDir(scratch).find { it.name == "renamed.txt" })

        sftp().deleteFile("$scratch/renamed.txt")
        assertTrue(sftp().listDir(scratch).isEmpty())
    }

    /**
     * The 1.17.1 bug, kept fixed: two listings at once on one ChannelSftp interleaved their
     * packets, and the session came down with "unknown type 107", a bad MAC, or an
     * IndexOutOfBounds deep inside JSch. SFTP matches replies to requests by reading them in
     * order from a single stream, so nothing may share a channel without taking turns.
     */
    @Test fun `the channel survives being used from several threads at once`() {
        repeat(3) { sftp().writeFile("$scratch/file-$it", "x".repeat(it + 1).toByteArray()) }

        val failures = CopyOnWriteArrayList<Throwable>()
        val threads = (1..4).map {
            Thread {
                repeat(15) {
                    runCatching {
                        val names = sftp().listDir(scratch).map { entry -> entry.name }.sorted()
                        if (names != listOf("file-0", "file-1", "file-2")) {
                            error("a listing came back wrong: $names")
                        }
                    }.onFailure(failures::add)
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(120_000) }

        assertTrue(
            "the channel did not survive:\n" + failures.joinToString("\n") { "${it::class.simpleName}: ${it.message}" },
            failures.isEmpty(),
        )
        assertTrue("and it is still usable afterwards", sftp().isConnected)
        assertEquals(3, sftp().listDir(scratch).size)
    }

    companion object {
        @BeforeClass @JvmStatic fun useOurBouncyCastle() {
            Security.removeProvider("BC")
            Security.addProvider(BouncyCastleProvider())
        }
    }
}
