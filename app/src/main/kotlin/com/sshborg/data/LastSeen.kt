package com.sshborg.data

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How long ago something happened, in the unit a person would say it in.
 *
 * Deciding *that* here and the wording elsewhere keeps the arithmetic — which has the traps — on the
 * JVM where it can be tested, and leaves the plural rules to the ten `strings.xml` that already know
 * them. Nothing in here knows a language.
 */
sealed interface LastSeen {

    /** Under a minute: no number is worth printing. */
    data object JustNow : LastSeen

    /** 1..59 minutes. */
    data class Minutes(val count: Int) : LastSeen

    /** 1..23 hours. */
    data class Hours(val count: Int) : LastSeen

    /** The calendar day before this one, however many hours back that is. */
    data object Yesterday : LastSeen

    /** 2..6 calendar days. */
    data class Days(val count: Int) : LastSeen

    /** A week or more: say the date instead, a count that large tells nobody anything. */
    data object Earlier : LastSeen
}

/**
 * Which of those [then] is, seen from [now].
 *
 * Two things are deliberate.
 *
 * **The day buckets count calendar days, not 24-hour blocks**, because "yesterday" is a thing people
 * say about a date and not about an interval: at half past midnight, something from eleven last
 * night is yesterday, and saying "1 day ago" of it would be wrong in both directions. Below a day
 * the hours are used anyway — "three hours ago" is more use at 01:00 than "yesterday" — so the
 * calendar only decides where it is the only honest answer. [zone] is the one the dates are read in;
 * the device's own, unless a test says otherwise.
 *
 * **A timestamp in the future is [LastSeen.JustNow]**, not a negative count. It happens for real: a
 * backup restored from a device whose clock was ahead, or a clock corrected backwards between the
 * connection and the list being drawn. There is no sensible number to print, and a minus sign in
 * front of a plural is how that would otherwise come out.
 */
fun lastSeen(
    then: Long,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): LastSeen {
    val elapsed = now - then
    if (elapsed < MINUTE) return LastSeen.JustNow
    if (elapsed < HOUR) return LastSeen.Minutes((elapsed / MINUTE).toInt())
    if (elapsed < DAY) return LastSeen.Hours((elapsed / HOUR).toInt())

    val days = ChronoUnit.DAYS.between(
        Instant.ofEpochMilli(then).atZone(zone).toLocalDate(),
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate(),
    )
    return when {
        days <= 1L -> LastSeen.Yesterday
        days < 7L  -> LastSeen.Days(days.toInt())
        else       -> LastSeen.Earlier
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
