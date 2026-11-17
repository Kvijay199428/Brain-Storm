package com.vegasol.brainstorm.feature.dashboard

import com.vegasol.brainstorm.Lockout
import com.vegasol.brainstorm.mascot.MascotState
import com.vegasol.brainstorm.ui.AppStatus

/** What the header is allowed to claim, kept deliberately separate from "a lockout row exists". */
enum class ProtectionStatus { ACTIVE, PARTIAL, PERMISSION_REQUIRED, MONITORING_PAUSED }

data class AppUsageUiModel(
    val packageName: String,
    val label: String,
    val usedSeconds: Long,
    val limitSeconds: Long,
    val percent: Float,
    val contentCount: Int,
    val contentSupported: Boolean,
    val lock: Lockout?,
    val status: AppStatus
) {
    val remainingSeconds: Long get() = (limitSeconds - usedSeconds).coerceAtLeast(0L)
}

/**
 * Everything the dashboard draws. The spec's requirement that the mascot, the cards and the app
 * list are driven by one object is enforced here: there is no second source of truth in the UI.
 */
data class DashboardUiState(
    val isLoading: Boolean = true,

    val monitoringActive: Boolean = false,
    val protection: ProtectionStatus = ProtectionStatus.PERMISSION_REQUIRED,
    val usageAccessGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val heartbeatFresh: Boolean = false,

    val totalUsageSeconds: Long = 0,
    val dailyBudgetSeconds: Long = 0,
    val perAppLimitsTotalSeconds: Long = 0,
    val usePercent: Float = 0f,
    val remainingSeconds: Long = 0,

    val totalContentCount: Int = 0,
    val contentCountingAvailable: Boolean = false,
    val contentDetectorExists: Boolean = false,

    val wellnessScore: Int = 0,
    val currentStreakDays: Int = 0,
    val daysWithinBudgetLast30: Int = 0,

    val monitoredApps: List<AppUsageUiModel> = emptyList(),

    val mascotState: MascotState = MascotState.HAPPY,
    val advice: String = "",

    val activeLockouts: List<Lockout> = emptyList(),
    val budgetLockout: Boolean = false,
    val enforcementOk: Boolean = true,

    val dayKey: String = ""
) {
    val hasApps: Boolean get() = monitoredApps.isNotEmpty()
    val isLocked: Boolean get() = activeLockouts.isNotEmpty()
}
