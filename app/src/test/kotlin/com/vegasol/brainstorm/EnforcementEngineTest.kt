package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnforcementEngineTest {

    @Test
    fun `a new attempt starts only on a not-visible to visible transition`() {
        assertTrue(EnforcementAttempts.shouldStartNew(false, null))
        assertTrue(EnforcementAttempts.shouldStartNew(true, null))
        val attempt = EnforcementAttempt(1L, "com.app", "id", 0L)
        assertFalse(EnforcementAttempts.shouldStartNew(true, attempt))
        assertTrue(EnforcementAttempts.shouldStartNew(false, attempt))
    }

    @Test
    fun `PiP requires package identity, visibility and small bounds`() {
        val restricted = setOf("in.startv.hotstar")
        assertTrue(
            PipWindowDetector.isRestrictedPip(
                PipWindowDetector.Window("in.startv.hotstar", true, false, 500, 300),
                restricted, 1080, 2400
            )
        )
        assertFalse(
            PipWindowDetector.isRestrictedPip(
                PipWindowDetector.Window("com.other", true, false, 500, 300), restricted, 1080, 2400
            )
        )
        assertFalse(
            PipWindowDetector.isRestrictedPip(
                PipWindowDetector.Window("in.startv.hotstar", true, false, 1080, 2400), restricted, 1080, 2400
            )
        )
        assertFalse(
            PipWindowDetector.isRestrictedPip(
                PipWindowDetector.Window("in.startv.hotstar", false, false, 500, 300), restricted, 1080, 2400
            )
        )
    }

    @Test
    fun `overall stays partial without process control`() {
        val report = EnforcementReport(
            visualBlock = CapabilityState.CONFIRMED,
            pipDetection = CapabilityState.CONFIRMED,
            playbackPause = CapabilityState.CONFIRMED,
            processTermination = CapabilityState.NOT_AVAILABLE
        )
        assertEquals(EnforcementState.PARTIAL, report.overall)
        assertTrue(report.summary().contains("no process control"))
    }

    @Test
    fun `report is reconstructed from stored fields`() {
        val lock = Lockout(
            packageName = "com.app", triggerType = Triggers.TIME, triggerValue = 0,
            limitValue = 0, activatedAt = 0, expiresAt = 0,
            visualBlock = "CONFIRMED", pipDetection = "NOT_AVAILABLE",
            playbackPause = "FAILED", processTermination = "NOT_AVAILABLE"
        )
        val report = EnforcementReport.from(lock)
        assertEquals(CapabilityState.CONFIRMED, report.visualBlock)
        assertEquals(CapabilityState.FAILED, report.playbackPause)
    }
}
