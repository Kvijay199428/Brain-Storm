package com.vegasol.brainstorm.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.feature.dashboard.DashboardUiState
import com.vegasol.brainstorm.feature.dashboard.ProtectionStatus
import com.vegasol.brainstorm.mascot.MascotPool
import com.vegasol.brainstorm.mascot.MascotSlot
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.BrainStormAppIcon
import com.vegasol.brainstorm.ui.Pill
import com.vegasol.brainstorm.ui.SurfaceCard

fun protectionLabel(p: ProtectionStatus): String = when (p) {
    ProtectionStatus.ACTIVE -> "Protection active"
    ProtectionStatus.PARTIAL -> "Partially protected"
    ProtectionStatus.PERMISSION_REQUIRED -> "Permission missing"
    ProtectionStatus.MONITORING_PAUSED -> "Monitoring paused"
}

fun protectionColor(p: ProtectionStatus): Color = when (p) {
    ProtectionStatus.ACTIVE -> Care.Success
    ProtectionStatus.PARTIAL -> Care.Warning
    ProtectionStatus.PERMISSION_REQUIRED -> Care.Danger
    ProtectionStatus.MONITORING_PAUSED -> Care.Warning
}

private fun heroCopy(p: ProtectionStatus): Pair<String, String> = when (p) {
    ProtectionStatus.ACTIVE -> "You're in control!" to "Your digital habits are being monitored."
    ProtectionStatus.PARTIAL -> "Partly protected" to "Blocking is best-effort right now — see the warning below."
    ProtectionStatus.PERMISSION_REQUIRED -> "Finish setup" to "Usage access is needed before I can watch your limits."
    ProtectionStatus.MONITORING_PAUSED -> "I can't see anything" to "Monitoring stopped. Restart it so today's numbers stay true."
}

@Composable
fun DashboardHeader(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.screen),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrainStormAppIcon(size = 38.dp)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text("Brain Storm", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary)
            Text("Digital wellness", fontSize = 11.sp, color = Care.TextSecondary)
        }
        IconButton(onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Care.TextSecondary)
        }
    }
}

@Composable
fun WelcomeSection(greeting: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(greeting, style = MaterialTheme.typography.headlineMedium, fontSize = 26.sp)
        Text(
            "Let's make today a little more focused.",
            fontSize = 13.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
fun MascotHeroCard(
    state: DashboardUiState,
    pool: MascotPool,
    onFix: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (headline, subline) = heroCopy(state.protection)
    SurfaceCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Pill(protectionLabel(state.protection).uppercase(), protectionColor(state.protection))
                Text(
                    headline, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Text(
                    subline, fontSize = 12.sp, color = Care.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (state.enforcementDetail.isNotBlank()) {
                    Text(
                        state.enforcementDetail, fontSize = 11.sp, color = Care.Warning,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            MascotSlot(
                pool = pool, slot = "hero", mood = state.mascotState,
                modifier = Modifier.size(120.dp)
            )
        }
        if (state.protection != ProtectionStatus.ACTIVE) {
            Row(
                Modifier
                    .padding(top = Space.m)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.tile))
                    .background(Care.tint(protectionColor(state.protection), 0.12f))
                    .clickable(onClick = onFix)
                    .padding(horizontal = Space.m, vertical = Space.s),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    fixHint(state),
                    Modifier.weight(1f), fontSize = 12.sp, color = Care.TextPrimary
                )
                Text("Fix →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = protectionColor(state.protection))
            }
        }
    }
}

fun fixHint(state: DashboardUiState): String = when {
    !state.usageAccessGranted -> "Grant usage access so I can measure today's screen time."
    state.protection == ProtectionStatus.MONITORING_PAUSED -> "Tap to restart the monitoring service."
    !state.overlayGranted -> "Allow display over other apps so the lock screen can appear."
    else -> "Enforcement could not be applied — check permissions and restart monitoring."
}
