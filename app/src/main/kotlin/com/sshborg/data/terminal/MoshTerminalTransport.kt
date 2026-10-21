package com.sshborg.data.terminal

import com.sshborg.data.ssh.MoshBootstrap
import com.sshborg.mosh.MoshCrypto
import com.sshborg.mosh.MoshProtocolException
import com.sshborg.mosh.MoshTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * The terminal's bytes over mosh: [MoshTransport] with a socket and a clock around it.
 *
 * Everything the protocol decides is in [MoshTransport], which owns no socket and no thread so
 * that it can be driven by a test with a clock it controls. This is the other half: the loop that
 * asks it what to send, puts it on the wire, waits for an answer or for the next deadline, and
 * hands what arrives back. There is nothing of Android in here either, which is what lets the
 * whole thing be gated from the JVM against a real `mosh-server`.
 *
 * ### Why the loop lives here and not in the screen's ViewModel
 *
 * [SshTerminalTransport] reads when somebody is reading, and that is enough: nothing is lost while
 * the terminal is in the background because TCP holds what arrives. Mosh has no such place to put
 * anything. The session exists only for as long as we keep answering — three seconds of heartbeat
 * at a time — so the loop has to outlive the screen, and in particular the ViewModel, which is
 * recreated by a mere switch between sibling tabs. It therefore runs in a scope this object owns,
 * started by [start] and ended by [close], which is to say for exactly as long as the session is
 * in the [com.sshborg.service.SessionManager]. That is also exactly as long as the foreground
 * service lives, so the process stays alive and the Wi-Fi radio stays out of power-save without
 * anything new being asked of either. See [survivesUnattended], which is the declaration of this.
 *
 * Because the loop runs whether or not anybody is reading, the bytes have to go somewhere when
 * nobody is: [unattendedSink], which is the session's own emulator. They are applied rather than
 * queued on purpose — a program left running in the background would otherwise pile up frames
 * without limit, where the emulator has a bounded scrollback and is the thing that will be shown
 * when the user comes back.
 *
 * ### What the socket is for
 *
 * Roaming, which is the whole feature, is the server's doing: it adopts whatever address it hears
 * an authentic packet from. The client's half is smaller but not nothing — it changes its own
 * local port when nothing has made it there and back for [MoshTransport.PORT_HOP_INTERVAL], and
 * when a send fails outright, which is what a Wi-Fi interface going away looks like from here. The
 * old sockets stay readable for [RETIRED_SOCKET_GRACE] afterwards, because an answer to what was
 * sent from them is still on its way.
 */
