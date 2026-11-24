package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Test

class PipCapabilityTest {

    @Test
    fun `classification separates explicit allow, deny and default`() {
        assertEquals(PipCapability.SUPPORTED_ALLOWED, PipCapabilityRules.classify(PipOpMode.ALLOWED))
        assertEquals(PipCapability.SUPPORTED_DISABLED, PipCapabilityRules.classify(PipOpMode.DENIED))
        assertEquals(PipCapability.DEFAULT_ALLOWED, PipCapabilityRules.classify(PipOpMode.DEFAULT))
        assertEquals(PipCapability.UNKNOWN, PipCapabilityRules.classify(PipOpMode.UNKNOWN))
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
        assertEquals("PiP default-allowed · support unconfirmed", PipCapabilityRules.label(PipCapability.DEFAULT_ALLOWED))
        assertEquals("PiP state unknown", PipCapabilityRules.label(PipCapability.UNKNOWN))
    }
}
