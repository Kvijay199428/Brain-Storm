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
 * Opt-in, best-effort detection of a restricted app's window, including PiP-sized windows.
 *
 * The lockout table is the source of truth; this service only notices that a restricted package is
 * still visible and asks the existing blocker to show its screen. It never reads window text and it
 * cannot terminate third-party playback — the UI says so explicitly.
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
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        scope.launch {
            val now = System.currentTimeMillis()
            val locks = Graph.db.lockouts().allActive().filter { it.expiresAt > now }
            if (locks.isEmpty()) return@launch
            val windows = windows ?: return@launch
            val wide = resources.displayMetrics.widthPixels * 0.7f
            val high = resources.displayMetrics.heightPixels * 0.7f
            for (window in windows) {
                val pkg = window.root?.packageName?.toString() ?: continue
                val lock = locks.firstOrNull { it.packageName == pkg } ?: continue
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val pipSized = bounds.width() < wide && bounds.height() < high
                if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY || pipSized) {
                    Blocker.show(this@LockoutAccessibilityService, lock)
                }
            }
        }
    }
}
