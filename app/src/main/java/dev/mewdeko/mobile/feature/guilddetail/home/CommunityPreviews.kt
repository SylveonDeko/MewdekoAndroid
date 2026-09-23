package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.BirthdayUser
import dev.mewdeko.mobile.core.model.XpLeaderboardEntry
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.RankedBar
import dev.mewdeko.mobile.core.ui.ScallopShape
import dev.mewdeko.mobile.feature.guilddetail.GuildOverviewState
import dev.mewdeko.mobile.feature.guilddetail.MemberSummary
import dev.mewdeko.mobile.feature.guilddetail.formatted
import dev.mewdeko.mobile.feature.messagestats.MessageStatsDetail
import dev.mewdeko.mobile.util.relativeToNow
import kotlinx.coroutines.delay
import kotlin.math.max

/** Spoken ordinals for the leaderboard summary. */
private val Ordinals = listOf("First", "Second", "Third", "Fourth", "Fifth")

/**
 * The Community chapter: XP podium, chatter, birthdays and the starboard
 * carousel, then chips for forms, counting and Patreon.
 */
@Composable
fun CommunityBand(
    overview: GuildOverviewState,
    community: CommunityData,
    loaded: Boolean,
    roles: HomeRoles,
    reduced: Boolean,
    onOpenFeature: (String) -> Unit,
) {
    val role = roles.community
    val badge = ScallopShape(4, 0.18f)
    if (!loaded) {
        SkeletonBand("Community", Icons.Default.Groups, role, badge, "xp", onOpenFeature)
        return
    }

    val xpUsers = community.xpStats?.totalUsers
    val headline = xpUsers?.toLong() ?: overview.memberStats?.total?.toLong()
    val descriptor = if (xpUsers != null) "members earning XP" else "members"
    val messages = community.messages?.takeIf { it.enabled }
    val showChatter = messages != null && (
        messages.topChannels.isNotEmpty() ||
            messages.topUsers.any { user -> user.userId?.let { it in overview.memberDirectory } == true }
        )

    CategoryBand(
        title = "Community",
        icon = Icons.Default.Groups,
        role = role,
        badgeShape = badge,
        seeAllId = "xp",
        headline = headline,
        descriptor = descriptor,
        headlineLoading = false,
        onOpenFeature = onOpenFeature,
        chips = communityChips(community),
        chipsLoading = false,
        previews = {
            if (community.xpTop.isNotEmpty()) {
                XpPodium(
                    entries = community.xpTop,
                    stats = community.xpStats,
                    roles = roles,
                    reduced = reduced,
                    onOpen = { onOpenFeature("xp") },
                    modifier = Modifier.homeInset(),
                )
            }
            if (showChatter && messages != null) {
                ChatterCard(
                    messages = messages,
                    directory = overview.memberDirectory,
                    roles = roles,
                    reduced = reduced,
                    onOpen = { onOpenFeature("messagestats") },
                    modifier = Modifier.homeInset(),
                )
            }
            if (community.birthdaysToday.isNotEmpty() || community.birthdaysUpcoming.isNotEmpty()) {
                BirthdayCard(
                    today = community.birthdaysToday,
                    upcoming = community.birthdaysUpcoming,
                    summary = community.birthdays,
                    reduced = reduced,
                    onOpen = { onOpenFeature("birthday") },
                    modifier = Modifier.homeInset(),
                )
            }
            if (community.highlights.isNotEmpty()) {
                StarboardCarousel(
                    highlights = community.highlights,
                    onOpen = { onOpenFeature("starboard") },
                )
            }
        },
    )
}

/** Configured community features as chips. Unconfigured ones go to the setup rail. */
private fun communityChips(community: CommunityData): List<BandChip> = buildList {
    community.forms?.takeIf { it.isNotEmpty() }?.let { forms ->
        val responses = forms.mapNotNull { it.responseCount }
        add(
            BandChip(
                icon = Icons.Default.Description,
                value = forms.size.formatted(),
                label = "Active forms",
                subtitle = if (responses.isEmpty()) null else "${responses.sum().formatted()} responses",
                featureId = "forms",
            )
        )
    }
    community.counting?.takeIf { it.isNotEmpty() }?.let { channels ->
        val top = channels.maxBy { it.currentNumber }
        val subtitle = buildString {
            append("Record ${top.highestNumber.formatted()}")
            if (channels.size > 1) append(" · ${channels.size} channels")
        }
        add(
            BandChip(
                icon = Icons.Default.Numbers,
                value = top.currentNumber.formatted(),
                label = "Counting",
                subtitle = subtitle,
                featureId = "counting",
                progress = (top.currentNumber.toFloat() / max(top.highestNumber, 1)).coerceIn(0f, 1f),
            )
        )
    }
    if (community.patreonConnected == true) {
        add(
            BandChip(
                icon = Icons.Default.Favorite,
                value = (community.patreonSupporters ?: 0).formatted(),
                label = "Patrons",
                featureId = "patreon",
            )
        )
    }
}

