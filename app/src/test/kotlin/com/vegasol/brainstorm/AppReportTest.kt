package com.vegasol.brainstorm

import com.vegasol.brainstorm.feature.apps.AppReportDerive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppReportTest {

    @Test
    fun `peak hour is null without recorded time`() {
        assertNull(AppReportDerive.peakHour(emptyList()))
        assertNull(AppReportDerive.peakHour(List(24) { 0L }))
    }

    @Test
    fun `peak hour picks the busiest hour`() {
        val hours = MutableList(24) { 0L }
        hours[9] = 600L
        hours[21] = 3_600L
        assertEquals(21, AppReportDerive.peakHour(hours))
    }

    @Test
    fun `peak hour keeps the earlier hour on a tie`() {
        val hours = MutableList(24) { 0L }
        hours[8] = 900L
        hours[19] = 900L
        assertEquals(8, AppReportDerive.peakHour(hours))
    }

    @Test
    fun `peak day ignores empty days and keeps the first on a tie`() {
        val days = listOf("2026-11-16" to 0L, "2026-11-17" to 600L, "2026-11-18" to 600L)
        assertEquals("2026-11-17", AppReportDerive.peakDay(days)?.first)
        assertNull(AppReportDerive.peakDay(listOf("2026-11-18" to 0L)))
    }

    @Test
    fun `average divides by calendar days, not active days`() {
        assertEquals(0L, AppReportDerive.averageSeconds(emptyList()))
        assertEquals(300L, AppReportDerive.averageSeconds(listOf(0L, 600L, 0L, 600L)))
    }

    @Test
    fun `active days require at least a minute`() {
        val days = listOf("a" to 59L, "b" to 60L, "c" to 0L, "d" to 600L)
        assertEquals(2, AppReportDerive.activeDays(days))
    }

    @Test
    fun `hour labels wrap at midnight`() {
        assertEquals("00:00-01:00", AppReportDerive.hourLabel(0))
        assertEquals("21:00-22:00", AppReportDerive.hourLabel(21))
        assertEquals("23:00-00:00", AppReportDerive.hourLabel(23))
        assertEquals("23:00-00:00", AppReportDerive.hourLabel(99))
    }

    @Test
    fun `apps are ranked by today's usage with unused apps last`() {
        val apps = listOf(
            MonitoredApp(packageName = "a", appName = "Alpha"),
            MonitoredApp(packageName = "b", appName = "Beta"),
            MonitoredApp(packageName = "c", appName = "Charlie")
        )
        val usage = listOf(
            DailyUsage(packageName = "a", usageDate = "2026-11-22", foregroundSeconds = 60),
            DailyUsage(packageName = "b", usageDate = "2026-11-22", foregroundSeconds = 600)
        )
        val ranked = AppReportDerive.rankByTodayUsage(apps, usage)
        assertEquals(listOf("b", "a", "c"), ranked.map { it.app.packageName })
        assertEquals(listOf(1, 2, 3), ranked.map { it.rank })
        assertEquals(600L, ranked.first().usedSeconds)
    }

    @Test
    fun `ranking keeps alphabetical order on ties and never loses an app`() {
        val apps = listOf(
            MonitoredApp(packageName = "z", appName = "Zebra"),
            MonitoredApp(packageName = "a", appName = "Alpha"),
            MonitoredApp(packageName = "m", appName = "Middle")
        )
        val ranked = AppReportDerive.rankByTodayUsage(apps, emptyList())
        assertEquals(listOf("a", "m", "z"), ranked.map { it.app.packageName })
        assertEquals(listOf(0L, 0L, 0L), ranked.map { it.usedSeconds })
    }

    @Test
    fun `duplicate usage rows for one package use the larger total`() {
        val apps = listOf(MonitoredApp(packageName = "a", appName = "Alpha"))
        val usage = listOf(
            DailyUsage(packageName = "a", usageDate = "x", foregroundSeconds = 30),
            DailyUsage(packageName = "a", usageDate = "y", foregroundSeconds = 90)
        )
        assertEquals(90L, AppReportDerive.rankByTodayUsage(apps, usage).single().usedSeconds)
    }
}
