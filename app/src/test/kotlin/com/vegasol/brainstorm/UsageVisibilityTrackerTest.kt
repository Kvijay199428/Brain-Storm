package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageVisibilityTrackerTest {

    private fun span(a: Long, b: Long) = UsageVisibilityTracker.TimeSpan(a, b)

    @Test
    fun `overlapping and touching spans are merged`() {
        val merged = UsageVisibilityTracker.merge(listOf(span(20, 40), span(0, 10), span(10, 30), span(50, 60)))
        assertEquals(listOf(span(0, 40), span(50, 60)), merged)
    }

    @Test
    fun `time is never double counted across PiP and main activity`() {
        val spans = listOf(span(0, 100), span(40, 160))
        assertEquals(160L, UsageVisibilityTracker.totalMillis(spans))
        assertEquals(0L, UsageVisibilityTracker.totalMillis(listOf(span(5, 5))))
    }

    @Test
    fun `foreground and visible time are reconciled by max not sum`() {
        assertEquals(600L, UsageVisibilityTracker.reconcileSeconds(600L, 300L))
        assertEquals(900L, UsageVisibilityTracker.reconcileSeconds(600L, 900L))
        assertEquals(0L, UsageVisibilityTracker.reconcileSeconds(-5L, 0L))
    }
}