/**
 * The top of the XP leaderboard as a podium in second, first, third order,
 * with ranks four and five as rows and guild totals underneath. At large
 * font scales every entry is a row.
 */
@Composable
fun XpPodium(
    entries: List<XpLeaderboardEntry>,
    stats: XpServerStats?,
    roles: HomeRoles,
    reduced: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ranked = remember(entries) { entries.sortedBy { if (it.rank > 0) it.rank else Int.MAX_VALUE } }
    val large = isLargeFont()
    val summary = buildString {
        append("XP leaderboard.")
        ranked.forEachIndexed { index, entry ->
            val ordinal = Ordinals.getOrElse(index) { "Rank ${index + 1}" }
            append(" $ordinal ${entry.username}, level ${entry.level}.")
        }
    }

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = summary },
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val rows = if (large) ranked else ranked.drop(3)
            if (!large) {
                val top = ranked.take(3)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    listOf(2, 1, 3).filter { it <= top.size }.forEachIndexed { order, place ->
                        key(place) {
                            PodiumColumn(
                                entry = top[place - 1],
                                place = place,
                                order = order,
                                roles = roles,
                                reduced = reduced,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            if (rows.isNotEmpty()) {
                Column {
                    rows.forEachIndexed { index, entry ->
                        if (index > 0 || !large) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        val position = if (large) index + 1 else index + 4
                        LeaderRow(entry = entry, rank = entry.rank.takeIf { it > 0 } ?: position)
                    }
                }
            }
            stats?.let {
                Text(
                    text = "Avg level ${"%.1f".format(it.averageLevel)} · Top level ${it.highestLevel.formatted()} · " +
                        "${HomeSeries.compact(it.totalXp)} XP earned",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One podium place: crown for first, ringed avatar, name, level and a pedestal that bounces up. */
@Composable
private fun PodiumColumn(
    entry: XpLeaderboardEntry,
    place: Int,
    order: Int,
    roles: HomeRoles,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGuildPalette.current
    val scheme = MaterialTheme.colorScheme
    val clamp = fontScaleClamp()
    val target = when (place) {
        1 -> 80.dp
        2 -> 56.dp
        else -> 40.dp
    } * clamp
    var grown by rememberSaveable { mutableStateOf(false) }
    val grow = remember { Animatable(if (grown || reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (grow.value < 1f) {
            delay(order * 80L)
            grow.animateTo(1f, HomeMotion.bouncy())
        }
        grown = true
    }
    val ring = if (place == 1) {
        BorderStroke(3.dp, Brush.sweepGradient(palette.gradient + palette.gradient.first()))
    } else {
        BorderStroke(2.dp, scheme.primary.copy(alpha = 0.5f))
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (place == 1) {
            Icon(
                Icons.Default.EmojiEvents,
                contentDescription = null,
                tint = roles.safety.color,
                modifier = Modifier.size(24.dp),
            )
        }
        Avatar(
            url = entry.avatarUrl,
            contentDescription = null,
            size = when (place) {
                1 -> 72
                2 -> 56
                else -> 52
            },
            ring = ring,
            fallbackText = entry.username,
        )
        Text(
            text = entry.username,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Lvl ${entry.level}",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(target * grow.value.coerceAtLeast(0f))
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(scheme.primaryContainer, scheme.primaryContainer.copy(alpha = 0.3f)),
                    ),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = place.toString(),
                style = MaterialTheme.typography.headlineMedium.tabular(),
                color = scheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** A leaderboard row with level progress. */
@Composable
fun LeaderRow(entry: XpLeaderboardEntry, rank: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#$rank",
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
        )
        Avatar(url = entry.avatarUrl, contentDescription = null, size = 32, fallbackText = entry.username)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.username,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            LinearProgressIndicator(
                progress = { (entry.levelXp.toFloat() / max(entry.requiredXp, 1L)).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = scheme.primary,
                trackColor = scheme.primaryContainer,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
        Surface(shape = CircleShape, color = scheme.primaryContainer, contentColor = scheme.onPrimaryContainer) {
            Text(
                text = "Lvl ${entry.level}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Where the conversation happens: top channels as ranked bars and the most
 * active members whose ids resolve to a known member. Raw ids are never shown.
 */
@Composable
fun ChatterCard(
    messages: MessageStatsDetail,
    directory: Map<String, MemberSummary>,
    roles: HomeRoles,
    reduced: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = messages.topChannels.take(3)
    val maxTotal = max(channels.maxOfOrNull { it.totalMessages } ?: 1L, 1L)
    val users = messages.topUsers
        .mapNotNull { user -> user.userId?.let { directory[it] }?.let { user to it } }
        .take(3)
    if (channels.isEmpty() && users.isEmpty()) return

    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (channels.isNotEmpty()) {
                Text(
                    text = "Top channels",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                channels.forEach { channel ->
                    RankedBar(
                        name = "#${channel.channelName?.takeIf { it.isNotBlank() } ?: "unknown"}",
                        value = HomeSeries.compact(channel.totalMessages),
                        fraction = channel.totalMessages.toFloat() / maxTotal,
                        color = roles.automation.color,
                        track = roles.automation.container,
                        animate = !reduced,
                    )
                }
            }
            if (users.isNotEmpty()) {
                Text(
                    text = "Most active",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                users.forEach { (stats, member) ->
                    Row(
                        modifier = Modifier.heightIn(min = 44.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            url = member.avatarUrl,
                            contentDescription = null,
                            size = 32,
                            fallbackText = member.name,
                        )
                        Text(
                            text = member.name,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = HomeSeries.compact(stats.totalMessages),
                                style = MaterialTheme.typography.labelMedium.tabular(),
                            )
                            Text(
                                text = "${stats.dailyMessages.formatted()} today",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Today's birthdays as a stacked avatar row, then the week ahead as chips. */
@Composable
fun BirthdayCard(
    today: List<BirthdayUser>,
    upcoming: List<BirthdayUser>,
    summary: BirthdaySummary?,
    reduced: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val clamp = fontScaleClamp()
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(HomeDimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val content = LocalContentColor.current
            if (today.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarStack(
                        users = today,
                        size = 36,
                        overlap = 12,
                        max = 5,
                        ringColor = scheme.secondaryContainer,
                        bubbleColor = scheme.secondary,
                        bubbleContent = scheme.onSecondary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = HomeSeries.birthdayTitle(today.map { it.displayName() }),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelMedium,
                            color = content.copy(alpha = 0.8f),
                        )
                    }
                    BouncingCake(reduced = reduced, tint = scheme.secondary)
                }
            }
            if (upcoming.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(upcoming, key = { it.userId.ifEmpty { it.username } }) { user ->
                        UpcomingChip(user = user, width = 76.dp * clamp)
                    }
                }
            }
            val withBirthdays = summary?.usersWithBirthdays ?: 0
            if (withBirthdays > 0) {
                Text(
                    text = "${withBirthdays.formatted()} members have a birthday set",
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.8f),
                )
            }
        }
    }
}

/** The name a birthday entry should show. */
private fun BirthdayUser.displayName(): String = nickname?.takeIf { it.isNotBlank() } ?: username

/** A cake icon that bounces once when the card first appears. */
@Composable
private fun BouncingCake(reduced: Boolean, tint: Color) {
    var bounced by rememberSaveable { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!reduced && !bounced) {
            val bounce = spring<Float>(dampingRatio = 0.4f, stiffness = 600f)
            scale.animateTo(1.25f, bounce)
            scale.animateTo(1f, bounce)
        }
        bounced = true
    }
    Icon(
        Icons.Default.Cake,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(28.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/**
 * Overlapping ringed avatars, capped at [max] with a "+N" bubble for the rest.
 */
@Composable
fun AvatarStack(
    users: List<BirthdayUser>,
    size: Int,
    overlap: Int,
    max: Int,
    ringColor: Color,
    bubbleColor: Color,
    bubbleContent: Color,
) {
    val shown = users.take(max)
    val extra = users.size - shown.size
    val count = shown.size + if (extra > 0) 1 else 0
    val step = size - overlap
    Box(modifier = Modifier.width((size + step * (count - 1).coerceAtLeast(0)).dp)) {
        shown.forEachIndexed { index, user ->
            Avatar(
                url = user.avatarUrl,
                contentDescription = null,
                size = size,
                ring = BorderStroke(2.dp, ringColor),
                fallbackText = user.displayName(),
                modifier = Modifier
                    .offset(x = (index * step).dp)
                    .background(ringColor, CircleShape),
            )
        }
        if (extra > 0) {
            Box(
                modifier = Modifier
                    .offset(x = (shown.size * step).dp)
                    .size(size.dp)
                    .background(bubbleColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+$extra",
                    style = MaterialTheme.typography.labelMedium,
                    color = bubbleContent,
                )
            }
        }
    }
}

/** One upcoming birthday: avatar, name and how many days away. */
@Composable
fun UpcomingChip(user: BirthdayUser, width: Dp) {
    val content = LocalContentColor.current
    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Avatar(url = user.avatarUrl, contentDescription = null, size = 44, fallbackText = user.displayName())
        Text(
            text = user.displayName(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Surface(shape = CircleShape, color = content.copy(alpha = 0.12f), contentColor = content) {
            Text(
                text = if (user.daysUntil == 1) "Tomorrow" else "in ${user.daysUntil}d",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Recently starred messages in a Material multi-browse carousel. Collapsed
 * items fade their text so only the image or quote glyph shows. At large
 * font scales the items stack vertically instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarboardCarousel(highlights: List<StarboardHighlight>, onOpen: () -> Unit) {
    val clamp = fontScaleClamp()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .homeInset(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Starboard",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
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
        if (isLargeFont()) {
            Column(
                modifier = Modifier.homeInset(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                highlights.forEach { highlight ->
                    StarItem(
                        highlight = highlight,
                        textAlpha = { 1f },
                        fill = false,
                        onOpen = onOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp)
                            .clip(MaterialTheme.shapes.extraLarge),
                    )
                }
            }
        } else {
            val state = rememberCarouselState { highlights.size }
            HorizontalMultiBrowseCarousel(
                state = state,
                preferredItemWidth = 280.dp,
                itemSpacing = 8.dp,
                contentPadding = PaddingValues(horizontal = HomeDimens.inset),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(208.dp * clamp),
            ) { index ->
                val info = carouselItemInfo
                StarItem(
                    highlight = highlights[index],
                    textAlpha = {
                        if (info.maxSize > info.minSize) {
                            ((info.size - info.minSize) / (info.maxSize - info.minSize)).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    },
                    fill = true,
                    onOpen = onOpen,
                    modifier = Modifier
                        .fillMaxSize()
                        .maskClip(MaterialTheme.shapes.extraLarge),
                )
            }
        }
    }
}

/**
 * One starred message: its image under a scrim, or a quote glyph on a tonal
 * field, with the author and the star count. Text layers read [textAlpha] in
 * the draw phase so carousel resizing never recomposes them.
 */
@Composable
fun StarItem(
    highlight: StarboardHighlight,
    textAlpha: () -> Float,
    fill: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val image = highlight.imageUrl?.takeIf { it.isNotBlank() }
    val author = highlight.authorName.ifBlank { "Unknown" }
    val text = highlight.content?.takeIf { it.isNotBlank() }
    val content = if (image != null) scheme.onSurface else scheme.onTertiaryContainer

    Box(
        modifier = modifier
            .clickable(onClickLabel = "Open starboard", onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = "Starred message by $author, ${highlight.starCount} stars"
            },
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.4f to Color.Transparent,
                            1f to scheme.surfaceContainerHighest,
                        ),
                    ),
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(scheme.tertiaryContainer),
            )
            Icon(
                Icons.Default.FormatQuote,
                contentDescription = null,
                tint = scheme.onTertiaryContainer.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(56.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fill) Modifier.fillMaxHeight() else Modifier.heightIn(min = 120.dp))
                .graphicsLayer { alpha = textAlpha() },
        ) {
            if (image == null && text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = content,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 48.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (image != null && text != null) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    url = highlight.authorAvatarUrl,
                    contentDescription = null,
                    size = 24,
                    fallbackText = author,
                )
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                highlight.createdAt?.let {
                    Text(
                        text = it.relativeToNow(),
                        style = MaterialTheme.typography.labelSmall,
                        color = content,
                        maxLines = 1,
                    )
                }
            }
        }

        Surface(
            shape = CircleShape,
            color = scheme.surfaceContainerHighest.copy(alpha = 0.9f),
            contentColor = scheme.onSurface,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .graphicsLayer { alpha = textAlpha() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (highlight.starEmote.startsWith("<")) {
                    Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        text = highlight.starCount.formatted(),
                        style = MaterialTheme.typography.labelLarge.tabular(),
                    )
                } else {
                    Text(
                        text = "${highlight.starEmote} ${highlight.starCount.formatted()}",
                        style = MaterialTheme.typography.labelLarge.tabular(),
                    )
                }
            }
        }
    }
}
