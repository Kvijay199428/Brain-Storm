package com.vegasol.brainstorm.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.feature.dashboard.AppUsageUiModel
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.SectionHeader
import com.vegasol.brainstorm.ui.StatusChip
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.ui.fmtClock
import com.vegasol.brainstorm.ui.fmtHm

@Composable
fun MonitoredAppsHeader(appCount: Int, onManage: () -> Unit, modifier: Modifier = Modifier) {
    SectionHeader("Your apps ($appCount)", modifier) {
        TextButton(onClick = onManage, contentPadding = PaddingValues(horizontal = Space.s)) {
            Text("View configured apps →", fontSize = 11.sp, color = Care.Primary)
        }
    }
}

@Composable
fun MonitoredAppRow(model: AppUsageUiModel, now: State<Long>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.tile))
            .background(Care.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(model.packageName)
        Column(Modifier.weight(1f).padding(start = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.label, Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold, fontSize = 14.sp
                )
                Text(fmtHm(model.usedSeconds), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            UsageProgressBar(model.percent, Modifier.padding(top = 6.dp), 6.dp)
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    detailLine(model, now), Modifier.weight(1f),
                    fontSize = 10.sp, color = Care.TextSecondary, maxLines = 1
                )
                StatusChip(model.status)
            }
        }
    }
}

private fun detailLine(model: AppUsageUiModel, now: State<Long>): String {
    val lock = model.lock
    return when {
        lock != null -> "Unlocks in ${fmtClock(lock.expiresAt - now.value)}"
        model.contentSupported -> "${model.contentCount} counted today · time limit ${fmtHm(model.limitSeconds)}"
        else -> "Limit ${fmtHm(model.limitSeconds)} · time only"
    }
}

@Composable
fun NoAppsCard(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(Care.Surface)
            .padding(Space.l),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No apps monitored yet", fontWeight = FontWeight.SemiBold)
        Text(
            "Add the apps you want Brainy to watch.", fontSize = 12.sp,
            color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
        )
        TextButton(onAdd, Modifier.padding(top = 4.dp)) { Text("Add apps") }
    }
}

@Composable
fun SectionSpacer() = Spacer(Modifier.height(Space.m))
