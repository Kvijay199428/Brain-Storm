package com.vegasol.brainstorm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vegasol.brainstorm.theme.Care
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Bar chart for a variable-length window. Labels are skipped for long windows so the axis stays
 * readable on a phone. When [limitSeconds] is set, a bar that went over the limit is drawn in the
 * danger colour and the scale always leaves room for the limit itself.
 */
@Composable
fun UsageBarChart(
    days: List<Pair<String, Long>>,
    todayKey: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    limitSeconds: Long? = null
) {
    val floor = maxOf(days.maxOfOrNull { it.second } ?: 0L, limitSeconds ?: 0L)
    val maxSecs = floor.coerceAtLeast(60L)
    val labelEvery = if (days.size <= 7) 1 else 5
    Row(
        modifier.fillMaxWidth().height(if (compact) 74.dp else 128.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        days.forEachIndexed { index, (dateKey, secs) ->
            val isToday = dateKey == todayKey
            val overLimit = limitSeconds != null && limitSeconds > 0 && secs > limitSeconds
            val fraction = if (secs <= 0) 0.02f else (secs.toFloat() / maxSecs).coerceIn(0.04f, 1f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (!compact) {
                    Text(
                        if (secs > 0) fmtHm(secs) else "—",
                        fontSize = 8.sp, maxLines = 1,
                        color = if (isToday) Care.TextPrimary else Care.TextSecondary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(Modifier.height(3.dp))
                }
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Care.SurfaceVariant))
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                when {
                                    overLimit -> Care.Danger
                                    isToday -> Care.Primary
                                    else -> Care.Primary.copy(alpha = 0.45f)
                                }
                            )
                    )
                }
                if (!compact) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isToday) "Today" else dayLabel(dateKey),
                        fontSize = 9.sp,
                        color = if (isToday) Care.Primary else Care.TextSecondary,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                } else if (index % labelEvery == 0 || isToday) {
                    Text(dayLabel(dateKey).take(1), fontSize = 8.sp, color = Care.TextSecondary)
                }
            }
        }
    }
}

private fun dayLabel(key: String): String = runCatching {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val d = sdf.parse(key) ?: return@runCatching key.takeLast(2)
    SimpleDateFormat("EEE", Locale.US).format(d)
}.getOrDefault(key.takeLast(2))

/**
 * 24 bars, one per clock hour of the current day. [highlightHour] gets the accent colour (the peak
 * hour on an app report, the current hour on the reports screen); [currentHour], when different,
 * stays slightly brighter than the rest so "now" is still visible.
 */
@Composable
fun HourlyBarChart(
    hours: List<Long>,
    modifier: Modifier = Modifier,
    highlightHour: Int? = null,
    currentHour: Int? = null
) {
    val maxSecs = (hours.maxOrNull() ?: 0L).coerceAtLeast(60L)
    Row(
        modifier.fillMaxWidth().height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        hours.forEachIndexed { hour, secs ->
            val isPeak = hour == highlightHour
            val isNow = hour == currentHour
            val fraction = if (secs <= 0) 0.02f else (secs.toFloat() / maxSecs).coerceIn(0.04f, 1f)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(Care.SurfaceVariant)
                    )
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(
                                when {
                                    isPeak -> Care.Primary
                                    isNow -> Care.Primary.copy(alpha = 0.7f)
                                    else -> Care.Primary.copy(alpha = 0.35f)
                                }
                            )
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    if (hour % 6 == 0) "%02d".format(hour) else "",
                    fontSize = 8.sp, maxLines = 1,
                    color = when {
                        isPeak -> Care.Primary
                        isNow -> Care.TextPrimary
                        else -> Care.TextSecondary
                    },
                    fontWeight = if (isPeak || isNow) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}
