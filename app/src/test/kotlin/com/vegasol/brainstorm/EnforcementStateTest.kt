package com.vegasol.brainstorm

import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementStateTest {

    @Test
    fun `successful result is ENFORCED and failure is PARTIAL`() {
        assertEquals(EnforcementState.ENFORCED, EnfResult(true, "package suspended").state)
        assertEquals(EnforcementState.PARTIAL, EnfResult(false, "overlay only").state)
    }

    @Test
    fun `unknown stored state falls back to PENDING`() {
        val lock = Lockout(packageName = "a", triggerType = Triggers.TIME, triggerValue = 0,
            limitValue = 0, activatedAt = 0, expiresAt = 0, enforcementState = "bogus")
        assertEquals(EnforcementState.PENDING, LockoutEnforcer.stateOf(lock))
        assertEquals(EnforcementState.ENFORCED, LockoutEnforcer.stateOf(lock.copy(enforcementState = "ENFORCED")))
    }
}
