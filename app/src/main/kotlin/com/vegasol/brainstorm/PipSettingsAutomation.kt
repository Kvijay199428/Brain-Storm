package com.vegasol.brainstorm

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class PipAutomationState {
    object Idle : PipAutomationState()
    data class Running(val index: Int, val total: Int, val pkg: String, val step: String) : PipAutomationState()
    data class Finished(val disabled: List<String>, val failed: List<String>) : PipAutomationState()
    data class Failed(val reason: String) : PipAutomationState()
}

/**
 * User-initiated, opt-in automation of Android's own PiP Settings screens.
 *
 * It only ever clicks nodes whose text is clearly the Picture-in-picture row and switches that are
 * reported as checked. If anything is ambiguous it aborts rather than clicking an unrelated setting.
 */
object PipSettingsAutomation {
    private const val MAX_EVENTS_PER_STEP = 40
    private val _state = MutableStateFlow<PipAutomationState>(PipAutomationState.Idle)
    val state: StateFlow<PipAutomationState> = _state

    private var queue: List<String> = emptyList()
    private var index = 0
    private var awaitingSwitch = false
    private var events = 0
    private val disabled = ArrayList<String>()
    private val failed = ArrayList<String>()

    fun start(packages: List<String>) {
        if (packages.isEmpty()) return
        queue = packages
        index = 0
        events = 0
        awaitingSwitch = false
        disabled.clear()
        failed.clear()
        openCurrent()
    }

    fun cancel() {
        queue = emptyList()
        _state.value = PipAutomationState.Idle
    }

    private fun openCurrent() {
        if (index >= queue.size) {
            _state.value = PipAutomationState.Finished(disabled.toList(), failed.toList())
            queue = emptyList()
            return
        }
        events = 0
        awaitingSwitch = false
        val pkg = queue[index]
        _state.value = PipAutomationState.Running(index + 1, queue.size, pkg, "Opening app settings")
        runCatching {
            Graph.app.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { abort("Cannot open settings for $pkg") }
    }

    /** Called by the lockout accessibility service on every relevant window event. */
    fun onWindowEvent(service: AccessibilityService) {
        val current = _state.value as? PipAutomationState.Running ?: return
        if (++events > MAX_EVENTS_PER_STEP) {
            failed += current.pkg
            index++
            openCurrent()
            return
        }
        val root = service.windows.orEmpty().firstOrNull { window ->
            (window.root?.packageName?.toString().orEmpty()).contains("settings", ignoreCase = true)
        }?.root ?: return

        if (!awaitingSwitch) {
            val row = findNode(root) { node ->
                val text = node.text?.toString().orEmpty()
                text.contains("picture-in-picture", ignoreCase = true) ||
                    text.equals("PiP", ignoreCase = true)
            }
            if (row != null && click(row)) {
                awaitingSwitch = true
                events = 0
                _state.value = current.copy(step = "Turning PiP off")
            }
            return
        }

        val switches = mutableListOf<AccessibilityNodeInfo>()
        collect(root) { node ->
            if (node.isCheckable && (node.className?.toString().orEmpty()).contains("Switch", ignoreCase = true)) {
                switches += node
            }
        }
        val target = switches.firstOrNull { it.isChecked } ?: switches.firstOrNull()
        if (target == null) return
        if (!target.isChecked || click(target)) {
            verifyAndAdvance(current.pkg)
        }
    }

    private fun verifyAndAdvance(pkg: String) {
        PipCapabilityScanner.refresh(pkg)
        val state = PipCapabilityScanner.scan(Graph.app, pkg)
        if (state == PipCapability.SUPPORTED_DISABLED) disabled += pkg else failed += pkg
        index++
        openCurrent()
    }

    private fun abort(reason: String) {
        failed.addAll(queue.drop(index))
        _state.value = PipAutomationState.Failed(reason)
        queue = emptyList()
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        for (i in 0 until 4) {
            val n = target ?: break
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            target = n.parent
        }
        return false
    }

    private fun findNode(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var found: AccessibilityNodeInfo? = null
        collect(root) { node -> if (found == null && match(node)) found = node }
        return found
    }

    private fun collect(node: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo) -> Unit) {
        visit(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, visit)
        }
    }
}
