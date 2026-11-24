package com.vegasol.brainstorm.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.MonitoredApp
import com.vegasol.brainstorm.PipCapability
import com.vegasol.brainstorm.PipCapabilityScanner
import com.vegasol.brainstorm.save
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.LockedNotice
import com.vegasol.brainstorm.ui.StatusChip
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.ui.fmtClock
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.fmtUsage
import com.vegasol.brainstorm.ui.nowTicker
import com.vegasol.brainstorm.ui.statusOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The only mutation in the app after setup: adding an app (irreversibly) with its own limit.
 * Everything else here is a read-only view. Adding happens on its own full page, not in a dialog.
 */
@Composable
fun AppsScreen(onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val now by nowTicker()
    val apps by Graph.db.apps().observeAll().collectAsState(emptyList())
    val key = Graph.engine.dayKey(now)
    val usage by remember(key) { Graph.db.usage().observeDay(key) }.collectAsState(emptyList())
    val locks by Graph.db.lockouts().observeActive().collectAsState(emptyList())
    val ranked = remember(apps, usage) { AppReportDerive.rankByTodayUsage(apps, usage) }
    val c = LocalContext.current
    var pipCaps by remember { mutableStateOf<Map<String, PipCapability>>(emptyMap()) }
    LaunchedEffect(apps) {
        pipCaps = withContext(Dispatchers.IO) {
            PipCapabilityScanner.scanAll(c, apps.map { it.packageName })
        }
    }

    var adding by remember { mutableStateOf(false) }

    if (adding) {
        AddAppScreen(
            existing = apps.map { it.packageName }.toSet(),
            limitTotalSec = apps.sumOf { (it.timeLimitMin ?: 0) } * 60L,
            budgetSec = Graph.prefs.budgetMin * 60L,
            onBack = { adding = false },
            onAdd = { inst, minutes ->
                adding = false
                scope.launch {
                    Graph.db.apps().save(
                        MonitoredApp(packageName = inst.pkg, appName = inst.label, timeLimitMin = minutes, enabled = true)
                    )
                }
            }
        )
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        Row(Modifier.fillMaxWidth().padding(top = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Applications", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                LockedNotice(text = "Limits were set during setup - locked")
            }
            Button(
                onClick = { adding = true },
                shape = RoundedCornerShape(Radius.button)
            ) { Text("Add", fontSize = 13.sp) }
        }

        Text(
            "Ranked by today's usage - your most used app is first. Adding an app is permanent: " +
                "it keeps its limit and cannot be removed or paused.",
            fontSize = 11.sp, color = Care.TextSecondary,
            modifier = Modifier.padding(vertical = Space.s)
        )

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = Space.xl), contentAlignment = Alignment.Center) {
                Text("No apps monitored yet. Tap Add to choose the first one.", fontSize = 13.sp, color = Care.TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                items(ranked, key = { it.app.packageName }) { rankedApp ->
                    val a = rankedApp.app
                    val u = usage.firstOrNull { it.packageName == a.packageName }
                    val lock = locks.firstOrNull { it.packageName == a.packageName }
                    val limitSec = (a.timeLimitMin ?: 0) * 60L
                    val used = rankedApp.usedSeconds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.tile))
                            .background(Care.Surface)
                            .clickable { onOpen(a.packageName) }
                            .padding(Space.m),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${rankedApp.rank}",
                            Modifier.width(18.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (rankedApp.rank == 1 && used > 0) Care.Primary else Care.TextSecondary
                        )
                        AppIcon(a.packageName)
                        Column(Modifier.weight(1f).padding(start = Space.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.appName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                StatusChip(statusOf(a, u, lock))
                            }
                            Text(
                                "${fmtUsage(used)} of ${fmtHm(limitSec)} today" +
                                    if (rankedApp.rank == 1 && used > 0) " - top app today" else "",
                                fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 2.dp)
                            )
                            UsageProgressBar(
                                if (limitSec > 0) used * 100f / limitSec else 0f,
                                Modifier.padding(top = 6.dp), 5.dp
                            )
                            if (pipCaps[a.packageName] == PipCapability.SUPPORTED_ALLOWED ||
                                pipCaps[a.packageName] == PipCapability.DEFAULT_ALLOWED
                            ) {
                                Text(
                                    "PiP enabled",
                                    fontSize = 10.sp, color = Care.Warning,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            if (lock != null) {
                                Text(
                                    "Unlocks in ${fmtClock(lock.expiresAt - now)}",
                                    fontSize = 11.sp, color = Care.Danger, modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
