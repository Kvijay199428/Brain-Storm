package com.vegasol.brainstorm

/** One continuous visibility attempt of one locked package. Duplicate events update, not recreate. */
data class EnforcementAttempt(
    val lockoutId: Long,
    val packageName: String,
    val attemptId: String,
    val startedAt: Long,
    var blockerVisible: Boolean = false,
    var pauseRequested: Boolean = false,
    var pauseConfirmed: Boolean = false,
    var pipDetected: Boolean = false,
    var lastPauseAt: Long = 0L,
    var retries: Int = 0
)

object EnforcementAttempts {
    fun newId(lockoutId: Long, packageName: String, now: Long): String = "$lockoutId:$packageName:$now"

    /** A new attempt starts only on a not-visible → visible transition (or a different lockout). */
    fun shouldStartNew(packageVisible: Boolean, current: EnforcementAttempt?): Boolean =
        !packageVisible || current == null
}
