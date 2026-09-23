package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.ToneRole
import dev.mewdeko.mobile.core.ui.ScallopShape

/** One configured feature shown as a compact tappable chip under a band. */
@Immutable
data class BandChip(
    val icon: ImageVector,
    val value: String,
    val label: String,
    val subtitle: String? = null,
    val featureId: String,
    val progress: Float? = null,
)

/**
 * A chapter of the guild home: badge, title and "See all", a headline number
 * with a descriptor, visual previews, then chips for the rest.
 *
 * The band has no container of its own. Header, headline and chips are inset
 * here; previews inset themselves so a carousel can run edge to edge.
 */
@Composable
fun CategoryBand(
    title: String,
    icon: ImageVector,
    role: ToneRole,
    badgeShape: Shape,
    seeAllId: String,
    headline: Long?,
    descriptor: String,
    headlineLoading: Boolean,
    onOpenFeature: (String) -> Unit,
    chips: List<BandChip>,
    chipsLoading: Boolean,
    headlineText: String? = null,
    headlineTrailing: (@Composable () -> Unit)? = null,
    previews: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HomeDimens.bandInner),
    ) {
        BandHeader(title, icon, role, badgeShape, seeAllId, onOpenFeature)
        BandHeadline(
            headline = if (headlineLoading) null else (headline ?: 0L),
            headlineText = headlineText,
            descriptor = descriptor,
            loading = headlineLoading,
            trailing = headlineTrailing,
        )
        previews()
        if (chips.isNotEmpty() || chipsLoading) {
            BandChipGrid(
                chips = chips,
                role = role,
                loading = chipsLoading,
                onOpenFeature = onOpenFeature,
            )
        }
    }
}

/** Badge, title and the "See all" button. */
@Composable
private fun BandHeader(
    title: String,
    icon: ImageVector,
    role: ToneRole,
    badgeShape: Shape,
    seeAllId: String,
    onOpenFeature: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .homeInset()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BandBadge(icon, role, badgeShape)
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TextButton(
            onClick = { onOpenFeature(seeAllId) },
            colors = ButtonDefaults.textButtonColors(contentColor = role.color),
        ) {
            Text("See all", style = MaterialTheme.typography.labelLarge)
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
            )
        }
    }
}

/** The band's expressive-shape icon badge. */
@Composable
fun BandBadge(icon: ImageVector, role: ToneRole, shape: Shape) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(role.container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = role.onContainer, modifier = Modifier.size(22.dp))
    }
}

/** The big number and its descriptor, stacked at large font scales. */
@Composable
private fun BandHeadline(
    headline: Long?,
    headlineText: String?,
    descriptor: String,
    loading: Boolean,
    trailing: (@Composable () -> Unit)?,
) {
    val numberStyle = MaterialTheme.typography.displayMedium
    val onSurface = MaterialTheme.colorScheme.onSurface
    if (isLargeFont()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .homeInset(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (headlineText != null) {
                Text(
                    text = headlineText,
                    style = numberStyle.tabular(),
                    color = onSurface,
                    modifier = Modifier.skeleton(loading),
                )
            } else {
                AnimatedCount(value = headline, style = numberStyle, color = onSurface)
            }
            Text(
                text = descriptor,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            trailing?.invoke()
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .homeInset(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (headlineText != null) {
                Text(
                    text = headlineText,
                    style = numberStyle.tabular(),
                    color = onSurface,
                    modifier = Modifier
                        .alignByBaseline()
                        .skeleton(loading),
                )
            } else {
                AnimatedCount(
                    value = headline,
                    style = numberStyle,
                    color = onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Text(
                text = descriptor,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alignByBaseline()
                    .weight(1f, fill = false),
            )
            if (trailing != null) {
                Box(modifier = Modifier.align(Alignment.CenterVertically)) { trailing() }
            }
        }
    }
}

/**
 * Chips in two columns on phones, three from 600dp and one at large font
 * scales. Two placeholder chips stand in while the band is still loading.
 */
@Composable
fun BandChipGrid(
    chips: List<BandChip>,
    role: ToneRole,
    loading: Boolean,
    onOpenFeature: (String) -> Unit,
) {
    val large = isLargeFont()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .homeInset(),
    ) {
        val cols = when {
            large -> 1
            maxWidth >= 600.dp -> 3
            else -> 2
        }
        if (chips.isEmpty() && loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(HomeDimens.chipMinHeight)
                            .skeleton(true, MaterialTheme.shapes.medium),
                    )
                }
            }
        } else {
            ChipRows(chips, cols, role, onOpenFeature)
        }
    }
}

/** Lays [chips] out in rows of [cols], each row as tall as its tallest chip. */
@Composable
private fun ChipRows(
    chips: List<BandChip>,
    cols: Int,
    role: ToneRole,
    onOpenFeature: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        chips.chunked(cols).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { chip ->
                    BandMetricChip(
                        chip = chip,
                        role = role,
                        onOpenFeature = onOpenFeature,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tappable metric chip: scalloped icon badge, value, label, optional progress. */
@Composable
fun BandMetricChip(
    chip: BandChip,
    role: ToneRole,
    onOpenFeature: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = listOfNotNull(chip.label, chip.value, chip.subtitle).joinToString(", ")
    Card(
        onClick = { onOpenFeature(chip.featureId) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .heightIn(min = HomeDimens.chipMinHeight)
            .semantics(mergeDescendants = true) { contentDescription = label },
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(ScallopShape(8, 0.08f))
                    .background(role.container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(chip.icon, contentDescription = null, tint = role.onContainer, modifier = Modifier.size(20.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = chip.value,
                    style = MaterialTheme.typography.titleLarge.tabular(),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = chip.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                chip.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                chip.progress?.let { progress ->
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp)
                            .height(3.dp),
                        color = role.color,
                        trackColor = role.container,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A band placeholder with the real header and skeleton content. */
@Composable
fun SkeletonBand(
    title: String,
    icon: ImageVector,
    role: ToneRole,
    badgeShape: Shape,
    seeAllId: String,
    onOpenFeature: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HomeDimens.bandInner),
    ) {
        BandHeader(title, icon, role, badgeShape, seeAllId, onOpenFeature)
        BandHeadline(
            headline = null,
            headlineText = null,
            descriptor = "",
            loading = true,
            trailing = null,
        )
        Box(
            modifier = Modifier
                .homeInset()
                .fillMaxWidth()
                .height(120.dp)
                .skeleton(true, MaterialTheme.shapes.large),
        )
        BandChipGrid(chips = emptyList(), role = role, loading = true, onOpenFeature = onOpenFeature)
    }
}
