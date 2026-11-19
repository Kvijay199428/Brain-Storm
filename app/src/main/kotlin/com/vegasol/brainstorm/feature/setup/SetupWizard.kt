package com.vegasol.brainstorm.feature.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.BudgetCheck
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.MonitorService
import com.vegasol.brainstorm.MonitoredApp
import com.vegasol.brainstorm.Perms
import com.vegasol.brainstorm.budgetCheck
import com.vegasol.brainstorm.mascot.MascotPool
import com.vegasol.brainstorm.mascot.MascotSlot
import com.vegasol.brainstorm.mascot.MascotState
import com.vegasol.brainstorm.mascot.rememberMascotPool
import com.vegasol.brainstorm.save
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.BrainStormAppIcon
import com.vegasol.brainstorm.ui.DurationPicker
import com.vegasol.brainstorm.ui.Inst
import com.vegasol.brainstorm.ui.SUGGESTED
import com.vegasol.brainstorm.ui.Stepper
import com.vegasol.brainstorm.ui.fmtMinutes
import com.vegasol.brainstorm.ui.installed
import com.vegasol.brainstorm.ui.rememberResumeTick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LAST_STEP = 6
private const val DEFAULT_APP_LIMIT = 60
private const val DEFAULT_BUDGET = 120

/**
 * One-time policy setup. When this finishes every field it collects is frozen: the Apps screen
 * can only add, and Settings/AppDetails become read-only views.
 */
@Composable
fun SetupWizard(onDone: () -> Unit) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val resume = rememberResumeTick()
    val pool = rememberMascotPool()
    DisposableEffect(Unit) { onDispose { pool.dispose() } }

    var step by remember { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf<List<Inst>>(emptyList()) }
    var selected by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var budget by remember { mutableIntStateOf(DEFAULT_BUDGET) }
    var lockHours by remember { mutableIntStateOf(24) }
    var resetHour by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<String?>(null) }
    var confirming by remember { mutableStateOf(false) }

    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) { installed(c) }
        apps = list
        selected = list.filter { it.pkg in SUGGESTED }.associate { it.pkg to DEFAULT_APP_LIMIT }
    }

    val sumMinutes = selected.values.sum()
    val check = budgetCheck(budget * 60L, sumMinutes * 60L)
    val tooTight = selected.filter { (_, min) -> min >= budget }

    val canNext = when (step) {
        2 -> Perms.usage(c)
        3 -> selected.isNotEmpty()
        4 -> check != BudgetCheck.UNREACHABLE
        else -> true
    }

    fun persist() {
        scope.launch {
            selected.forEach { (pkg, min) ->
                val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
                Graph.db.apps().save(
                    MonitoredApp(packageName = pkg, appName = label, timeLimitMin = min, enabled = true)
                )
            }
            Graph.prefs.budgetMin = budget
            Graph.prefs.lockHours = lockHours
            Graph.prefs.resetHour = resetHour
            Graph.prefs.setupLocked = true
            Graph.prefs.onboarded = true
            MonitorService.start(c)
            confirming = false
            onDone()
        }
    }

    Column(Modifier.fillMaxSize().background(Care.Background).systemBarsPadding()) {
        // Header + progress
        Column(Modifier.padding(horizontal = Space.screen, vertical = Space.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrainStormAppIcon(size = 38.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("Brain Storm", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary)
                    Text("One-time setup", fontSize = 11.sp, color = Care.TextSecondary)
                }
            }
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(LAST_STEP + 1) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(if (i <= step) Care.Primary else Care.SurfaceVariant)
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = Space.screen)) {
            when (step) {
                0 -> StepWelcome(pool)
                1 -> StepPrivacy()
                2 -> StepPermissions(c, notif, resume)
                3 -> StepApps(apps, selected, onToggle = { pkg, on ->
                    selected = if (on) selected + (pkg to DEFAULT_APP_LIMIT) else selected - pkg
                }, onEdit = { editing = it })
                4 -> StepBudget(budget, onBudget = { budget = it }, sumMinutes = sumMinutes, check = check, tooTight = tooTight)
                5 -> StepLock(lockHours, resetHour, { lockHours = it }, { resetHour = it })
                else -> StepSummary(selected, apps, budget, sumMinutes, lockHours, resetHour, check, tooTight)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = Space.screen, vertical = Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > 0) TextButton({ step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = canNext,
                onClick = { if (step < LAST_STEP) step++ else confirming = true },
                shape = RoundedCornerShape(Radius.button)
            ) {
                Text(if (step == LAST_STEP) "Lock configuration" else "Next")
            }
        }
    }

    editing?.let { pkg ->
        val label = apps.firstOrNull { it.pkg == pkg }?.label ?: pkg
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(label) },
            text = {
                DurationPicker(
                    label = "Daily limit",
                    minutes = selected[pkg] ?: DEFAULT_APP_LIMIT,
                    onChange = { selected = selected + (pkg to it) },
                    hint = "Frozen after setup — choose carefully."
                )
            },
            confirmButton = { TextButton({ editing = null }) { Text("Done") } }
        )
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Lock this configuration?") },
            text = {
                Text(
                    "Apps, per-app limits, the ${fmtMinutes(budget)} budget, the $lockHours-hour lock " +
                        "and the ${resetHour}:00 reset hour are permanent.\n\n" +
                        "You can add more apps later, but you can never remove one, change a limit " +
                        "or shorten a lock. Clearing app data is the only reset.",
                    fontSize = 13.sp
                )
            },
            confirmButton = { TextButton({ persist() }) { Text("Lock it in") } },
            dismissButton = { TextButton({ confirming = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StepWelcome(pool: MascotPool) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MascotSlot(
            pool = pool,
            slot = "setup",
            mood = MascotState.WELCOME,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(Modifier.height(Space.m))
        Text("Welcome to Brain Storm", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            "Set a daily budget, give each app its own limit, and Brainy locks them the moment " +
                "you go over — for a full day, with no early unlock.",
            textAlign = TextAlign.Center, fontSize = 13.sp, color = Care.TextSecondary,
            modifier = Modifier.padding(top = Space.m)
        )
    }
}

@Composable
private fun StepPrivacy() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Your privacy", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "• Usage is processed only on this device.\n" +
                "• No account, no server, no ads.\n" +
                "• Brain Storm never records your screen. It reads other apps' on-screen labels " +
                "only if you later turn on the optional content detection.\n" +
                "• You can disable monitoring or uninstall at any time.\n\n" +
                "Strict mode means no early unlock from inside Brain Storm. It is high-friction, " +
                "not tamper-proof: Android always lets you revoke permissions or clear app data.",
            modifier = Modifier.padding(top = Space.m), fontSize = 13.sp, color = Care.TextPrimary
        )
    }
}

