package com.vegasol.brainstorm.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegasol.brainstorm.BuildConfig
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.update.InstallStart
import com.vegasol.brainstorm.update.UpdateState
import java.util.concurrent.TimeUnit

/**
 * Update controls. Everything shown here is what actually happened: no "up to date" unless a
 * release list really was read, no "installed" unless Android reported success.
 */
@Composable
fun UpdatesCard() {
    val updates = Graph.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val lastCheck = remember(state) { updates.lastChecked }
    val installResult = remember(state) { updates.lastInstallResult }

    LaunchedEffect(Unit) { updates.maybeCheck() }

    SurfaceCard(Modifier.fillMaxWidth()) {
        SectionHeader("Updates")
        Spacer(Modifier.height(Space.s))
        SettingRow("Installed", "${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}")
        SettingRow("Channel", BuildConfig.CHANNEL.uppercase())
        SettingRow("Last checked", if (lastCheck == 0L) "never" else ago(lastCheck))

        val busy = state is UpdateState.Checking || state is UpdateState.Downloading
        when (val s = state) {
            is UpdateState.Checking -> Status("Checking GitHub…", Care.TextSecondary)
            is UpdateState.UpToDate -> Status("You are on the newest ${BuildConfig.CHANNEL} build.", Care.Success)
            is UpdateState.Failed -> Status(s.message, Care.Danger)
            is UpdateState.Available ->
                Status("Version ${s.info.versionName} (build ${s.info.versionCode}) is available.", Care.Primary)
            is UpdateState.NeedsPermission ->
                Status("Downloaded and verified. Allow Brain Storm to install apps, then install again.", Care.Warning)
            is UpdateState.Downloading -> {
                Status("Downloading… ${s.percent}%", Care.TextSecondary)
                UsageProgressBar(s.percent.toFloat(), Modifier.padding(top = 6.dp), 6.dp, Care.Primary)
            }
            is UpdateState.Ready -> Status("Verified. Opening the installer…", Care.Success)
            is UpdateState.InstallStarted -> when (s.outcome) {
                InstallStart.Silent -> Status("Installed silently (Device Owner).", Care.Success)
                InstallStart.SystemInstaller ->
                    Status("Android's installer is open — confirm there to finish.", Care.Primary)
                else -> Status("Install could not start.", Care.Danger)
            }
            UpdateState.Unknown -> Unit
        }

        if (installResult != null) {
            Status(
                installResult,
                if (installResult.startsWith("Update installed")) Care.Success else Care.Warning
            )
        }

        Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            OutlinedButton(
                { updates.check() },
                shape = RoundedCornerShape(Radius.button),
                enabled = !busy
            ) { Text("Check now", fontSize = 12.sp) }
            when (val s = state) {
                is UpdateState.Available -> Button(
                    { updates.downloadAndInstall(s.info) },
                    shape = RoundedCornerShape(Radius.button),
                    enabled = !busy
                ) { Text("Download & install", fontSize = 12.sp) }
                is UpdateState.NeedsPermission -> Button(
                    { updates.openInstallPermissionSettings() },
                    shape = RoundedCornerShape(Radius.button)
                ) { Text("Allow installs", fontSize = 12.sp) }
                is UpdateState.Failed -> Button(
                    { updates.check() },
                    shape = RoundedCornerShape(Radius.button)
                ) { Text("Retry", fontSize = 12.sp) }
                else -> Unit
            }
        }

        Text(
            "Updates are read from GitHub Releases and checked twice before anything installs: the " +
                "SHA-256 from version.json must match the file, and its signing certificate must be " +
                "the one that signed this installation. Android always shows its install dialog on " +
                "this edition — a silent update is only possible for a Device Owner build.",
            fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = Space.s)
        )
        if (installResult != null) {
            OutlinedButton({ updates.clearInstallResult() }, Modifier.padding(top = 4.dp)) {
                Text("Dismiss", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Status(text: String, color: Color) {
    Text(text, fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 6.dp))
}

private fun ago(at: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - at)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 60 * 48 -> "${minutes / 60} h ago"
        else -> "${minutes / (60 * 24)} d ago"
    }
}
