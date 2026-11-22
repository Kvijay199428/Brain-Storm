package com.vegasol.brainstorm.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.ContentCounting
import com.vegasol.brainstorm.Detectors
import com.vegasol.brainstorm.DeviceInventory
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.Lockout
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.BackButton
import com.vegasol.brainstorm.ui.HourlyBarChart
import com.vegasol.brainstorm.ui.LockedNotice
import com.vegasol.brainstorm.ui.Pill
import com.vegasol.brainstorm.ui.StatusChip
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.UsageBarChart
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.fmtUsage
import com.vegasol.brainstorm.ui.nowTicker
import com.vegasol.brainstorm.ui.statusOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only report for one app: today's budget progress, the hour it was used most, and 7/30-day
 * trends. Enforcement values come from the same daily/hourly rows the rest of the app uses, so this
 * screen cannot disagree with the lock logic. Apps that are not monitored open here too (the device
 * ranking includes them); they simply have no limit or lock section.
 */
@Composable
fun AppReportScreen(pkg: String, onBack: () -> Unit) {
    val now by nowTicker()
    val apps by Graph.db.apps().observeAll().collectAsState(emptyList())
    val key = Graph.engine.dayKey(now)
    val keys30 = remember(key) { Graph.engine.dayKeys(30) }
    val usageToday by remember(key) { Graph.db.usage().observeDay(key) }.collectAsState(emptyList())
    val history by remember(pkg, keys30) {
        Graph.db.usage().observeSinceFor(pkg, keys30.last())
    }.collectAsState(emptyList())
    val hourly by remember(pkg, key) {
        Graph.db.hourly().observeDayFor(pkg, key)
    }.collectAsState(emptyList())
    val locks by Graph.db.lockouts().observeActive().collectAsState(emptyList())

    val a = apps.firstOrNull { it.packageName == pkg }
    val u = usageToday.firstOrNull { it.packageName == pkg }
    val lock = locks.firstOrNull { it.packageName == pkg }
    val used = u?.foregroundSeconds ?: 0L
    val limitSec = (a?.timeLimitMin ?: 0) * 60L
    val label = a?.appName
        ?: u?.appName?.takeIf { it.isNotBlank() }
        ?: DeviceInventory.userApps(LocalContext.current)[pkg]
        ?: pkg

    val totalsByDay = history.associate { it.usageDate to it.foregroundSeconds }
    val chrono30 = keys30.reversed().map { it to (totalsByDay[it] ?: 0L) }
    val chrono7 = chrono30.takeLast(7)
    val hours = LongArray(24).also { slots ->
        hourly.forEach { if (it.hour in 0..23) slots[it.hour] = it.foregroundSeconds }
    }.toList()
    val todayHours = hours.sum()
    val peakHour = AppReportDerive.peakHour(hours)
    val avg7 = AppReportDerive.averageSeconds(chrono7.map { it.second })
    val active7 = AppReportDerive.activeDays(chrono7)
    val hasDetector = ContentCounting.hasDetector(pkg)
    val counting = ContentCounting.active(LocalContext.current, pkg)

    Column(Modifier.fillMaxSize().padding(Space.screen)) {
        BackButton(onBack)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, 48.dp)
            Column(Modifier.weight(1f).padding(start = Space.m)) {
                Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(pkg, fontSize = 11.sp, color = Care.TextSecondary)
            }
            if (a != null) StatusChip(statusOf(a, u, lock))
            else Pill("Not limited", Care.TextSecondary)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
        TodayCard(u, used, limitSec)
        HourCard(hours, todayHours, peakHour)
        WeekCard(chrono7, key, limitSec, avg7, active7, AppReportDerive.peakDay(chrono7))
        MonthCard(chrono30, avg7)

        SurfaceCard(Modifier.fillMaxWidth()) {
            Text("Clips counted", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    if (counting) "${u?.contentCount ?: 0}" else "\u2014",
                    fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Care.Secondary
                )
                Text(
                    if (counting) " today" else " counting unavailable",
                    fontSize = 11.sp, color = Care.TextSecondary,
                    modifier = Modifier.padding(bottom = 5.dp, start = 4.dp)
                )
            }
            Text(
                when {
                    counting -> "Only the number of newly detected clips is stored; no titles or screen text."
                    hasDetector -> "A detector exists (${Detectors.label(pkg)}). Turn it on in Settings > Content detection."
                    else -> Detectors.label(pkg)
                },
                fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (lock != null) LockCard(lock, now)

        if (a != null) {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text("Policy", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "Daily limit ${fmtHm(limitSec)}. Set during setup and locked: it cannot be changed or removed.",
                    fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
                )
                LockedNotice(
                    text = "Adding apps is allowed; removing or editing them is not.",
                    modifier = Modifier.padding(top = Space.s)
                )
            }
        } else {
            SurfaceCard(Modifier.fillMaxWidth()) {
                Text("Not monitored", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    "This app is recorded for the device report, but it has no daily limit, no budget " +
                        "share and no lock. Adding it from the Apps tab is permanent: after that the " +
                        "limit cannot be changed or removed.",
                    fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(Space.section))
        }
    }
}

