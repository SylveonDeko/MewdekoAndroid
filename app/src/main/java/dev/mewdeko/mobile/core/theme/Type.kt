package dev.mewdeko.mobile.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * How far each band of the type scale is pulled in from the Material default.
 *
 * Material's ramp is tuned for prose. This app is a dense control surface of
 * stat tiles, settings rows, and list entries, where the stock sizes cost rows
 * per screen without adding legibility, so the large end is cut hardest and
 * body text only modestly.
 */
private const val DisplayScale = 0.72f
private const val HeadlineScale = 0.75f
private const val TitleScale = 0.80f
private const val BodyScale = 0.85f
private const val LabelScale = 0.85f

/**
 * The smallest size any role is allowed to reach.
 *
 * Below roughly this the supporting text under a stat or a settings row stops
 * being comfortably readable at arm's length, so scaling stops here rather
 * than continuing proportionally.
 */
private const val MinimumSp = 10f

/**
 * Scales a style down while preserving its line-height ratio.
 *
 * Scaling line height independently would crush the leading on any role that
 * lands on [MinimumSp].
 */
private fun TextStyle.scaled(factor: Float): TextStyle {
    val original = fontSize.value
    if (original <= 0f) return this
    val ratio = if (lineHeight.value > 0f) lineHeight.value / original else 1.4f
    val target = (original * factor).coerceAtLeast(MinimumSp)
    return copy(fontSize = target.sp, lineHeight = (target * ratio).sp)
}

/**
 * Material 3 type scale, tightened throughout and weighted at the display end
 * so the numeric stat tiles the dashboard leans on stay dense.
 */
val MewdekoTypography = Typography(
    displayLarge = Default.displayLarge.scaled(DisplayScale)
        .copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Default.displayMedium.scaled(DisplayScale)
        .copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = Default.displaySmall.scaled(DisplayScale)
        .copy(fontWeight = FontWeight.Bold),
    headlineLarge = Default.headlineLarge.scaled(HeadlineScale)
        .copy(fontWeight = FontWeight.Bold),
    headlineMedium = Default.headlineMedium.scaled(HeadlineScale)
        .copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = Default.headlineSmall.scaled(HeadlineScale)
        .copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Default.titleLarge.scaled(TitleScale)
        .copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Default.titleMedium.scaled(TitleScale)
        .copy(fontWeight = FontWeight.SemiBold),
    titleSmall = Default.titleSmall.scaled(TitleScale)
        .copy(fontWeight = FontWeight.Medium),
    bodyLarge = Default.bodyLarge.scaled(BodyScale),
    bodyMedium = Default.bodyMedium.scaled(BodyScale),
    bodySmall = Default.bodySmall.scaled(BodyScale),
    labelLarge = Default.labelLarge.scaled(LabelScale)
        .copy(fontWeight = FontWeight.SemiBold),
    labelMedium = Default.labelMedium.scaled(LabelScale),
    labelSmall = Default.labelSmall.scaled(LabelScale),
)

/** Monospaced style for snowflakes, hashes, and other machine identifiers. */
val MonospaceStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp,
    textAlign = TextAlign.Start,
)

/**
 * Label style for the bottom navigation bars.
 *
 * Left at Material's stock size rather than [LabelScale]: five destinations
 * packed into one 80dp-tall dock read as cramped once the caption under each
 * icon is scaled down to the same density as list rows.
 */
val NavBarLabelStyle = Default.labelMedium.copy(fontWeight = FontWeight.Medium)
