package com.vegasol.brainstorm.feature.apps

import com.vegasol.brainstorm.DailyUsage
import com.vegasol.brainstorm.MonitoredApp

/** One app in the Apps-tab order, with its rank (1 = used most today) and today's foreground time. */
data class RankedApp(val app: MonitoredApp, val usedSeconds: Long, val rank: Int)

/**
 * Pure derivations for the per-app report and the Apps-tab ranking. No Android or Compose types, so
 * the numbers the screens claim (peak hour, best day, average, today's order) are unit-tested directly.
 */
object AppReportDerive {

    /**
     * Today's Apps-tab order: busiest app first. Apps with no recorded time keep their place in the
     * list but sink below the used ones; ties are alphabetical so the order never jumps around.
     */
    fun rankByTodayUsage(apps: List<MonitoredApp>, usage: List<DailyUsage>): List<RankedApp> {
        val seconds = HashMap<String, Long>()
        usage.forEach { row ->
            seconds[row.packageName] = maxOf(seconds[row.packageName] ?: 0L, row.foregroundSeconds)
        }
        return apps
            .sortedWith(
                compareByDescending<MonitoredApp> { a -> seconds[a.packageName] ?: 0L }
                    .thenBy { it.appName.lowercase() }
            )
            .mapIndexed { index, app ->
                RankedApp(app, seconds[app.packageName] ?: 0L, index + 1)
            }
    }


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
