package dev.mewdeko.mobile.feature.reputation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("rewards", "Rewards", Icons.Default.WorkspacePremium),
)

/** Member-to-member reputation. */
@Composable
fun ReputationScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ReputationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAddReward by remember { mutableStateOf(false) }
    var pendingDeleteReward by remember { mutableStateOf<RepRoleReward?>(null) }

    FeatureScaffold(
        title = "Reputation",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.EmojiEvents)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Members",
                    value = "${state.stats?.totalUsers ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Rep given",
                    value = "${state.stats?.totalRepGiven ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Average",
                    value = "${state.stats?.averageRepPerUser ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "leaderboard" -> SectionCard {
                SectionCardHeader("Top members", Icons.Default.Leaderboard)
                if (state.leaderboard.isEmpty()) {
                    EmptyState("No reputation recorded yet.", icon = Icons.Default.EmojiEvents)
                } else {
                    state.leaderboard.forEach { entry ->
                        ListItem(
                            leadingContent = {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${entry.rank}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = when (entry.rank) {
                                            1 -> MaterialTheme.colorScheme.primary
                                            2, 3 -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            },
                            headlineContent = {
                                Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            trailingContent = {
                                Text(
                                    text = "${entry.reputation}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            "rewards" -> SectionCard {
                SectionCardHeader("Role rewards", Icons.Default.WorkspacePremium)
                if (state.rewards.isEmpty()) {
                    EmptyState("No role rewards configured.")
                } else {
                    state.rewards.forEach { reward ->
                        ListItem(
                            headlineContent = { Text("@${reward.roleName}") },
                            supportingContent = {
                                Text(
                                    buildString {
                                        append("${reward.repRequired} rep")
                                        if (reward.xpReward > 0) append(" · +${reward.xpReward} XP")
                                        if (reward.removeOnDrop) append(" · removed on drop")
                                    }
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { pendingDeleteReward = reward }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove reward",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
                OutlinedButton(
                    onClick = { showAddReward = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Add role reward", modifier = Modifier.padding(start = 6.dp))
                }
            }

            else -> {
                SectionCard {
                    SectionCardHeader("General", Icons.Default.Tune)
                    SwitchRow(
                        title = "Reputation enabled",
                        checked = state.config.enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                    SwitchRow(
                        title = "Allow negative reputation",
                        subtitle = "Members can subtract reputation as well as add it",
                        checked = state.config.enableNegativeRep,
                        onCheckedChange = viewModel::setNegativeRep,
                    )
                    SwitchRow(
                        title = "Allow anonymous gifts",
                        checked = state.config.enableAnonymous,
                        onCheckedChange = viewModel::setAnonymous,
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "No notification channel",
                        label = "Announce changes in",
                        selectedId = state.config.notificationChannel,
                        onSelect = viewModel::setNotificationChannel,
                    )
                }

                SectionCard {
                    SectionCardHeader("Limits", Icons.Default.Tune)
                    SliderRow(
                        label = "Cooldown",
                        value = state.config.defaultCooldownMinutes.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..1440f,
                        valueLabel = "${state.config.defaultCooldownMinutes}m",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 30, 60, 360, 1440).forEach { minutes ->
                            TextButton(onClick = { viewModel.setCooldown(minutes) }) {
                                Text(if (minutes == 0) "None" else "${minutes}m")
                            }
                        }
                    }
                    SliderRow(
                        label = "Daily limit",
                        value = state.config.dailyLimit.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..50f,
                        valueLabel = if (state.config.dailyLimit == 0) "Unlimited"
                        else "${state.config.dailyLimit}",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 1, 3, 5, 10).forEach { limit ->
                            TextButton(onClick = { viewModel.setDailyLimit(limit) }) {
                                Text(if (limit == 0) "None" else "$limit")
                            }
                        }
                    }
                    SliderRow(
                        label = "Weekly limit",
                        value = (state.config.weeklyLimit ?: 0).toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..200f,
                        valueLabel = state.config.weeklyLimit?.toString() ?: "Unlimited",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(null, 10, 25, 50).forEach { limit ->
                            TextButton(onClick = { viewModel.setWeeklyLimit(limit) }) {
                                Text(limit?.toString() ?: "None")
                            }
                        }
                    }
                }

                SectionCard {
                    SectionCardHeader("Eligibility", Icons.Default.Tune)
                    SliderRow(
                        label = "Minimum account age",
                        value = state.config.minAccountAgeDays.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..365f,
                        valueLabel = "${state.config.minAccountAgeDays}d",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 7, 30, 90).forEach { days ->
                            TextButton(onClick = { viewModel.setMinAccountAge(days) }) {
                                Text(if (days == 0) "Any" else "${days}d")
                            }
                        }
                    }
                    SliderRow(
                        label = "Minimum time in server",
                        value = state.config.minServerMembershipHours.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..720f,
                        valueLabel = "${state.config.minServerMembershipHours}h",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 24, 72, 168).forEach { hours ->
                            TextButton(onClick = { viewModel.setMinServerMembership(hours) }) {
                                Text(if (hours == 0) "Any" else "${hours}h")
                            }
                        }
                    }
                    SliderRow(
                        label = "Minimum messages",
                        value = state.config.minMessageCount.toFloat(),
                        onValueChange = { },
                        onValueChangeFinished = { },
                        valueRange = 0f..500f,
                        valueLabel = "${state.config.minMessageCount}",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 10, 50, 100).forEach { count ->
                            TextButton(onClick = { viewModel.setMinMessageCount(count) }) {
                                Text(if (count == 0) "Any" else "$count")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddReward) {
        var roleId by remember { mutableStateOf<String?>(null) }
        var repRequired by remember { mutableIntStateOf(10) }
        var xpReward by remember { mutableIntStateOf(0) }
        var removeOnDrop by remember { mutableStateOf(true) }
        var announceDm by remember { mutableStateOf(false) }
        var announceChannel by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddReward = false },
            title = { Text("Role reward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a role",
                        label = "Role",
                        selectedId = roleId,
                        onSelect = { roleId = it },
                    )
                    SliderRow(
                        label = "Reputation required",
                        value = repRequired.toFloat(),
                        onValueChange = { repRequired = it.toInt() },
                        valueRange = 1f..500f,
                        valueLabel = "$repRequired",
                    )
                    SliderRow(
                        label = "Bonus XP",
                        value = xpReward.toFloat(),
                        onValueChange = { xpReward = it.toInt() },
                        valueRange = 0f..5000f,
                        valueLabel = "$xpReward",
                    )
                    SwitchRow(
                        title = "Remove if rep drops",
                        checked = removeOnDrop,
                        onCheckedChange = { removeOnDrop = it },
                    )
                    SwitchRow(
                        title = "Announce by DM",
                        checked = announceDm,
                        onCheckedChange = { announceDm = it },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "No announcement",
                        label = "Announce in",
                        selectedId = announceChannel,
                        onSelect = { announceChannel = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        roleId?.let {
                            viewModel.upsertRoleReward(
                                roleId = it,
                                repRequired = repRequired,
                                removeOnDrop = removeOnDrop,
                                announceChannel = announceChannel,
                                announceDm = announceDm,
                                xpReward = xpReward,
                            )
                        }
                        showAddReward = false
                    },
                    enabled = roleId != null,
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddReward = false }) { Text("Cancel") }
            },
        )
    }

    pendingDeleteReward?.let { reward ->
        ConfirmDialog(
            title = "Remove role reward?",
            message = "@${reward.roleName} is no longer granted at ${reward.repRequired} rep.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeRoleReward(reward.roleId) },
            onDismiss = { pendingDeleteReward = null },
        )
    }
}
