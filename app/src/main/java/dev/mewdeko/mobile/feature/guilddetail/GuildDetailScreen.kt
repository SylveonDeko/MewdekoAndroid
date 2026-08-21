package dev.mewdeko.mobile.feature.guilddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.theme.MewdekoTheme
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.feature.guilddetail.home.BotGuildProfile
import dev.mewdeko.mobile.feature.guilddetail.home.GuildHomeViewModel
import dev.mewdeko.mobile.feature.guilddetail.home.HomeSections
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault())

/**
 * The guild dashboard.
 *
 * Re-themes the whole subtree with the guild's derived palette, so every
 * Material component below picks up the server's identity automatically.
 */
@Composable
fun GuildDetailScreen(
    guild: GuildRouteArgs,
    userId: String,
    onBack: () -> Unit,
    onOpenFeature: (String) -> Unit,
    onOpenFeatureBrowser: () -> Unit,
    viewModel: GuildOverviewViewModel = hiltViewModel(),
    homeViewModel: GuildHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val palette by viewModel.palette.collectAsStateWithLifecycle()

    MewdekoTheme(palette = palette) {
        FeatureScaffold(
            title = guild.name.ifEmpty { state.info?.name.orEmpty() },
            subtitle = state.info?.let { "${it.memberCount} members" },
            onBack = onBack,
            loadState = loadState,
            onRefresh = {
                viewModel.load(refreshing = true)
                homeViewModel.invalidate()
            },
            onRetry = { viewModel.load() },
            actions = {
                IconButton(onClick = onOpenFeatureBrowser) {
                    Icon(Icons.Default.Apps, contentDescription = "All features")
                }
            },
        ) {
            GuildHeader(guild = guild, state = state)

            SectionCard {
                SectionCardHeader("Members", Icons.Default.Groups)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Total",
                        value = state.memberStats?.total?.formatted() ?: "-",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Humans",
                        value = state.memberStats?.humans?.formatted() ?: "-",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Bots",
                        value = state.memberStats?.bots?.formatted() ?: "-",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionCard {
                SectionCardHeader("Roles", Icons.Default.Shield)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Roles",
                        value = state.roleStats?.totalRoles?.formatted() ?: "-",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Saved states",
                        value = state.roleStats?.roleStates?.formatted() ?: "-",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Role greets",
                        value = state.roleStats?.roleGreets?.formatted() ?: "-",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionCard {
                SectionCardHeader("Membership flow", Icons.Default.Timeline)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Joins (30d)",
                        value = state.joinStats?.summary?.total?.formatted() ?: "-",
                        icon = Icons.AutoMirrored.Filled.Login,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Leaves (30d)",
                        value = state.leaveStats?.summary?.total?.formatted() ?: "-",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                }
                val joins = state.joinStats?.dailyStats.orEmpty()
                val leaves = state.leaveStats?.dailyStats.orEmpty()
                if (joins.isNotEmpty() || leaves.isNotEmpty()) {
                    JoinLeaveChart(
                        joins = joins,
                        leaves = leaves,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
            }

            state.bot?.let { bot ->
                SectionCard {
                    SectionCardHeader("Bot", Icons.Default.SmartToy)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Latency", "${bot.botLatency} ms", Modifier.weight(1f))
                        StatTile("Commands", bot.commandsCount.formatted(), Modifier.weight(1f))
                        StatTile("Modules", bot.modulesCount.formatted(), Modifier.weight(1f))
                    }
                    Text(
                        text = "${bot.botName} ${bot.botVersion} on Discord.Net ${bot.dNetVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            homeState.profile?.let { profile ->
                BotProfileCard(profile)
            }

            HomeSections(
                state = homeState,
                onEnsureLoaded = homeViewModel::ensureLoaded,
                onOpenFeature = onOpenFeature,
            )
        }
    }
}

/** The bot's identity inside this guild, as the dashboard leads its overview. */
@Composable
private fun BotProfileCard(profile: BotGuildProfile) {
    val hasAnything = listOfNotNull(
        profile.nickname?.takeIf { it.isNotBlank() },
        profile.bio?.takeIf { it.isNotBlank() },
        profile.avatarUrl?.takeIf { it.isNotBlank() },
    ).isNotEmpty()
    if (!hasAnything) return

    SectionCard {
        SectionCardHeader("Bot profile", Icons.Default.SmartToy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(
                url = profile.avatarUrl,
                contentDescription = null,
                size = 44,
                fallbackIcon = Icons.Default.SmartToy,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.nickname?.takeIf { it.isNotBlank() } ?: "No nickname set",
                    style = MaterialTheme.typography.titleSmall,
                )
                profile.bio?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuildHeader(guild: GuildRouteArgs, state: GuildOverviewState) {
    val bannerUrl = state.info?.bannerUrl?.takeIf { it.isNotBlank() }
    val shape = MaterialTheme.shapes.large

    /*
     * With a banner the text sits on artwork and needs a scrim to stay
     * readable. Without one the dashboard uses a low-alpha wash of the guild
     * colour rather than a saturated fill, so ordinary theme text colours
     * apply and contrast does not depend on whichever hues the icon produced.
     */
    val onBanner = bannerUrl != null
    val titleColor = if (onBanner) Color.White else MaterialTheme.colorScheme.onSurface
    val detailColor = if (onBanner) {
        Color.White.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onBanner) {
                    Modifier
                } else {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = shape,
                        )
                }
            ),
    ) {
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                        )
                    ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(
                url = guild.iconUrl ?: state.info?.iconUrl,
                contentDescription = guild.name,
                size = 56,
                fallbackIcon = Icons.Default.Shield,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = guild.name.ifEmpty { state.info?.name.orEmpty() },
                    style = MaterialTheme.typography.titleLarge,
                    color = titleColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.info?.createdAt?.let { created ->
                    HeaderDetail(
                        icon = Icons.Default.CalendarMonth,
                        text = "Created ${DateFormat.format(created)}",
                        color = detailColor,
                    )
                }
                state.info?.premiumTier?.takeIf { it > 0 }?.let { tier ->
                    HeaderDetail(
                        icon = Icons.Default.Star,
                        text = "Boost tier $tier",
                        color = detailColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderDetail(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/** Formats a count with thousands separators. */
fun Int.formatted(): String = "%,d".format(this)

/** Formats a long count with thousands separators. */
fun Long.formatted(): String = "%,d".format(this)
