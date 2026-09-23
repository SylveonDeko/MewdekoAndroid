package dev.mewdeko.mobile.feature.guilddetail.home

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.GuildInfo
import dev.mewdeko.mobile.core.ui.RingGauge
import dev.mewdeko.mobile.core.ui.Sparkbars
import dev.mewdeko.mobile.core.ui.Sparkline
import dev.mewdeko.mobile.feature.guilddetail.GuildMemberStats
import dev.mewdeko.mobile.feature.guilddetail.GuildRoleStats
import dev.mewdeko.mobile.feature.guilddetail.formatted
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Leaf corner shapes whose small corner points at the centre of the 2x2 grid. */
private object LeafShapes {
    /** Top-left tile. */
    val members: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 8.dp, bottomStart = 28.dp)

    /** Top-right tile. */
    val messages: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 8.dp)

    /** Bottom-left tile. */
    val tickets: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 8.dp, bottomEnd = 28.dp, bottomStart = 28.dp)

    /** Bottom-right tile. */
    val moderation: Shape = RoundedCornerShape(topStart = 8.dp, topEnd = 28.dp, bottomEnd = 28.dp, bottomStart = 28.dp)
}

/** A trend or context pill on a pulse tile. */
@Immutable
data class DeltaSpec(val text: String, val icon: ImageVector)

/** The arrow matching the sign of [n]. */
private fun trendIcon(n: Int): ImageVector = when {
    n > 0 -> Icons.AutoMirrored.Filled.TrendingUp
    n < 0 -> Icons.AutoMirrored.Filled.TrendingDown
    else -> Icons.AutoMirrored.Filled.TrendingFlat
}

/**
 * The four headline tiles: members, messages today, open tickets and mod
 * actions, each with a trend and a small visual.
 *
 * Two columns on phones, four from 600dp, and one at large font scales.
 */
