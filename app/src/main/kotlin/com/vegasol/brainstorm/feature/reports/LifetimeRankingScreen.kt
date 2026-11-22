package com.vegasol.brainstorm.feature.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.vegasol.brainstorm.DeviceInventory
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.BackButton
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.fmtUsage

/**
 * All-time ranking over the whole usage log. Every app with recorded foreground time is included,
 * not just monitored ones. Tapping a row opens the same per-app report used everywhere else.
 */
@Composable
fun LifetimeRankingScreen(onBack: () -> Unit, onOpenApp: (String) -> Unit) {
    val rows by Graph.db.usage().observeAll().collectAsState(emptyList())
    val apps by Graph.db.apps().observeAll().collectAsState(emptyList())
    val ctx = LocalContext.current
    val names = remember(rows, apps) {
        buildMap<String, String> {
            putAll(DeviceInventory.userApps(ctx))
            rows.forEach { if (it.appName.isNotBlank()) put(it.packageName, it.appName) }
            apps.forEach { put(it.packageName, it.appName) }
        }
    }
    val ranking = remember(rows) { ReportsDerive.lifetimeRanking(rows) }
    val total = remember(ranking) { ReportsDerive.lifetimeTotal(ranking) }

    Column(Modifier.fillMaxSize().padding(Space.screen)) {
        BackButton(onBack)
        Column {
            Text("All-time ranking", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Computed on-device from your whole usage log.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = Space.m),
            verticalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            item("summary") {
                SurfaceCard(Modifier.fillMaxWidth()) {
                    Text("Total tracked time", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(fmtUsage(total), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (ranking.isEmpty()) " no apps recorded yet"
                            else " across ${ranking.size} ${if (ranking.size == 1) "app" else "apps"}",
                            fontSize = 12.sp, color = Care.TextSecondary,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                }
            }

            if (ranking.isEmpty()) {
                item("empty") {
                    Text("No usage recorded yet.", fontSize = 12.sp, color = Care.TextSecondary)
                }
            } else {
                item("rankHeader") { SectionHeader("Lifetime by app") }
                itemsIndexed(ranking, key = { _, row -> row.packageName }) { index, row ->
                    LifetimeRow(
                        rank = index + 1,
                        row = row,
                        label = names[row.packageName] ?: row.label,
                        deviceTotal = total,
                        onClick = { onOpenApp(row.packageName) }
                    )
                }
            }

            item("end") { Spacer(Modifier.height(Space.section)) }
        }
    }
}

@Composable
private fun LifetimeRow(
    rank: Int,
    row: LifetimeRank,
    label: String,
    deviceTotal: Long,
    onClick: () -> Unit
) {
    val share = ReportsDerive.sharePercent(row.seconds, deviceTotal)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile))
            .background(Care.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$rank",
            Modifier.width(20.dp),
            fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (rank == 1) Care.Primary else Care.TextSecondary
        )
        AppIcon(row.packageName, 34.dp)
        Column(Modifier.weight(1f).padding(start = Space.s)) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (rank == 1) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
            Text(
                if (rank == 1) "Top app - $share% of all tracked time"
                else "$share% of all tracked time",
                fontSize = 10.sp,
                color = if (rank == 1) Care.Primary else Care.TextSecondary
            )
        }
        Text(
            fmtUsage(row.seconds),
            fontWeight = FontWeight.Bold,
            color = Care.Primary,
            modifier = Modifier.padding(start = Space.s)
        )
    }
}
