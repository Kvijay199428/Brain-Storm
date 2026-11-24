package com.vegasol.brainstorm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

enum class PipDismissResult { DISMISSED, SWIPE_DISMISSED, NO_CONTROL, FAILED, SKIPPED }

object PipDismissalRules {
    /** One controlled attempt per PiP appearance; no infinite clicking. */
    fun shouldAttempt(enabled: Boolean, lastAttemptAt: Long?, now: Long, cooldownMs: Long): Boolean =
        enabled && (lastAttemptAt == null || now - lastAttemptAt >= cooldownMs)
}

/**
 * Best-effort, opt-in automation of Android's own PiP controls. Only a restricted PiP window that
 * was positively identified is acted on; close-control candidates must lie within that window's
 * screen area. No arbitrary coordinates are clicked.
 */
object PipDismissalController {
    private const val COOLDOWN_MS = 60_000L
    private val attempted = HashMap<String, Long>()

    suspend fun attempt(service: AccessibilityService, pkg: String, pipBounds: Rect): PipDismissResult {
        if (!Graph.prefs.pipAutoDismiss) return PipDismissResult.SKIPPED
        val now = System.currentTimeMillis()
        if (!PipDismissalRules.shouldAttempt(true, attempted[pkg], now, COOLDOWN_MS)) return PipDismissResult.SKIPPED
        attempted[pkg] = now

        // Strategy 1: reveal the controls with a real tap, then click a verified Close node.
        if (canGesture(service)) tap(service, pipBounds.centerX().toFloat(), pipBounds.centerY().toFloat())
        delay(500L)
        val control = findCloseNode(service, pipBounds)
        if (control != null && runCatching {
                control.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }.getOrDefault(false)
        ) {
            delay(600L)
            if (!isPipVisible(service, pkg)) return PipDismissResult.DISMISSED
        }

        // Strategy 2: Android also supports swiping a PiP window down to dismiss it.
        if (canGesture(service) && swipeDown(service, pipBounds)) {
            delay(700L)
            if (!isPipVisible(service, pkg)) return PipDismissResult.SWIPE_DISMISSED
        }
        return if (isPipVisible(service, pkg)) PipDismissResult.FAILED else PipDismissResult.DISMISSED
    }

    private fun isPipVisible(service: AccessibilityService, pkg: String): Boolean =
        service.windows.orEmpty().any { window ->
            window.root?.packageName?.toString() == pkg
        }

    private fun findCloseNode(service: AccessibilityService, pipBounds: Rect): AccessibilityNodeInfo? {
        val area = Rect(pipBounds)
        area.inset(-140, -140)
        for (window in service.windows.orEmpty()) {
            val root = window.root ?: continue
            if (!(root.packageName?.toString().orEmpty()).contains("systemui", ignoreCase = true)) continue
            findClose(root, area)?.let { return it }
        }
        return null
    }

    private fun findClose(node: AccessibilityNodeInfo, area: Rect): AccessibilityNodeInfo? {
        if (node.isClickable && isCloseControl(node)) {
            val bounds = Rect()
            runCatching { node.getBoundsInScreen(bounds) }
            if (Rect.intersects(bounds, area)) return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClose(child, area)?.let { return it }
        }
        return null
    }

    private fun isCloseControl(node: AccessibilityNodeInfo): Boolean {
        val label = "${node.contentDescription ?: ""} ${node.viewIdResourceName ?: ""}".lowercase()
        return listOf("close", "dismiss", "pip_close", "close_button").any { label.contains(it) }
    }

    private fun canGesture(service: AccessibilityService): Boolean {
        val info = service.serviceInfo ?: return false
        return info.capabilities and
            android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
    }

    private suspend fun tap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        return dispatch(service, path, 90L)
    }

    private suspend fun swipeDown(service: AccessibilityService, bounds: Rect): Boolean {
        val x = bounds.centerX().toFloat()
        val bottom = service.resources.displayMetrics.heightPixels + 240f
        val path = Path().apply {
            moveTo(x, bounds.centerY().toFloat())
            lineTo(x, bottom)
        }
        return dispatch(service, path, 380L)
    }

    private suspend fun dispatch(service: AccessibilityService, path: Path, durationMs: Long): Boolean {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val done = CompletableDeferred<Boolean>()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gesture: GestureDescription?) { done.complete(true) }
            override fun onCancelled(gesture: GestureDescription?) { done.complete(false) }
        }
        val sent = runCatching {
            service.dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), callback,
                Handler(Looper.getMainLooper()))
        }.getOrDefault(false)
        return sent && (withTimeoutOrNull(1_200L) { done.await() } ?: false)
    }

    fun reset() { attempted.clear() }
}
