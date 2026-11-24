package com.vegasol.brainstorm

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reports window/PiP visibility events to the enforcement coordinator. It never enforces anything
 * itself and never reads window text; only package, type, visibility and bounds are inspected.
 */
class LockoutAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        scope.launch {
            PipSettingsAutomation.onWindowEvent(this@LockoutAccessibilityService)
            val now = System.currentTimeMillis()
            val restricted = Graph.db.lockouts().allActive()
                .filter { it.expiresAt > now }
                .mapTo(HashSet()) { it.packageName }
            if (restricted.isEmpty()) return@launch
            val windows = windows ?: return@launch
            val metrics = resources.displayMetrics
            val seen = HashSet<String>()
            for (window in windows) {
                val pkg = window.root?.packageName?.toString() ?: continue
                if (pkg !in restricted) continue
                seen += pkg
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val pip = PipWindowDetector.isRestrictedPip(
                    PipWindowDetector.Window(
                        packageName = pkg,
                        visible = true,
                        overlay = window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                        width = bounds.width(),
                        height = bounds.height()
                    ),
                    restricted, metrics.widthPixels, metrics.heightPixels
                )
                EnforcementCoordinator.onPackageVisible(pkg, pip = pip)
                if (pip) {
                    val result = PipDismissalController.attempt(this@LockoutAccessibilityService, pkg, bounds)
                    EnforcementCoordinator.onPipDismissal(pkg, result)
                }
            }
            restricted.forEach { pkg ->
                if (pkg !in seen) EnforcementCoordinator.onPackageHidden(pkg)
            }
        }
    }
}
