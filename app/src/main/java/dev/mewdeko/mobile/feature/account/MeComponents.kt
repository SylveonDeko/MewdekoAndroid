package dev.mewdeko.mobile.feature.account

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mewdeko.mobile.core.model.XpEntry
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.feature.guilddetail.home.rememberReducedMotion
import dev.mewdeko.mobile.feature.guilddetail.home.tabular
import dev.mewdeko.mobile.util.withSeparators

/** The value a profile row shows when the field is empty. */
internal const val NotSet = "Not set"

/**
 * A section title placed above its cards: a small [GlyphOrb] in [tint] and a
 * heading, announced as a heading to accessibility services.
 */
@Composable
internal fun MeSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlyphOrb(icon, tint = tint)
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A card of rows in the guild wash, framed at the `30` tint of [tint], with
 * an optional small-caps [overline] title.
 */
@Composable
internal fun MeCard(
    modifier: Modifier = Modifier,
    overline: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    GuildCard(modifier = modifier.fillMaxWidth(), border = guildBorder(tint)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            if (overline != null) {
                MeOverline(overline, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
            }
            content()
        }
    }
}

/** A small uppercase label that titles a group of rows. */
@Composable
internal fun MeOverline(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/** A hairline between rows in a [MeCard], optionally inset past a leading glyph. */
@Composable
internal fun MeDivider(modifier: Modifier = Modifier, inset: Int = 0) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A label and value row with an optional leading glyph in the primary and
 * an optional subtitle. [NotSet] values fade to the outline tone.
 */
@Composable
internal fun MeValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) MeRowGlyph(icon, scheme.primary)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurface)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.tabular(),
            fontWeight = FontWeight.Medium,
            color = if (value == NotSet) scheme.outline else scheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A 24dp-wide leading glyph for a card row. */
@Composable
internal fun MeRowGlyph(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.width(24.dp), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * The state line of a card without data: a primary spinner and "Loading"
 * while [loading], otherwise the failure prompt.
 */
@Composable
internal fun MeStatusLine(loading: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = if (loading) "Loading" else "Couldn't load this section. Pull to refresh.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A caption shown when a list inside a card is empty. */
@Composable
internal fun MeEmptyLine(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 12.dp),
    )
}

/** A value numeral over a caption, for small inline stats in a row of three. */
@Composable
internal fun RowScope.MiniStat(value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

/** Segment opacities of [ChannelShareStrip], from the busiest channel down. */
private val ShareOpacities = listOf(1f, 0.75f, 0.55f, 0.4f, 0.25f)

/**
 * A 10dp capsule split into one segment per count, sized by share, in [tint]
 * stepping down in opacity from the busiest channel.
 */
@Composable
internal fun ChannelShareStrip(counts: List<Long>, tint: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        counts.forEachIndexed { index, count ->
            Box(
                modifier = Modifier
                    .weight(count.coerceAtLeast(1L).toFloat())
                    .fillMaxHeight()
                    .background(tint.copy(alpha = ShareOpacities[index.coerceAtMost(ShareOpacities.lastIndex)])),
            )
        }
    }
}

/**
 * A highlight word as a capsule in the primary at 14% with a hairline and a
 * remove button in the error tone.
 */
@Composable
internal fun HighlightChip(word: String, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Remove $word", onClick = onRemove)
                    .semantics { contentDescription = "Remove $word" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * One server's level: the name with a "Level N" pill, then a capsule track
 * filled with the palette's start to middle gradient, scaled against the top
 * server, and the XP total.
 */
@Composable
internal fun XpLevelRow(entry: XpEntry, maxXp: Long, modifier: Modifier = Modifier) {
    val palette = LocalGuildPalette.current
    val start = palette.gradientStart.color
    val mid = palette.gradientMid.color
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val reduced = rememberReducedMotion()
    val target = (entry.totalXp.toFloat() / maxXp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    var shown by remember { mutableFloatStateOf(if (reduced) target else 0f) }
    LaunchedEffect(target) { shown = target }
    val fraction by animateFloatAsState(shown, spring(stiffness = 200f), label = "xpFill")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${entry.guildName}, level ${entry.level}, ${entry.totalXp} XP"
            },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = entry.guildName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatePill(
                text = "Level ${entry.level}",
                tone = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.ArrowCircleUp,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp),
            ) {
                val radius = CornerRadius(size.height / 2f)
                drawRoundRect(color = track, cornerRadius = radius)
                val width = maxOf(size.height, size.width * fraction)
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(start, mid), startX = 0f, endX = width),
                    topLeft = Offset.Zero,
                    size = Size(width, size.height),
                    cornerRadius = radius,
                )
            }
            Text(
                text = "${entry.totalXp.withSeparators()} XP",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