@Composable
fun PulseGrid(
    info: GuildInfo?,
    memberStats: GuildMemberStats?,
    roleStats: GuildRoleStats?,
    flow: List<HomeSeries.FlowDay>,
    community: CommunityData,
    communityLoaded: Boolean,
    security: SecurityData,
    securityLoaded: Boolean,
    roles: HomeRoles,
    reduced: Boolean,
    entered: EnteredKeys,
    onOpenFeature: (String) -> Unit,
) {
    val tiles = listOf<@Composable (Modifier) -> Unit>(
        { modifier -> MembersTile(info, memberStats, flow, roles, reduced, onOpenFeature, modifier) },
        { modifier -> MessagesTile(community, communityLoaded, roles, onOpenFeature, modifier) },
        { modifier -> TicketsTile(community, communityLoaded, roleStats, roles, reduced, onOpenFeature, modifier) },
        { modifier -> ModerationTile(security, securityLoaded, roles, onOpenFeature, modifier) },
    )
    val large = isLargeFont()

    Column(verticalArrangement = Arrangement.spacedBy(HomeDimens.gridGap)) {
        HomeSectionHeader(
            title = "At a glance",
            trailing = if (flow.isEmpty()) {
                null
            } else {
                {
                    Text(
                        text = HomeSeries.periodLabel(flow),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val cols = when {
                large -> 1
                maxWidth >= 600.dp -> 4
                else -> 2
            }
            Column(verticalArrangement = Arrangement.spacedBy(HomeDimens.gridGap)) {
                tiles.chunked(cols).forEachIndexed { rowIndex, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(HomeDimens.gridGap),
                    ) {
                        row.forEachIndexed { columnIndex, tile ->
                            val index = rowIndex * cols + columnIndex
                            tile(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .riseOnce("pulse-$index", entered, delayMillis = index * 40),
                            )
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/**
 * A tappable tonal tile: icon and title, a large value, an optional trend
 * pill and caption, and a small visual pinned to the bottom.
 */
@Composable
fun PulseTile(
    icon: ImageVector,
    title: String,
    value: @Composable () -> Unit,
    delta: DeltaSpec?,
    subline: String?,
    container: Color,
    content: Color,
    shape: Shape,
    onClick: () -> Unit,
    a11y: String,
    modifier: Modifier = Modifier,
    visual: (@Composable () -> Unit)? = null,
) {
    val large = isLargeFont()
    val clamp = fontScaleClamp()
    Card(
        onClick = onClick,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .heightIn(min = HomeDimens.pulseMinHeight)
                .padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            value()
            if (delta != null) DeltaChip(delta, content)
            if (subline != null) {
                Text(
                    text = subline,
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.8f),
                    maxLines = if (large) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeDimens.pulseVisual * clamp),
            ) {
                visual?.invoke()
            }
        }
    }
}

/** A small translucent pill with a trend arrow and a short phrase. */
@Composable
fun DeltaChip(spec: DeltaSpec, content: Color) {
    Surface(shape = CircleShape, color = content.copy(alpha = 0.12f), contentColor = content) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(spec.icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                text = spec.text,
                style = MaterialTheme.typography.labelMedium.tabular(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The value slot style shared by every tile. */
@Composable
private fun TileValue(value: Long?, content: Color) {
    AnimatedCount(
        value = value,
        style = MaterialTheme.typography.displaySmall,
        color = content,
    )
}

/** A plain text value for states that are not a count. */
@Composable
private fun TileText(text: String, content: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall.tabular(),
        color = content,
        maxLines = 1,
    )
}

/** Members, with the net change and an estimated member count line. */
@Composable
private fun MembersTile(
    info: GuildInfo?,
    memberStats: GuildMemberStats?,
    flow: List<HomeSeries.FlowDay>,
    roles: HomeRoles,
    reduced: Boolean,
    onOpenFeature: (String) -> Unit,
    modifier: Modifier,
) {
    val role = roles.community
    val total = memberStats?.total ?: info?.memberCount
    val net = flow.sumOf { it.net }
    val estimate = remember(total, flow) {
        if (total != null && flow.size >= 2) HomeSeries.memberEstimate(total, flow) else emptyList()
    }
    val a11y = buildString {
        append("Members, ")
        append(total?.formatted() ?: "loading")
        if (flow.isNotEmpty()) {
            val direction = when {
                net > 0 -> "up ${abs(net)}"
                net < 0 -> "down ${abs(net)}"
                else -> "no change"
            }
            append(", ").append(direction).append(" over ${flow.size} days")
        }
        memberStats?.let {
            append(", ${it.humans.formatted()} people and ${it.bots.formatted()} bots")
        }
        append(".")
        if (estimate.isNotEmpty()) append(" Trend estimated from joins and leaves.")
    }
    PulseTile(
        icon = Icons.Default.Groups,
        title = "Members",
        value = { TileValue(total?.toLong(), role.onContainer) },
        delta = if (flow.isEmpty()) null else DeltaSpec("${HomeSeries.signed(net)} in ${flow.size}d", trendIcon(net)),
        subline = memberStats?.let { "${it.humans.formatted()} people · ${it.bots.formatted()} bots" } ?: "members",
        container = role.container,
        content = role.onContainer,
        shape = LeafShapes.members,
        onClick = { onOpenFeature("serverstats") },
        a11y = a11y,
        modifier = modifier,
        visual = if (estimate.isEmpty()) {
            null
        } else {
            {
                Sparkline(
                    values = estimate,
                    color = role.onContainer,
                    animate = !reduced,
                    modifier = Modifier.matchParentSizeInBox(),
                )
            }
        },
    )
}

/** Messages today, the busiest local hour, and an hour-of-day histogram. */
@Composable
private fun MessagesTile(
    community: CommunityData,
    loaded: Boolean,
    roles: HomeRoles,
    onOpenFeature: (String) -> Unit,
    modifier: Modifier,
) {
    val messages = community.messages
    val disabled = loaded && (messages == null || !messages.enabled)
    val context = LocalContext.current
    val is24h = remember(context) { DateFormat.is24HourFormat(context) }
    val buckets = remember(messages) {
        messages?.busiestHours?.let { HomeSeries.localHourBuckets(it) }.orEmpty()
    }
    val peak = HomeSeries.peakHour(buckets)

    if (disabled) {
        val content = MaterialTheme.colorScheme.onSurface
        PulseTile(
            icon = Icons.Default.Forum,
            title = "Messages today",
            value = { TileText("Off", content) },
            delta = null,
            subline = "Tracking disabled",
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
            content = content,
            shape = LeafShapes.messages,
            onClick = { onOpenFeature("messagestats") },
            a11y = "Messages today, tracking disabled",
            modifier = modifier,
        )
        return
    }

    val role = roles.automation
    val today = if (loaded) messages?.dailyMessages else null
    val a11y = buildString {
        append("Messages today, ")
        append(today?.formatted() ?: "loading")
        messages?.let { append(", ${it.totalMessages.formatted()} all time") }
        peak?.let { append(", busiest around ${HomeSeries.hourLabel(it, is24h)}") }
    }
    PulseTile(
        icon = Icons.Default.Forum,
        title = "Messages today",
        value = { TileValue(today, role.onContainer) },
        delta = peak?.let { DeltaSpec("Busiest ${HomeSeries.hourLabel(it, is24h)}", Icons.Default.Schedule) },
        subline = messages?.let { "${HomeSeries.compact(it.totalMessages)} all time" },
        container = role.container,
        content = role.onContainer,
        shape = LeafShapes.messages,
        onClick = { onOpenFeature("messagestats") },
        a11y = a11y,
        modifier = modifier,
        visual = if (buckets.isEmpty() || peak == null) {
            null
        } else {
            {
                Sparkbars(
                    values = buckets,
                    color = role.onContainer,
                    highlight = peak,
                    dimAlpha = 0.45f,
                    modifier = Modifier.matchParentSizeInBox(),
                )
            }
        },
    )
}

/** Open tickets with a share ring, or a roles tile when tickets are not set up. */
@Composable
private fun TicketsTile(
    community: CommunityData,
    loaded: Boolean,
    roleStats: GuildRoleStats?,
    roles: HomeRoles,
    reduced: Boolean,
    onOpenFeature: (String) -> Unit,
    modifier: Modifier,
) {
    val role = roles.entertainment
    val tickets = community.tickets

    if (!loaded) {
        PulseTile(
            icon = Icons.Default.ConfirmationNumber,
            title = "Open tickets",
            value = { TileValue(null, role.onContainer) },
            delta = null,
            subline = null,
            container = role.container,
            content = role.onContainer,
            shape = LeafShapes.tickets,
            onClick = { onOpenFeature("tickets") },
            a11y = "Open tickets, loading",
            modifier = modifier,
        )
        return
    }

    if (tickets == null) {
        val subline = roleStats?.let { "${it.savedRoles.formatted()} saved · ${it.roleGreets.formatted()} greets" }
        PulseTile(
            icon = Icons.Default.Shield,
            title = "Roles",
            value = { TileValue(roleStats?.totalRoles?.toLong(), role.onContainer) },
            delta = null,
            subline = subline,
            container = role.container,
            content = role.onContainer,
            shape = LeafShapes.tickets,
            onClick = { onOpenFeature("rolestates") },
            a11y = buildString {
                append("Roles, ").append(roleStats?.totalRoles?.formatted() ?: "loading")
                if (subline != null) append(", ").append(subline.replace(" · ", ", "))
            },
            modifier = modifier,
        )
        return
    }

    val open = tickets.openTickets
    val total = tickets.totalTickets
    val fraction = open.toFloat() / max(total, 1)
    val pct = (fraction * 100).roundToInt()
    val panels = community.ticketPanels?.takeIf { it > 0 }
    val subline = buildString {
        append("${tickets.closedTickets.formatted()} closed of ${total.formatted()}")
        if (panels != null) append(" · ${panels.formatted()} panels")
    }
    PulseTile(
        icon = Icons.Default.ConfirmationNumber,
        title = "Open tickets",
        value = { TileValue(open.toLong(), role.onContainer) },
        delta = null,
        subline = subline,
        container = role.container,
        content = role.onContainer,
        shape = LeafShapes.tickets,
        onClick = { onOpenFeature("tickets") },
        a11y = "Open tickets, ${open.formatted()}, ${subline.replace(" · ", ", ")}, $pct percent open",
        modifier = modifier,
        visual = {
            Row(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RingGauge(
                    fraction = fraction,
                    color = role.onContainer,
                    track = role.onContainer.copy(alpha = 0.15f),
                    size = 36.dp,
                    stroke = 5.dp,
                    animate = !reduced,
                )
                Column {
                    Text(
                        text = "$pct% open",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        maxLines = 1,
                    )
                    if (tickets.averageResponseTime > 0) {
                        Text(
                            text = "First reply ${HomeSeries.roundedMinutes(tickets.averageResponseTime)}",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

/** Warnings issued this week against last week, with a two-week daily histogram. */
@Composable
private fun ModerationTile(
    security: SecurityData,
    loaded: Boolean,
    roles: HomeRoles,
    onOpenFeature: (String) -> Unit,
    modifier: Modifier,
) {
    val role = roles.safety
    val warnings = security.warnings

    if (!loaded || warnings == null) {
        PulseTile(
            icon = Icons.Default.Gavel,
            title = "Mod actions",
            value = {
                if (loaded) TileText("-", role.onContainer) else TileValue(null, role.onContainer)
            },
            delta = null,
            subline = if (loaded) "Unavailable" else null,
            container = role.container,
            content = role.onContainer,
            shape = LeafShapes.moderation,
            onClick = { onOpenFeature("moderation") },
            a11y = if (loaded) "Mod actions, unavailable" else "Mod actions, loading",
            modifier = modifier,
        )
        return
    }

    val dates = remember(warnings) { warnings.mapNotNull { it.dateAdded } }
    val (current, previous) = remember(dates) { HomeSeries.weekOverWeek(dates) }
    val daily = remember(dates) { HomeSeries.dailyCounts(dates, 14) }
    val diff = current - previous
    val delta = when {
        diff > 0 -> DeltaSpec("+$diff vs last week", Icons.AutoMirrored.Filled.TrendingUp)
        diff < 0 -> DeltaSpec("$diff vs last week", Icons.AutoMirrored.Filled.TrendingDown)
        else -> DeltaSpec("Same as last week", Icons.AutoMirrored.Filled.TrendingFlat)
    }
    val trend = when {
        diff > 0 -> "up $diff on last week"
        diff < 0 -> "down ${abs(diff)} on last week"
        else -> "same as last week"
    }
    PulseTile(
        icon = Icons.Default.Gavel,
        title = "Mod actions",
        value = { TileValue(current.toLong(), role.onContainer) },
        delta = delta,
        subline = "this week · ${warnings.size.formatted()} warnings total",
        container = role.container,
        content = role.onContainer,
        shape = LeafShapes.moderation,
        onClick = { onOpenFeature("moderation") },
        a11y = "Mod actions this week, ${current.formatted()}, $trend, ${warnings.size.formatted()} warnings total",
        modifier = modifier,
        visual = {
            Sparkbars(
                values = daily,
                color = role.onContainer,
                highlight = daily.lastIndex,
                dimAlpha = 0.5f,
                modifier = Modifier.matchParentSizeInBox(),
            )
        },
    )
}

/** Fills the visual slot of a pulse tile. */
private fun Modifier.matchParentSizeInBox(): Modifier = this
    .fillMaxWidth()
    .fillMaxHeight()
