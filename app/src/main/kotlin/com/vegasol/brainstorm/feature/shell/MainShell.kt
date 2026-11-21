package com.vegasol.brainstorm.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vegasol.brainstorm.Graph
import com.vegasol.brainstorm.feature.apps.AppReportScreen
import com.vegasol.brainstorm.feature.apps.AppsScreen
import com.vegasol.brainstorm.feature.dashboard.DashboardRoute
import com.vegasol.brainstorm.feature.reports.ReportsScreen
import com.vegasol.brainstorm.feature.settings.SettingsScreen
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.ui.BrainStormAppIcon

private data class Destination(val label: String, val icon: ImageVector)

@Composable
fun MainShell() {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<String?>(null) }
    val locks by Graph.db.lockouts().observeActive().collectAsStateWithLifecycle(emptyList())

    Scaffold(
        containerColor = Care.Background,
        bottomBar = {
            DashboardBottomNavigation(
                selected = tab,
                hasLockout = locks.isNotEmpty(),
                onSelect = { tab = it; detail = null }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Care.Background)) {
            val d = detail
            if (d != null) {
                BackHandler { detail = null }
                AppReportScreen(d) { detail = null }
            } else when (tab) {
                0 -> DashboardRoute(
                    onOpenTab = { tab = it },
                    onOpenSettings = { tab = 3 }
                )
                1 -> AppsScreen { detail = it }
                2 -> ReportsScreen { detail = it }
                else -> SettingsScreen()
            }
        }
    }
}

@Composable
private fun DashboardBottomNavigation(selected: Int, hasLockout: Boolean, onSelect: (Int) -> Unit) {
    val items = listOf(
        Destination("Home", Icons.Filled.Home),
        Destination("Apps", Icons.AutoMirrored.Filled.List),
        Destination("Reports", Icons.Filled.DateRange),
        Destination("Settings", Icons.Filled.Settings)
    )
    NavigationBar(containerColor = Care.Surface, tonalElevation = 0.dp) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = {
                    if (index == 0) {
                        val iconModifier = Modifier.alpha(if (selected == 0) 1f else 0.65f)
                        if (hasLockout) {
                            BadgedBox(badge = { Badge(containerColor = Care.Danger) }) {
                                BrainStormAppIcon(size = 24.dp, modifier = iconModifier)
                            }
                        } else {
                            BrainStormAppIcon(size = 24.dp, modifier = iconModifier)
                        }
                    } else {
                        Icon(item.icon, contentDescription = item.label)
                    }
                },
                label = { Text(item.label, fontSize = 11.sp) }
            )
        }
    }
}
