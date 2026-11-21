package com.vegasol.brainstorm.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.UsageBuckets
import com.vegasol.brainstorm.feature.dashboard.Derive
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.HourlyBarChart
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.UsageBarChart
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.fmtUsage
import com.vegasol.brainstorm.ui.minuteTicker
import java.text.DateFormat
import java.util.Date

@Composable
fun ReportsScreen(onOpenApp: (String) -> Unit) {
    val now by minuteTicker()
    val todayKey = remember(now / 60_000) { Graph.engine.dayKey(now) }
    val keys30 = remember(todayKey) { Graph.engine.dayKeys(30, now) }
    val rows by remember(keys30) { Graph.db.usage().observeSince(keys30.last()) }.collectAsStateWithLifecycle(emptyList())
    val hourly by Graph.db.hourly().observeDay(todayKey).collectAsStateWithLifecycle(emptyList())
    val lockHistory by Graph.db.lockouts().observeHistory().collectAsStateWithLifecycle(emptyList())
    val apps by Graph.db.apps().observeAll().collectAsStateWithLifecycle(emptyList())
    val names = remember(apps) { apps.associate { it.packageName to it.appName } }

    val budgetSec = Graph.prefs.budgetMin * 60L
    val perHour = remember(hourly) {
        val sums = LongArray(24)
        hourly.forEach { if (it.hour in 0..23) sums[it.hour] += it.foregroundSeconds }
        sums.toList()
    }
    val currentHour = remember(now / 60_000) { UsageBuckets.hourOf(now) }
    val hourlyTotal = remember(perHour) { perHour.sum() }
    val totalsByDay = remember(rows) {
        rows.groupBy { it.usageDate }.mapValues { e -> e.value.sumOf { it.foregroundSeconds } }
    }
    val chrono30 = remember(keys30, totalsByDay) { keys30.reversed().map { it to (totalsByDay[it] ?: 0L) } }
    val chrono7 = remember(chrono30) { chrono30.takeLast(7) }
    val streak = remember(keys30, totalsByDay) { Derive.streakDays(keys30.reversed(), totalsByDay, budgetSec) }
    val best = remember(keys30, totalsByDay) { Derive.bestStreak(keys30.reversed(), totalsByDay, budgetSec) }
    val clean30 = remember(keys30, totalsByDay) { Derive.withinBudgetCount(keys30, totalsByDay, budgetSec) }
    val weekTotals = chrono7.sumOf { it.second }
    val prevWeekTotals = chrono30.dropLast(7).takeLast(7).sumOf { it.second }
    val weeklyDelta = if (prevWeekTotals > 0) ((weekTotals - prevWeekTotals) * 100.0 / prevWeekTotals) else 0.0

    val todayRows = rows.filter { it.usageDate == todayKey }.sortedByDescending { it.foregroundSeconds }
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        item("title") {
            Column {
                Text("Reports", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Computed on-device from your own usage log.", fontSize = 11.sp, color = Care.TextSecondary)
            }
        }

        item("week") {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Weekly overview", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Past 7 days", fontSize = 11.sp, color = Care.TextSecondary)
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
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("30-day trend", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("avg ${fmtHm(chrono30.sumOf { it.second } / 30)}", fontSize = 11.sp, color = Care.TextSecondary)
                }
                UsageBarChart(chrono30, todayKey, Modifier.padding(top = Space.m), compact = true)
                Text(
                    "Dashed line = ${fmtHm(budgetSec)} daily budget",
                    fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item("hourly") {
            SurfaceCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Today by hour", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (hourlyTotal > 0) "Monitored apps only Â· ${fmtHm(hourlyTotal)} today"
                            else "Monitored apps only",
                            fontSize = 11.sp, color = Care.TextSecondary
                        )
                    }
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

        item("streak") {
            SurfaceCard {
                SectionHeader("Streaks & achievements")
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

        item("todayHeader") { SectionHeader("Today by app") }
        if (todayRows.isEmpty()) {
            item("todayEmpty") {
                Text("No usage recorded today yet.", fontSize = 12.sp, color = Care.TextSecondary)
            }
        } else {
            items(todayRows, key = { it.packageName }) { row ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile))
                        .background(Care.Surface)
                        .clickable { onOpenApp(row.packageName) }
                        .padding(Space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(names[row.packageName] ?: row.packageName, Modifier.weight(1f), fontSize = 13.sp)
                    Text(fmtUsage(row.foregroundSeconds), fontWeight = FontWeight.Bold, color = Care.Primary)
                }
            }
        }

        item("lockHeader") { SectionHeader("Lock history") }
        if (lockHistory.isEmpty()) {
            item("lockEmpty") {
                Text("No lockouts yet. Nice discipline!", fontSize = 12.sp, color = Care.TextSecondary)
            }
        } else {
            items(lockHistory, key = { it.id }) { l ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile))
                        .background(Care.Surface).padding(Space.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            (if (l.triggerType == "BUDGET") "Daily budget Â· " else "") +
                                (names[l.packageName] ?: l.packageName),
                            fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                        )
                        Text(
                            "${l.triggerType} Â· ${df.format(Date(l.activatedAt))}",
                            fontSize = 10.sp, color = Care.TextSecondary
                        )
                    }
                    Text(
                        l.status,
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = if (l.status == "ACTIVE") Care.Danger else Care.Success
                    )
                }
            }
        }

        item("footnote") {
            Text(
                "Media-playback and content-consumption trends are not shown: no validated detector " +
                    "is installed, so any number would be invented.",
                fontSize = 10.sp, color = Care.TextSecondary
            )
        }
        item("end") { Spacer(Modifier.height(Space.section)) }
    }
}

@Composable
private fun Achievement(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Care.Primary)
        Text(label, fontSize = 10.sp, color = Care.TextSecondary)
    }
}
