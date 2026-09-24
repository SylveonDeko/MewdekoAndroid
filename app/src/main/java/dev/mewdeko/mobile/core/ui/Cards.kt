package dev.mewdeko.mobile.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Whether the current Material scheme is a dark one.
 *
 * Read from the background luminance so it also holds under the system
 * Material You source.
 */
@Composable
@ReadOnlyComposable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * The dashboard's card wash: a 135 degree, three stop linear gradient of the
 * guild gradient (start, mid, end) at low alpha, drawn over a surface.
 *
 * Mirrors the web `linear-gradient(135deg, start10, mid15, end10)` card
 * background exactly, using the hex alpha steps in [DashAlpha]; [strength]
 * scales every stop and stays at one for standard cards.
 */
@Composable
@ReadOnlyComposable
fun guildWash(strength: Float = 1f): Brush {
    val palette = LocalGuildPalette.current
    val edge = DashAlpha.Hex10 * strength
    val middle = DashAlpha.Hex15 * strength
    return Brush.linearGradient(
        listOf(
            palette.gradientStart.color.copy(alpha = edge),
            palette.gradientMid.color.copy(alpha = middle),
            palette.gradientEnd.color.copy(alpha = edge),
        ),
    )
}

/**
 * A single hue wash for surfaces owned by one role or accent, running from
 * the dashboard's `20` tint in the top-left corner to its `10` tint in the
 * bottom-right, the same 135 degree direction as [guildWash].
 */
fun toneWash(color: Color, strength: Float = 1f): Brush = Brush.linearGradient(
    listOf(
        color.copy(alpha = DashAlpha.Hex20 * strength),
        color.copy(alpha = DashAlpha.Hex10 * strength),
    ),
)

/** The 1dp border at the dashboard's `30` tint that frames every guild surface. */
@Composable
@ReadOnlyComposable
fun guildBorder(color: Color = MaterialTheme.colorScheme.primary): BorderStroke =
    BorderStroke(1.dp, color.copy(alpha = DashAlpha.Hex30))

/**
 * [ink] when it holds 4.5:1 on [background], otherwise the scheme's
 * `onSurface`, for text drawn in a solid palette color. Icons keep the
 * palette color regardless.
 */
@Composable
@ReadOnlyComposable
fun readableInk(ink: Color, background: Color = MaterialTheme.colorScheme.surface): Color {
    val fg = ink.luminance()
    val bg = background.luminance()
    val ratio = (maxOf(fg, bg) + 0.05f) / (minOf(fg, bg) + 0.05f)
    return if (ratio >= 4.5f) ink else MaterialTheme.colorScheme.onSurface
}

/**
 * Paints [base] then [wash] inside [shape], for Material containers whose own
 * container color is set to transparent so the wash sits under their content
 * and state layers.
 */
fun Modifier.washed(base: Color, wash: Brush, shape: Shape): Modifier = this
    .background(base, shape)
    .background(wash, shape)

/**
 * A Material 3 card dressed in the guild identity: the surface-low tone,
 * a [guildWash] over it and a [guildBorder].
 *
 * Pass [onClick] for a tappable card; ripples and state layers still come
 * from the M3 card.
 */
@Composable
fun GuildCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    wash: Brush = guildWash(),
    border: BorderStroke? = guildBorder(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val dressed = modifier.washed(MaterialTheme.colorScheme.surfaceContainerLow, wash, shape)
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = dressed,
            shape = shape,
            colors = colors,
            border = border,
            content = content,
        )
    } else {
        Card(
            modifier = dressed,
            shape = shape,
            colors = colors,
            border = border,
            content = content,
        )
    }
}

/**
 * Themed card container shared across the per-guild section tabs.
 *
 * A [GuildCard]: the guild gradient washed over the container-low tone with
 * a primary hairline, matching the web dashboard's card treatment.
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: Int = 16,
    content: @Composable ColumnScope.() -> Unit,
) {
    GuildCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(contentPadding.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

/**
 * Header row with a tinted icon glyph and a title, used inside [SectionCard].
 *
 * The glyph badge is the dashboard's icon background: the accent at the `20`
 * tint with a `30` border and the icon in the solid accent, primary unless
 * [tint] overrides it.
 */
@Composable
fun SectionCardHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    val badgeShape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(accent.copy(alpha = DashAlpha.Hex20), badgeShape)
                .border(1.dp, accent.copy(alpha = DashAlpha.Hex30), badgeShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Tappable feature row used inside section tabs to navigate to a deeper view. */
@Composable
fun FeatureLinkCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    GuildCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        wash = guildWash(),
        border = guildBorder(accent),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accent.copy(alpha = DashAlpha.Hex20),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Compact stat tile used in section grids.
 *
 * Carries the standard card wash (or a single hue wash of [tint]) with a
 * `30` hairline; the value and icon read in the solid accent color.
 * [valueModifier] decorates the value text, for example with a loading
 * skeleton. [valueMaxLines] defaults to one line for numbers; callers whose
 * value is text, such as a name or a state, pass 2 so it wraps instead of
 * being cut off.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    icon: ImageVector? = null,
    valueModifier: Modifier = Modifier,
    valueMaxLines: Int = 1,
) {
    val accent = tint ?: MaterialTheme.colorScheme.primary
    val shape = MaterialTheme.shapes.medium
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StatTileMinHeight)
            .washed(
                base = MaterialTheme.colorScheme.surfaceContainerLow,
                wash = if (tint == null) guildWash() else toneWash(tint),
                shape = shape,
            ),
        shape = shape,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = guildBorder(accent),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(bottom = 2.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = readableInk(accent, MaterialTheme.colorScheme.surfaceContainerLow),
                maxLines = valueMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = valueModifier,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Keeps tiles in a row the same height when one label wraps and another does not. */
private val StatTileMinHeight = 72.dp