@Composable
private fun StepPermissions(
    c: android.content.Context,
    notif: androidx.activity.result.ActivityResultLauncher<String>,
    resume: Int
) {
    // Re-read the system state whenever the wizard comes back to the foreground.
    val usageOk = remember(resume) { Perms.usage(c) }
    val overlayOk = remember(resume) { Perms.overlay(c) }
    val notifOk = remember(resume) { Perms.notif(c) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Permissions", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Usage access is required. The other two make the lock reliable.",
            fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 4.dp)
        )
        PermissionRow("Usage access (required)", "Lets Brain Storm measure how long selected apps are open.", usageOk) {
            c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        PermissionRow("Display over other apps", "Lets the lock screen appear on top of a locked app.", overlayOk) {
            c.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${c.packageName}"))
            )
        }
        PermissionRow("Notifications", "Shows the ongoing monitoring status.", notifOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, desc: String, ok: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(Radius.tile))
            .background(Care.Surface)
            .padding(Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(desc, fontSize = 11.sp, color = Care.TextSecondary)
        }
        if (ok) Text("✅") else Button(onGrant, shape = RoundedCornerShape(Radius.button)) { Text("Grant") }
    }
}

@Composable
private fun StepApps(
    apps: List<Inst>,
    selected: Map<String, Int>,
    onToggle: (String, Boolean) -> Unit,
    onEdit: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text("Apps to limit", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Pick the apps and set each one's daily limit. You can add more later — never remove.",
            fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(vertical = 4.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            items(apps, key = { it.pkg }) { a ->
                val mins = selected[a.pkg]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(a.pkg, mins == null) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(mins != null, { onToggle(a.pkg, it) })
                    AppIcon(pkg = a.pkg, size = 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (a.hasDetector) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "detector",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Care.Primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.pill))
                                        .background(Care.tint(Care.Primary, 0.16f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            buildString {
                                append(a.pkg)
                                a.category?.let { append("  ·  ${it.shortLabel}") }
                                if (a.pkg in SUGGESTED && a.category == null) append("  ·  suggested")
                            },
                            fontSize = 10.sp, color = Care.TextSecondary
                        )
                    }
                    if (mins != null) {
                        TextButton({ onEdit(a.pkg) }) { Text(fmtMinutes(mins), fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBudget(
    budget: Int,
    onBudget: (Int) -> Unit,
    sumMinutes: Int,
    check: BudgetCheck,
    tooTight: Map<String, Int>
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Daily budget", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Total time across every monitored app. When it's gone, all of them lock.",
            fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(vertical = 6.dp)
        )
        DurationPicker("Budget", budget, onBudget, hint = "Your whole day of monitored screen time.")
        Spacer(Modifier.height(Space.l))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.tile)).background(Care.Surface).padding(Space.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Per-app limits total", fontSize = 11.sp, color = Care.TextSecondary)
                Text(fmtMinutes(sumMinutes), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Budget", fontSize = 11.sp, color = Care.TextSecondary)
                Text(fmtMinutes(budget), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Care.Primary)
            }
        }
        when (check) {
            BudgetCheck.UNREACHABLE -> Banner(
                "Your budget is larger than the sum of per-app limits, so it could never fire. " +
                    "Lower the budget below ${fmtMinutes(sumMinutes)}.",
                Care.Danger
            )
            BudgetCheck.REDUNDANT -> Banner(
                "Budget equals the sum of per-app limits — redundant, since each app is already " +
                    "stopped at its own limit.",
                Care.Warning
            )
            BudgetCheck.VALID -> Banner("Per-app limits and the budget are both reachable.", Care.Success)
        }
        if (tooTight.isNotEmpty()) {
            Banner(
                "One app alone can exhaust the day: " +
                    tooTight.entries.joinToString { (pkg, min) ->
                        "${pkg.substringAfterLast('.')} ${fmtMinutes(min)}"
                    } + ". Consider 15–30 m per app.",
                Care.Warning
            )
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    Text(
        text,
        fontSize = 11.sp,
        color = Care.TextPrimary,
        modifier = Modifier
            .padding(top = Space.m)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.tile))
            .background(Care.tint(color, 0.14f))
            .padding(Space.m)
    )
}

@Composable
private fun StepLock(
    lockHours: Int,
    resetHour: Int,
    onLock: (Int) -> Unit,
    onReset: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Lock rules", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "How long a lock lasts, and when the daily counters roll over.",
            fontSize = 12.sp, color = Care.TextSecondary, modifier = Modifier.padding(vertical = 6.dp)
        )
        Card(shape = RoundedCornerShape(Radius.card), colors = CardDefaults.cardColors(containerColor = Care.Surface)) {
            Column(Modifier.padding(Space.m)) {
                Stepper("Lock duration", lockHours, 1, 72, suffix = " h", onChange = onLock)
                Stepper("Daily reset hour", resetHour, 0, 23, suffix = ":00", onChange = onReset)
            }
        }
        Text(
            "Locks always run their full duration — they are never shortened, and there is no " +
                "emergency unlock.",
            fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = Space.m)
        )
    }
}

