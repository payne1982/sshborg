package com.sshborg.mosh

import java.security.SecureRandom

/**
 * The transport state machine: what to send, when to send it, and what to do with what arrives.
 * Phase 4 of `docs/mosh-plan.md`.
 *
 * It owns no socket and starts no thread. Every entry point takes the current time and [tick]
 * hands back the datagrams to put on the wire, so the whole of the protocol's timing can be driven
 * by a test with a clock it controls — which is the only honest way to check retransmission and
 * heartbeats, and the reason this class has no `Thread.sleep` and no `System.currentTimeMillis`
 * anywhere in it. The socket, the port hopping and the service lifecycle are phase 6's business.
 *
 * ### What the client has to keep
 *
 * Upward, a list of states we have sent (a state being a count of user actions, see
 * [MoshUserStream]) and the newest one we believe the server holds. Downward, **one** state only:
 * the live terminal. Mosh's own client keeps a queue of terminal snapshots so that it can apply a
 * diff computed against an older one; we deliberately do not, because our terminal is a live
 * emulator and copying it per frame is not a trade we want. An instruction whose `old_num` is not
 * the state we hold is therefore dropped, unacknowledged, and the server — which diffs from what
 * we last acknowledged — sends one we can use. The cost is an occasional extra retransmission; the
 * gain is that the screen has exactly one copy.
 */
