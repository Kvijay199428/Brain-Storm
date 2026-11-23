package com.vegasol.brainstorm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The only component allowed to enforce a lockout or show the blocker.
 *
 * Detection sources (foreground, PiP, media) report events here. One continuous visibility of a
 * locked package is one [EnforcementAttempt]; duplicate events update that attempt instead of
 * relaunching the blocker. Bounded retries are used for playback control and always recorded.
 */
object EnforcementCoordinator {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val attempts = LinkedHashMap<Long, EnforcementAttempt>()
    private val reports = LinkedHashMap<Long, EnforcementReport>()
    private val visiblePackages = HashSet<String>()

    private fun media(): MediaSessionMonitor = MediaSessionMonitor(Graph.app)

    private fun stateOf(value: String, fallback: CapabilityState) =
        runCatching { CapabilityState.valueOf(value) }.getOrDefault(fallback)

    private fun reportFor(l: Lockout, a: EnforcementAttempt): EnforcementReport = reports[l.id] ?: EnforcementReport(
        visualBlock = stateOf(l.visualBlock, CapabilityState.NOT_REQUESTED),
        pipDetection = stateOf(l.pipDetection, CapabilityState.NOT_AVAILABLE),
        playbackPause = stateOf(l.playbackPause, CapabilityState.NOT_AVAILABLE),
        processTermination = stateOf(l.processTermination, CapabilityState.NOT_AVAILABLE)
    )

    private suspend fun activeLockout(pkg: String, now: Long = System.currentTimeMillis()): Lockout? =
        Graph.db.lockouts().allActive().firstOrNull { it.packageName == pkg && it.expiresAt > now }

    private suspend fun persist(l: Lockout, a: EnforcementAttempt, report: EnforcementReport) {
        reports[l.id] = report
        Graph.db.lockouts().setReport(
            l.id, report.visualBlock.name, report.pipDetection.name,
            report.playbackPause.name, report.processTermination.name, a.attemptId
        )
        Graph.db.lockouts().setState(l.id, report.overall.name)
        Graph.db.lockouts().note(l.id, report.summary())
    }

    /** All sources funnel through here; [pip] and [media] describe which detector fired. */
    suspend fun onPackageVisible(pkg: String, pip: Boolean = false, media: Boolean = false) = mutex.withLock {
        val now = System.currentTimeMillis()
        val lock = activeLockout(pkg, now) ?: return@withLock
        val visible = pkg in visiblePackages || pip || media
        val current = attempts[lock.id]

        if (EnforcementAttempts.shouldStartNew(visible, current)) {
            val attempt = EnforcementAttempt(
                lock.id, pkg, EnforcementAttempts.newId(lock.id, pkg, now), now, pipDetected = pip
            )
            attempts[lock.id] = attempt
            visiblePackages += pkg
            Blocker.reset(lock.id)
            Blocker.show(Graph.app, lock)
            attempt.blockerVisible = true
            val report = reportFor(lock, attempt).copy(
                visualBlock = CapabilityState.CONFIRMED,
                pipDetection = if (pip && Perms.lockoutAccessibility(Graph.app)) CapabilityState.CONFIRMED
                else if (pip) CapabilityState.REQUESTED else CapabilityState.NOT_AVAILABLE,
                processTermination = if (EnforcementProvider.isDeviceOwner(Graph.app)) CapabilityState.CONFIRMED
                else CapabilityState.NOT_AVAILABLE
            )
            persist(lock, attempt, report)
        } else if (current != null) {
            if (pip && Perms.lockoutAccessibility(Graph.app)) current.pipDetected = true
            if (!current.blockerVisible) {
                Blocker.show(Graph.app, lock)
                current.blockerVisible = true
            }
            val old = reportFor(lock, current)
            persist(lock, current, old.copy(
                visualBlock = CapabilityState.CONFIRMED,
                pipDetection = if (current.pipDetected) CapabilityState.CONFIRMED else old.pipDetection
            ))
        }
        if (media) requestPause(lock, now)
    }

    suspend fun onPackageHidden(pkg: String) = mutex.withLock {
        visiblePackages.remove(pkg)
    }

    suspend fun onMediaPlaying(pkg: String) {
        onPackageVisible(pkg, media = true)
    }

    private suspend fun requestPause(lock: Lockout, now: Long) {
        val attempt = attempts[lock.id] ?: return
        if (!Graph.prefs.pauseLockedPlayback || !Perms.notificationAccess(Graph.app)) {
            persist(lock, attempt, reportFor(lock, attempt).copy(playbackPause = CapabilityState.NOT_AVAILABLE))
            return
        }
        attempt.pauseRequested = true
        attempt.lastPauseAt = now
        attempt.retries = 1
        media().pause(setOf(lock.packageName))
        persist(lock, attempt, reportFor(lock, attempt).copy(playbackPause = CapabilityState.REQUESTED))
        scope.launch { delay(1_000L); verifyPause(lock.id); Unit }
    }

    private suspend fun verifyPause(lockoutId: Long): Unit = mutex.withLock {
        val lock = Graph.db.lockouts().allActive().firstOrNull { it.id == lockoutId } ?: return@withLock
        val attempt = attempts[lockoutId] ?: return@withLock
        val monitor = media()
        if (!monitor.isPlaying(lock.packageName)) {
            attempt.pauseConfirmed = true
            persist(lock, attempt, reportFor(lock, attempt).copy(playbackPause = CapabilityState.CONFIRMED))
            return@withLock
        }
        if (attempt.retries < 3) {
            attempt.retries++
            monitor.pause(setOf(lock.packageName))
            persist(lock, attempt, reportFor(lock, attempt).copy(playbackPause = CapabilityState.REQUESTED))
            scope.launch { delay(2_000L); verifyPause(lockoutId); Unit }
        } else {
            persist(lock, attempt, reportFor(lock, attempt).copy(playbackPause = CapabilityState.FAILED))
        }
    }

    /** Drop state for lockouts that expired or were removed. */
    suspend fun reconcile() = mutex.withLock {
        val activeIds = Graph.db.lockouts().allActive().mapTo(HashSet()) { it.id }
        (attempts.keys - activeIds).forEach { id ->
            attempts.remove(id); reports.remove(id); Blocker.reset(id)
        }
        val activePkgs = Graph.db.lockouts().allActive().mapTo(HashSet()) { it.packageName }
        visiblePackages.removeAll { it !in activePkgs }
    }
}