@Composable
private fun TodayCard(u: com.vegasol.brainstorm.DailyUsage?, used: Long, limitSec: Long) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Text("Today", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(fmtUsage(used), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary)
            Text(
                if (limitSec > 0) " of ${fmtHm(limitSec)}" else " tracked",
                fontSize = 13.sp, color = Care.TextSecondary,
                modifier = Modifier.padding(bottom = 5.dp)
            )
        }
        if (limitSec > 0) {
            UsageProgressBar(
                used * 100f / limitSec,
                Modifier.padding(top = 8.dp), 7.dp
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Metric("Remaining", fmtHm((limitSec - used).coerceAtLeast(0L)), Modifier.weight(1f))
                Metric("Limit", fmtHm(limitSec), Modifier.weight(1f))
                Metric("Clips", if (u != null) "${u.contentCount}" else "0", Modifier.weight(1f))
            }
            Text(
                if (used >= limitSec) "Limit reached - the app locks when this happens."
                else "Time is real foreground time from Android's usage log.",
                fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Metric("Limit", "None", Modifier.weight(1f))
                Metric("Clips", if (u != null) "${u.contentCount}" else "0", Modifier.weight(1f))
            }
            Text(
                "Recorded for the device report. No limit, budget share or lock applies to this app.",
                fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun HourCard(hours: List<Long>, total: Long, peak: Int?) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Text("Today by hour", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (total <= 0) {
            Text(
                "No usage recorded today yet.", fontSize = 12.sp, color = Care.TextSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            HourlyBarChart(
                hours,
                Modifier.padding(top = Space.s),
                highlightHour = peak,
                currentHour = com.vegasol.brainstorm.UsageBuckets.hourOf(System.currentTimeMillis())
            )
            if (peak != null) {
                Text(
                    "Peak ${AppReportDerive.hourLabel(peak)} - ${fmtUsage(hours[peak])}",
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Care.Primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                "Foreground time is split at clock boundaries, so a session from 21:58 counts in both hours.",
                fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun WeekCard(
    days: List<Pair<String, Long>>,
    todayKey: String,
    limitSec: Long,
    average: Long,
    activeDays: Int,
    peakDay: Pair<String, Long>?
) {
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 7 days", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            if (days.sumOf { it.second } > 0) {
                Text(
                    "avg ${fmtUsage(average)}", fontSize = 11.sp, color = Care.TextSecondary
                )
            }
        }
        UsageBarChart(
            days, todayKey, Modifier.padding(top = Space.s),
            limitSeconds = limitSec.takeIf { it > 0 }
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Metric("Daily average", fmtUsage(average), Modifier.weight(1f))
            Metric("Active days", "$activeDays / 7", Modifier.weight(1f))
            Metric("Best day", peakDay?.let { shortDay(it.first) } ?: "-", Modifier.weight(1f))
        }
        if (limitSec > 0) {
            Text(
                "Bars above your ${fmtHm(limitSec)} limit are shown in red.",
                fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun MonthCard(days: List<Pair<String, Long>>, avg7: Long) {
    val total = days.sumOf { it.second }
    SurfaceCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Last 30 days", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "avg ${fmtUsage(if (days.isEmpty()) 0 else total / 30)}",
                fontSize = 11.sp, color = Care.TextSecondary
            )
        }
        UsageBarChart(days, "", Modifier.padding(top = Space.s), compact = true)
        Text(
            "Total ${fmtUsage(total)} across the window. Last 7-day average was ${fmtUsage(avg7)}.",
            fontSize = 10.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun LockCard(lock: Lockout, now: Long) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
            .background(Care.tint(Care.Danger, 0.12f)).padding(Space.m)
    ) {
        Text(
            "Locked until " + java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.SHORT, java.text.DateFormat.SHORT
            ).format(Date(lock.expiresAt)),
            fontWeight = FontWeight.Bold
        )
        Text("Unlocks in ${com.vegasol.brainstorm.ui.fmtClock(lock.expiresAt - now)}", fontSize = 12.sp, color = Care.Danger)
        Text(
            "Trigger: ${lock.triggerType} - ${lock.enforcementNote.ifBlank { "enforcement unknown" }}",
            fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 10.sp, color = Care.TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun shortDay(key: String): String = runCatching {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val d = sdf.parse(key) ?: return@runCatching key
    SimpleDateFormat("EEE d", Locale.US).format(d)
}.getOrDefault(key)
