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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegasol.brainstorm.BuildConfig
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
    val batteryOk = remember(resume, c) { Perms.batteryUnrestricted(c) }
    val now by minuteTicker()
    val apps by Graph.db.apps().observeAll().collectAsStateWithLifecycle(emptyList())
    val prefs = Graph.prefs
    val heartbeatFresh = now - prefs.heartbeat < 90_000
    val perAppTotal = remember(apps) { apps.sumOf { (it.timeLimitMin ?: 0) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        Column {
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            LockedNotice()
        }

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
            SettingRow("Enforcement edition", BuildConfig.ENFORCEMENT_MODE)
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
