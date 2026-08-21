package dev.mewdeko.mobile.feature.guilddetail.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.util.relativeToNow

/** A count plus the feature it opens. */
private data class Metric(val label: String, val value: String, val featureId: String)

/**
 * Draws every category band of the guild home.
 *
 * Each band asks the view model to load itself the first time it composes,
 * which for a scrolling column means the first time it comes near the
 * viewport.
 */
@Composable
fun HomeSections(
    state: GuildHomeState,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    CommunitySection(state, onEnsureLoaded, onOpenFeature)
    EntertainmentSection(state, onEnsureLoaded, onOpenFeature)
    ActionsSection(state, onEnsureLoaded, onOpenFeature)
    SecuritySection(state, onEnsureLoaded, onOpenFeature)
    SettingsSection(state, onEnsureLoaded, onOpenFeature)
}

@Composable
private fun CommunitySection(
    state: GuildHomeState,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onEnsureLoaded(HomeSection.COMMUNITY) }
    val data = state.community

    SectionCard {
        SectionCardHeader("Community", Icons.Default.Groups)

        if (state.isLoading(HomeSection.COMMUNITY)) {
            BandSpinner()
            return@SectionCard
        }

        data.xpStats?.let { xp ->
            MetricGrid(
                listOf(
                    Metric("Ranked members", xp.totalUsers.pretty(), "xp"),
                    Metric("Total XP", xp.totalXp.compact(), "xp"),
                    Metric("Avg level", "%.1f".format(xp.averageLevel), "xp"),
                    Metric("Top level", xp.highestLevel.pretty(), "xp"),
                ),
                onOpenFeature,
            )
        }

        if (data.xpTop.isNotEmpty()) {
            Label("Leaderboard")
            data.xpTop.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onOpenFeature("xp") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "#${entry.rank}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Avatar(entry.avatarUrl, contentDescription = null, size = 26)
                    Text(
                        entry.username,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Lv ${entry.level}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        val counts = buildList {
            data.messages?.takeIf { it.enabled }?.let {
                add(Metric("Messages today", it.dailyMessages.compact(), "messagestats"))
                add(Metric("Messages total", it.totalMessages.compact(), "messagestats"))
            }
            data.birthdays?.let {
                add(Metric("Birthdays today", it.todaysBirthdayCount.pretty(), "birthday"))
                add(Metric("Birthdays set", it.usersWithBirthdays.pretty(), "birthday"))
            }
            data.tickets?.let {
                add(Metric("Open tickets", it.openTickets.pretty(), "tickets"))
                add(Metric("Tickets total", it.totalTickets.pretty(), "tickets"))
            }
            data.activeForms?.let { add(Metric("Active forms", it.pretty(), "forms")) }
            data.countingChannels?.let { add(Metric("Counting", it.pretty(), "counting")) }
            data.patreonSupporters?.let { add(Metric("Patrons", it.pretty(), "patreon")) }
        }
        MetricGrid(counts, onOpenFeature)

        data.tickets?.takeIf { it.averageResponseTime > 0 }?.let {
            Text(
                "Average first response ${it.averageResponseTime.roundedMinutes()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (data.highlights.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Starboard", Icons.Default.Star)
            data.highlights.forEach { post ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onOpenFeature("starboard") },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Avatar(post.authorAvatarUrl, contentDescription = null, size = 26)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            post.authorName.ifEmpty { "Unknown" },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        post.content?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        "${post.starEmote} ${post.starCount}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntertainmentSection(
    state: GuildHomeState,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onEnsureLoaded(HomeSection.ENTERTAINMENT) }
    val data = state.entertainment

    SectionCard {
        SectionCardHeader("Entertainment", Icons.Default.CardGiftcard)

        if (state.isLoading(HomeSection.ENTERTAINMENT)) {
            BandSpinner()
            return@SectionCard
        }

        data.music?.currentTrack?.let { queued ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableRow { onOpenFeature("music") },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        queued.track.title.ifEmpty { "Unknown track" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail = listOfNotNull(
                        queued.track.author?.takeIf { it.isNotBlank() },
                        data.music.channelName?.takeIf { it.isNotBlank() }?.let { "in $it" },
                    ).joinToString(" · ")
                    if (detail.isNotEmpty()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                data.music.queue?.size?.takeIf { it > 0 }?.let {
                    Text(
                        "+$it queued",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        MetricGrid(
            buildList {
                data.giveaways?.let { add(Metric("Giveaways", it.pretty(), "giveaways")) }
                data.customVoiceChannels?.let {
                    add(Metric("Voice rooms", it.pretty(), "customvoice"))
                }
            },
            onOpenFeature,
        )
    }
}

@Composable
private fun ActionsSection(
    state: GuildHomeState,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onEnsureLoaded(HomeSection.ACTIONS) }
    val data = state.actions

    SectionCard {
        SectionCardHeader("Actions", Icons.Default.Bolt)
        if (state.isLoading(HomeSection.ACTIONS)) {
            BandSpinner()
            return@SectionCard
        }
        MetricGrid(
            buildList {
                data.roleGreets?.let { add(Metric("Role greets", it.pretty(), "rolegreets")) }
                data.roleStates?.let { add(Metric("Role states", it.pretty(), "rolestates")) }
                data.multiGreets?.let { add(Metric("Multi-greets", it.pretty(), "multigreets")) }
                data.repeaters?.let { add(Metric("Repeaters", it.pretty(), "repeaters")) }
            },
            onOpenFeature,
        )
    }
}

@Composable
private fun SecuritySection(
    state: GuildHomeState,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onEnsureLoaded(HomeSection.SECURITY) }
    val data = state.security

    SectionCard {
        SectionCardHeader("Security", Icons.Default.Shield)
        if (state.isLoading(HomeSection.SECURITY)) {
            BandSpinner()
            return@SectionCard
        }
        MetricGrid(
            buildList {
                data.activeProtections?.let {
                    add(Metric("Protections", "$it/${data.totalProtections}", "administration"))
                }
                data.warnings?.let { add(Metric("Warnings", it.pretty(), "moderation")) }
            },
            onOpenFeature,
        )

        if (data.recentActions.isNotEmpty()) {
            Label("Recent actions")
            data.recentActions.take(5).forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickableRow { onOpenFeature("moderation") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Gavel,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            action.username ?: "User ${action.userId}",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        action.reason?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    action.dateAdded?.let {
                        Text(
                            it.relativeToNow(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    state: GuildHomeState,
    onEnsureLoaded: (HomeSection) -> Unit,
    onOpenFeature: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onEnsureLoaded(HomeSection.SETTINGS) }
    val data = state.settings

    SectionCard {
        SectionCardHeader("Settings", Icons.Default.Tune)
        if (state.isLoading(HomeSection.SETTINGS)) {
            BandSpinner()
            return@SectionCard
        }
        MetricGrid(
            buildList {
                data.autoAssignHumans?.let {
                    add(Metric("Auto-assign", it.pretty(), "administration"))
                }
                data.autoAssignBots?.let {
                    add(Metric("Bot roles", it.pretty(), "administration"))
                }
                data.selfAssignable?.let {
                    add(Metric("Self-assign", it.pretty(), "administration"))
                }
                data.ticketPanels?.let { add(Metric("Panels", it.pretty(), "tickets")) }
            },
            onOpenFeature,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricGrid(metrics: List<Metric>, onOpenFeature: (String) -> Unit) {
    if (metrics.isEmpty()) return
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        metrics.forEach { metric ->
            StatTile(
                label = metric.label,
                value = metric.value,
                modifier = Modifier
                    .weight(1f)
                    .clickableRow { onOpenFeature(metric.featureId) },
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun BandSpinner() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
    }
}

/** Thousands-separated. */
private fun Int.pretty(): String = "%,d".format(this)

/** Abbreviates large totals so they fit a half-width tile. */
private fun Long.compact(): String = when {
    this >= 1_000_000_000 -> "%.1fB".format(this / 1_000_000_000.0)
    this >= 1_000_000 -> "%.1fM".format(this / 1_000_000.0)
    this >= 10_000 -> "%.1fK".format(this / 1_000.0)
    else -> "%,d".format(this)
}

/** Renders a minute count as a rounded human duration. */
private fun Double.roundedMinutes(): String = when {
    this >= 1440 -> "%.1f days".format(this / 1440)
    this >= 60 -> "%.1f hours".format(this / 60)
    else -> "%.0f min".format(this)
}