@Composable
private fun StepSummary(
    selected: Map<String, Int>,
    apps: List<Inst>,
    budget: Int,
    sumMinutes: Int,
    lockHours: Int,
    resetHour: Int,
    check: BudgetCheck,
    tooTight: Map<String, Int>
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Review", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "This is permanent. Check it twice.",
            fontSize = 12.sp, color = Care.Danger, modifier = Modifier.padding(vertical = 6.dp)
        )
        Card(shape = RoundedCornerShape(Radius.card), colors = CardDefaults.cardColors(containerColor = Care.Surface)) {
            Column(Modifier.padding(Space.m), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SummaryRow("Daily budget", fmtMinutes(budget), strong = true)
                SummaryRow("Per-app limits total", fmtMinutes(sumMinutes))
                SummaryRow("Lock duration", "$lockHours h")
                SummaryRow("Daily reset", "$resetHour:00")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                selected.entries.sortedBy { it.value }.forEach { (pkg, min) ->
                    SummaryRow(apps.firstOrNull { it.pkg == pkg }?.label ?: pkg, fmtMinutes(min))
                }
            }
        }
        if (check == BudgetCheck.VALID) {
            Banner("When ${fmtMinutes(budget)} is used up, every monitored app locks for $lockHours h.", Care.Primary)
        }
        if (tooTight.isNotEmpty()) {
            Banner("At least one app can consume the entire budget on its own.", Care.Warning)
        }
        Text(
            "\nAdding apps later is allowed. Removing them, or changing any value above, is not.",
            fontSize = 11.sp, color = Care.TextSecondary
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (strong) Care.Primary else Care.TextPrimary)
    }
}
