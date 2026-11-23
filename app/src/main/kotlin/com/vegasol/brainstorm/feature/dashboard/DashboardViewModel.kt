package com.vegasol.brainstorm.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vegasol.brainstorm.ContentCounting
import com.vegasol.brainstorm.DailyUsage
import com.vegasol.brainstorm.EnforcementProvider
import com.vegasol.brainstorm.EnforcementState
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.Lockout
import com.vegasol.brainstorm.MonitoredApp
import com.vegasol.brainstorm.Perms
import com.vegasol.brainstorm.Startup
import com.vegasol.brainstorm.Triggers
import com.vegasol.brainstorm.theme.UsageThresholds
import com.vegasol.brainstorm.ui.appLabel
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.statusOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Single owner of dashboard state. Room flows are combined with a coarse clock (the day key can
 * roll over at the configured reset hour) and a second-granularity ticker that only the
 * countdowns consume.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val clock = MutableStateFlow(System.currentTimeMillis())

    /** Exposed separately so only the countdown composables recompose every second. */
    val now: StateFlow<Long> = clock.asStateFlow()

    private val refreshTick = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                clock.value = System.currentTimeMillis()
            }
        }
    }

    private val minuteTick = clock.map { it / 60_000L }.distinctUntilChanged()
    private val dayKeyFlow = minuteTick.map { Graph.engine.dayKey(it * 60_000L) }.distinctUntilChanged()

    private data class Core(
        val apps: List<MonitoredApp>,
        val usage: List<DailyUsage>,
        val locks: List<Lockout>,
        val dayKey: String
    )

    // Coarse tick only: the day key rolls over at resetHour and the heartbeat can go stale
    // between database writes. The 1-second clock is deliberately NOT an input here, so the
    // dashboard state object is not rebuilt every tick.
    private val core = combine(
        Graph.db.apps().observeAll(),
        dayKeyFlow.flatMapLatest { Graph.db.usage().observeDay(it) },
        Graph.db.lockouts().observeActive(),
        dayKeyFlow,
        minuteTick
    ) { apps, usage, locks, dayKey, _ -> Core(apps, usage, locks, dayKey) }

    private val history = combine(
        dayKeyFlow.flatMapLatest { Graph.db.usage().observeSince(Graph.engine.dayKeys(HISTORY_DAYS).last()) },
        Graph.db.lockouts().observeHistory(),
        refreshTick
    ) { usage, lockHistory, _ -> usage to lockHistory }

    /**
     * First frame only. Room needs a beat to emit, and the old placeholder defaulted to
     * PERMISSION_REQUIRED, so the hero flashed "Finish setup" on every cold start even when setup
     * was already finished. Permissions, the heartbeat and the frozen budget are cheap synchronous
     * reads, so the placeholder tells the truth and only the measured numbers wait for the database.
     */
    private val initial: DashboardUiState = run {
        val ctx = getApplication<Application>()
        val usageOk = Perms.usage(ctx)
        val overlayOk = Perms.overlay(ctx)
        val budgetSec = Graph.prefs.budgetMin * 60L
        val hbFresh = System.currentTimeMillis() - Graph.prefs.heartbeat < HEARTBEAT_STALE_MS
        val protection = when {
            !usageOk -> ProtectionStatus.PERMISSION_REQUIRED
            !hbFresh -> ProtectionStatus.MONITORING_PAUSED
            !overlayOk && !EnforcementProvider.isDeviceOwner(ctx) -> ProtectionStatus.PARTIAL
            else -> ProtectionStatus.ACTIVE
        }
        DashboardUiState(
            isLoading = true,
            monitoringActive = hbFresh && usageOk,
            protection = protection,
            usageAccessGranted = usageOk,
            overlayGranted = overlayOk,
            heartbeatFresh = hbFresh,
            dailyBudgetSeconds = budgetSec,
            remainingSeconds = budgetSec,
            mascotState = Derive.mascot(
                hasActiveLockout = false,
                degraded = !usageOk || !hbFresh,
                maxPercent = 0f,
                usedSeconds = 0L
            ),
            dayKey = Graph.engine.dayKey(System.currentTimeMillis())
        )
    }

    val state: StateFlow<DashboardUiState> = combine(core, history) { c, (usageHistory, lockHistory) ->
        build(c, usageHistory, lockHistory)
    }
        // Releases the launch splash. Only a real emission counts: the `initial` placeholder is
        // itself isLoading=true, so it can never dismiss the splash.
        .onEach { if (!it.isLoading) Startup.dashboardReady.set(true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    /** Called on resume so permission changes made outside the app are picked up. */
    fun refresh() {
        refreshTick.value += 1
    }

    private fun build(
        c: Core,
        historyUsage: List<DailyUsage>,
        lockHistory: List<Lockout>
    ): DashboardUiState {
        val budgetSec = Graph.prefs.budgetMin * 60L
        // daily_usage also holds apps that are not monitored (whole-device reports), so every
        // budget-facing number has to be filtered back to the frozen policy.
        val watched = c.apps.mapTo(HashSet()) { it.packageName }
        val monitoredUsage = c.usage.filter { it.packageName in watched }
        val totalUsed = monitoredUsage.sumOf { it.foregroundSeconds }
        val perAppTotal = c.apps.sumOf { (it.timeLimitMin ?: 0) * 60L }
        val budgetPercent = Derive.percent(totalUsed, budgetSec)

        val ctx = getApplication<Application>()
        val countingOn = ContentCounting.enabled(ctx)

        val apps = c.apps.map { a ->
            val u = c.usage.firstOrNull { it.packageName == a.packageName }
            val limitSec = (a.timeLimitMin ?: 0) * 60L
            val used = u?.foregroundSeconds ?: 0L
            AppUsageUiModel(
                packageName = a.packageName,
                label = a.appName,
                usedSeconds = used,
                limitSeconds = limitSec,
                percent = Derive.percent(used, limitSec),
                contentCount = u?.contentCount ?: 0,
                contentSupported = countingOn && ContentCounting.hasDetector(a.packageName),
                lock = c.locks.firstOrNull { it.packageName == a.packageName },
                status = statusOf(a, u, c.locks.firstOrNull { it.packageName == a.packageName })
            )
        }.sortedByDescending { it.usedSeconds }

        val maxPercent = maxOf(budgetPercent, apps.maxOfOrNull { it.percent } ?: 0f)

        val usageOk = Perms.usage(ctx)
        val overlayOk = Perms.overlay(ctx)
        val hbFresh = System.currentTimeMillis() - Graph.prefs.heartbeat < HEARTBEAT_STALE_MS
        val notEnforced = c.locks.any {
            it.enforcementState == EnforcementState.PARTIAL.name || it.enforcementNote.startsWith(NOT_ENFORCED)
        }

        val protection = when {
            !usageOk -> ProtectionStatus.PERMISSION_REQUIRED
            !hbFresh -> ProtectionStatus.MONITORING_PAUSED
            notEnforced -> ProtectionStatus.PARTIAL
            !overlayOk && !EnforcementProvider.isDeviceOwner(ctx) -> ProtectionStatus.PARTIAL
            else -> ProtectionStatus.ACTIVE
        }

        val totalsByDay = historyUsage
            .filter { it.packageName in watched }
            .groupBy { it.usageDate }
            .mapValues { entry -> entry.value.sumOf { it.foregroundSeconds } }
        val keys = Graph.engine.dayKeys(HISTORY_DAYS).reversed()          // oldest → newest
        val streak = Derive.streakDays(keys, totalsByDay, budgetSec)
        val clean30 = Derive.withinBudgetCount(keys, totalsByDay, budgetSec)
        val lockoutToday = lockHistory.any { Graph.engine.dayKey(it.activatedAt) == c.dayKey }
        val score = Derive.wellnessScore(
            usedSeconds = totalUsed,
            budgetSeconds = budgetSec,
            lockedApps = c.locks.size,
            monitoredApps = apps.size,
            lockoutToday = lockoutToday
        )

        val budgetLock = c.locks.firstOrNull { it.triggerType == Triggers.BUDGET }
        val remaining = Derive.remainingSeconds(totalUsed, budgetSec)
        val advice = when {
            budgetLock != null ->
                "Your daily budget is spent. I'm holding the line until ${timeOf(budgetLock.expiresAt)}."
            c.locks.isNotEmpty() -> {
                val l = c.locks.first()
                "${labelFor(l.packageName, apps)} is resting until ${timeOf(l.expiresAt)}."
            }
            maxPercent >= UsageThresholds.FULL ->
                "You've reached today's limit. That's the whole allowance — let's take the long break."
            maxPercent >= UsageThresholds.HIGH ->
                "You're close to your daily budget. Maybe wrap up and do something offline?"
            maxPercent >= UsageThresholds.MID ->
                "${fmtHm(totalUsed)} in. A short break now keeps the rest of the day yours."
            streak >= 2 ->
                "Great work — $streak days in a row inside your budget. Your future self thanks you."
            else -> "You have ${fmtHm(remaining)} left of your ${fmtHm(budgetSec)} budget today."
        }

        return DashboardUiState(
            isLoading = false,
            monitoringActive = hbFresh && usageOk,
            protection = protection,
            usageAccessGranted = usageOk,
            overlayGranted = overlayOk,
            heartbeatFresh = hbFresh,
            totalUsageSeconds = totalUsed,
            dailyBudgetSeconds = budgetSec,
            perAppLimitsTotalSeconds = perAppTotal,
            usePercent = budgetPercent,
            remainingSeconds = remaining,
            totalContentCount = monitoredUsage.sumOf { it.contentCount },
            contentCountingAvailable = apps.any { it.contentSupported },
            contentDetectorExists = c.apps.any { ContentCounting.hasDetector(it.packageName) },
            wellnessScore = score,
            currentStreakDays = streak,
            daysWithinBudgetLast30 = clean30,
            monitoredApps = apps,
            mascotState = Derive.mascot(
                hasActiveLockout = c.locks.isNotEmpty(),
                degraded = !usageOk || !hbFresh,
                maxPercent = maxPercent,
                usedSeconds = totalUsed
            ),
            advice = advice,
            activeLockouts = c.locks,
            budgetLockout = budgetLock != null,
            enforcementOk = !notEnforced,
            dayKey = c.dayKey
        )
    }

    private fun labelFor(pkg: String, apps: List<AppUsageUiModel>): String =
        apps.firstOrNull { it.packageName == pkg }?.label ?: appLabel(getApplication(), pkg)

    private fun timeOf(ms: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))

    companion object {
        private const val HISTORY_DAYS = 30
        private const val HEARTBEAT_STALE_MS = 90_000L
        private const val NOT_ENFORCED = "NOT ENFORCED"
    }
}
