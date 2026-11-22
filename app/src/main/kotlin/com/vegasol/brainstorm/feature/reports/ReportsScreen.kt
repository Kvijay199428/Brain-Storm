package com.vegasol.brainstorm.feature.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegasol.brainstorm.DeviceInventory
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.UsageBuckets
import com.vegasol.brainstorm.feature.dashboard.Derive
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.HourlyBarChart
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.UsageBarChart
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.fmtUsage
import com.vegasol.brainstorm.ui.minuteTicker

/**
 * Whole-device report. `daily_usage` holds every included app (user-installed plus monitored), so
 * the totals and the device summary here cover the device, while streaks and the budget line stay on
 * the monitored apps that the frozen policy actually enforces. Today's app ranking lives on the Apps
 * tab; lock history lives on each app's own report.
 */
@Composable
fun ReportsScreen(onOpenLifetimeRanking: () -> Unit) {
    val now by minuteTicker()
    val todayKey = remember(now / 60_000) { Graph.engine.dayKey(now) }
    val keys30 = remember(todayKey) { Graph.engine.dayKeys(30, now) }
    val rows by remember(keys30) { Graph.db.usage().observeSince(keys30.last()) }.collectAsStateWithLifecycle(emptyList())
    val hourly by Graph.db.hourly().observeDay(todayKey).collectAsStateWithLifecycle(emptyList())
    val apps by Graph.db.apps().observeAll().collectAsStateWithLifecycle(emptyList())

    val ctx = LocalContext.current
    val deviceLabels = remember(now / 60_000) { DeviceInventory.userApps(ctx) }
    val names = remember(rows, apps, deviceLabels) {
        buildMap {
            putAll(deviceLabels)
            rows.forEach { if (it.appName.isNotBlank()) put(it.packageName, it.appName) }
            apps.forEach { put(it.packageName, it.appName) }
        }
    }
    val monitored = remember(apps) { apps.mapTo(HashSet()) { it.packageName } }

    val budgetSec = Graph.prefs.budgetMin * 60L
    val perHour = remember(hourly) {
        val sums = LongArray(24)
        hourly.forEach { if (it.hour in 0..23) sums[it.hour] += it.foregroundSeconds }
        sums.toList()
    }
    val currentHour = remember(now / 60_000) { UsageBuckets.hourOf(now) }
    val hourlyTotal = remember(perHour) { perHour.sum() }

    val deviceTotals = remember(rows) { ReportsDerive.totalsByDay(rows) }
    val monitoredTotals = remember(rows, monitored) {
        rows.filter { it.packageName in monitored }
            .groupBy { it.usageDate }
            .mapValues { e -> e.value.sumOf { it.foregroundSeconds } }
    }
    val chrono30 = remember(keys30, deviceTotals) { keys30.reversed().map { it to (deviceTotals[it] ?: 0L) } }
    val chrono7 = remember(chrono30) { chrono30.takeLast(7) }
    val weekTotals = chrono7.sumOf { it.second }
    val prevWeekTotals = chrono30.dropLast(7).takeLast(7).sumOf { it.second }
    val weeklyDelta = if (prevWeekTotals > 0) ((weekTotals - prevWeekTotals) * 100.0 / prevWeekTotals) else 0.0

    val streak = remember(keys30, monitoredTotals) { Derive.streakDays(keys30.reversed(), monitoredTotals, budgetSec) }
    val best = remember(keys30, monitoredTotals) { Derive.bestStreak(keys30.reversed(), monitoredTotals, budgetSec) }
    val clean30 = remember(keys30, monitoredTotals) { Derive.withinBudgetCount(keys30, monitoredTotals, budgetSec) }

    val ranking = remember(rows, todayKey) { ReportsDerive.ranking(rows, todayKey) }
    val deviceToday = remember(ranking) { ranking.sumOf { it.foregroundSeconds } }
    val monitoredToday = monitoredTotals[todayKey] ?: 0L
    val topApp = ranking.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        Column(Modifier.padding(top = Space.l)) {
            Text("Reports", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Computed on-device from your own usage log.", fontSize = 11.sp, color = Care.TextSecondary)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
        item("device") {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text("Today on this device", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text(fmtUsage(deviceToday), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (ranking.isEmpty()) " no apps used yet"
                        else " across ${ranking.size} ${if (ranking.size == 1) "app" else "apps"}",
                        fontSize = 12.sp, color = Care.TextSecondary,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                if (topApp != null) {
                    Text(
                        "Most used: ${names[topApp.packageName] ?: topApp.packageName} - " +
                            "${fmtUsage(topApp.foregroundSeconds)} " +
                            "(${ReportsDerive.sharePercent(topApp.foregroundSeconds, deviceToday)}% of device time)",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Care.Primary,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = Space.m))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Monitored apps against your budget", fontSize = 10.sp, color = Care.TextSecondary)
                        Text(
                            "${fmtUsage(monitoredToday)} of ${fmtHm(budgetSec)}",
                            fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "${percentOf(monitoredToday, budgetSec)}%",
                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = Care.Primary
                    )
                }
                UsageProgressBar(
                    percentOf(monitoredToday, budgetSec).toFloat(),
                    Modifier.padding(top = 8.dp), 7.dp
                )
                Text(
                    "Only monitored apps count against the budget and the lock rules.",
                    fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item("lifetime") {
            SurfaceCard(Modifier.fillMaxWidth().clickable(onClick = onOpenLifetimeRanking)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("All-time ranking", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Apps ranked by total time since tracking began",
                            fontSize = 11.sp, color = Care.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Text(">", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Care.Primary)
                }
            }
        }

        item("hourly") {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("Today by hour", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (hourlyTotal > 0) "Whole device - ${fmtUsage(hourlyTotal)} today"
                        else "Whole device",
                        fontSize = 11.sp, color = Care.TextSecondary
                    )
                }
                if (hourlyTotal <= 0) {
                    Text(
                        "No usage recorded today yet.",
                        fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = Space.m)
                    )
                } else {
                    HourlyBarChart(perHour, Modifier.padding(top = Space.m), highlightHour = currentHour, currentHour = currentHour)
                    Text(
                        "Each bar is foreground time spent inside that clock hour. Data comes from " +
                            "Android's own event log and is split at hour boundaries, so a session " +
                            "that started at 10:58 appears under both 10 and 11.",
                        fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        item("week") {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weekly overview", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Whole device - past 7 days", fontSize = 11.sp, color = Care.TextSecondary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(fmtHm(weekTotals), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Care.Primary)
                        Text(
                            when {
                                prevWeekTotals <= 0 -> "no comparison yet"
                                weeklyDelta <= -1 -> "${"%.0f".format(-weeklyDelta)}% less than last week"
                                weeklyDelta >= 1 -> "${"%.0f".format(weeklyDelta)}% more than last week"
                                else -> "same as last week"
                            },
                            fontSize = 10.sp, color = Care.TextSecondary
                        )
                    }
                }
                UsageBarChart(chrono7, todayKey, Modifier.padding(top = Space.m))
            }
        }

        item("month") {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("30-day trend", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "avg ${fmtUsage(chrono30.sumOf { it.second } / 30)}",
                        fontSize = 11.sp, color = Care.TextSecondary
                    )
                }
                UsageBarChart(chrono30, todayKey, Modifier.padding(top = Space.m), compact = true)
                Text(
                    "Whole device, every day. Brain Storm, launchers and the system UI are excluded.",
                    fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item("streak") {
            SurfaceCard(Modifier.fillMaxWidth()) {
                SectionHeader("Streaks & achievements")
                Text(
                    "Monitored apps against your ${fmtHm(budgetSec)} budget",
                    fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 2.dp)
                )
                Row(Modifier.fillMaxWidth().padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    Achievement(if (streak > 0) "$streak" else "0", "Day streak", Modifier.weight(1f))
                    Achievement("$best", "Best streak", Modifier.weight(1f))
                    Achievement("$clean30", "Clean days /30", Modifier.weight(1f))
                }
                HorizontalDivider(Modifier.padding(vertical = Space.m))
                Text(
                    if (streak >= 2) "You've stayed inside your budget $streak days running."
                    else "Stay inside ${fmtHm(budgetSec)} today to start a streak.",
                    fontSize = 11.sp, color = Care.TextSecondary
                )
            }
        }

        item("footnote") {
            Text(
                "Media-playback and content-consumption trends are not shown for apps without a " +
                    "validated detector, so no number on this screen is invented.",
                fontSize = 10.sp, color = Care.TextSecondary
            )
        }
        item("end") { Spacer(Modifier.height(Space.section)) }
        }
    }
}

@Composable
private fun Achievement(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Care.Primary)
        Text(label, fontSize = 10.sp, color = Care.TextSecondary)
    }
}

private fun percentOf(partSeconds: Long, totalSeconds: Long): Int =
    if (partSeconds <= 0L || totalSeconds <= 0L) 0
    else Math.round(partSeconds * 100.0 / totalSeconds).toInt().coerceIn(0, 100)
