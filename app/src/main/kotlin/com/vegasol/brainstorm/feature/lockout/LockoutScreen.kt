package com.vegasol.brainstorm.feature.lockout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.Lockout
import com.vegasol.brainstorm.Triggers
import com.vegasol.brainstorm.mascot.MascotCommand
import com.vegasol.brainstorm.mascot.MascotSlot
import com.vegasol.brainstorm.mascot.MascotState
import com.vegasol.brainstorm.mascot.rememberMascotPool
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.appLabel
import com.vegasol.brainstorm.ui.fmtClock
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.nowTicker
import java.text.DateFormat
import java.util.Date

/**
 * Full-screen lock. Deliberately offers one action only: go home. There is no early unlock,
 * including for a budget lockout.
 */
@Composable
fun LockoutScreen(pkg: String, onHome: () -> Unit) {
    val c = LocalContext.current
    val now by nowTicker()
    val pool = rememberMascotPool()
    var rec by remember(pkg) { mutableStateOf<Lockout?>(null) }

    LaunchedEffect(pkg) { rec = Graph.db.lockouts().active(pkg) }
    DisposableEffect(Unit) { onDispose { pool.dispose() } }

    val lock = rec
    val left = if (lock == null) 0L else (lock.expiresAt - now).coerceAtLeast(0L)
    val unlocked = lock == null || left <= 0L
    val budget = lock?.triggerType == Triggers.BUDGET
    val name = remember(pkg) { appLabel(c, pkg) }

    val headline = when {
        unlocked -> "You're free!"
        budget -> "Daily budget spent"
        else -> "Time's up"
    }
    val reason = when {
        lock == null -> "Your lock has ended. Use the time wisely."
        budget ->
            "You used ${fmtHm(lock.triggerValue)} across your monitored apps today " +
                "(budget ${fmtHm(lock.limitValue)}), so everything is paused."
        lock.triggerType == Triggers.TIME ->
            "You used $name for ${fmtHm(lock.triggerValue)} (limit ${fmtHm(lock.limitValue)})."
        else ->
            "You watched ${lock.triggerValue} items in $name (limit ${lock.limitValue})."
    }

    Column(
        Modifier.fillMaxSize().background(Care.Background).systemBarsPadding().padding(Space.screen),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val mascotState = if (unlocked) MascotState.UNLOCK else MascotState.LOCKED
        MascotSlot(
            pool = pool, slot = "lockout",
            mood = mascotState,
            command = MascotCommand(
                state = mascotState,
                countdownSeconds = left
            ),
            modifier = Modifier.fillMaxWidth().weight(1f)
        )

        Text(headline, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(
            reason, fontSize = 13.sp, color = Care.TextSecondary, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Space.s)
        )

        val active = lock?.takeIf { !unlocked }
        if (active != null) {
            Text(
                fmtClock(left),
                fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Care.Danger,
                modifier = Modifier.padding(top = Space.m)
            )
            Text(
                "Unlocks " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(active.expiresAt)),
                fontSize = 12.sp, color = Care.TextSecondary
            )
            Column(
                Modifier
                    .padding(top = Space.m)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.tile))
                    .background(Care.tint(Care.Warning, 0.1f))
                    .padding(Space.m)
            ) {
                Text(
                    "No early unlock. This lock runs its full duration — that's the whole design.",
                    fontSize = 11.sp, color = Care.TextPrimary
                )
                if (active.enforcementNote.startsWith("NOT ENFORCED")) {
                    Text(
                        "⚠ Android did not accept the enforcement request (" +
                            active.enforcementNote.removePrefix("NOT ENFORCED: ") +
                            "). Without Device Owner provisioning this lock is best-effort.",
                        fontSize = 10.sp, color = Care.Warning, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Text(
                "Close the phone and read something great. Your future self will thank you.",
                fontSize = 12.sp, color = Care.TextSecondary, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.m)
            )
        }

        Button(
            onHome,
            Modifier.padding(top = Space.l).fillMaxWidth(),
            shape = RoundedCornerShape(Radius.button)
        ) { Text(if (unlocked) "Close" else "Go to home") }
    }
}
