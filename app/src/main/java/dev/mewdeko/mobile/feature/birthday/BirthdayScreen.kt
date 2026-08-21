package dev.mewdeko.mobile.feature.birthday

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDate

/** Birthday announcements, roles, reminders, and the guild's birthday roster. */
@Composable
fun BirthdayScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: BirthdayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingReset by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Birthdays",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { pendingReset = true }) {
                Icon(Icons.Default.Restore, contentDescription = "Reset settings")
            }
        },
        floatingActionButton = {
            if (state.hasUnsavedChanges) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::save,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text(if (state.isSaving) "Saving…" else "Save changes") },
                )
            }
        },
    ) {
        state.stats?.let { stats ->
            SectionCard {
                SectionCardHeader("Overview", Icons.Default.Cake)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Today", "${stats.todaysBirthdayCount}", Modifier.weight(1f))
                    StatTile("With birthday", "${stats.usersWithBirthdays}", Modifier.weight(1f))
                    StatTile("Members", "${stats.totalUsers}", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(
                        label = "Announcing",
                        value = "${stats.usersWithAnnouncementsEnabled}",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Coverage",
                        value = "%.0f%%".format(stats.birthdaySetPercentage),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        SectionCard {
            SectionCardHeader("Features", Icons.Default.Settings)
            BirthdayFeature.entries.forEach { feature ->
                SwitchRow(
                    title = feature.label,
                    subtitle = feature.blurb,
                    checked = feature.isEnabled(state.enabledFeatures),
                    onCheckedChange = { viewModel.toggleFeature(feature) },
                )
            }
        }

        SectionCard {
            SectionCardHeader("Destinations", Icons.Default.Tag)
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "No announcement channel",
                label = "Announcement channel",
                selectedId = state.channelId,
                onSelect = viewModel::setChannel,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "No birthday role",
                label = "Birthday role",
                selectedId = state.roleId,
                onSelect = viewModel::setRole,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "No ping role",
                label = "Ping role",
                selectedId = state.pingRoleId,
                onSelect = viewModel::setPingRole,
            )
        }

        SectionCard {
            SectionCardHeader("Timing", Icons.Default.Public)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Public),
                options = BirthdayTimezone.presets.map { SelectorOption(it.id, it.label) },
                placeholder = "UTC",
                label = "Default timezone",
                selectedId = state.timezone,
                onSelect = { viewModel.setTimezone(it ?: "UTC") },
            )
            SliderRow(
                label = "Reminder days ahead",
                value = state.reminderDays.toFloat(),
                onValueChange = { viewModel.setReminderDays(it.toInt()) },
                valueRange = 0f..30f,
                valueLabel = if (state.reminderDays == 0) "Off" else "${state.reminderDays}d",
            )
        }

        SectionCard {
            SectionCardHeader("Announcement message", Icons.Default.ChatBubble)
            EmbedMessageEditor(
                message = state.message,
                onMessageChange = viewModel::setMessage,
            )
            Text(
                text = "Placeholders like %user% and %server% are substituted by the bot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard {
            SectionCardHeader("Today", Icons.Default.Today)
            if (state.todays.isEmpty()) {
                EmptyState("No birthdays today.", icon = Icons.Default.Cake)
            } else {
                state.todays.forEach { BirthdayRow(it) }
            }
        }

        SectionCard {
            SectionCardHeader("Upcoming", Icons.Default.Cake)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7, 14, 30, 90).forEach { days ->
                    FilterChip(
                        selected = state.upcomingDays == days,
                        onClick = { viewModel.setUpcomingDays(days) },
                        label = { Text("${days}d") },
                    )
                }
            }
            if (state.upcoming.isEmpty()) {
                EmptyState("No birthdays in the next ${state.upcomingDays} days.")
            } else {
                state.upcoming.forEach { BirthdayRow(it) }
            }
        }

        SectionCard {
            SectionCardHeader("All members with birthdays", Icons.Default.Groups)
            if (state.allUsers.isEmpty()) {
                EmptyState("Nobody has set a birthday yet.", icon = Icons.Default.Cake)
            } else {
                state.allUsers
                    .sortedBy { it.daysUntil ?: Int.MAX_VALUE }
                    .forEach { BirthdayRow(it) }
            }
        }
    }

    if (pendingReset) {
        ConfirmDialog(
            title = "Reset birthday settings?",
            message = "Channels, roles, timezone, and the announcement message return to the " +
                "bot's defaults.",
            confirmLabel = "Reset",
            onConfirm = viewModel::reset,
            onDismiss = { pendingReset = false },
        )
    }
}

@Composable
private fun BirthdayRow(user: BirthdayUserDetail) {
    ListItem(
        headlineContent = {
            Text(user.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                user.birthday?.let { TagChip(it.shortDate(), icon = Icons.Default.Cake) }
                user.daysUntil?.let { days ->
                    TagChip(
                        when {
                            days == 0 -> "Today"
                            days == 1 -> "Tomorrow"
                            else -> "In $days days"
                        }
                    )
                }
                user.birthdayTimezone?.takeIf { it.isNotBlank() }?.let {
                    TagChip(it, icon = Icons.Default.Public)
                }
            }
        },
        leadingContent = {
            Avatar(url = user.avatarUrl, contentDescription = user.displayName)
        },
        trailingContent = {
            if (!user.birthdayAnnouncementsEnabled) {
                Text(
                    text = "Silent",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(vertical = 2.dp),
    )
}
