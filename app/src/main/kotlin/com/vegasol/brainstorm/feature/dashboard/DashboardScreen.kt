package com.vegasol.brainstorm.feature.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.MonitorService
import com.vegasol.brainstorm.Perms
import com.vegasol.brainstorm.feature.dashboard.components.ContentMetricsCard
import com.vegasol.brainstorm.feature.dashboard.components.DashboardHeader
import com.vegasol.brainstorm.feature.dashboard.components.LockoutStatusCard
import com.vegasol.brainstorm.feature.dashboard.components.MascotAdviceCard
import com.vegasol.brainstorm.feature.dashboard.components.MascotHeroCard
import com.vegasol.brainstorm.feature.dashboard.components.ScreenTimeCard
import com.vegasol.brainstorm.feature.dashboard.components.UpdateBanner
import com.vegasol.brainstorm.feature.dashboard.components.WelcomeSection
import com.vegasol.brainstorm.feature.dashboard.components.WellnessScoreCard
import com.vegasol.brainstorm.mascot.MascotPool
import com.vegasol.brainstorm.mascot.rememberMascotPool
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.rememberResumeTick
import com.vegasol.brainstorm.update.UpdateState
import java.util.Calendar

@Composable
fun DashboardRoute(
    onOpenTab: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val vm: DashboardViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val now = vm.now.collectAsStateWithLifecycle()
    val update by Graph.updates.state.collectAsStateWithLifecycle()
    val pool = rememberMascotPool()
    val ctx = LocalContext.current
    val resume = rememberResumeTick()

    LaunchedEffect(resume) {
        if (Perms.usage(ctx)) MonitorService.start(ctx)
        vm.refresh()
        // Throttled to one GitHub call per six hours inside the manager.
        Graph.updates.maybeCheck()
    }
    DisposableEffect(Unit) { onDispose { pool.dispose() } }

    val onFix: () -> Unit = {
        when {
            !Perms.usage(ctx) ->
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            !Perms.overlay(ctx) ->
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
            else -> MonitorService.start(ctx)
        }
    }

    DashboardScreen(
        state = state,
        now = now,
        pool = pool,
        update = update,
        onOpenTab = onOpenTab,
        onOpenSettings = onOpenSettings,
        onFix = onFix
    )
}

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    now: State<Long>,
    pool: MascotPool,
    update: UpdateState,
    onOpenTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onFix: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        DashboardHeader(onSettings = onOpenSettings)
        // Nothing below the header is drawn until the first database emission. The old placeholder
        // painted "Finish setup", "no apps" and a row of zeroes for a beat on every cold start, and
        // setup is finished by the time the dashboard exists at all.
        if (state.isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            DashboardBody(state, now, pool, update, onOpenTab, onOpenSettings, onFix)
        }
    }
}

@Composable
private fun DashboardBody(
    state: DashboardUiState,
    now: State<Long>,
    pool: MascotPool,
    update: UpdateState,
    onOpenTab: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onFix: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Space.screen, vertical = Space.m),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        item("welcome") { WelcomeSection(greeting()) }
        item("hero") { MascotHeroCard(state, pool, onFix) }
        if (update is UpdateState.Available) {
            item("update") { UpdateBanner(update, onOpenSettings) }
        }
        item("time") { ScreenTimeCard(state, onClick = { onOpenTab(2) }) }
        item("content") { ContentMetricsCard(state) }

        if (state.isLocked) {
            item("lockout") { LockoutStatusCard(state, now) }
        }

        item("wellness") { WellnessScoreCard(state) }
        item("advice") { MascotAdviceCard(state, pool) }
        item("end") { Spacer(Modifier.height(Space.section)) }
    }
}

fun greeting(now: Long = System.currentTimeMillis()): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning!"
        hour < 18 -> "Good afternoon!"
        else -> "Good evening!"
    }
}
