package com.vegasol.brainstorm.mascot

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.Prefs
import org.json.JSONObject

private const val TAG = "Mascot"

/**
 * Brainy's mood. Maps to the JavaScript state keys in mascot.html.
 * Keep this as the only place where JS keys are defined.
 */
enum class MascotState(val jsKey: String) {
    WELCOME("launch"),
    HAPPY("dashboard"),
    THINKING("thinking"),
    CONCERNED("warning"),
    LIMIT("limit"),
    LOCKED("lock"),          // meditation pose — was "lockout", now "lock"
    CELEBRATING("goal"),
    UNLOCK("unlock"),
    PERMISSION_REQUIRED("permission"),
    MONITORING_PAUSED("error"),
    REPORTS("reports")
}

/** Typed face expressions that exist in Brainy's production FACE table. */
enum class MascotExpression(val jsKey: String) {
    HAPPY("happy"),
    SOFT_SMILE("softSmile"),
    THOUGHTFUL("thoughtful"),
    CONCERNED("concerned"),
    NERVOUS_SMILE("nervousSmile"),
    DISCIPLINED("disciplined"),
    CALM("calm"),
    VERY_HAPPY("veryHappy"),
    PROUD("proud"),
    ATTENTIVE("attentive"),
    DISBELIEF("disbelief"),
    GIGGLE("giggle"),
    SIDE_EYE("sideEye")
}

/**
 * Typed gestures with a working pose mapping in `mascot.html`.
 * The director's walking/dancing states are not exposed, so only these three exist.
 */
enum class MascotGesture(val jsKey: String) {
    FACE_PALM("facePalm"),
    SNAP("snap"),
    CLAP_SLOW("clapSlow")
}

/** Render quality profile matching the JS quality table. */
enum class MascotQuality(val jsKey: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high")
}

/** Three personality axes, each clamped to 0..1. */
data class MascotPersonality(
    val expressiveness: Float = 0.8f,
    val playfulness: Float = 0.65f,
    val calmness: Float = 0.85f
) {
    fun normalized() = copy(
        expressiveness = expressiveness.coerceIn(0f, 1f),
        playfulness = playfulness.coerceIn(0f, 1f),
        calmness = calmness.coerceIn(0f, 1f)
    )
}

/** Persisted mascot appearance preferences. */
data class MascotPreferences(
    val quality: MascotQuality = MascotQuality.NORMAL,
    val personality: MascotPersonality = MascotPersonality(),
    val reduceMotion: Boolean = false,
    val touchReactions: Boolean = true
)

/** A single typed command to send to a slot. */
data class MascotCommand(
    val state: MascotState,
    val message: String? = null,
    val countdownSeconds: Long? = null
)

/**
 * Keeps WebViews alive across recomposition and across LazyColumn item disposal.
 * Sends typed commands through the versioned `window.Brainy` bridge.
 * Falls back gracefully when the bridge is not ready or WebGL is unavailable.
 */
class MascotPool {
    private val views      = HashMap<String, WebView>()
    private val ready      = HashMap<String, Boolean>()
    private val commands   = HashMap<String, MascotCommand>()
    private val prefs      = HashMap<String, MascotPreferences>()

    /** One-shot guard so a broken asset reloads at most once instead of looping. */
    private val retries = HashMap<String, Int>()

