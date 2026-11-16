package com.vegasol.brainstorm.feature.dashboard

import com.vegasol.brainstorm.mascot.MascotState
import com.vegasol.brainstorm.theme.UsageThresholds

/**
 * Pure derivation rules. No Android, no Compose, no database types — everything here is unit
 * tested directly so the thresholds can't drift from what the UI renders.
 */
object Derive {

    /** Used/limit as a percentage, never negative, never NaN. */
    fun percent(usedSeconds: Long, limitSeconds: Long): Float =
        if (limitSeconds <= 0L) 0f else (usedSeconds * 100f / limitSeconds).coerceAtLeast(0f)

    fun remainingSeconds(usedSeconds: Long, limitSeconds: Long): Long =
        (limitSeconds - usedSeconds).coerceAtLeast(0L)

    /**
     * Mascot precedence, in order: lockout beats everything, then a broken monitoring setup,
     * then the usage thresholds, then "nothing happened yet today".
     */
    fun mascot(
        hasActiveLockout: Boolean,
        degraded: Boolean,
        maxPercent: Float,
        usedSeconds: Long
    ): MascotState = when {
        hasActiveLockout -> MascotState.LOCKED
        degraded -> MascotState.PERMISSION_REQUIRED
        maxPercent >= UsageThresholds.FULL -> MascotState.LIMIT
        maxPercent >= UsageThresholds.HIGH -> MascotState.CONCERNED
        maxPercent >= UsageThresholds.MID -> MascotState.THINKING
        usedSeconds <= 0L -> MascotState.WELCOME
        else -> MascotState.HAPPY
    }

    /**
     * 60 points for time discipline, 25 for keeping every app inside its own limit, 15 for a
     * lockout-free day. App-defined and stated as such in the UI — not a measurement.
     */
    fun wellnessScore(
        usedSeconds: Long,
        budgetSeconds: Long,
        lockedApps: Int,
        monitoredApps: Int,
        lockoutToday: Boolean
    ): Int {
        if (budgetSeconds <= 0L) return 0
        val usedFraction = (usedSeconds.toDouble() / budgetSeconds).coerceIn(0.0, 1.0)
        val timeScore = 60.0 * (1.0 - usedFraction)
        val limitScore = when {
            monitoredApps <= 0 -> 25.0
            lockedApps <= 0 -> 25.0
            else -> 25.0 * (1.0 - (lockedApps.toDouble() / monitoredApps).coerceIn(0.0, 1.0))
        }
        val lockScore = if (lockoutToday) 0.0 else 15.0
        return Math.round(timeScore + limitScore + lockScore).toInt().coerceIn(0, 100)
    }

    /**
     * Consecutive days at or under budget, counted back from the most recent key. A day with no
     * recorded usage counts as a clean day (zero usage is not an overshoot).
     */
    fun streakDays(dayKeysAscending: List<String>, totalsByDay: Map<String, Long>, budgetSeconds: Long): Int {
        if (budgetSeconds <= 0L) return 0
        var streak = 0
        for (key in dayKeysAscending.asReversed()) {
            val used = totalsByDay[key] ?: 0L
            if (used <= budgetSeconds) streak++ else break
        }
        return streak
    }

    fun withinBudgetCount(dayKeys: List<String>, totalsByDay: Map<String, Long>, budgetSeconds: Long): Int {
        if (budgetSeconds <= 0L) return 0
        return dayKeys.count { (totalsByDay[it] ?: 0L) <= budgetSeconds }
    }

    fun bestStreak(dayKeysAscending: List<String>, totalsByDay: Map<String, Long>, budgetSeconds: Long): Int {
        if (budgetSeconds <= 0L) return 0
        var best = 0
        var run = 0
        for (key in dayKeysAscending) {
            if ((totalsByDay[key] ?: 0L) <= budgetSeconds) {
                run++
                if (run > best) best = run
            } else run = 0
        }
        return best
    }

    /** Hours:minutes from now until [expiresAt], clamped at zero. */
    fun remainingUntil(now: Long, expiresAt: Long): Long = (expiresAt - now).coerceAtLeast(0L)
}
