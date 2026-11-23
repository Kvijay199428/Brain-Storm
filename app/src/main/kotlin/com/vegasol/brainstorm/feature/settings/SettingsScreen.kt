package com.vegasol.brainstorm.feature.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegasol.brainstorm.BuildConfig
import com.vegasol.brainstorm.EnforcementProvider
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.MonitorService
import com.vegasol.brainstorm.Perms
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.LockedNotice
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.minuteTicker
import com.vegasol.brainstorm.ui.rememberResumeTick

/** Read-only by design: every policy value here was fixed by the one-time setup. */
@Composable
fun SettingsScreen() {
    val c = LocalContext.current
    val resume = rememberResumeTick()
    val accessibility = remember(resume, c) { Perms.accessibility(c) }
    val pipDetection = remember(resume, c) { Perms.lockoutAccessibility(c) }
    val notificationAccess = remember(resume, c) { Perms.notificationAccess(c) }
    val batteryOk = remember(resume, c) { Perms.batteryUnrestricted(c) }
    val now by minuteTicker()
    val apps by Graph.db.apps().observeAll().collectAsStateWithLifecycle(emptyList())
    val prefs = Graph.prefs
    val heartbeatFresh = now - prefs.heartbeat < 90_000
    val perAppTotal = remember(apps) { apps.sumOf { (it.timeLimitMin ?: 0) } }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        Column(Modifier.padding(top = Space.l)) {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            LockedNotice()
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
        SurfaceCard(Modifier.fillMaxWidth()) {
            SectionHeader("Policy · locked")
            Spacer(Modifier.height(Space.s))
            SettingRow("Daily budget", fmtHm(prefs.budgetMin * 60L))
            SettingRow("Per-app limits total", fmtHm(perAppTotal * 60L))
            SettingRow("Lock duration", "${prefs.lockHours} h")
            SettingRow("Daily reset hour", "${prefs.resetHour}:00")
            SettingRow("Monitored apps", "${apps.size}")
            HorizontalDivider(Modifier.padding(vertical = Space.s))
            Text(
                "These values were chosen once in setup. Brain Storm offers no way to change them " +
                    "from inside the app — that is the point.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
        }

        SurfaceCard(Modifier.fillMaxWidth()) {
            SectionHeader("Monitoring")
            Spacer(Modifier.height(Space.s))
            SettingRow(
                "Usage access",
                if (Perms.usage(c)) "granted" else "missing",
                good = Perms.usage(c)
            )
            SettingRow(
                "Display over other apps",
                if (Perms.overlay(c)) "granted" else "missing",
                good = Perms.overlay(c)
            )
            SettingRow(
                "Service heartbeat",
                if (heartbeatFresh) "current" else "stale",
                good = heartbeatFresh
            )
            SettingRow("Enforcement edition", EnforcementProvider.edition(c))
            SettingRow("Limit evaluation", "OR · first limit reached")
            SettingRow("Early unlock", "disabled")
            HorizontalDivider(Modifier.padding(vertical = Space.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedButton(
                    { c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    shape = RoundedCornerShape(Radius.button)
                ) { Text("Usage settings", fontSize = 12.sp) }
                OutlinedButton(
                    {
                        c.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
                        )
                    },
                    shape = RoundedCornerShape(Radius.button)
                ) { Text("Overlay settings", fontSize = 12.sp) }
            }
            Button(
                { MonitorService.start(c) },
                Modifier.fillMaxWidth().padding(top = Space.s),
                shape = RoundedCornerShape(Radius.button)
            ) { Text("Restart monitoring") }
        }

        SurfaceCard(Modifier.fillMaxWidth()) {
            SectionHeader("Background reliability")
            Spacer(Modifier.height(Space.s))
            SettingRow(
                "Battery optimisation",
                if (batteryOk) "unrestricted" else "optimised · may pause",
                good = batteryOk
            )
            HorizontalDivider(Modifier.padding(vertical = Space.s))
            Text(
                "Android may pause background apps it considers idle, and some manufacturers " +
                    "(Xiaomi, Oppo, vivo, Samsung) are stricter than the platform default. If the " +
                    "heartbeat goes stale, allow Brain Storm to run in the background in the " +
                    "system battery settings. Monitoring restarts by itself after a reboot or an " +
                    "app update.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
            OutlinedButton(
                { c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                Modifier.fillMaxWidth().padding(top = Space.m),
                shape = RoundedCornerShape(Radius.button)
            ) { Text("Battery settings", fontSize = 12.sp) }
        }

        UpdatesCard()

        // The mascot playground is a beta-only experiment: stable users get a smaller, calmer
        // Settings page, while beta testers keep the controls to exercise it.
        if (BuildConfig.CHANNEL.lowercase() != "stable") {
            BrainyMascotCard()
        }

        SurfaceCard(Modifier.fillMaxWidth()) {
            SectionHeader("Content detection · experimental")
            Spacer(Modifier.height(Space.s))
            SettingRow(
                "YouTube Shorts counting",
                if (accessibility) "on" else "off",
                good = if (accessibility) true else null
            )
            HorizontalDivider(Modifier.padding(vertical = Space.s))
            Text(
                "Off by default. Turning it on opens Android's accessibility screen, where you " +
                    "allow Brain Storm to read the on-screen labels of apps that have a detector " +
                    "(currently YouTube Shorts). Labels are analysed on this device and are never " +
                    "stored or uploaded. Counts are informational: locks still trigger on time " +
                    "only. Android may turn this off after an app update, or ask you to allow " +
                    "restricted settings for a sideloaded build.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
            OutlinedButton(
                { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                Modifier.fillMaxWidth().padding(top = Space.m),
                shape = RoundedCornerShape(Radius.button)
            ) {
                Text(
                    if (accessibility) "Accessibility settings" else "Turn on content detection",
                    fontSize = 12.sp
                )
            }
        }

        SurfaceCard(Modifier.fillMaxWidth()) {
            SectionHeader("Lockout detection · beta")
            Spacer(Modifier.height(Space.s))
            SettingRow(
                "PiP window detection",
                if (pipDetection) "on" else "off",
                good = if (pipDetection) true else null
            )
            SettingRow(
                "Playback detection",
                if (notificationAccess) "on" else "off",
                good = if (notificationAccess) true else null
            )
            HorizontalDivider(Modifier.padding(vertical = Space.s))
            Text(
                "Both are off by default and opt in through Android settings. They can show the " +
                    "blocking screen when a locked app is visible in picture-in-picture or is still " +
                    "playing in the background. On a standard install Android does not let Brain " +
                    "Storm stop that playback; Device Owner package suspension is the only full " +
                    "enforcement path.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
            Row(
                Modifier.fillMaxWidth().padding(top = Space.m),
                horizontalArrangement = Arrangement.spacedBy(Space.s)
            ) {
                OutlinedButton(
                    { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    Modifier.weight(1f), shape = RoundedCornerShape(Radius.button)
                ) { Text("PiP access", fontSize = 12.sp) }
                OutlinedButton(
                    { c.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    Modifier.weight(1f), shape = RoundedCornerShape(Radius.button)
                ) { Text("Playback access", fontSize = 12.sp) }
            }
        }

        SurfaceCard(Modifier.fillMaxWidth(), color = Care.SurfaceVariant.copy(alpha = 0.5f)) {
            Text("Privacy", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                "Usage is read from Android's own event log on this device. If you turn on " +
                    "content detection, on-screen labels from the detected apps are analysed on " +
                    "the device. Nothing is uploaded and there is no account.",
                fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
            )
        }

        Text(
            "Clearing app data or uninstalling resets every policy value. Android always allows " +
                "that — Brain Storm is high-friction, not tamper-proof.",
            fontSize = 10.sp, color = Care.TextSecondary
        )
        Spacer(Modifier.height(Space.section))
        }
    }
}

@Composable
internal fun SettingRow(label: String, value: String, good: Boolean? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(8.dp))
            .background(Care.Background.copy(alpha = 0.0f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when (good) {
                true -> Care.Success
                false -> Care.Danger
                null -> Care.TextPrimary
            }
        )
    }
}

@Composable
fun BrainyMascotCard() {
    val prefs = Graph.prefs
    var quality by remember { mutableStateOf(prefs.mascotQuality) }
    var expressiveness by remember { mutableFloatStateOf(prefs.mascotExpressiveness) }
    var playfulness by remember { mutableFloatStateOf(prefs.mascotPlayfulness) }
    var calmness by remember { mutableFloatStateOf(prefs.mascotCalmness) }
    var reduceMotion by remember { mutableStateOf(prefs.mascotReduceMotion) }
    var touchReactions by remember { mutableStateOf(prefs.mascotTouchEnabled) }

    SurfaceCard(Modifier.fillMaxWidth()) {
        SectionHeader("Brainy Mascot")
        Spacer(Modifier.height(Space.s))

        Text("Render quality", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().padding(vertical = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.s)
        ) {
            listOf("low" to "Low", "normal" to "Normal", "high" to "High").forEach { (key, label) ->
                val selected = quality.equals(key, ignoreCase = true)
                if (selected) {
                    Button(
                        onClick = { quality = key; prefs.mascotQuality = key },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.button)
                    ) { Text(label, fontSize = 12.sp) }
                } else {
                    OutlinedButton(
                        onClick = { quality = key; prefs.mascotQuality = key },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radius.button)
                    ) { Text(label, fontSize = 12.sp) }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = Space.s))
        Text("Personality", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text("Fine-tune Brainy's responsiveness and demeanor.", fontSize = 11.sp, color = Care.TextSecondary)

        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Expressiveness", Modifier.weight(1f), fontSize = 12.sp)
            Text("${(expressiveness * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = expressiveness,
            onValueChange = { expressiveness = it; prefs.mascotExpressiveness = it },
            valueRange = 0f..1f
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Playfulness", Modifier.weight(1f), fontSize = 12.sp)
            Text("${(playfulness * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = playfulness,
            onValueChange = { playfulness = it; prefs.mascotPlayfulness = it },
            valueRange = 0f..1f
        )

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Calmness", Modifier.weight(1f), fontSize = 12.sp)
            Text("${(calmness * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = calmness,
            onValueChange = { calmness = it; prefs.mascotCalmness = it },
            valueRange = 0f..1f
        )

        OutlinedButton(
            onClick = {
                prefs.resetMascotPersonality()
                expressiveness = prefs.mascotExpressiveness
                playfulness = prefs.mascotPlayfulness
                calmness = prefs.mascotCalmness
            },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = RoundedCornerShape(Radius.button)
        ) { Text("Reset personality", fontSize = 12.sp) }

        HorizontalDivider(Modifier.padding(vertical = Space.s))

        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reduce motion", fontSize = 13.sp)
                Text("Subdued poses and slower, minimal movement", fontSize = 11.sp, color = Care.TextSecondary)
            }
            Switch(
                checked = reduceMotion,
                onCheckedChange = { reduceMotion = it; prefs.mascotReduceMotion = it }
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Touch reactions", fontSize = 13.sp)
                Text("Tap or boop Brainy on the home screen", fontSize = 11.sp, color = Care.TextSecondary)
            }
            Switch(
                checked = touchReactions,
                onCheckedChange = { touchReactions = it; prefs.mascotTouchEnabled = it }
            )
        }
    }
}
