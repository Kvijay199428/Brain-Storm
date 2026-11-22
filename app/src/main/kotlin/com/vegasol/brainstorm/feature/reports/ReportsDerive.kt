package com.vegasol.brainstorm.feature.reports

import com.vegasol.brainstorm.DailyUsage

/** One app in the all-time ranking, aggregated across every day in the usage log. */
data class LifetimeRank(val packageName: String, val label: String, val seconds: Long)

/**
 * Pure derivations for whole-device reports. No Android or database types beyond the row itself, so
 * the ranking and the numbers the screen prints are unit-tested directly.
 */
object ReportsDerive {

    /**
     * Today's ranking, busiest first, ties broken by label. Zero-time rows are dropped: a monitored
     * app that was never opened should not take a rank above an app that was.
     */
    fun ranking(rows: List<DailyUsage>, date: String): List<DailyUsage> =
        rows.asSequence()
            .filter { it.usageDate == date && it.foregroundSeconds > 0 }
            .groupBy { it.packageName }
            .mapNotNull { (_, list) -> list.maxByOrNull { it.foregroundSeconds } }
            .sortedWith(
                compareByDescending<DailyUsage> { it.foregroundSeconds }
                    .thenBy { it.appName.ifBlank { it.packageName }.lowercase() }
            )

    fun top(rows: List<DailyUsage>, date: String): DailyUsage? = ranking(rows, date).firstOrNull()

    /** Whole-device total for one day. */
    fun totalFor(rows: List<DailyUsage>, date: String): Long =
        rows.sumOf { if (it.usageDate == date) it.foregroundSeconds else 0L }

    /** Whole-device totals per day, keyed like `usageDate`. */
    fun totalsByDay(rows: List<DailyUsage>): Map<String, Long> =
        rows.groupBy { it.usageDate }.mapValues { e -> e.value.sumOf { it.foregroundSeconds } }

    /** Rounded share of the device total, 0..100; 0 when there is no total to divide by. */
    fun sharePercent(partSeconds: Long, totalSeconds: Long): Int {
        if (partSeconds <= 0L || totalSeconds <= 0L) return 0
        val pct = Math.round(partSeconds * 100.0 / totalSeconds).toInt()
        return pct.coerceIn(0, 100)
    }

    /**
     * All-time ranking: sum every recorded day per package, busiest first. Zero-time apps are
     * dropped and ties are alphabetical so a row never shifts for no reason. The longest label
     * seen for a package wins, since an older row may predate a persisted app name.
     */
    fun lifetimeRanking(rows: List<DailyUsage>): List<LifetimeRank> {
        val totals = LinkedHashMap<String, Long>()
        val labels = HashMap<String, String>()
        rows.forEach { row ->
            totals[row.packageName] = (totals[row.packageName] ?: 0L) + row.foregroundSeconds
            val known = labels[row.packageName].orEmpty()
            if (row.appName.length > known.length) labels[row.packageName] = row.appName
        }
        return totals
            .map { (pkg, seconds) -> LifetimeRank(pkg, labels[pkg]?.takeIf { it.isNotBlank() } ?: pkg, seconds) }
            .filter { it.seconds > 0L }
            .sortedWith(compareByDescending<LifetimeRank> { it.seconds }.thenBy { it.label.lowercase() })
    }

    fun lifetimeTotal(ranking: List<LifetimeRank>): Long = ranking.sumOf { it.seconds }
}