class MoshTerminalTransport(
    bootstrap: MoshBootstrap,
    /**
     * How long to keep trying while nothing at all comes back, before calling the session lost.
     *
     * Not the server's own patience, which is a different number set when it is started and is
     * deliberately much larger — a week, as mosh itself suggests, so that a server forgotten by a
     * killed app does not hold a shell for ever. This is ours, and it is a compromise with
     * something the app does not have yet: mosh's own client shows a "contact lost" line and goes
     * on trying indefinitely, and until there is somewhere to say that, a terminal that cannot be
     * reached is a photograph with nothing to explain it. So we stay patient for long enough to
     * cover what mosh is actually for — a tunnel, a lift, a change of network, a phone asleep —
     * and after that we say so rather than go on showing a screen that is no longer anybody's.
     */
    private val silenceLimitMs: Long = SILENCE_LIMIT,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    /** Where host bytes go while no [read] is attached: the session's own emulator. */
    private val unattendedSink: (ByteArray, Int) -> Unit,
) : TerminalTransport {

    /**
     * Where the server is. The port is the one it printed; the address is the one SSH used, which
     * is all the handover gives us and all the stock client uses either.
     *
     * Resolved here, so this must be constructed off the main thread — as the bootstrap that
     * precedes it already has to be, being an SSH round trip.
     *
     * One limit, inherited from the handover saying nothing about the address: `mosh-server -s`
     * binds to the address the SSH connection arrived on, so a host with several addresses whose
     * first one is not the one SSH ended up using would leave us talking to a port nobody is
     * listening on. It looks like a blocked path, which is reported, and the cure is to name the
     * address in the host instead of the name. The stock client avoids it by resolving the name
     * itself and then ssh-ing to the address; we cannot, because the SSH session is not ours to
     * re-dial by the time the question arises.
     */
    private val peer: InetSocketAddress = InetSocketAddress(bootstrap.host, bootstrap.port)

    private val mosh = MoshTransport(
        MoshCrypto.fromPrintableKey(bootstrap.key),
        object : MoshTransport.Listener {
            override fun onHostBytes(bytes: ByteArray) {
                // Handed over, not applied: see [toScreen].
                toScreen.trySend(bytes)
            }
            // The server telling us how big it thinks the terminal is. We are the ones who decide
            // that, and we have already told it, so there is nothing here to obey.
            override fun onHostResize(width: Int, height: Int) {}
        },
        mtu = if (peer.address is Inet4Address) MoshTransport.IPV4_MTU else MoshTransport.IPV6_MTU,
    )

    /**
     * Host bytes on their way to the screen, and deliberately not delivered on the loop's thread.
     *
     * That is not tidiness. Applying them means taking the emulator's lock, and the thing that
     * draws the screen holds that same lock for as long as a draw takes — tens of milliseconds on
     * a weak device, for a terminal a hundred columns wide. A loop blocked there is a loop that is
     * not acknowledging frames, and a server whose frame has not been acknowledged retransmits
     * instead of advancing. Measured on a LAN against a real server, with a draw of 20 ms every
     * 30: the median keystroke went from 17 ms to 31 ms and every other one to **270 ms**, with
     * the stale frames nearly doubled. Under SSH the same contention only slows a reader, which
     * TCP covers for; here it slows the protocol itself, which is the difference this whole class
     * exists to respect.
     *
     * One consumer, so the order the server sent them in is the order the screen gets them in.
     */
    private val toScreen = Channel<ByteArray>(Channel.UNLIMITED)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val selector: Selector = Selector.open()

    /** Our local ports, newest last. Only the newest sends; all of them are read. */
    private val sockets = ArrayList<LocalPort>()

    private class LocalPort(val channel: DatagramChannel) {
        /** When we stopped sending from it; [Long.MAX_VALUE] while it is the one in use. */
        var retiredAt = Long.MAX_VALUE
    }

    /**
     * What the user has done, waiting to be handed to the state machine.
     *
     * [write] and [resize] are called from whatever thread the screen is on; [MoshTransport] is
     * single-threaded by design and expects to be touched only by the loop. A queue plus a
     * [Selector.wakeup] is the whole of the handover, and it costs nothing in latency: a keystroke
     * is not sent the instant it is noticed anyway, but [MoshTransport.SEND_MINDELAY] later, so
     * that a burst of them becomes one packet.
     */
    private val pending = ConcurrentLinkedQueue<Event>()

    private sealed interface Event {
        class Typed(val bytes: ByteArray) : Event
        class Resized(val columns: Int, val rows: Int) : Event
    }

    private val ended = CompletableDeferred<TerminalTransport.End>()
    private val firstContact = CompletableDeferred<Boolean>()

    /** The live reader, if the terminal is on screen. Written by [read], read by the loop. */
    @Volatile
    private var attached: ((ByteArray, Int) -> Unit)? = null

    @Volatile
    private var started = false

    @Volatile
    private var closing = false

    @Volatile
    private var hopRequested = false

    private var lastHeard = 0L
    private var hops = 0
    private var protocolErrors = 0
    private var failedSends = 0

    /** How many times we have moved to a new local port. Diagnostic, and asserted on by the gate. */
    val localPortHops: Int get() = hops

    /** How many of our ports are still open: the one we send from, plus the retired ones. */
    val openLocalPorts: Int get() = sockets.size

    /** The smoothed round-trip estimate, which is what the frame rate is derived from. */
    val roundTripMs: Int get() = mosh.timing.srtt.toInt()

    /** See [MoshTransport.staleFrames]: the cost of holding one terminal instead of several. */
    val staleFrames: Int get() = mosh.staleFrames

    /**
     * Datagrams from the server's address that we could not open or parse. On a working session
     * this stays at zero; anything else is worth knowing about, and is in the error report.
     */
    val undecipherableDatagrams: Int get() = protocolErrors

    override val isConnected: Boolean get() = !ended.isCompleted

    override val survivesUnattended: Boolean get() = false

    /**
     * Opens the first socket and starts the loop. Idempotent; call it before [awaitFirstContact].
     *
     * Separate from the constructor because it has an effect on the network, and because the
     * caller wants the object in hand to close it if the handshake does not come off.
     */
    fun start() {
        if (started) return
        started = true
        openSocket()
        scope.launch {
            // Drains to the end: a channel that is closed hands out what is already in it first,
            // so the last frames of a session still reach the screen.
            for (bytes in toScreen) (attached ?: unattendedSink)(bytes, bytes.size)
        }
        scope.launch {
            val end = try {
                pump()
            } catch (e: Exception) {
                TerminalTransport.End.Lost(null, "mosh: the session loop stopped\n" + e.stackTraceToString())
            }
            tearDown()
            // After the sockets are gone, and in this order: a reader waiting on [ended] wakes up
            // to a transport that has already finished letting go of everything it held.
            firstContact.complete(false)
            ended.complete(end)
        }
    }

    /**
     * Waits for the first datagram to come back from the server, which is the only proof that
     * there is a path at all.
     *
     * There is no handshake in the protocol and no error from the far end for a blocked path: an
     * inbound UDP port the network will not carry looks exactly like a server that has not spoken
     * yet. So the one honest test is to send and wait, and false here is
     * [com.sshborg.data.ssh.MoshUnavailable.Reason.NoUdpAnswer] for the caller to report.
     */
    suspend fun awaitFirstContact(timeoutMs: Long = FIRST_CONTACT_TIMEOUT): Boolean =
        withTimeoutOrNull(timeoutMs) { firstContact.await() } ?: false

    /**
     * Sends from a new local port as soon as the loop comes round.
     *
     * The loop does this by itself when nothing has made it there and back for
     * [MoshTransport.PORT_HOP_INTERVAL], and when a send fails outright. This is for being *told*
     * instead of noticing: a network change is a thing Android announces, and acting on the
     * announcement is ten seconds faster than waiting to time out. The gate uses it to prove the
     * server follows us, which is the roaming the whole feature is for.
     */
    fun changeLocalPort() {
        if (closing || ended.isCompleted) return
        hopRequested = true
        selector.wakeup()
    }

    override fun write(data: ByteArray) {
        if (closing || ended.isCompleted) return
        pending.add(Event.Typed(data.copyOf()))
        selector.wakeup()
    }

    override fun resize(columns: Int, rows: Int) {
        if (closing || ended.isCompleted) return
        pending.add(Event.Resized(columns, rows))
        selector.wakeup()
    }

    override suspend fun read(onBytes: (ByteArray, Int) -> Unit): TerminalTransport.End {
        attached = onBytes
        try {
            return ended.await()
        } finally {
            // Only if nobody else has taken over in the meantime, which a tab switch does: it
            // starts the arriving screen's reader before the leaving one's job is cancelled.
            if (attached === onBytes) attached = null
        }
    }

    /**
     * Asks the server to go away, and stops the loop whether or not it answers.
     *
     * Saying goodbye is worth the moment it takes: without it the server detaches and sits there
     * until its own network timeout — minutes of a process, and of a terminal, for a session the
     * user has closed. It is not waited for, because this is called from the main thread while a
     * tab is being closed.
     */
    override fun close() {
        closing = true
        if (!started) {
            tearDown()
            ended.complete(TerminalTransport.End.CleanExit)
            return
        }
        selector.wakeup()
    }

    // ─────────────────────────── the loop ───────────────────────────

    private fun pump(): TerminalTransport.End {
        val buffer = ByteBuffer.allocateDirect(RECEIVE_BUFFER)
        var lastHopAt = clock()
        var hopWanted = false
        var closeDeadline = Long.MAX_VALUE
        var farewellDeadline = Long.MAX_VALUE
        val startedAt = clock()

        while (true) {
            var now = clock()
            drainPending()

            if (closing && !mosh.shutdownInProgress) {
                mosh.startShutdown(now)
                closeDeadline = now + CLOSE_GRACE
            }
            if (mosh.counterpartyShutdown && farewellDeadline == Long.MAX_VALUE) {
                // The server is going away — the shell exited, or it was told to. One more tick
                // gets our acknowledgement out so it can stop retrying and exit now rather than
                // on its timeout; then there is nothing left to be connected to.
                farewellDeadline = now + FAREWELL_GRACE
            }

            if (send(mosh.tick(now))) hopWanted = true

            if (mosh.shutdownAcknowledged) return TerminalTransport.End.CleanExit
            if (now >= closeDeadline) return TerminalTransport.End.CleanExit
            if (now >= farewellDeadline) return TerminalTransport.End.CleanExit
            val silentSince = if (lastHeard != 0L) lastHeard else startedAt
            if (now - silentSince > silenceLimitMs) return lost(now - silentSince)

            if (!closing && (hopWanted || hopRequested || mosh.shouldHopLocalPort(now, lastHopAt)) &&
                now - lastHopAt >= HOP_MIN_INTERVAL
            ) {
                hop(now)
                lastHopAt = now
                hopWanted = false
                hopRequested = false
            }
            pruneRetiredSockets(now)

            // Wait for an answer or for the state machine's next deadline, whichever is sooner. The
            // ceiling is there so that the clocks of the loop — giving up, hopping, saying goodbye
            // — are never further off than that, no matter how far away the protocol's next event.
            val wait = (mosh.nextWakeup() - now).coerceIn(1L, MAX_WAIT)
            selector.select(wait)
            now = clock()
            if (receive(buffer, now)) hopWanted = true
        }
    }

    private fun drainPending() {
        while (true) {
            when (val event = pending.poll() ?: return) {
                is Event.Typed -> mosh.type(event.bytes)
                is Event.Resized -> mosh.resize(event.columns, event.rows)
            }
        }
    }

    /** @return true if the socket should be replaced. */
    private fun send(datagrams: List<ByteArray>): Boolean {
        if (datagrams.isEmpty()) return false
        val socket = sockets.last().channel
        var broken = false
        for (datagram in datagrams) {
            try {
                socket.send(ByteBuffer.wrap(datagram), peer)
            } catch (e: IOException) {
                failedSends++
                if (e.message?.contains("too long", ignoreCase = true) == true) {
                    // EMSGSIZE: the path will not carry what we guessed it would. The datagram is
                    // dropped and the state it carried is retransmitted, smaller, by the next tick.
                    mosh.useFallbackMtu()
                } else {
                    // No route, or the interface this socket was bound to has gone: which is what
                    // turning the Wi-Fi off looks like from here, and is cured by a new socket on
                    // whatever network the phone has now rather than by ending the session.
                    broken = true
                }
            }
        }
        return broken
    }

    /** @return true if the socket should be replaced. */
    private fun receive(buffer: ByteBuffer, now: Long): Boolean {
        val keys = selector.selectedKeys()
        if (keys.isEmpty()) return false
        val iterator = keys.iterator()
        var broken = false
        while (iterator.hasNext()) {
            val key = iterator.next()
            iterator.remove()
            val channel = key.channel() as DatagramChannel
            while (true) {
                buffer.clear()
                val from: SocketAddress? = try {
                    channel.receive(buffer)
                } catch (e: IOException) {
                    // A socket that cannot even be read from is finished; if it is the one we send
                    // from, the cure is the same as for a failed send.
                    if (channel === sockets.last().channel) broken = true
                    key.cancel()
                    break
                }
                if (from == null) break
                // Not from the server: dropped without a word. The crypto would refuse it anyway,
                // but counting it as a protocol error would blame the server for somebody else.
                if (from != peer) continue
                buffer.flip()
                val datagram = ByteArray(buffer.remaining())
                buffer.get(datagram)
                lastHeard = now
                firstContact.complete(true)
                try {
                    mosh.onDatagram(datagram, now)
                } catch (_: MoshProtocolException) {
                    // Someone else's packet, a damaged one, or our own played back at us. Dropping
                    // it is the whole response — anyone can send us a datagram — but it is counted,
                    // because a session where this is not zero is worth knowing about.
                    protocolErrors++
                }
                if (send(mosh.tick(now))) broken = true
            }
        }
        return broken
    }

    // ─────────────────────────── the sockets ───────────────────────────

    private fun openSocket(): LocalPort {
        val channel = DatagramChannel.open(
            if (peer.address is Inet4Address) java.net.StandardProtocolFamily.INET
            else java.net.StandardProtocolFamily.INET6
        )
        channel.configureBlocking(false)
        channel.bind(null)
        channel.register(selector, SelectionKey.OP_READ)
        return LocalPort(channel).also { sockets.add(it) }
    }

    /**
     * Sends from a new local port from now on.
     *
     * The old one is kept, readable, for [RETIRED_SOCKET_GRACE]: the server does not know about the
     * new one until a packet from it arrives, so what it is sending now is still addressed to the
     * old one, and throwing those away would mean losing a frame for no reason.
     */
    private fun hop(now: Long) {
        sockets.last().retiredAt = now
        openSocket()
        hops++
    }

    private fun pruneRetiredSockets(now: Long) {
        if (sockets.size <= 1) return
        val stale = sockets.filter { it.retiredAt != Long.MAX_VALUE && now - it.retiredAt > RETIRED_SOCKET_GRACE }
        // And a ceiling whatever the clock says, as mosh has: a session hopping faster than the
        // grace period must not be able to accumulate descriptors.
        val excess = (sockets.size - MAX_SOCKETS).coerceAtLeast(0)
        val done = (stale + sockets.take(excess)).distinct().filter { it !== sockets.last() }
        for (port in done) {
            runCatching { port.channel.close() }
            sockets.remove(port)
        }
    }

    private fun tearDown() {
        toScreen.close()
        for (port in sockets) runCatching { port.channel.close() }
        sockets.clear()
        runCatching { selector.close() }
    }

    /**
     * @param silentFor how long nothing has been heard, which is the only symptom there is. There
     *   is no summary: only the caller can reach the string resources, and it has a generic line
     *   for exactly this. The detail is what somebody could paste into a bug report — and carries
     *   no key, no address and no host name.
     */
    private fun lost(silentFor: Long): TerminalTransport.End.Lost = TerminalTransport.End.Lost(
        summary = null,
        detail = "mosh: nothing heard for ${silentFor / 1000}s, which is as long as we wait\n" +
            "round trip ~${mosh.timing.srtt.toInt()}ms, local port hops $hops, sends refused " +
            "$failedSends, undecipherable datagrams $protocolErrors",
    )

    private companion object {
        /** Enough for any datagram the protocol can produce, with room to see that it is too big. */
        const val RECEIVE_BUFFER = 4096

        /** The ceiling on how long the loop sleeps, so its own clocks stay roughly honest. */
        const val MAX_WAIT = 250L

        /** How long a session with no answer at all is given before it is called a failure. */
        const val FIRST_CONTACT_TIMEOUT = 10_000L

        /** How long a session hears nothing before it is called lost. See [silenceLimitMs]. */
        const val SILENCE_LIMIT = 30 * 60 * 1000L

        /** How long [close] waits for the server to acknowledge the goodbye. */
        const val CLOSE_GRACE = 1_000L

        /** How long to keep answering a server that has said it is going away. */
        const val FAREWELL_GRACE = 300L

        /** Never hop twice inside this, however many sends fail: hopping is not a cure for loss. */
        const val HOP_MIN_INTERVAL = 1_000L

        /** How long a port we no longer send from is still worth listening on. */
        const val RETIRED_SOCKET_GRACE = 60_000L

        /** Mosh's own ceiling (`network.h`: MAX_PORTS_OPEN), for the same reason. */
        const val MAX_SOCKETS = 9
    }
}
