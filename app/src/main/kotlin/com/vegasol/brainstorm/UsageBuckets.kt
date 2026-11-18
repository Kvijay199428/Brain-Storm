package com.vegasol.brainstorm

import java.util.Calendar
import java.util.TimeZone

/** One contiguous piece of foreground time that falls inside a single clock hour. */
data class HourSlice(val hour: Int, val startMs: Long, val endMs: Long) {
    val millis: Long get() = endMs - startMs
}

/**
 * Attributes foreground time to the hour it was actually spent in.
 *
 * Android reports an app as "resumed at X, paused at Y". If that interval crosses an hour
 * boundary the whole span must not be booked into the starting hour, otherwise a session that
 * began at 10:58 would make 11:00 look idle. This is pure arithmetic with an explicit time zone,
 * so it is unit-tested off device; [UsageEngine] only feeds it intervals.
 */
object UsageBuckets {

    /**
     * Splits the half-open interval `[start, end)` at every clock-hour boundary in [zone]. Returns an empty list for
     * empty or inverted intervals, and never returns a zero-length slice.
     */
    fun slices(start: Long, end: Long, zone: TimeZone = TimeZone.getDefault()): List<HourSlice> {
        if (end <= start) return emptyList()
        val out = ArrayList<HourSlice>(2)
        var cursor = start
        while (cursor < end) {
            val stop = minOf(nextHourStart(cursor, zone), end)
            out.add(HourSlice(hourOf(cursor, zone), cursor, stop))
            cursor = stop
        }
        return out
    }

    /** Clock hour (0–23) that [ms] falls in. */
    fun hourOf(ms: Long, zone: TimeZone = TimeZone.getDefault()): Int =
        Calendar.getInstance(zone).apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    /** First millisecond of the hour after the one containing [ms]. */
    private fun nextHourStart(ms: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = ms
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.HOUR_OF_DAY, 1)
        }.timeInMillis
}
