package com.sshborg.mosh

import com.sshborg.TestConfig
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * The smallest loop that can hold a mosh session, shared by the tests that need a real
 * `mosh-server` — see `scripts/mosh-lab.sh` and `scripts/mosh-gate.sh`.
 *
 * Tick, send what comes back, wait for a datagram or for the next deadline, tick again. The real
 * one belongs to the app, with the foreground service and the Wi-Fi lock around it; this exists to
 * drive the protocol from a test, not to be that.
 *
 * Everything the host sends is also kept verbatim in [recorded], because the repaint gate works by
 * replaying that stream into two different terminal emulators and comparing what they draw.
 */
internal class MoshLabSession(lab: TestConfig.Mosh) : AutoCloseable {

    private val address: InetAddress = InetAddress.getByName(lab.host)
    private val port = lab.port
    private var socket = DatagramSocket()
    private val output = StringBuilder()
    private var lastHeard = 0L
    private var lastHostBytes = 0L

    /** Every byte the host has sent, in order: the input to the repaint gate's comparison. */
    val recorded = java.io.ByteArrayOutputStream()

    /** Datagrams we could not open or parse. On a loopback server this should stay at zero. */
    var protocolErrors = 0
        private set

    private val transport = MoshTransport(
        MoshCrypto.fromPrintableKey(lab.key),
        object : MoshTransport.Listener {
            override fun onHostBytes(bytes: ByteArray) {
                output.append(String(bytes, Charsets.ISO_8859_1))
                recorded.write(bytes)
                lastHostBytes = now()
            }
        },
    )

    private fun now(): Long = System.nanoTime() / 1_000_000

    val largestAssembly: Int get() = transport.largestAssembly
    val conflictingFragments: Int get() = transport.conflictingFragments

    fun type(text: String) = transport.type(text.toByteArray())

    /** Raw bytes, for keys a shell command cannot express — escape, arrows, control characters. */
    fun send(bytes: ByteArray) = transport.type(bytes)

    fun resize(width: Int, height: Int) = transport.resize(width, height)

    /** Replaces the socket, as a change of network would, and keeps everything else. */
    fun rebindLocalPort() {
        socket.close()
        socket = DatagramSocket()
    }

    /** The last of what the host sent, with the escapes made visible: for failure messages. */
    fun outputTail(characters: Int = 400): String =
        output.takeLast(characters).toString()
            .replace("\u001b", "<ESC>")
            .replace("\r", "<CR>")
            .replace("\n", "<LF>")

    fun awaitOutput(text: String, timeoutMs: Long): Boolean =
        pumpUntil(timeoutMs) { output.contains(text) }

    fun awaitLargeAssembly(minFragments: Int, timeoutMs: Long): Boolean =
        pumpUntil(timeoutMs) { transport.largestAssembly >= minFragments }

    fun heardFromServerWithin(timeoutMs: Long): Boolean {
        val before = lastHeard
        return pumpUntil(timeoutMs) { lastHeard > before }
    }

    fun idle(forMs: Long) {
        pumpUntil(forMs) { false }
    }

    /**
     * Pumps until the host has answered and then gone quiet for [quietMs], which is how a screen
     * is known to have finished being painted. There is no "done" in the protocol — a frame is
     * just the newest state — so silence is the only signal available, and it has to outlast the
     * 250 ms worst-case frame interval without waiting for the 3 s heartbeat.
     *
     * Waiting for the answer *first* is not belt and braces, it is the whole thing. This is called
     * straight after keystrokes are handed to the transport, and at that instant they have not
     * been sent: a tick schedules them 8 ms out so that a burst becomes one packet. So the screen
     * is already quiet, and a version of this that only waited for silence returned at once, every
     * time, without the keystrokes ever leaving — which is how the repaint gate's first run
     * recorded fifteen checkpoints of the same untouched prompt and called itself satisfied.
     */
    fun awaitQuiet(quietMs: Long = 600, timeoutMs: Long = 15_000): Boolean {
        val since = now()
        if (!pumpUntil(timeoutMs) { lastHostBytes > since }) return false
        pumpUntil(timeoutMs) { now() - lastHostBytes >= quietMs }
        return now() - lastHostBytes >= quietMs
    }

    fun shutdown(timeoutMs: Long): Boolean {
        transport.startShutdown(now())
        return pumpUntil(timeoutMs) { transport.shutdownAcknowledged || transport.shutdownTimedOut(now()) } &&
            transport.shutdownAcknowledged
    }

    private fun pumpUntil(timeoutMs: Long, done: () -> Boolean): Boolean {
        val deadline = now() + timeoutMs
        while (now() < deadline) {
            send(transport.tick(now()))
            if (done()) return true
            val wait = (transport.nextWakeup() - now()).coerceIn(1, 100)
            socket.soTimeout = wait.toInt()
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
                lastHeard = now()
                transport.onDatagram(packet.data.copyOf(packet.length), now())
                send(transport.tick(now()))
            } catch (_: SocketTimeoutException) {
                // Nothing arrived in time, which is ordinary: tick again and wait some more.
            } catch (_: MoshProtocolException) {
                // Someone else's packet, or a damaged one. Dropping it is the whole response —
                // but it is counted, because on loopback there is nobody else to blame.
                protocolErrors++
            }
            if (done()) return true
        }
        return done()
    }

    private fun send(datagrams: List<ByteArray>) {
        for (datagram in datagrams) {
            socket.send(DatagramPacket(datagram, datagram.size, address, port))
        }
    }

    override fun close() = socket.close()
}
