package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetRuleTest {

    // ───────────── reachability of the budget rule ─────────────
    @Test
    fun `budget below the sum of per-app limits is valid`() {
        assertEquals(BudgetCheck.VALID, budgetCheck(60 * 60L, 3 * 60 * 60L))
    }

    @Test
    fun `budget equal to the sum is redundant`() {
        assertEquals(BudgetCheck.REDUNDANT, budgetCheck(3 * 60 * 60L, 3 * 60 * 60L))
    }

    @Test
    fun `budget above the sum can never fire`() {
        // Every app stops at its own limit, so total usage can never exceed the sum.
        assertEquals(BudgetCheck.UNREACHABLE, budgetCheck(4 * 60 * 60L, 3 * 60 * 60L))
    }

    // ───────────── budget evaluation ─────────────
    @Test
    fun `budget fires exactly at the limit and not a second before`() {
        assertFalse(BudgetEvaluator.eval(3_599, 60).shouldLock)
        assertTrue(BudgetEvaluator.eval(3_600, 60).shouldLock)
    }

    @Test
    fun `budget percentage and remaining are clamped`() {
        val eval = BudgetEvaluator.eval(5_400, 60)
        assertEquals(150f, eval.percent, 0.001f)
        assertEquals(0L, eval.remainingSeconds)

        val half = BudgetEvaluator.eval(1_800, 60)
        assertEquals(1_800L, half.remainingSeconds)
        assertEquals(50f, half.percent, 0.001f)
    }

    @Test
    fun `a zero budget never fires`() {
        assertFalse(BudgetEvaluator.eval(10_000, 0).shouldLock)
    }

    // ───────────── per-app rules are untouched ─────────────
    @Test
    fun `time limit fires at the boundary`() {
        val app = MonitoredApp(packageName = "com.example", appName = "Example", timeLimitMin = 60)
        assertFalse(LimitEvaluator.eval(app, usage(3_599), contentSupported = false).shouldLock)
        assertTrue(LimitEvaluator.eval(app, usage(3_600), contentSupported = false).shouldLock)
    }

    @Test
    fun `content limit never fires without a validated detector`() {
        val app = MonitoredApp(
            packageName = "com.example", appName = "Example",
            timeLimitMin = 600, contentLimit = 10
        )
        val heavy = usage(60, content = 999)
        val eval = LimitEvaluator.eval(app, heavy, contentSupported = false)
        assertFalse(eval.contentExceeded)
        assertFalse(eval.shouldLock)
    }

    @Test
    fun `content limit fires when a detector is registered`() {
        val app = MonitoredApp(
            packageName = "com.example", appName = "Example",
            timeLimitMin = 600, contentLimit = 10
        )
        val eval = LimitEvaluator.eval(app, usage(60, content = 10), contentSupported = true)
        assertTrue(eval.shouldLock)
        assertEquals(Triggers.CONTENT, eval.trigger)
    }

    @Test
    fun `missing usage row means no lock`() {
        val app = MonitoredApp(packageName = "com.example", appName = "Example", timeLimitMin = 1)
        assertFalse(LimitEvaluator.eval(app, null, contentSupported = false).shouldLock)
    }

    private fun usage(seconds: Long, content: Int = 0) = DailyUsage(
        packageName = "com.example",
        usageDate = "2026-11-16",
        foregroundSeconds = seconds,
        contentCount = content
    )
}