    private fun shouldRetry(slot: String): Boolean {
        val used = retries[slot] ?: 0
        if (used >= 1) return false
        retries[slot] = used + 1
        return true
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun view(ctx: Context, slot: String): WebView = views.getOrPut(slot) {
        WebView(ctx).apply {
            setBackgroundColor(0)
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            overScrollMode = WebView.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) {
                    // Verify the bridge API, then replay queued state.
                    v.evaluateJavascript("window.Brainy && window.Brainy.version") { result ->
                        val ver = result?.trim()?.trimEnd('"')?.trimStart('"')?.toIntOrNull() ?: 0
                        if (ver >= 1) {
                            ready[slot] = true
                            Log.d(TAG, "$slot: Brainy bridge v$ver ready")
                            val p = prefs[slot] ?: runCatching { Graph.prefs.mascotPreferences(ctx) }.getOrNull()
                            p?.let { applyPreferences(slot, it, force = true) }
                            commands[slot]?.let { apply(slot, it, force = true) }
                        } else {
                            Log.w(TAG, "$slot: Brainy bridge not found (got $result)")
                            ready[slot] = false
                            if (shouldRetry(slot)) {
                                // A transient staging/asset failure should not leave the slot dead.
                                v.postDelayed({ if (views[slot] === v) v.reload() }, 600)
                            } else {
                                Log.w(TAG, "$slot: falling back to legacy setState in this slot")
                                commands[slot]?.let { cmd ->
                                    v.evaluateJavascript("if(window.setState)setState('${cmd.state.jsKey}')", null)
                                }
                            }
                        }
                    }
                }

                override fun onReceivedError(
                    v: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError
                ) {
                    Log.w(TAG, "load failed $slot: ${error.description} (${request.url})")
                    if (request.isForMainFrame && shouldRetry(slot)) {
                        v.postDelayed({ if (views[slot] === v) v.reload() }, 1_200)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    Log.d(TAG, "$slot: ${m.message()} @${m.lineNumber()}")
                    return true
                }
            }

            loadUrl("file:///android_asset/mascot.html")
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────

    /**
     * Apply a typed command. If the bridge is not ready yet, the command is queued
     * and replayed in onPageFinished. Deduplicates unchanged state+countdown pairs.
     */
    fun apply(slot: String, command: MascotCommand, force: Boolean = false) {
        val prev = commands[slot]
        if (!force && prev?.state == command.state && prev.countdownSeconds == command.countdownSeconds) return
        commands[slot] = command
        val v = views[slot] ?: return
        if (ready[slot] != true) return   // queued; will replay in onPageFinished

        if (force || prev?.state != command.state) {
            val js = buildString {
                append("(function(){")
                append("if(!window.Brainy){if(window.setState)setState('${command.state.jsKey}');return}")
                val opts = JSONObject().apply {
                    command.message?.let { put("message", it) }
                }
                append("Brainy.setState('${command.state.jsKey}',${opts});")
                command.countdownSeconds?.let { secs ->
                    append("Brainy.setCountdown(${secs.coerceAtLeast(0L)});")
                }
                append("})()")
            }
            v.evaluateJavascript(js, null)
        } else if (prev.countdownSeconds != command.countdownSeconds && command.countdownSeconds != null) {
            v.evaluateJavascript("if(window.Brainy)Brainy.setCountdown(${command.countdownSeconds.coerceAtLeast(0L)});", null)
        }
    }

    /** Convenience overload for the common case of just a state. */
    fun apply(slot: String, state: MascotState) = apply(slot, MascotCommand(state))

    fun setCountdown(slot: String, seconds: Long) {
        val v = views[slot] ?: return
        if (ready[slot] != true) return
        v.evaluateJavascript("if(window.Brainy)Brainy.setCountdown(${seconds.coerceAtLeast(0L)})", null)
    }

    // ── Preferences ────────────────────────────────────────────────────────────

    fun applyPreferences(slot: String, p: MascotPreferences, force: Boolean = false) {
        val prev = prefs[slot]
        if (!force && prev == p) return
        prefs[slot] = p
        val v = views[slot] ?: return
        if (ready[slot] != true) return   // queued; will replay in onPageFinished

        val norm = p.personality.normalized()
        val personalityJson = JSONObject().apply {
            put("expressiveness", norm.expressiveness.toDouble())
            put("playfulness", norm.playfulness.toDouble())
            put("calmness", norm.calmness.toDouble())
        }
        val js = buildString {
            append("(function(){if(!window.Brainy)return;")
            append("Brainy.setQuality('${p.quality.jsKey}');")
            append("Brainy.setPersonality($personalityJson);")
            append("Brainy.setReducedMotion(${if (p.reduceMotion) "true" else "false"});")
            append("Brainy.setInteractive(${if (p.touchReactions) "true" else "false"});")
            append("})()")
        }
        v.evaluateJavascript(js, null)
    }

    // ── Expressions & Gestures ─────────────────────────────────────────────────

    fun playExpression(slot: String, expression: MascotExpression) = playExpression(slot, expression.jsKey)

    fun playExpression(slot: String, expression: String) {
        val v = views[slot] ?: return
        if (ready[slot] != true) return
        v.evaluateJavascript("if(window.Brainy)Brainy.playExpression('$expression')", null)
    }

    fun playGesture(slot: String, gesture: MascotGesture) = playGesture(slot, gesture.jsKey)

    fun playGesture(slot: String, gesture: String) {
        val v = views[slot] ?: return
        if (ready[slot] != true) return
        v.evaluateJavascript("if(window.Brainy)Brainy.playGesture('$gesture')", null)
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun pause(slot: String) {
        views[slot]?.evaluateJavascript(
            "if(window.Brainy)Brainy.pause();else if(window.brainyPause)brainyPause()", null
        )
    }

    fun resume(slot: String) {
        views[slot]?.evaluateJavascript(
            "if(window.Brainy)Brainy.resume();else if(window.brainyResume)brainyResume()", null
        )
    }

    fun disposeSlot(slot: String) {
        views[slot]?.let { v ->
            runCatching { (v.parent as? android.view.ViewGroup)?.removeView(v) }
            v.evaluateJavascript("if(window.Brainy)Brainy.dispose()", null)
            runCatching { v.destroy() }
        }
        views.remove(slot)
        ready.remove(slot)
        commands.remove(slot)
        prefs.remove(slot)
        retries.remove(slot)
    }

    fun dispose() {
        views.keys.toList().forEach { disposeSlot(it) }
    }
}

/** Check whether system-level animation reduction is turned on in Android settings. */
fun isSystemReducedMotion(context: Context): Boolean = try {
    android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f
    ) == 0f
} catch (e: Exception) {
    false
}

/** Read typed mascot preferences from persisted app preferences. */
fun Prefs.mascotPreferences(context: Context? = null): MascotPreferences {
    val q = when (mascotQuality.lowercase()) {
        "low" -> MascotQuality.LOW
        "high" -> MascotQuality.HIGH
        else -> MascotQuality.NORMAL
    }
    val systemRm = context?.let { isSystemReducedMotion(it) } ?: false
    return MascotPreferences(
        quality = q,
        personality = MascotPersonality(
            expressiveness = mascotExpressiveness,
            playfulness = mascotPlayfulness,
            calmness = mascotCalmness
        ),
        reduceMotion = mascotReduceMotion || systemRm,
        touchReactions = mascotTouchEnabled
    )
}

@Composable
fun rememberMascotPool(): MascotPool = remember { MascotPool() }

/**
 * A slot in the pool. The animation loop is stopped while off-composition so only
 * on-screen mascots cost frames. Automatically applies persisted mascot preferences.
 */
@Composable
fun MascotSlot(
    pool: MascotPool,
    slot: String,
    mood: MascotState,
    modifier: Modifier = Modifier,
    command: MascotCommand = MascotCommand(mood),
    preferences: MascotPreferences? = null
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentPrefs = preferences ?: remember(
        Graph.prefs.mascotQuality,
        Graph.prefs.mascotExpressiveness,
        Graph.prefs.mascotPlayfulness,
        Graph.prefs.mascotCalmness,
        Graph.prefs.mascotReduceMotion,
        Graph.prefs.mascotTouchEnabled
    ) {
        runCatching { Graph.prefs.mascotPreferences(ctx) }.getOrNull() ?: MascotPreferences()
    }

    // Stop animation work when the activity is not visible. onRelease only covers
    // composition changes; without this a backgrounded dashboard keeps animating.
    DisposableEffect(lifecycle, slot) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> pool.pause(slot)
                Lifecycle.Event.ON_RESUME -> pool.resume(slot)
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { c ->
            pool.view(c, slot).also {
                pool.applyPreferences(slot, currentPrefs)
                pool.apply(slot, command)
                pool.resume(slot)
            }
        },
        update = {
            pool.applyPreferences(slot, currentPrefs)
            pool.apply(slot, command)
        },
        onRelease = { pool.pause(slot) }
    )
}
