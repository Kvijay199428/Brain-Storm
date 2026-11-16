package com.vegasol.brainstorm

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vegasol.brainstorm.feature.lockout.LockoutScreen
import com.vegasol.brainstorm.feature.setup.SetupWizard
import com.vegasol.brainstorm.feature.shell.MainShell
import com.vegasol.brainstorm.theme.BrainStormTheme
import com.vegasol.brainstorm.theme.Care

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
        Graph.init(this)
        setContent {
            BrainStormTheme {
                Surface(Modifier.fillMaxSize(), color = Care.Background) { Root() }
            }
        }
    }
}

@Composable
fun Root() {
    // The wizard runs exactly once. `setupLocked` is the authoritative freeze flag; `onboarded`
    // is kept as a fallback for installs that finished setup before the flag existed.
    var frozen by remember { mutableStateOf(Graph.prefs.setupLocked || Graph.prefs.onboarded) }
    if (!frozen) SetupWizard { frozen = true } else MainShell()
}

/** Lock screen shown over a locked app: mascot, reason, live countdown, exact unlock time. */
class BlockingActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        enableEdgeToEdge()
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
