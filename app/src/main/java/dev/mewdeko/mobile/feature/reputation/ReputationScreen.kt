package dev.mewdeko.mobile.feature.reputation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Tabs = listOf(
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("rewards", "Rewards", Icons.Default.WorkspacePremium),
)

private val HistoryTimestampFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

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
                StatTile(
                    label = "Transactions",
                    value = "${state.stats?.totalTransactions ?: 0}",
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
                            supportingContent = {
                                Text(
                                    text = "User ID: ${entry.userId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${entry.reputation}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    IconButton(onClick = { viewModel.openHistory(entry) }) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = "View history for ${entry.username}",
                                        )
                                    }
                                }
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
                    SettingNumberField(
                        label = "Cooldown (minutes)",
                        value = state.config.defaultCooldownMinutes,
                        onCommit = viewModel::setCooldown,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 30, 60, 360, 1440).forEach { minutes ->
                            TextButton(onClick = { viewModel.setCooldown(minutes) }) {
                                Text(if (minutes == 0) "None" else "${minutes}m")
                            }
                        }
                    }
                    SettingNumberField(
                        label = "Daily limit",
                        value = state.config.dailyLimit,
                        onCommit = viewModel::setDailyLimit,
                        placeholder = "Unlimited",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 1, 3, 5, 10).forEach { limit ->
                            TextButton(onClick = { viewModel.setDailyLimit(limit) }) {
                                Text(if (limit == 0) "None" else "$limit")
                            }
                        }
                    }
                    SettingNullableNumberField(
                        label = "Weekly limit",
                        value = state.config.weeklyLimit,
                        onCommit = viewModel::setWeeklyLimit,
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
                    SettingNumberField(
                        label = "Minimum account age (days)",
                        value = state.config.minAccountAgeDays,
                        onCommit = viewModel::setMinAccountAge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 7, 30, 90).forEach { days ->
                            TextButton(onClick = { viewModel.setMinAccountAge(days) }) {
                                Text(if (days == 0) "Any" else "${days}d")
                            }
                        }
                    }
                    SettingNumberField(
                        label = "Minimum time in server (hours)",
                        value = state.config.minServerMembershipHours,
                        onCommit = viewModel::setMinServerMembership,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0, 24, 72, 168).forEach { hours ->
                            TextButton(onClick = { viewModel.setMinServerMembership(hours) }) {
                                Text(if (hours == 0) "Any" else "${hours}h")
                            }
                        }
                    }
                    SettingNumberField(
                        label = "Minimum messages",
                        value = state.config.minMessageCount,
                        onCommit = viewModel::setMinMessageCount,
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
        var repRequiredText by remember { mutableStateOf("10") }
        var xpRewardText by remember { mutableStateOf("0") }
        var removeOnDrop by remember { mutableStateOf(true) }
        var announceDm by remember { mutableStateOf(false) }
        var announceChannel by remember { mutableStateOf<String?>(null) }
        val repRequired = repRequiredText.toIntOrNull()

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
                    MewdekoTextField(
                        value = repRequiredText,
                        onValueChange = { repRequiredText = it.filter(Char::isDigit).take(9) },
                        label = "Reputation required",
                        numeric = true,
                        isError = repRequired == null || repRequired < 1,
                        supportingText = if (repRequired == null || repRequired < 1) "Must be at least 1" else null,
                    )
                    MewdekoTextField(
                        value = xpRewardText,
                        onValueChange = { xpRewardText = it.filter(Char::isDigit).take(9) },
                        label = "Bonus XP",
                        numeric = true,
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
                        val role = roleId
                        val rep = repRequired
                        if (role != null && rep != null && rep >= 1) {
                            viewModel.upsertRoleReward(
                                roleId = role,
                                repRequired = rep,
                                removeOnDrop = removeOnDrop,
                                announceChannel = announceChannel,
                                announceDm = announceDm,
                                xpReward = xpRewardText.toIntOrNull() ?: 0,
                            )
                            showAddReward = false
                        }
                    },
                    enabled = roleId != null && repRequired != null && repRequired >= 1,
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

    state.historyTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::closeHistory,
            title = { Text("Reputation history") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${target.username} · ${target.reputation} reputation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when {
                        state.historyLoading && state.historyEntries.isEmpty() -> Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) { CircularProgressIndicator() }

                        state.historyFailed && state.historyEntries.isEmpty() ->
                            EmptyState("Failed to load reputation history.", icon = Icons.Default.History)

                        state.historyEntries.isEmpty() ->
                            EmptyState(
                                "No reputation has been given to this member yet.",
                                icon = Icons.Default.History,
                            )

                        else -> {
                            state.historyEntries.forEach { entry ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (entry.amount >= 0) "+${entry.amount}" else "${entry.amount}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (entry.amount >= 0) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.error
                                            },
                                        )
                                        Text(
                                            text = "  ${entry.repType} from " +
                                                if (entry.isAnonymous) "an anonymous member" else "user ${entry.giverId}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    entry.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                                        Text(
                                            text = reason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = HistoryTimestampFormat.format(entry.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    onClick = { viewModel.loadHistory(state.historyPage - 1) },
                                    enabled = state.historyPage > 1 && !state.historyLoading,
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = null)
                                    Text("Previous")
                                }
                                Text(
                                    "Page ${state.historyPage}",
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                TextButton(
                                    onClick = { viewModel.loadHistory(state.historyPage + 1) },
                                    enabled = state.historyEntries.size >= 20 && !state.historyLoading,
                                ) {
                                    Text("Next")
                                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeHistory) { Text("Close") }
            },
        )
    }
}

/**
 * A whole-number setting field that persists via [onCommit] once its typed
 * value differs from [value]. Mirrors the dashboard's plain number inputs.
 */
@Composable
private fun SettingNumberField(
    label: String,
    value: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var draft by remember(value) { mutableStateOf(value.toString()) }
    val parsed = draft.toIntOrNull()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MewdekoTextField(
            value = draft,
            onValueChange = { draft = it.filter(Char::isDigit).take(9) },
            label = label,
            placeholder = placeholder,
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { parsed?.let(onCommit) },
            enabled = parsed != null && parsed != value,
        ) {
            Icon(Icons.Default.Check, contentDescription = "Apply $label")
        }
    }
}

/**
 * A nullable whole-number setting field. An empty box commits `null`
 * (unlimited); mirrors the dashboard's optional number input.
 */
@Composable
private fun SettingNullableNumberField(
    label: String,
    value: Int?,
    onCommit: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    val next: Int? = draft.toIntOrNull()
    val changed = (draft.isEmpty() && value != null) || (next != null && next != value)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MewdekoTextField(
            value = draft,
            onValueChange = { draft = it.filter(Char::isDigit).take(9) },
            label = label,
            placeholder = "Unlimited",
            numeric = true,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = { onCommit(if (draft.isEmpty()) null else next) },
            enabled = changed,
        ) {
            Icon(Icons.Default.Check, contentDescription = "Apply $label")
        }
    }
}
