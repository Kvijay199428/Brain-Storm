package com.vegasol.brainstorm

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.vegasol.brainstorm.feature.lockout.LockoutScreen
import com.vegasol.brainstorm.feature.setup.SetupWizard
import com.vegasol.brainstorm.feature.shell.MainShell
import com.vegasol.brainstorm.theme.BrainStormTheme
import com.vegasol.brainstorm.theme.Care

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        // Before super.onCreate: the splash is ours to hold from the first frame.
        val splash = installSplashScreen()
        super.onCreate(b)
        val launchedAt = SystemClock.uptimeMillis()
        // Room needs a moment on a cold start, and dropping the splash early used to expose an
        // empty dashboard that flashed "Finish setup" and then real numbers. Holding the branded
        // splash removes that gap entirely. The cap is a safety net: if the database never
        // answers, the user still gets the app instead of a frozen logo.
        splash.setKeepOnScreenCondition {
            !Startup.dashboardReady.get() && SystemClock.uptimeMillis() - launchedAt < MAX_SPLASH_HOLD_MS
        }
        edgeToEdge()
        Graph.init(this)
        setContent {
            BrainStormTheme {
                Surface(Modifier.fillMaxSize(), color = Care.Background) { Root() }
            }
        }
    }

    private companion object {
        const val MAX_SPLASH_HOLD_MS = 2_500L
    }
}

@Composable
fun Root() {
    // The wizard runs exactly once. `setupLocked` is the authoritative freeze flag; `onboarded`
    // is kept as a fallback for installs that finished setup before the flag existed.
    var frozen by remember { mutableStateOf(Graph.prefs.setupLocked || Graph.prefs.onboarded) }
    // First run has no dashboard to wait for, so the wizard must not sit behind the splash.
    LaunchedEffect(frozen) { if (!frozen) Startup.dashboardReady.set(true) }
    if (!frozen) SetupWizard { frozen = true } else MainShell()
}

/**
 * The app is dark-only in this pass, so both system bars always need light icons. The stock
 * `enableEdgeToEdge()` follows the *system* theme: on a phone in light mode it would draw dark
 * status-bar icons over our dark background, which is how the monitoring icon became invisible.
 */
private fun ComponentActivity.edgeToEdge() = enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
)

/** Lock screen shown over a locked app: mascot, reason, live countdown, exact unlock time. */
class BlockingActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        edgeToEdge()
        Graph.init(this)
        val pkg = intent.getStringExtra("pkg") ?: ""
        setContent {
            BrainStormTheme {
                BackHandler { goHome() }
                Surface(Modifier.fillMaxSize(), color = Care.Background) {
                    LockoutScreen(pkg) { goHome() }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }
}
