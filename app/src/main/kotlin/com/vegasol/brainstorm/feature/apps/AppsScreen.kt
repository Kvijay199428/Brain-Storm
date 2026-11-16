package com.vegasol.brainstorm.feature.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
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
import com.vegasol.brainstorm.save
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.DurationPicker
import com.vegasol.brainstorm.ui.Inst
import com.vegasol.brainstorm.ui.LockedNotice
import com.vegasol.brainstorm.ui.StatusChip
import com.vegasol.brainstorm.ui.UsageProgressBar
import com.vegasol.brainstorm.ui.fmtClock
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.installed
import com.vegasol.brainstorm.ui.nowTicker
import com.vegasol.brainstorm.ui.statusOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The only mutation in the app after setup: adding an app (irreversibly) with its own limit.
 * Everything else here is a read-only view.
 */
@Composable
fun AppsScreen(onOpen: (String) -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by nowTicker()
    val apps by Graph.db.apps().observeAll().collectAsState(emptyList())
    val key = Graph.engine.dayKey(now)
    val usage by remember(key) { Graph.db.usage().observeDay(key) }.collectAsState(emptyList())
    val locks by Graph.db.lockouts().observeActive().collectAsState(emptyList())

    var picking by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Inst?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.screen)) {
        Row(Modifier.fillMaxWidth().padding(top = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Applications", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                LockedNotice(text = "Limits were set during setup · locked")
            }
            Button(
                onClick = { picking = true },
                shape = RoundedCornerShape(Radius.button)
            ) { Text("Add", fontSize = 13.sp) }
        }

        Text(
            "Adding an app is permanent: it keeps its limit and cannot be removed or paused.",
            fontSize = 11.sp, color = Care.TextSecondary,
            modifier = Modifier.padding(vertical = Space.s)
        )

        if (apps.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = Space.xl), contentAlignment = Alignment.Center) {
                Text("No apps monitored yet. Tap Add to choose the first one.", fontSize = 13.sp, color = Care.TextSecondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                items(apps, key = { it.packageName }) { a ->
                    val u = usage.firstOrNull { it.packageName == a.packageName }
                    val lock = locks.firstOrNull { it.packageName == a.packageName }
                    val limitSec = (a.timeLimitMin ?: 0) * 60L
                    val used = u?.foregroundSeconds ?: 0L
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.tile))
                            .background(Care.Surface)
                            .clickable { onOpen(a.packageName) }
                            .padding(Space.m),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIcon(a.packageName)
                        Column(Modifier.weight(1f).padding(start = Space.m)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.appName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                StatusChip(statusOf(a, u, lock))
                            }
                            Text(
                                "${fmtHm(used)} of ${fmtHm(limitSec)} today",
                                fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 2.dp)
                            )
                            UsageProgressBar(
                                if (limitSec > 0) used * 100f / limitSec else 0f,
                                Modifier.padding(top = 6.dp), 5.dp
                            )
                            if (lock != null) {
                                Text(
                                    "🔒 Unlocks in ${fmtClock(lock.expiresAt - now)}",
                                    fontSize = 11.sp, color = Care.Danger, modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        AddAppDialog(
            existing = apps.map { it.packageName }.toSet(),
            onDismiss = { picking = false },
            onPick = { inst ->
                picking = false
                pending = inst
            }
        )
    }

    pending?.let { inst ->
        var minutes by remember(inst.pkg) { mutableIntStateOf(60) }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Add ${inst.label}") },
            text = {
                Column {
                    DurationPicker(
                        "Daily limit", minutes, { minutes = it },
                        hint = "Frozen once added — this app can never be removed or changed."
                    )
                    Text(
                        "Adding raises your per-app limits total to " +
                            "${fmtHm((apps.sumOf { (it.timeLimitMin ?: 0) } + minutes) * 60L)}. " +
                            "Your ${fmtHm(Graph.prefs.budgetMin * 60L)} budget is unchanged.",
                        fontSize = 11.sp, color = Care.TextSecondary,
                        modifier = Modifier.padding(top = Space.m)
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val name = inst.label
                    val pkg = inst.pkg
                    val mins = minutes
                    pending = null
                    scope.launch {
                        Graph.db.apps().save(
                            MonitoredApp(packageName = pkg, appName = name, timeLimitMin = mins, enabled = true)
                        )
                    }
                }) { Text("Add and freeze") }
            },
            dismissButton = { TextButton({ pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AddAppDialog(existing: Set<String>, onDismiss: () -> Unit, onPick: (Inst) -> Unit) {
    val c = LocalContext.current
    var list by remember { mutableStateOf<List<Inst>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        list = withContext(Dispatchers.IO) { installed(c) }.filter { it.pkg !in existing }
    }
    val shown = remember(list, query) {
        if (query.isBlank()) list
        else list.filter { it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an app") },
        text = {
            Column(Modifier.heightIn(max = 460.dp)) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("Search") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (shown.isEmpty()) {
                    Text(
                        if (list.isEmpty()) "Every installed app is already monitored." else "No match.",
                        fontSize = 12.sp, color = Care.TextSecondary,
                        modifier = Modifier.padding(top = Space.m)
                    )
                } else {
                    LazyColumn(Modifier.padding(top = Space.s)) {
                        items(shown, key = { it.pkg }) { a ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(a) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIcon(a.pkg, 30.dp)
                                Column(Modifier.padding(start = Space.s)) {
                                    Text(a.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(a.pkg, fontSize = 10.sp, color = Care.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

/** Read-only detail view. No stepper, no toggle: policy is frozen. */
@Composable
fun AppDetailsScreen(pkg: String, onBack: () -> Unit) {
    val now by nowTicker()
    val apps by Graph.db.apps().observeAll().collectAsState(emptyList())
    val key = Graph.engine.dayKey(now)
    val usage by remember(key) { Graph.db.usage().observeDay(key) }.collectAsState(emptyList())
    val locks by Graph.db.lockouts().observeActive().collectAsState(emptyList())

    val a = apps.firstOrNull { it.packageName == pkg }
    if (a == null) {
        Column(Modifier.padding(Space.screen)) {
            TextButton(onBack) { Text("← Back") }
            Text("App not found")
        }
        return
    }

    val u = usage.firstOrNull { it.packageName == pkg }
    val lock = locks.firstOrNull { it.packageName == pkg }
    val used = u?.foregroundSeconds ?: 0L
    val limitSec = (a.timeLimitMin ?: 0) * 60L
    val supported = com.vegasol.brainstorm.Detectors.support(pkg) == com.vegasol.brainstorm.Support.SUPPORTED

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        TextButton(onBack, contentPadding = PaddingValues(0.dp)) { Text("← Back") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, 48.dp)
            Column(Modifier.weight(1f).padding(start = Space.m)) {
                Text(a.appName, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(pkg, fontSize = 11.sp, color = Care.TextSecondary)
            }
        }
        StatusChip(statusOf(a, u, lock))

        StatRow("Today", fmtHm(used), "of ${fmtHm(limitSec)} limit")
        UsageProgressBar(if (limitSec > 0) used * 100f / limitSec else 0f)

        HorizontalDivider(Modifier.padding(vertical = Space.xs))
        StatRow("Remaining today", fmtHm((limitSec - used).coerceAtLeast(0L)))
        StatRow("Daily limit", fmtHm(limitSec))
        StatRow("Content counting", if (supported) "${u?.contentCount ?: 0}" else "Not available")
        if (!supported) {
            Text(
                com.vegasol.brainstorm.Detectors.label(pkg),
                fontSize = 11.sp, color = Care.TextSecondary
            )
        }

        if (lock != null) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.card))
                    .background(Care.tint(Care.Danger, 0.12f)).padding(Space.m)
            ) {
                Text(
                    "🔒 Locked until " + java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.SHORT, java.text.DateFormat.SHORT
                    ).format(java.util.Date(lock.expiresAt)),
                    fontWeight = FontWeight.Bold
                )
                Text("Unlocks in ${fmtClock(lock.expiresAt - now)}", fontSize = 12.sp, color = Care.Danger)
                Text(
                    "Trigger: ${lock.triggerType} · ${lock.enforcementNote.ifBlank { "enforcement unknown" }}",
                    fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        HorizontalDivider(Modifier.padding(vertical = Space.xs))
        LockedNotice(text = "Set during setup · this app's limit cannot be changed or removed")
        Text(
            "Locks are fixed for the configured duration and can never be shortened. Adding apps is " +
                "allowed; removing or editing them is not.",
            fontSize = 11.sp, color = Care.TextSecondary
        )
        Spacer(Modifier.height(Space.section))
    }
}

@Composable
private fun StatRow(label: String, value: String, sub: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 13.sp)
            if (sub != null) Text(sub, fontSize = 11.sp, color = Care.TextSecondary)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
