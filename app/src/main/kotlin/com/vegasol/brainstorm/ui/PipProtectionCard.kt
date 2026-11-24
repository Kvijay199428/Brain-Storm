package com.vegasol.brainstorm.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.MonitoredApp
import com.vegasol.brainstorm.PipCapability
import com.vegasol.brainstorm.PipCapabilityRules
import com.vegasol.brainstorm.PipCapabilityScanner
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Space
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only PiP capability report plus the opt-in close-control toggle. */
@Composable
fun PipProtectionCard() {
    val c = LocalContext.current
    val resume = rememberResumeTick()
    var apps by remember { mutableStateOf<List<MonitoredApp>>(emptyList()) }
    var caps by remember { mutableStateOf<Map<String, PipCapability>>(emptyMap()) }
    var autoDismiss by remember { mutableStateOf(Graph.prefs.pipAutoDismiss) }

    LaunchedEffect(resume) {
        val monitored = Graph.db.apps().enabled()
        apps = monitored
        caps = withContext(Dispatchers.IO) {
            PipCapabilityScanner.scanAll(c, monitored.map { it.packageName })
        }
    }

    SurfaceCard(Modifier.fillMaxWidth()) {
        SectionHeader("Picture-in-picture protection")
        Spacer(Modifier.height(Space.s))
        Text(
            "Watch which apps can float video over the screen. Brain Storm cannot change another " +
                "app's PiP permission — open its settings and switch it off yourself.",
            fontSize = 11.sp, color = Care.TextSecondary
        )
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Try to close PiP automatically", fontSize = 13.sp)
                Text(
                    "Opt-in beta: taps Android's own PiP Close control. Best effort; works only " +
                        "where the control is exposed.",
                    fontSize = 10.sp, color = Care.Warning
                )
            }
            Switch(
                checked = autoDismiss,
                onCheckedChange = {
                    autoDismiss = it
                    Graph.prefs.pipAutoDismiss = it
                }
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "Use 'Disable PiP for all selected apps' near the top of Settings to change these " +
                "automatically, or open an app's settings below to change it manually.",
            fontSize = 11.sp, color = Care.TextSecondary
        )
        Spacer(Modifier.height(Space.s))
        apps.forEach { app ->
            val capability = caps[app.packageName] ?: PipCapability.UNKNOWN
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(app.appName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        PipCapabilityRules.label(capability),
                        fontSize = 10.sp,
                        color = if (capability == PipCapability.SUPPORTED_ALLOWED ||
                            capability == PipCapability.DEFAULT_ALLOWED
                        ) Care.Warning else Care.TextSecondary
                    )
                }
                TextButton({
                    runCatching {
                        c.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${app.packageName}")
                            )
                        )
                    }
                }) { Text("Settings", fontSize = 11.sp) }
            }
        }
    }
}
