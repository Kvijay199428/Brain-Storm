package com.vegasol.brainstorm

import com.vegasol.brainstorm.feature.dashboard.Derive
import com.vegasol.brainstorm.mascot.MascotState
import org.junit.Assert.assertEquals
import org.junit.Test

class DerivationTest {

    // ───────────── percent / remaining ─────────────
    @Test
    fun `percent is zero when no limit is configured`() {
        assertEquals(0f, Derive.percent(3_600, 0), 0.001f)
    }

    @Test
    fun `percent can exceed one hundred and is never negative`() {
        assertEquals(150f, Derive.percent(5_400, 3_600), 0.001f)
        assertEquals(0f, Derive.percent(-10, 3_600), 0.001f)
    }

    @Test
    fun `remaining never goes negative`() {
        assertEquals(1_800L, Derive.remainingSeconds(1_800, 3_600))
        assertEquals(0L, Derive.remainingSeconds(7_200, 3_600))
    }

    // ───────────── mascot precedence ─────────────
    @Test
    fun `lockout beats every other condition`() {
        assertEquals(
            MascotState.LOCKED,
            Derive.mascot(hasActiveLockout = true, degraded = true, maxPercent = 120f, usedSeconds = 9_000)
        )
    }

    @Test
    fun `broken monitoring beats usage thresholds`() {
        assertEquals(
            MascotState.PERMISSION_REQUIRED,
            Derive.mascot(hasActiveLockout = false, degraded = true, maxPercent = 100f, usedSeconds = 9_000)
        )
    }

    @Test
    fun `thresholds map to the right mood`() {
        assertEquals(MascotState.LIMIT, mood(100f))
        assertEquals(MascotState.CONCERNED, mood(80f))
        assertEquals(MascotState.THINKING, mood(50f))
        assertEquals(MascotState.HAPPY, mood(49.9f))
    }

    @Test
    fun `no usage yet is a welcome, not a threshold state`() {
        assertEquals(
            MascotState.WELCOME,
            Derive.mascot(hasActiveLockout = false, degraded = false, maxPercent = 0f, usedSeconds = 0)
        )
    }

    private fun mood(percent: Float) =
        Derive.mascot(hasActiveLockout = false, degraded = false, maxPercent = percent, usedSeconds = 60)

    // ───────────── wellness score ─────────────
    @Test
    fun `perfect day scores one hundred`() {
        assertEquals(
            100,
            Derive.wellnessScore(0, 3_600, lockedApps = 0, monitoredApps = 3, lockoutToday = false)
        )
    }

    @Test
    fun `score is forty when the whole budget is spent`() {
        // 0 time + 25 limit + 15 lock-free
        assertEquals(
            40,
            Derive.wellnessScore(3_600, 3_600, lockedApps = 0, monitoredApps = 3, lockoutToday = false)
        )
    }

    @Test
    fun `spending half the budget costs thirty points`() {
        assertEquals(
            70,
            Derive.wellnessScore(1_800, 3_600, lockedApps = 0, monitoredApps = 3, lockoutToday = false)
        )
    }

    @Test
    fun `a lockout today removes only the fifteen point component`() {
        assertEquals(
            85,
            Derive.wellnessScore(0, 3_600, lockedApps = 0, monitoredApps = 3, lockoutToday = true)
        )
    }

    @Test
    fun `half the apps locked costs half the limit component`() {
        // 60 time + 12.5 limit + 15 lock-free = 87.5 → 88
        assertEquals(
            88,
            Derive.wellnessScore(0, 3_600, lockedApps = 1, monitoredApps = 2, lockoutToday = false)
        )
    }

    @Test
    fun `no configured budget scores zero instead of dividing by zero`() {
        assertEquals(0, Derive.wellnessScore(600, 0, 0, 3, false))
    }

    // ───────────── streaks ─────────────
    private val keys = listOf("2026-11-11", "2026-11-12", "2026-11-13", "2026-11-14")

    @Test
    fun `streak counts back from the newest day and stops at the first overshoot`() {
        val totals = mapOf(
            "2026-11-14" to 1_000L,
            "2026-11-13" to 3_600L,
            "2026-11-12" to 4_000L,
            "2026-11-11" to 100L
        )
        assertEquals(2, Derive.streakDays(keys, totals, 3_600))
    }

    @Test
    fun `an overshoot today breaks the streak entirely`() {
        val totals = mapOf("2026-11-14" to 3_700L, "2026-11-13" to 100L)
        assertEquals(0, Derive.streakDays(keys, totals, 3_600))
    }

    @Test
    fun `days with no recorded usage count as clean days`() {
        assertEquals(4, Derive.streakDays(keys, emptyMap(), 3_600))
    }

    @Test
    fun `best streak finds the longest historical run`() {
        val totals = mapOf(
            "2026-11-11" to 100L,
            "2026-11-12" to 100L,
            "2026-11-13" to 9_000L,
            "2026-11-14" to 100L
        )
        assertEquals(2, Derive.bestStreak(keys, totals, 3_600))
    }

    @Test
    fun `clean day count covers the whole window`() {
        val totals = mapOf("2026-11-11" to 100L, "2026-11-13" to 9_000L)
        assertEquals(3, Derive.withinBudgetCount(keys, totals, 3_600))
    }
}
