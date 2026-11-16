package com.vegasol.brainstorm.mascot

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private const val TAG = "Mascot"

/**
 * Brainy's mood. The enum is the only thing the UI is allowed to pass around; the mapping to a
 * pose in `mascot.html` lives here so a rename can never silently fall back to a default face.
 */
enum class MascotState(val htmlKey: String) {
    WELCOME("welcome"),
    HAPPY("idle"),
    THINKING("thinking"),
    CONCERNED("warning"),
    LIMIT("limit"),
    LOCKED("lockout"),
    CELEBRATING("success"),
    UNLOCK("unlock"),
    PERMISSION_REQUIRED("permission")
}

/**
 * Keeps the WebViews alive across recomposition *and* across LazyColumn item disposal.
 * Without this, scrolling the advice card off-screen destroys the WebView and three.js reloads
 * the whole scene when it comes back — a visible white flash on every pass.
 */
class MascotPool {
    private val views = HashMap<String, WebView>()
    private val moods = HashMap<String, MascotState>()

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
                    apply(slot, moods[slot] ?: MascotState.HAPPY)
                }

                override fun onReceivedError(
                    v: WebView, request: android.webkit.WebResourceRequest, error: android.webkit.WebResourceError
                ) {
                    Log.w(TAG, "load failed $slot: ${error.description} (${request.url})")
                }
            }
            // Console output is the only way to see why the mascot failed to boot on a device.
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                    Log.d(TAG, "$slot: ${m.message()} @${m.lineNumber()}")
                    return true
                }
            }
            loadUrl("file:///android_asset/mascot.html")
        }
    }

    fun apply(slot: String, mood: MascotState) {
        if (moods[slot] == mood) return
        moods[slot] = mood
        views[slot]?.evaluateJavascript("if(window.setState)setState('${mood.htmlKey}')", null)
    }

    fun pause(slot: String) {
        views[slot]?.evaluateJavascript("if(window.brainyPause)brainyPause()", null)
    }

    fun resume(slot: String) {
        views[slot]?.evaluateJavascript("if(window.brainyResume)brainyResume()", null)
    }

    fun dispose() {
        views.values.forEach { runCatching { it.destroy() } }
        views.clear()
        moods.clear()
    }
}

@Composable
fun rememberMascotPool(): MascotPool = remember { MascotPool() }

/**
 * A slot in the pool. The animation loop is stopped while the slot is off-composition so only
 * the on-screen mascot costs frames.
 */
@Composable
fun MascotSlot(pool: MascotPool, slot: String, mood: MascotState, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            pool.view(ctx, slot).also {
                pool.apply(slot, mood)
                pool.resume(slot)
            }
        },
        update = { pool.apply(slot, mood) },
        onRelease = { pool.pause(slot) }
    )
}
