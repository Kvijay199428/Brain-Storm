package com.vegasol.brainstorm.feature.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.theme.Care
import com.vegasol.brainstorm.theme.Radius
import com.vegasol.brainstorm.theme.Space
import com.vegasol.brainstorm.ui.AppIcon
import com.vegasol.brainstorm.ui.BackButton
import com.vegasol.brainstorm.ui.DurationPicker
import com.vegasol.brainstorm.ui.Inst
import com.vegasol.brainstorm.ui.SurfaceCard
import com.vegasol.brainstorm.ui.fmtHm
import com.vegasol.brainstorm.ui.installed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-page replacement for the old Add dialog. Step one loads and searches installed apps; step
 * two sets the frozen limit. The loading state gets its own animated indicator so an empty list is
 * never mistaken for "no apps found". Layout and cards follow the other detail pages.
 */
@Composable
fun AddAppScreen(
    existing: Set<String>,
    limitTotalSec: Long,
    budgetSec: Long,
    onBack: () -> Unit,
    onAdd: (Inst, Int) -> Unit
) {
    val c = LocalContext.current
    var list by remember { mutableStateOf<List<Inst>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Inst?>(null) }
    var minutes by remember { mutableIntStateOf(60) }

    LaunchedEffect(Unit) {
        list = withContext(Dispatchers.IO) { installed(c) }.filter { it.pkg !in existing }
    }

    val shown = remember(list, query) {
        val available = list ?: emptyList()
        if (query.isBlank()) available
        else available.filter {
            it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true)
        }
    }

    fun goBack() {
        if (selected != null) selected = null else onBack()
    }
    BackHandler { goBack() }

    Column(
        Modifier.fillMaxSize().padding(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.m)
    ) {
        BackButton(onClick = { goBack() })

        val picked = selected
        if (picked == null) {
            Text("Add an app", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Choose an app from this device. Adding it is permanent - its limit cannot be " +
                    "changed or removed.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            when {
                list == null -> {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = Space.l),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = Care.Primary, modifier = Modifier.size(48.dp))
                            Text(
                                "Finding apps on this device...",
                                fontSize = 12.sp, color = Care.TextSecondary,
                                modifier = Modifier.padding(top = Space.m)
                            )
                        }
                    }
                }
                shown.isEmpty() -> {
                    SurfaceCard(Modifier.fillMaxWidth()) {
                        Text(
                            if (list!!.isEmpty()) "Every installed app is already monitored." else "No match.",
                            fontSize = 13.sp, color = Care.TextSecondary,
                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Space.s)
                    ) {
                        items(shown, key = { it.pkg }) { a ->
                            SurfaceCard(
                                Modifier.fillMaxWidth().clickable {
                                    minutes = 60
                                    selected = a
                                }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIcon(a.pkg, 34.dp)
                                    Column(Modifier.weight(1f).padding(start = Space.m)) {
                                        Text(a.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(a.pkg, fontSize = 10.sp, color = Care.TextSecondary)
                                    }
                                    if (a.detected) {
                                        Text(
                                            a.category.label.uppercase(),
                                            fontSize = 10.sp, color = Care.Primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text("Set a daily limit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            SurfaceCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(picked.pkg, 44.dp)
                    Column(Modifier.weight(1f).padding(start = Space.m)) {
                        Text(picked.label, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(picked.pkg, fontSize = 11.sp, color = Care.TextSecondary)
                    }
                }
            }
            DurationPicker(
                "Daily limit", minutes, { minutes = it },
                hint = "Frozen once added - this app can never be removed or changed."
            )
            Text(
                "Adding raises your per-app limits total to " +
                    "${fmtHm(limitTotalSec + minutes * 60L)}. " +
                    "Your ${fmtHm(budgetSec)} budget is unchanged.",
                fontSize = 11.sp, color = Care.TextSecondary
            )
            Button(
                onClick = { onAdd(picked, minutes) },
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.button)
            ) { Text("Add and freeze") }
        }
    }
}
