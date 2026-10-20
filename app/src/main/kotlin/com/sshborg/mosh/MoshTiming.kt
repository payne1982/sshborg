package com.sshborg.mosh

import kotlin.math.abs
import kotlin.math.ceil

/**
 * The round-trip estimate, and the two sixteen-bit timestamps every packet carries.
 *
 * Mosh measures the round trip with the timestamps inside the encryption rather than with the
 * sequence numbers, which is what lets it keep measuring across a change of address. The estimator
 * is the ordinary Jacobson one, with mosh's own starting values: a pessimistic first guess that
 * the first real measurement replaces outright.
 *
 * The reply field is the part that is easy to get wrong. It is not the timestamp we received: it
 * is that timestamp **plus the time we sat on it**, so the far end's subtraction measures the
 * network and not our own scheduling delay. It is also used once — after it has been put in a
 * packet it is cleared, and a packet with nothing to report carries [NONE].
 */
class MoshTiming {

    var srtt = 1000.0
        private set
    var rttvar = 500.0
        private set

    private var measured = false
    private var savedTimestamp = NONE
    private var savedAt = 0L

    /** Retransmission timeout: how long to wait before assuming a state did not arrive. */
    fun rto(): Long = ceil(srtt + 4 * rttvar).toLong().coerceIn(MIN_RTO, MAX_RTO)

    /** The gap between frames we are willing to send: half the round trip, within reason. */
    fun sendInterval(): Long = ceil(srtt / 2.0).toLong().coerceIn(SEND_INTERVAL_MIN, SEND_INTERVAL_MAX)

    /** For every packet accepted as fresh: their clock to echo back, and our measurement. */
    fun onPacketReceived(theirTimestamp: Int, theirReply: Int, now: Long) {
        if (theirTimestamp != NONE) {
            savedTimestamp = theirTimestamp
            savedAt = now
        }
        if (theirReply == NONE) return

        val measurement = diff16(stamp(now), theirReply).toDouble()
        // A reply from before a suspend, or from a process that was stopped, says nothing about the
        // network: mosh throws away anything implausible rather than let it poison the estimate.
        if (measurement >= IMPLAUSIBLE_RTT) return

        if (!measured) {
            srtt = measurement
            rttvar = measurement / 2
            measured = true
        } else {
            rttvar = (1 - BETA) * rttvar + BETA * abs(srtt - measurement)
            srtt = (1 - ALPHA) * srtt + ALPHA * measurement
        }
    }

    /**
     * What to put in the reply field of a packet going out now, consuming it: a second packet in
     * the same burst carries [NONE], as mosh's does.
     */
    fun timestampReplyFor(now: Long): Int {
        if (savedTimestamp == NONE || now - savedAt >= HELD_TOO_LONG) return NONE
        val reply = (savedTimestamp + (now - savedAt).toInt()) and 0xFFFF
        savedTimestamp = NONE
        savedAt = 0
        return reply
    }

    companion object {
        /** Sixteen bits of ones: "nothing to report". */
        const val NONE = MoshPacket.NONE

        const val SEND_INTERVAL_MIN = 20L
        const val SEND_INTERVAL_MAX = 250L
        const val MIN_RTO = 50L
        const val MAX_RTO = 1000L

        private const val ALPHA = 1.0 / 8.0
        private const val BETA = 1.0 / 4.0
        private const val IMPLAUSIBLE_RTT = 5000
        private const val HELD_TOO_LONG = 1000

        /** Our millisecond clock as it goes on the wire. */
        fun stamp(now: Long): Int = (now and 0xFFFF).toInt()

        /** The gap between two wire timestamps, taking the sixteen-bit wrap into account. */
        fun diff16(newer: Int, older: Int): Int = (newer - older) and 0xFFFF
    }
}
