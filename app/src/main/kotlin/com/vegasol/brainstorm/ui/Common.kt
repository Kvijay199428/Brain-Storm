package com.vegasol.brainstorm.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vegasol.brainstorm.DailyUsage
import com.vegasol.brainstorm.Lockout
import com.vegasol.brainstorm.MonitoredApp
import com.vegasol.brainstorm.theme.*
import kotlinx.coroutines.delay

// ───────────── Formatters ─────────────
fun fmtDur(s: Long): String = when {
    s >= 3600 -> "${s / 3600}h ${"%02d".format((s / 60) % 60)}m"
    else -> "${s / 60}m ${s % 60}s"
}

/** Compact duration for cards: 2h 35m / 35m */
fun fmtHm(s: Long): String = if (s >= 3600) "${s / 3600}h ${"%02d".format((s / 60) % 60)}m" else "${s / 60}m"

fun fmtClock(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

fun fmtMinutes(min: Int): String = fmtHm(min * 60L)

// ───────────── Clock helpers ─────────────
@Composable
fun nowTicker(): State<Long> {
    val s = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); s.longValue = System.currentTimeMillis() } }
    return s
}

/** Coarse clock for screens that do not need per-second countdowns. */
@Composable
fun minuteTicker(): State<Long> {
    val s = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); s.longValue = System.currentTimeMillis() } }
    return s
}

/** Increments on every ON_RESUME so permission state can be re-read after a settings trip. */
@Composable
fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var t by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) t++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return t
}

// ───────────── App status ─────────────
enum class AppStatus(val label: String, val color: Color) {
    ACTIVE("Active", Care.Success),
    NEAR("Limit near", Care.Warning),
    LOCKED("Locked", Care.Danger),
    PAUSED("Paused", Care.TextSecondary)
}

fun statusOf(a: MonitoredApp, u: DailyUsage?, lock: Lockout?): AppStatus {
    if (lock != null) return AppStatus.LOCKED
    if (!a.enabled) return AppStatus.PAUSED
    val t = a.timeLimitMin?.let { (u?.foregroundSeconds ?: 0L) * 100f / (it * 60f) } ?: 0f
    val c = a.contentLimit?.let { (u?.contentCount ?: 0) * 100f / it } ?: 0f
    return if (maxOf(t, c) >= UsageThresholds.HIGH) AppStatus.NEAR else AppStatus.ACTIVE
}

// ───────────── Primitive UI ─────────────
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, dot: Boolean = true) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Care.tint(color))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(Radius.pill)).background(color))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun StatusChip(s: AppStatus) = Pill(s.label, s.color)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(), modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall, color = Care.TextSecondary
        )
        trailing?.invoke()
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    color: Color = Care.Surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.card))
            .background(color)
            .padding(horizontal = Space.l, vertical = Space.m),
        content = content
    )
}

/**
 * Filled bar for a 0–100 value. [color] defaults to the usage ramp (more is worse); pass
 * `scoreColor(...)` when the value is a score, where more is better.
 */
@Composable
fun UsageProgressBar(
    percent: Float,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    color: Color = progressColor(percent)
) {
    val target = (percent / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, label = "usage")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(Care.SurfaceVariant)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Radius.pill))
                .background(color)
        )
    }
}

@Composable
fun InfoBanner(text: String, color: Color, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.tile))
            .background(Care.tint(color, 0.12f))
            .padding(horizontal = Space.m, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), fontSize = 12.sp, color = Care.TextPrimary)
        action?.invoke()
    }
}

/** Read-only policy notice shown after the one-time setup freezes configuration. */
@Composable
fun LockedNotice(modifier: Modifier = Modifier, text: String = "Set during setup · locked") {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("🔒", fontSize = 11.sp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 11.sp, color = Care.TextSecondary)
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = Space.xl), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, fontSize = 13.sp, color = Care.TextSecondary)
    }
}

// ───────────── App icon (no Coil dependency) ─────────────
@Composable
fun AppIcon(pkg: String, size: Dp = 38.dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96) }.getOrNull()
            ?: runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap() }.getOrNull()
    }
    if (bmp != null) {
        androidx.compose.foundation.Image(
            bitmap = bmp.asImageBitmap(), contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Care.SurfaceVariant),
            contentAlignment = Alignment.Center
        ) { Text(pkg.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Care.TextSecondary) }
    }
}

fun appLabel(c: Context, pkg: String): String =
    runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(pkg, 0)).toString() }
        .getOrDefault(pkg)

// ───────────── Duration editing (hours + minutes, 5-minute steps) ─────────────
const val DURATION_STEP = 5
const val MIN_DURATION = 5
const val MAX_DURATION = 12 * 60

@Composable
private fun UnitStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    max: Int = 99,
    enabled: Boolean = true,
    format: (Int) -> String = { "%02d".format(it) }
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Care.TextSecondary)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { onChange((value - step).coerceAtLeast(0)) },
                modifier = Modifier.size(34.dp), enabled = enabled
            ) { Text("−", fontSize = 16.sp) }
            Text(
                format(value), Modifier.widthIn(min = 42.dp),
                textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            FilledTonalIconButton(
                onClick = { onChange((value + step).coerceAtMost(max)) },
                modifier = Modifier.size(34.dp), enabled = enabled
            ) { Text("+", fontSize = 16.sp) }
        }
    }
}

/**
 * Hours + minutes picker. Minutes move in [DURATION_STEP] increments; the caller applies the
 * overall [minMinutes] / [maxMinutes] clamp so the two fields can never fight each other.
 */
@Composable
fun DurationPicker(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    enabled: Boolean = true
) {
    val total = minutes.coerceIn(MIN_DURATION, MAX_DURATION)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(fmtMinutes(total), fontWeight = FontWeight.Bold, color = Care.Primary)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.m)
        ) {
            UnitStepper(
                "Hours", total / 60,
                onChange = { h -> onChange(((h * 60) + (total % 60)).coerceIn(MIN_DURATION, MAX_DURATION)) },
                modifier = Modifier.weight(1f), max = 11, enabled = enabled
            )
            UnitStepper(
                "Minutes", total % 60,
                onChange = { m -> onChange((((total / 60) * 60) + m).coerceIn(MIN_DURATION, MAX_DURATION)) },
                modifier = Modifier.weight(1f), step = DURATION_STEP, max = 55, enabled = enabled
            )
        }
        if (hint != null) Text(hint, fontSize = 11.sp, color = Care.TextSecondary, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun Stepper(label: String, value: Int, lo: Int, hi: Int, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 13.sp)
        FilledTonalIconButton(onClick = { if (value > lo) onChange((value - step).coerceAtLeast(lo)) }, modifier = Modifier.size(32.dp)) {
            Text("−")
        }
        Text("$value$suffix", Modifier.widthIn(min = 56.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        FilledTonalIconButton(onClick = { if (value < hi) onChange((value + step).coerceAtMost(hi)) }, modifier = Modifier.size(32.dp)) {
            Text("+")
        }
    }
}
