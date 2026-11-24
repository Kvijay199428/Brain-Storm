package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Test

class PipCapabilityTest {

    @Test
    fun `classification separates support from permission`() {
        assertEquals(PipCapability.SUPPORTED_ALLOWED, PipCapabilityRules.classify(true, true))
        assertEquals(PipCapability.SUPPORTED_DISABLED, PipCapabilityRules.classify(true, false))
        assertEquals(PipCapability.NOT_SUPPORTED, PipCapabilityRules.classify(false, true))
        assertEquals(PipCapability.UNKNOWN, PipCapabilityRules.classify(null, true))
        assertEquals(PipCapability.UNKNOWN, PipCapabilityRules.classify(true, null))
    }

    @Test
    fun `dismissal is attempted once per appearance`() {
        assert(PipDismissalRules.shouldAttempt(true, null, 10_000L, 60_000L))
        assert(!PipDismissalRules.shouldAttempt(false, null, 10_000L, 60_000L))
        assert(!PipDismissalRules.shouldAttempt(true, 9_000L, 10_000L, 60_000L))
        assert(PipDismissalRules.shouldAttempt(true, 0L, 60_000L, 60_000L))
    }

    @Test
    fun `labels explain the state`() {
        assertEquals("PiP supported · permission enabled", PipCapabilityRules.label(PipCapability.SUPPORTED_ALLOWED))
        assertEquals("PiP support not confirmed", PipCapabilityRules.label(PipCapability.NOT_SUPPORTED))
    }
}
