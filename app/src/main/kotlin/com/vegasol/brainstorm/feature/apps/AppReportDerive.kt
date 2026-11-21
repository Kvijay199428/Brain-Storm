package com.vegasol.brainstorm.feature.apps

/**
 * Pure derivations for the per-app report. No Android or database types, so the numbers the screen
 * claims (peak hour, best day, average) are unit-tested directly.
 */
object AppReportDerive {

    /** Index of the clock hour with the most foreground time, or null when nothing was recorded. */
    fun peakHour(hours: List<Long>): Int? {
        var best: Int? = null
        hours.forEachIndexed { hour, seconds ->
            if (seconds > 0 && (best == null || seconds > hours[best!!])) best = hour
        }
        return best
    }

    /** Day with the most foreground time, or null when nothing was recorded. Ties keep the first. */
    fun peakDay(days: List<Pair<String, Long>>): Pair<String, Long>? =
        days.filter { it.second > 0 }.maxByOrNull { it.second }

    fun averageSeconds(seconds: List<Long>): Long =
        if (seconds.isEmpty()) 0L else seconds.sum() / seconds.size

    /** Days that crossed [minSeconds]; one-minute noise does not count as "used". */
    fun activeDays(days: List<Pair<String, Long>>, minSeconds: Long = 60L): Int =
        days.count { it.second >= minSeconds }

    /** "21:00-22:00" for hour 21; wraps 23 to midnight. */
    fun hourLabel(hour: Int): String {
        val h = hour.coerceIn(0, 23)
        return "%02d:00-%02d:00".format(h, (h + 1) % 24)
    }
}
