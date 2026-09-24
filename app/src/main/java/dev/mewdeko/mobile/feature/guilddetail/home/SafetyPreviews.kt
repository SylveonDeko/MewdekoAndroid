package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.RemoveModerator
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.ToneRole
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.ScallopShape
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.feature.guilddetail.formatted
import dev.mewdeko.mobile.navigation.FeatureCategory
import dev.mewdeko.mobile.util.relativeToNow
import java.time.Instant

/** The number of protection modules the status endpoint reports. */
private const val ProtectionCount = 5

/**
 * The Safety chapter: protections switched on, a per-module grid, and the
 * latest warnings.
 */
@Composable
fun SafetyBand(
    security: SecurityData,
    loaded: Boolean,
    roles: HomeRoles,
    reduced: Boolean,
    onOpenFeature: (String) -> Unit,
    onOpenCategory: (FeatureCategory) -> Unit,
) {
    val role = roles.safety
    val badge = ScallopShape(12, 0.06f)
    val onSeeAll = { onOpenCategory(FeatureCategory.SECURITY) }
    if (!loaded) {
        SkeletonBand("Safety", Icons.Default.Shield, role, badge, onSeeAll, onOpenFeature)
        return
    }

    val protection = security.protection
    val warnings = security.warnings.orEmpty()
    val active = protection?.activeCount ?: 0
    val chips = buildList {
        if (warnings.isNotEmpty()) {
            add(
                BandChip(
                    icon = Icons.Default.Warning,
                    value = warnings.size.formatted(),
                    label = "Total warnings",
                    featureId = "moderation",
                )
            )
        }
    }

    CategoryBand(
        title = "Safety",
        icon = Icons.Default.Shield,
        role = role,
        badgeShape = badge,
        onSeeAll = onSeeAll,
        headline = if (protection != null) active.toLong() else warnings.size.toLong(),
        headlineText = if (protection != null) "$active/$ProtectionCount" else null,
        descriptor = if (protection != null) "protections active" else "warnings on record",
        headlineLoading = false,
        headlineTrailing = if (protection != null) {
            { ShieldMeter(active = active, total = ProtectionCount, role = role, reduced = reduced) }
        } else {
            null
        },
        onOpenFeature = onOpenFeature,
        chips = chips,
        chipsLoading = false,
        previews = {
            if (protection != null) {
                ProtectionGrid(
                    flags = protection,
                    role = role,
                    onOpen = { onOpenFeature("administration") },
                    modifier = Modifier.homeInset(),
                )
            }
            if (warnings.isNotEmpty()) {
                RecentWarningsCard(
                    warnings = warnings,
                    role = role,
                    onOpen = { onOpenFeature("moderation") },
                    modifier = Modifier.homeInset(),
                )
            }
        },
    )
}

/**
 * A row of pills, one per protection module, filled for the active count.
 * The pills fill in one after another the first time the meter shows.
 */
@Composable
fun ShieldMeter(active: Int, total: Int, role: ToneRole, reduced: Boolean) {
    var shown by rememberSaveable { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { shown = true }
    Row(
        modifier = Modifier.semantics { contentDescription = "$active of $total protections active" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { index ->
            val on = shown && index < active
            val color by animateColorAsState(
                targetValue = if (on) role.color else role.container,
                animationSpec = if (reduced) snap<Color>() else tween<Color>(300, delayMillis = index * 60),
                label = "shieldPill$index",
            )
            Box(
                modifier = Modifier
                    .size(20.dp, 10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/** Every protection module with its state spelled out, not only colored. */
@Composable
fun ProtectionGrid(
    flags: ProtectionFlags,
    role: ToneRole,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        "Anti-raid" to flags.antiRaid.enabled,
        "Anti-spam" to flags.antiSpam.enabled,
        "Anti-alt" to flags.antiAlt.enabled,
        "Mass mention" to flags.antiMassMention.enabled,
        "Mass post" to flags.antiMassPost.enabled,
    )
    val cols = if (isLargeFont()) 1 else 2
    GuildCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        border = guildBorder(role.color),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.chunked(cols).forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { (name, on) ->
                        ProtectionPill(
                            name = name,
                            on = on,
                            role = role,
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
}

/** One protection module: shield icon, name and "Active" or "Off". */
@Composable
fun ProtectionPill(name: String, on: Boolean, role: ToneRole, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (on) role.container else scheme.surfaceContainerHigh,
        contentColor = if (on) role.onContainer else scheme.onSurfaceVariant,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (on) Icons.Default.VerifiedUser else Icons.Default.RemoveModerator,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (on) LocalContentColor.current else scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (on) "Active" else "Off",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * The two most recent warnings. The warning record carries no member name,
 * so each row shows the reason, the moderator and when.
 */
@Composable
fun RecentWarningsCard(
    warnings: List<RecentModerationAction>,
    role: ToneRole,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latest = warnings.sortedByDescending { it.dateAdded ?: Instant.EPOCH }.take(2)
    GuildCard(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        border = guildBorder(role.color),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Recent",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            latest.forEach { warning ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = role.color,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(18.dp),
                    )
                    Column {
                        Text(
                            text = warning.reason?.takeIf { it.isNotBlank() } ?: "No reason given",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildList {
                            add(warning.moderator?.takeIf { it.isNotBlank() } ?: "Unknown")
                            warning.dateAdded?.let { add(it.relativeToNow()) }
                            if (warning.forgiven) add("Forgiven")
                        }.joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
