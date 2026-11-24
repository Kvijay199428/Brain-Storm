package com.vegasol.brainstorm

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.delay

enum class PipDismissResult { DISMISSED, NO_CONTROL, FAILED, SKIPPED }

object PipDismissalRules {
    /** One controlled attempt per PiP appearance; no infinite clicking. */
    fun shouldAttempt(enabled: Boolean, lastAttemptAt: Long?, now: Long, cooldownMs: Long): Boolean =
        enabled && (lastAttemptAt == null || now - lastAttemptAt >= cooldownMs)
}

/**
 * Best-effort, opt-in automation of Android's own PiP Close control.
 *
 * Only a verified, clickable close node in a System UI window is clicked. A single gesture may be
 * used to reveal the controls on the restricted PiP window itself. No arbitrary coordinates are
 * clicked, and no third-party app code is invoked directly.
 */
object PipDismissalController {
    private const val COOLDOWN_MS = 60_000L
    private val attempted = HashMap<String, Long>()

    suspend fun attempt(service: AccessibilityService, pkg: String, pipBounds: Rect): PipDismissResult {
        if (!Graph.prefs.pipAutoDismiss) return PipDismissResult.SKIPPED
        val now = System.currentTimeMillis()
        if (!PipDismissalRules.shouldAttempt(true, attempted[pkg], now, COOLDOWN_MS)) return PipDismissResult.SKIPPED
        attempted[pkg] = now

        var node = findCloseNode(service)
        if (node == null && canGesture(service)) {
            revealControls(service, pipBounds)
            delay(500L)
            node = findCloseNode(service)
        }
        val control = node ?: return PipDismissResult.NO_CONTROL
        val clicked = runCatching { control.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
        if (!clicked) return PipDismissResult.FAILED
        delay(600L)
        val stillPip = service.windows.orEmpty().any { window ->
            window.root?.packageName?.toString() == pkg && isSmall(window, service)
        }
        return if (stillPip) PipDismissResult.FAILED else PipDismissResult.DISMISSED
    }

    private fun isSmall(window: AccessibilityWindowInfo, service: AccessibilityService): Boolean {
        val bounds = Rect()
        window.getBoundsInScreen(bounds)
        val metrics = service.resources.displayMetrics
        return PipWindowDetector.isRestrictedPip(
            PipWindowDetector.Window(window.root?.packageName?.toString(), true, false, bounds.width(), bounds.height()),
            setOf(window.root?.packageName?.toString().orEmpty()), metrics.widthPixels, metrics.heightPixels
        )
    }

    private fun canGesture(service: AccessibilityService): Boolean {
        val info = service.serviceInfo ?: return false
        return info.capabilities and
            android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES != 0
    }

    private fun findCloseNode(service: AccessibilityService): AccessibilityNodeInfo? {
        for (window in service.windows.orEmpty()) {
            val root = window.root ?: continue
            val systemUi = (root.packageName?.toString().orEmpty()).contains("systemui", ignoreCase = true)
            if (!systemUi) continue
            findClose(root)?.let { return it }
        }
        return null
    }

    private fun findClose(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable && isCloseControl(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findClose(child)?.let { return it }
        }
        return null
    }

    private fun isCloseControl(node: AccessibilityNodeInfo): Boolean {
        val label = "${node.contentDescription ?: ""} ${node.viewIdResourceName ?: ""}".lowercase()
        return listOf("close", "dismiss", "pip_close", "close_button").any { label.contains(it) }
    }

    private fun revealControls(service: AccessibilityService, bounds: Rect) {
        val x = bounds.centerX().toFloat()
        val y = bounds.centerY().toFloat()
        val path = Path().apply { moveTo(x, y); lineTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        runCatching { service.dispatchGesture(gesture, null, Handler(Looper.getMainLooper())) }
    }

    fun reset() { attempted.clear() }
}
