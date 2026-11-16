package com.vegasol.brainstorm.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens. Single source of truth for colour, spacing and shape.
 * Dark theme ships first; the tokens are theme-agnostic so a light scheme is a later swap
 * in [BrainStormTheme] only.
 */
object Care {
    val Background = Color(0xFF0B1020)
    val Surface = Color(0xFF151C30)
    val SurfaceVariant = Color(0xFF1C2540)
    val Outline = Color(0xFF2A3350)

    val Primary = Color(0xFF8B7CFF)
    val Secondary = Color(0xFF48C6EF)

    val Success = Color(0xFF35C98A)
    val Warning = Color(0xFFFFB547)
    val Danger = Color(0xFFFF647C)

    val TextPrimary = Color(0xFFF5F6FF)
    val TextSecondary = Color(0xFFA1AAC2)

    /** Soft tint used behind icons and chips. */
    fun tint(c: Color, alpha: Float = 0.15f) = c.copy(alpha = alpha)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val screen = 20.dp
    val section = 24.dp
    val xl = 32.dp
}

object Radius {
    val card = 20.dp
    val button = 16.dp
    val tile = 14.dp
    val pill = 50.dp
}

/**
 * Percent thresholds shared by the pure derivation layer and the renderer, so the colour and
 * the mascot mood can never disagree.
 */
object UsageThresholds {
    const val MID = 50f
    const val HIGH = 80f
    const val FULL = 100f
}

/** Progress bar colour for a used/limit percentage. */
fun progressColor(percent: Float): Color = when {
    percent >= UsageThresholds.FULL -> Care.Danger
    percent >= UsageThresholds.HIGH -> Care.Warning
    percent >= UsageThresholds.MID -> Care.Primary
    else -> Care.Success
}

/**
 * Colour for a wellness score, where a higher number is better. The inverse of [progressColor]:
 * reusing the usage ramp would paint a perfect 100 in danger red.
 */
fun scoreColor(score: Int): Color = when {
    score >= 80 -> Care.Success
    score >= 50 -> Care.Warning
    else -> Care.Danger
}

private val Scheme = darkColorScheme(
    primary = Care.Primary,
    onPrimary = Color.White,
    primaryContainer = Care.SurfaceVariant,
    onPrimaryContainer = Care.TextPrimary,
    secondary = Care.Secondary,
    onSecondary = Color(0xFF04121A),
    background = Care.Background,
    onBackground = Care.TextPrimary,
    surface = Care.Surface,
    onSurface = Care.TextPrimary,
    surfaceVariant = Care.SurfaceVariant,
    onSurfaceVariant = Care.TextSecondary,
    outline = Care.Outline,
    error = Care.Danger,
    onError = Color.White
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Care.TextPrimary),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Care.TextPrimary),
    bodyLarge = TextStyle(fontSize = 15.sp, color = Care.TextPrimary),
    bodyMedium = TextStyle(fontSize = 13.sp, color = Care.TextPrimary),
    bodySmall = TextStyle(fontSize = 11.sp, color = Care.TextSecondary),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(Radius.tile),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(Radius.card)
)

@Composable
fun BrainStormTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = Scheme,
    typography = AppTypography,
    shapes = AppShapes,
    content = content
)

/** Backwards-compatible aliases so older call sites keep reading naturally. */
val Purple = Care.Primary
val Bg = Care.Background
val CardBg = Care.Surface
