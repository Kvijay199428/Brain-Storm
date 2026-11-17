package com.vegasol.brainstorm.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.feature.dashboard.DashboardUiState
import com.vegasol.brainstorm.mascot.MascotPool
import com.vegasol.brainstorm.mascot.MascotSlot
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.theme.progressColor
import com.vegasol.brainstorm.theme.scoreColor
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.ui.fmtClock
import com.vegasol.brainstorm.ui.fmtHm
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@Composable
fun ScreenTimeCard(state: DashboardUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SurfaceCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Today's screen time".uppercase(), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = Care.TextSecondary
            )
            Text("TODAY", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Care.Primary)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                fmtHm(state.totalUsageSeconds),
                Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary
            )
            Text(
                "${state.usePercent.roundToInt()}%",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = progressColor(state.usePercent)
            )
        }
        Text(
            "of ${fmtHm(state.dailyBudgetSeconds)} daily budget",
            fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(bottom = 10.dp)
        )
        UsageProgressBar(state.usePercent)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Remaining", fontSize = 11.sp, color = Care.TextSecondary)
                Text(fmtHm(state.remainingSeconds), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Daily budget", fontSize = 11.sp, color = Care.TextSecondary)
                Text(fmtHm(state.dailyBudgetSeconds), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (state.hasApps) {
            Text(
                "Across ${state.monitoredApps.size} ${if (state.monitoredApps.size == 1) "app" else "apps"} · per-app limits total ${fmtHm(state.perAppLimitsTotalSeconds)}",
                fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun MetricTile(
    value: String,
    label: String,
    sub: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(Care.Surface)
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Care.TextPrimary)
        Text(sub, fontSize = 10.sp, color = Care.TextSecondary, maxLines = 2)
    }
}

@Composable
fun ContentMetricsCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        MetricTile(
            value = if (state.contentCountingAvailable) "${state.totalContentCount}" else "—",
            label = "Clips watched",
            sub = when {
                state.contentCountingAvailable -> "YouTube Shorts · detected today"
                state.contentDetectorExists -> "counting is off · Settings"
                else -> "not available · no detector"
            },
            accent = Care.Secondary,
            modifier = Modifier.weight(1f)
        )
        MetricTile(
            value = "${state.usePercent.roundToInt()}%",
            label = "Time consumed",
            sub = "${fmtHm(state.totalUsageSeconds)} of ${fmtHm(state.dailyBudgetSeconds)}",
            accent = progressColor(state.usePercent),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun LockoutStatusCard(state: DashboardUiState, now: State<Long>, modifier: Modifier = Modifier) {
    val lock = state.activeLockouts.firstOrNull() ?: return
    val clock = now.value
    val budget = state.budgetLockout
    val title = if (budget) "Daily budget exhausted" else "${labelOf(state, lock.packageName)} is locked"
    val sub = if (budget) "Every monitored app is locked until the countdown ends."
    else "This app is blocked until the countdown ends."

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(Care.tint(Care.Danger, 0.12f))
            .padding(horizontal = Space.l, vertical = Space.m)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔒", fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Text(sub, fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                fmtClock(lock.expiresAt - clock),
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Care.Danger,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Unlocks " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(lock.expiresAt)),
                fontSize = 10.sp, color = Care.TextSecondary, textAlign = TextAlign.End
            )
        }
        if (!state.enforcementOk) {
            Text(
                "⚠ Enforcement reported failure — the lock may be bypassable on this device.",
                fontSize = 10.sp, color = Care.Warning, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

private fun labelOf(state: DashboardUiState, pkg: String): String =
    state.monitoredApps.firstOrNull { it.packageName == pkg }?.label ?: pkg

@Composable
fun WellnessScoreCard(state: DashboardUiState, modifier: Modifier = Modifier) {
    var showInfo by remember { mutableStateOf(false) }
    SurfaceCard(modifier.fillMaxWidth()) {
        SectionHeader("Your progress")
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                "${state.wellnessScore}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Care.Primary
            )
            Text("/ 100", fontSize = 14.sp, color = Care.TextSecondary, modifier = Modifier.padding(bottom = 6.dp, start = 4.dp))
            Spacer(Modifier.weight(1f))
            TextButton({ showInfo = true }) { Text("How?", fontSize = 12.sp) }
        }
        Text("Daily wellness score", fontSize = 12.sp, color = Care.TextSecondary)
        UsageProgressBar(
            state.wellnessScore.toFloat(), Modifier.padding(top = 10.dp), 8.dp,
            color = scoreColor(state.wellnessScore)
        )
        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${state.currentStreakDays}-day streak inside your budget",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold
            )
            Text(
                "Within budget on ${state.daysWithinBudgetLast30} of the last 30 days.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("How the score works") },
            text = {
                Text(
                    "Brain Storm's own motivational score — not a medical or scientific measurement.\n\n" +
                        "• 60 points for staying inside your daily budget\n" +
                        "• 25 points when no app reaches its own limit\n" +
                        "• 15 points for a lockout-free day\n\n" +
                        "Your budget is ${fmtHm(state.dailyBudgetSeconds)} a day across ${state.monitoredApps.size} apps.",
                    fontSize = 13.sp
                )
            },
            confirmButton = { TextButton({ showInfo = false }) { Text("Got it") } }
        )
    }
}

@Composable
fun MascotAdviceCard(state: DashboardUiState, pool: MascotPool, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(Care.SurfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MascotSlot(
            pool = pool, slot = "advice", mood = state.mascotState,
            modifier = Modifier.size(82.dp)
        )
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            Text("A little reminder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Care.Primary)
            Text(
                state.advice, fontSize = 12.sp, color = Care.TextPrimary,
                modifier = Modifier.padding(top = 3.dp)
            )
            Text(
                "Brainy is cheering you on.", fontSize = 10.sp, color = Care.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