class MoshTransport(
    private val crypto: MoshCrypto,
    private val listener: Listener,
    private val mtu: Int = DEFAULT_MTU,
    private val random: SecureRandom = SecureRandom(),
) {

    interface Listener {
        /**
         * Terminal output to apply. The emulator may answer it (device status and identification
         * replies); that answer belongs in [type], not in a discarded buffer.
         */
        fun onHostBytes(bytes: ByteArray)

        fun onHostResize(width: Int, height: Int) {}

        /** Predictive echo only; there is no prediction engine yet, so this is for the record. */
        fun onEchoAck(number: Long) {}
    }

    private class SentState(var timestamp: Long, val number: Long, val actionCount: Long)

    private val sequence = MoshSequence()
    private val replay = MoshReplayFilter(weAreClient = true)
    private val assembly = MoshFragmentAssembly()
    private val fragmenter = MoshFragmenter()
    private val user = MoshUserStream()

    /** Exposed because the round-trip estimate is the most useful thing to show in a diagnostic. */
    val timing = MoshTiming()

    private val sent = ArrayList<SentState>().apply { add(SentState(0, 0, 0)) }
    private var assumedReceiver = 0

    private var ackNumber = 0L
    private var heldState = 0L
    private var pendingDataAck = false

    private var lastHeard = 0L
    private var lastRoundtrip = 0L
    private var nextAckTime = 0L
    private var nextSendTime = NEVER
    private var mindelayClock = NEVER

    private var shutdownStart = NEVER
    private var shutdownTries = 0

    var shutdownInProgress = false
        private set

    /** True once the server has acknowledged our shutdown, or said it is going away itself. */
    var shutdownAcknowledged = false
        private set

    var counterpartyShutdown = false
        private set

    // ─────────────────────────── what the user does ───────────────────────────

    fun type(bytes: ByteArray) = user.type(bytes)

    fun resize(width: Int, height: Int) = user.resize(width, height)

    /**
     * Begin the closing handshake: from here on every packet claims the shutdown state number, and
     * the session is over when the server acknowledges it or [shutdownTimedOut] gives up on it.
     */
    fun startShutdown(now: Long) {
        if (shutdownInProgress) return
        shutdownInProgress = true
        shutdownStart = now
    }

    fun shutdownTimedOut(now: Long): Boolean =
        shutdownInProgress && (shutdownTries >= SHUTDOWN_RETRIES || now - shutdownStart >= ACTIVE_RETRY_TIMEOUT)

    // ─────────────────────────── the clock ───────────────────────────

    /**
     * When [tick] next has something to do, so an I/O loop can wait rather than spin.
     *
     * The contract, which mosh's own main loop follows too: **tick after every event** — a
     * keystroke, a resize, an arriving datagram — and again when this time comes. A tick is what
     * schedules work as well as what performs it, so the tick that first notices a keystroke
     * computes when it may go out (never sooner than [SEND_MINDELAY], so that a burst of keys
     * becomes one packet) and the tick at that moment is the one that sends it.
     */
    fun nextWakeup(): Long = minOf(nextAckTime, nextSendTime)

    /**
     * The datagrams to send now, if any.
     *
     * Three things can make a packet: a diff we have not sent, an acknowledgement we owe (within
     * [ACK_DELAY] of hearing something), and the [ACK_INTERVAL] heartbeat that tells the server we
     * are still here. The heartbeat is also what the battery note in the plan is about: three
     * seconds is mosh's figure, not ours.
     */
    fun tick(now: Long): List<ByteArray> {
        calculateTimers(now)
        if (now < nextAckTime && now < nextSendTime) return emptyList()

        val diff = user.diffFrom(sent[assumedReceiver].actionCount)
        return when {
            diff.isEmpty() && now >= nextAckTime -> sendEmptyAck(now)
            diff.isEmpty() -> {
                if (now >= nextSendTime) {
                    nextSendTime = NEVER
                    mindelayClock = NEVER
                }
                emptyList()
            }
            now >= nextSendTime || now >= nextAckTime -> sendDiff(diff, now)
            else -> emptyList()
        }
    }

    /**
     * True when nothing has made it there and back for [PORT_HOP_INTERVAL]: the cure is a new
     * local port, because the usual reason is a NAT binding that has gone stale. Only the client
     * does this — the server adopts whatever address it hears from, which is the roaming.
     */
    fun shouldHopLocalPort(now: Long, lastHopAt: Long): Boolean =
        now - lastRoundtrip > PORT_HOP_INTERVAL && now - lastHopAt > PORT_HOP_INTERVAL

    // ─────────────────────────── what arrives ───────────────────────────

    /**
     * @throws MoshProtocolException if the datagram is not ours — a failed integrity check, our own
     *   traffic played back, or a malformed instruction. The caller drops it and carries on; it is
     *   not a reason to end the session, because anyone can send us a packet.
     */
    fun onDatagram(datagram: ByteArray, now: Long) {
        val opened = crypto.open(datagram)
        val fresh = replay.accept(opened.nonce)
        val packet = MoshPacket.decode(opened.plaintext)

        if (fresh) {
            timing.onPacketReceived(packet.timestamp, packet.timestampReply, now)
            lastHeard = now
        }

        val payload = assembly.add(MoshFragment.decode(packet.payload)) ?: return
        val instruction = MoshInstruction.decode(payload)

        processAcknowledgement(instruction.ackNumber, now)

        if (instruction.newNumber == SHUTDOWN_NUMBER) {
            // The far end is going away. Acknowledge it so it can stop retrying, and stop
            // pretending we hold its states.
            counterpartyShutdown = true
            ackNumber = SHUTDOWN_NUMBER
            pendingDataAck = true
            return
        }
        // Already applied: the duplicate of a frame we have is not an error, it is a retransmission.
        if (instruction.newNumber == heldState) return
        // We keep one state, the live terminal. A diff against anything else is unusable, and
        // applying it would corrupt the screen — but the server is sending it because it does not
        // yet know which state we hold, and it will go on sending it at frame rate until it does.
        // So the frame is dropped *and* an acknowledgement is scheduled straight away: in the lab
        // that turned a 1.3-second stall at the start of every session, during which the prompt
        // was being retransmitted and thrown away, into one round trip.
        if (instruction.oldNumber != heldState) {
            pendingDataAck = true
            return
        }

        if (instruction.diff.isNotEmpty()) {
            for (action in MoshDiffs.decodeHostDiff(instruction.diff)) {
                when (action) {
                    is MoshDiffs.HostAction.HostBytes -> listener.onHostBytes(action.bytes)
                    is MoshDiffs.HostAction.Resize -> listener.onHostResize(action.width, action.height)
                    is MoshDiffs.HostAction.EchoAck -> listener.onEchoAck(action.number)
                }
            }
            pendingDataAck = true
        }
        heldState = instruction.newNumber
        ackNumber = instruction.newNumber
    }

    // ─────────────────────────── the sender ───────────────────────────

    private fun calculateTimers(now: Long) {
        updateAssumedReceiver(now)
        user.dropThrough(sent.first().actionCount)

        if (pendingDataAck && nextAckTime > now + ACK_DELAY) nextAckTime = now + ACK_DELAY

        val last = sent.last()
        val active = lastHeard + ACTIVE_RETRY_TIMEOUT > now
        nextSendTime = when {
            // Something typed that has not gone out yet: as soon as the frame rate allows, but
            // never within SEND_MINDELAY of noticing it, so a burst of keys becomes one packet.
            user.count != last.actionCount -> {
                if (mindelayClock == NEVER) mindelayClock = now
                maxOf(mindelayClock + SEND_MINDELAY, last.timestamp + timing.sendInterval())
            }
            // Sent, not acknowledged, and the session is alive: keep offering it at frame rate.
            user.count != sent[assumedReceiver].actionCount && active ->
                maxOf(last.timestamp + timing.sendInterval(), if (mindelayClock == NEVER) 0 else mindelayClock + SEND_MINDELAY)
            // Something older is still unacknowledged: retransmit on the round-trip timeout.
            user.count != sent.first().actionCount && active -> last.timestamp + timing.rto() + ACK_DELAY
            else -> NEVER
        }

        if (shutdownInProgress || ackNumber == SHUTDOWN_NUMBER) {
            nextAckTime = last.timestamp + timing.sendInterval()
        }
    }

    /** Gives recently sent states the benefit of the doubt, as mosh does, instead of assuming loss. */
    private fun updateAssumedReceiver(now: Long) {
        assumedReceiver = 0
        val window = timing.rto() + ACK_DELAY
        for (i in 1 until sent.size) {
            if (now - sent[i].timestamp < window) assumedReceiver = i else return
        }
    }

    private fun sendDiff(diff: ByteArray, now: Long): List<ByteArray> {
        val last = sent.last()
        val number = when {
            shutdownInProgress -> SHUTDOWN_NUMBER
            user.count == last.actionCount -> last.number
            else -> last.number + 1
        }
        if (number == last.number) last.timestamp = now else addSentState(SentState(now, number, user.count))

        val datagrams = sendInFragments(diff, number, now)
        assumedReceiver = sent.lastIndex
        nextAckTime = now + ACK_INTERVAL
        nextSendTime = NEVER
        mindelayClock = NEVER
        return datagrams
    }

    private fun sendEmptyAck(now: Long): List<ByteArray> {
        val number = if (shutdownInProgress) SHUTDOWN_NUMBER else sent.last().number + 1
        addSentState(SentState(now, number, user.count))
        val datagrams = sendInFragments(EMPTY, number, now)
        nextAckTime = now + ACK_INTERVAL
        nextSendTime = NEVER
        return datagrams
    }

    private fun sendInFragments(diff: ByteArray, number: Long, now: Long): List<ByteArray> {
        val instruction = MoshInstruction(
            oldNumber = sent[assumedReceiver].number,
            newNumber = number,
            ackNumber = ackNumber,
            throwawayNumber = sent.first().number,
            diff = diff,
            chaff = chaff(),
        )
        if (number == SHUTDOWN_NUMBER) shutdownTries++
        pendingDataAck = false

        // The budget is what is left of the path MTU once the nonce, the timestamps and the tag
        // have taken their share; the fragment header comes out of it inside the fragmenter.
        val budget = mtu - NONCE_AND_TIMESTAMPS - MoshCrypto.TAG_BYTES
        return fragmenter.fragments(instruction, budget).map { fragment ->
            val packet = MoshPacket(
                timestamp = MoshTiming.stamp(now),
                // Only the first datagram of a burst carries the reply: it is consumed when used.
                timestampReply = timing.timestampReplyFor(now),
                payload = fragment.encode(),
            )
            crypto.seal(MoshNonce(sequence.next(), toClient = false), packet.encode())
        }
    }

    private fun addSentState(state: SentState) {
        sent.add(state)
        // Mosh's ceiling, and its way of keeping it: drop from the middle, where a gap costs one
        // retransmission, rather than the oldest, which everything else is still measured against.
        if (sent.size > MAX_SENT_STATES) sent.removeAt(sent.size - 16)
    }

    private fun processAcknowledgement(ack: Long, now: Long) {
        if (sent.none { it.number == ack }) return   // acknowledging something we have already freed
        val before = sent.size
        sent.removeAll { java.lang.Long.compareUnsigned(it.number, ack) < 0 }
        if (before != sent.size || ack == sent.first().number) lastRoundtrip = now
        if (ack == SHUTDOWN_NUMBER) shutdownAcknowledged = true
        assumedReceiver = assumedReceiver.coerceAtMost(sent.lastIndex)
    }

    private fun chaff(): ByteArray {
        // Random padding, 0 to 16 bytes. It is not decoration: without it the length of a datagram
        // tells an observer how much was typed, which is the sort of thing a keystroke-timing
        // attack is built on.
        val length = random.nextInt(CHAFF_MAX + 1)
        return if (length == 0) EMPTY else ByteArray(length).also { random.nextBytes(it) }
    }

    companion object {
        /** Mosh's starting MTU for both address families; it falls back to 500 on EMSGSIZE. */
        const val DEFAULT_MTU = 1280
        const val FALLBACK_MTU = 500

        const val SEND_MINDELAY = 8L
        const val ACK_DELAY = 100L
        const val ACK_INTERVAL = 3_000L
        const val ACTIVE_RETRY_TIMEOUT = 10_000L
        const val PORT_HOP_INTERVAL = 10_000L
        const val SHUTDOWN_RETRIES = 16

        /** The state number that means "I am going away": all ones, as an unsigned 64-bit value. */
        const val SHUTDOWN_NUMBER = -1L

        private const val MAX_SENT_STATES = 32
        private const val CHAFF_MAX = 16
        private const val NONCE_AND_TIMESTAMPS = MoshCrypto.NONCE_WIRE_BYTES + 4
        private const val NEVER = Long.MAX_VALUE
        private val EMPTY = ByteArray(0)
    }
}
