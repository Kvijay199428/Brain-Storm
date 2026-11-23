package com.vegasol.brainstorm

/**
 * Central enforcement coordinator.
 *
 * The lockout row is the source of truth; this class only applies OS-level enforcement and records
 * the state Android actually confirmed. "We asked for it" is never stored as "enforced".
 */
class LockoutEnforcer(private val db: BrainStormDb, private val enf: EnforcementController) {

    suspend fun enforce(l: Lockout) {
        val r = runCatching { enf.apply(l) }.getOrElse { EnfResult(false, it.message ?: "enforcement threw") }
        val note = if (r.ok) r.note else "NOT ENFORCED: ${r.note}"
        db.lockouts().setEnforcement(l.id, r.state.name, note)
    }

    suspend fun release(l: Lockout) {
        val r = runCatching { enf.remove(l) }.getOrElse { EnfResult(false, it.message ?: "release threw") }
        val state = if (r.ok) EnforcementState.RELEASED else EnforcementState.PARTIAL
        val note = if (r.ok) "released" else "NOT RELEASED: ${r.note}"
        db.lockouts().setEnforcement(l.id, state.name, note)
    }

    companion object {
        fun stateOf(l: Lockout): EnforcementState =
            runCatching { EnforcementState.valueOf(l.enforcementState) }.getOrDefault(EnforcementState.PENDING)
    }
}
