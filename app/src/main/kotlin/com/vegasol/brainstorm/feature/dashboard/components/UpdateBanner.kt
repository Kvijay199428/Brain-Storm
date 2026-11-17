package com.vegasol.brainstorm.feature.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.update.UpdateState

/**
 * One line on the dashboard when a newer build exists. Renders nothing otherwise — an empty
 * "you are up to date" card every day would be noise.
 */
@Composable
fun UpdateBanner(state: UpdateState, onOpen: () -> Unit) {
    val info = (state as? UpdateState.Available)?.info ?: return
    SurfaceCard(Modifier.fillMaxWidth(), color = Care.tint(Care.Primary, 0.12f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Update available", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Brain Storm ${info.versionName} (build ${info.versionCode}) is published on GitHub.",
                    fontSize = 11.sp, color = Care.TextSecondary
                )
            }
            TextButton(onOpen) { Text("Open", fontSize = 12.sp) }
        }
    }
}
