package com.vegasol.brainstorm.feature.reports

import com.vegasol.brainstorm.DailyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportsDeriveTest {

    private fun row(pkg: String, seconds: Long, name: String = "", date: String = "2026-11-22") =
        DailyUsage(packageName = pkg, usageDate = date, foregroundSeconds = seconds, appName = name)

    @Test
    fun `ranking is busiest first and drops days it was not asked about`() {
        val rows = listOf(
            row("a", 60),
            row("b", 600),
            row("c", 30, date = "2026-11-21")
        )
        assertEquals(listOf("b", "a"), ReportsDerive.ranking(rows, "2026-11-22").map { it.packageName })
    }

    @Test
    fun `zero-time apps do not take a rank`() {
        val rows = listOf(row("a", 0), row("b", 120))
        assertEquals(listOf("b"), ReportsDerive.ranking(rows, "2026-11-22").map { it.packageName })
        assertNull(ReportsDerive.top(listOf(row("a", 0)), "2026-11-22"))
    }

    @Test
    fun `ties are broken by label so the order is stable`() {
        val rows = listOf(row("z", 300, "Zebra"), row("a", 300, "Alpha"))
        assertEquals(listOf("Alpha", "Zebra"), ReportsDerive.ranking(rows, "2026-11-22").map { it.appName })
    }

    @Test
    fun `duplicate rows for one package keep the larger total`() {
        val rows = listOf(row("a", 100), row("a", 400))
        assertEquals(400L, ReportsDerive.ranking(rows, "2026-11-22").single().foregroundSeconds)
    }

    @Test
    fun `device total sums every included app for that day only`() {
        val rows = listOf(row("a", 60), row("b", 60), row("c", 900, date = "2026-11-21"))
        assertEquals(120L, ReportsDerive.totalFor(rows, "2026-11-22"))
        assertEquals(
            mapOf("2026-11-22" to 120L, "2026-11-21" to 900L),
            ReportsDerive.totalsByDay(rows)
        )
    }

    @Test
    fun `share is rounded and clamped`() {
        assertEquals(0, ReportsDerive.sharePercent(0, 100))
        assertEquals(0, ReportsDerive.sharePercent(10, 0))
        assertEquals(67, ReportsDerive.sharePercent(2, 3))
        assertEquals(100, ReportsDerive.sharePercent(300, 100))
    }

    @Test
    fun `lifetime ranking sums every day and drops zero-time apps`() {
        val rows = listOf(
            row("a", 60, "Alpha", "2026-11-21"),
            row("a", 240, "Alpha", "2026-11-22"),
            row("b", 100, "Beta", "2026-11-22"),
            row("c", 0, "Charlie", "2026-11-22")
        )
        val ranking = ReportsDerive.lifetimeRanking(rows)
        assertEquals(listOf("a", "b"), ranking.map { it.packageName })
        assertEquals(300L, ranking.first().seconds)
        assertEquals(400L, ReportsDerive.lifetimeTotal(ranking))
    }

    @Test
    fun `lifetime ties are alphabetical and labels fall back to the package`() {
        val rows = listOf(row("z", 300, "Zebra"), row("a", 300, "Alpha"), row("p", 30))
        assertEquals(
            listOf("Alpha", "Zebra", "p"),
            ReportsDerive.lifetimeRanking(rows).map { it.label }
        )
    }

    @Test
    fun `lifetime ranking prefers the longest persisted label`() {
        val rows = listOf(row("a", 60, "", "2026-11-20"), row("a", 60, "Alpha", "2026-11-22"))
        assertEquals("Alpha", ReportsDerive.lifetimeRanking(rows).single().label)
    }
}
