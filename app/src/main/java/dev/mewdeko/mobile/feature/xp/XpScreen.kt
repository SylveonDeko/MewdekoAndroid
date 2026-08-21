package dev.mewdeko.mobile.feature.xp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.Avatar
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
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.compact

private val Tabs = listOf(
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("rewards", "Rewards", Icons.Default.WorkspacePremium),
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("exclusions", "Excluded", Icons.Default.Block),
)

/** Leveling, leaderboard, and rewards. */
@Composable
fun XpScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: XpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAddRoleReward by remember { mutableStateOf(false) }
    var showAddCurrencyReward by remember { mutableStateOf(false) }
    var adjustingMember by remember { mutableStateOf<String?>(null) }
    var pendingRemoveRole by remember { mutableStateOf<XpRoleRewardModel?>(null) }
    var pendingRemoveCurrency by remember { mutableStateOf<XpCurrencyRewardModel?>(null) }

    FeatureScaffold(
        title = "XP System",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.hasUnsavedSettings) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveSettings,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save settings") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Star)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Members",
                    value = "${state.serverStats?.totalUsers ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Total XP",
                    value = (state.serverStats?.totalXp ?: 0L).compact(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Avg level",
                    value = "%.1f".format(state.serverStats?.averageLevel ?: 0.0),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Highest",
                    value = "${state.serverStats?.highestLevel ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "rewards" -> {
                SectionCard {
                    SectionCardHeader("Role rewards", Icons.Default.WorkspacePremium)
                    if (state.roleRewards.isEmpty()) {
                        EmptyState("No role rewards configured.")
                    } else {
                        state.roleRewards.forEach { reward ->
                            ListItem(
                                headlineContent = {
                                    Text("Level ${reward.level}")
                                },
                                supportingContent = {
                                    Text("@${reward.roleName ?: reward.roleId.orEmpty()}")
                                },
                                trailingContent = {
                                    IconButton(onClick = { pendingRemoveRole = reward }) {
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
                        onClick = { showAddRoleReward = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add role reward", modifier = Modifier.padding(start = 6.dp))
                    }
                }

                SectionCard {
                    SectionCardHeader("Currency rewards", Icons.Default.Paid)
                    if (state.currencyRewards.isEmpty()) {
                        EmptyState("No currency rewards configured.")
                    } else {
                        state.currencyRewards.forEach { reward ->
                            ListItem(
                                headlineContent = { Text("Level ${reward.level}") },
                                supportingContent = { Text("${reward.amount.compact()} currency") },
                                trailingContent = {
                                    IconButton(onClick = { pendingRemoveCurrency = reward }) {
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
                        onClick = { showAddCurrencyReward = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add currency reward", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            "settings" -> {
                SectionCard {
                    SectionCardHeader("Gain rates", Icons.Default.Tune)
                    SwitchRow(
                        title = "XP gain enabled",
                        checked = !state.settings.xpGainDisabled,
                        onCheckedChange = { value ->
                            viewModel.edit { it.copy(xpGainDisabled = !value) }
                        },
                    )
                    SliderRow(
                        label = "XP per message",
                        value = state.settings.xpPerMessage.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(xpPerMessage = value.toInt()) }
                        },
                        valueRange = 0f..100f,
                        valueLabel = "${state.settings.xpPerMessage}",
                    )
                    SliderRow(
                        label = "Message cooldown",
                        value = state.settings.messageXpCooldown.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(messageXpCooldown = value.toInt()) }
                        },
                        valueRange = 0f..600f,
                        valueLabel = "${state.settings.messageXpCooldown}s",
                    )
                    SliderRow(
                        label = "Voice XP per minute",
                        value = state.settings.voiceXpPerMinute.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(voiceXpPerMinute = value.toInt()) }
                        },
                        valueRange = 0f..50f,
                        valueLabel = "${state.settings.voiceXpPerMinute}",
                    )
                    SliderRow(
                        label = "Voice timeout",
                        value = state.settings.voiceXpTimeout.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(voiceXpTimeout = value.toInt()) }
                        },
                        valueRange = 0f..240f,
                        valueLabel = if (state.settings.voiceXpTimeout == 0) "Off"
                        else "${state.settings.voiceXpTimeout}m",
                    )
                    SliderRow(
                        label = "XP multiplier",
                        value = state.settings.xpMultiplier.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit {
                                it.copy(xpMultiplier = (value * 10).toInt() / 10.0)
                            }
                        },
                        valueRange = 0.1f..5f,
                        valueLabel = "%.1fx".format(state.settings.xpMultiplier),
                    )
                    SliderRow(
                        label = "First message bonus",
                        value = state.settings.firstMessageBonus.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(firstMessageBonus = value.toInt()) }
                        },
                        valueRange = 0f..500f,
                        valueLabel = "${state.settings.firstMessageBonus}",
                    )
                }

                SectionCard {
                    SectionCardHeader("Levels", Icons.Default.Tune)
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Star),
                        options = XpCurveType.entries.map {
                            SelectorOption(it.raw.toString(), it.label)
                        },
                        placeholder = "Standard",
                        label = "Level curve",
                        selectedId = state.settings.xpCurveType.toString(),
                        onSelect = { raw ->
                            viewModel.edit { it.copy(xpCurveType = raw?.toIntOrNull() ?: 0) }
                        },
                    )
                    SwitchRow(
                        title = "Exclusive role rewards",
                        subtitle = "Replace the previous reward role instead of stacking",
                        checked = state.settings.exclusiveRoleRewards,
                        onCheckedChange = { value ->
                            viewModel.edit { it.copy(exclusiveRoleRewards = value) }
                        },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "Same channel as the message",
                        label = "Level-up announcements",
                        selectedId = state.settings.levelUpChannel.takeIf { it != "0" },
                        onSelect = { id ->
                            viewModel.edit { it.copy(levelUpChannel = id ?: "0") }
                        },
                    )
                    EmbedMessageEditor(
                        message = EmbedMessage.parse(state.settings.levelUpMessage),
                        onMessageChange = viewModel::setLevelUpMessage,
                    )
                    MewdekoTextField(
                        value = state.settings.customXpImageUrl,
                        onValueChange = { value ->
                            viewModel.edit { it.copy(customXpImageUrl = value) }
                        },
                        label = "XP card background URL",
                    )
                }

                SectionCard {
                    SectionCardHeader("Decay", Icons.Default.Tune)
                    SwitchRow(
                        title = "Decay inactive members",
                        subtitle = "Slowly remove XP from members who stop participating",
                        checked = state.settings.enableXpDecay,
                        onCheckedChange = { value ->
                            viewModel.edit { it.copy(enableXpDecay = value) }
                        },
                    )
                    SliderRow(
                        label = "Inactive days before decay",
                        value = state.settings.inactivityDaysBeforeDecay.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(inactivityDaysBeforeDecay = value.toInt()) }
                        },
                        valueRange = 1f..365f,
                        valueLabel = "${state.settings.inactivityDaysBeforeDecay}d",
                    )
                    SliderRow(
                        label = "Daily decay",
                        value = state.settings.dailyDecayPercentage.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit {
                                it.copy(dailyDecayPercentage = (value * 10).toInt() / 10.0)
                            }
                        },
                        valueRange = 0f..25f,
                        valueLabel = "%.1f%%".format(state.settings.dailyDecayPercentage),
                    )
                }
            }

            "exclusions" -> {
                SectionCard {
                    SectionCardHeader("Excluded channels", Icons.Default.Block)
                    Text(
                        text = "Messages in these channels award no XP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.availableChannels.forEach { channel ->
                        ListItem(
                            headlineContent = { Text("#${channel.name}") },
                            trailingContent = {
                                Checkbox(
                                    checked = channel.id in state.excludedChannels,
                                    onCheckedChange = null,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableRow { viewModel.toggleExcludedChannel(channel.id) },
                        )
                    }
                }
                SectionCard {
                    SectionCardHeader("Excluded roles", Icons.Default.Block)
                    Text(
                        text = "Members holding these roles award no XP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.availableRoles.forEach { role ->
                        ListItem(
                            headlineContent = { Text("@${role.name}") },
                            trailingContent = {
                                Checkbox(
                                    checked = role.id in state.excludedRoles,
                                    onCheckedChange = null,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableRow { viewModel.toggleExcludedRole(role.id) },
                        )
                    }
                }
            }

            else -> SectionCard {
                SectionCardHeader("Leaderboard", Icons.Default.Leaderboard)
                if (state.leaderboard.isEmpty()) {
                    EmptyState("No XP recorded yet.", icon = Icons.Default.Star)
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
                                Column {
                                    Text(
                                        "Level ${entry.level} · ${entry.totalXp.compact()} XP" +
                                            if (entry.bonusXp > 0) {
                                                " (+${entry.bonusXp.compact()} bonus)"
                                            } else {
                                                ""
                                            }
                                    )
                                    if (entry.requiredXp > 0) {
                                        LinearProgressIndicator(
                                            progress = {
                                                (entry.levelXp.toFloat() /
                                                    entry.requiredXp.toFloat())
                                                    .coerceIn(0f, 1f)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(
                                        url = entry.avatarUrl,
                                        contentDescription = entry.username,
                                        size = 32,
                                    )
                                    IconButton(onClick = { adjustingMember = entry.userId }) {
                                        Icon(Icons.Default.Tune, contentDescription = "Adjust XP")
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = { viewModel.setPage(state.leaderboardPage - 1) },
                            enabled = state.leaderboardPage > 1,
                        ) { Text("Previous") }
                        Text(
                            text = "Page ${state.leaderboardPage}",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        TextButton(
                            onClick = { viewModel.setPage(state.leaderboardPage + 1) },
                            enabled = state.leaderboard.size >= 25,
                        ) { Text("Next") }
                    }
                }
            }
        }
    }

    if (showAddRoleReward) {
        var level by remember { mutableIntStateOf(5) }
        var roleId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddRoleReward = false },
            title = { Text("Add role reward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SliderRow(
                        label = "Level",
                        value = level.toFloat(),
                        onValueChange = { level = it.toInt().coerceAtLeast(1) },
                        valueRange = 1f..200f,
                        valueLabel = "$level",
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Role,
                        options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                        placeholder = "Pick a role",
                        label = "Role",
                        selectedId = roleId,
                        onSelect = { roleId = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        roleId?.let { viewModel.addRoleReward(level, it) }
                        showAddRoleReward = false
                    },
                    enabled = roleId != null,
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddRoleReward = false }) { Text("Cancel") }
            },
        )
    }

    if (showAddCurrencyReward) {
        var level by remember { mutableIntStateOf(5) }
        var amount by remember { mutableStateOf("100") }
        AlertDialog(
            onDismissRequest = { showAddCurrencyReward = false },
            title = { Text("Add currency reward") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SliderRow(
                        label = "Level",
                        value = level.toFloat(),
                        onValueChange = { level = it.toInt().coerceAtLeast(1) },
                        valueRange = 1f..200f,
                        valueLabel = "$level",
                    )
                    MewdekoTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit) },
                        label = "Amount",
                        numeric = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addCurrencyReward(level, amount.toIntOrNull() ?: 0)
                        showAddCurrencyReward = false
                    },
                    enabled = amount.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddCurrencyReward = false }) { Text("Cancel") }
            },
        )
    }

    adjustingMember?.let { memberId ->
        var amount by remember(memberId) { mutableStateOf("100") }
        AlertDialog(
            onDismissRequest = { adjustingMember = null },
            title = { Text("Adjust XP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MewdekoTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit) },
                        label = "Amount",
                        numeric = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                viewModel.addUserXp(memberId, amount.toIntOrNull() ?: 0)
                                adjustingMember = null
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Add") }
                        OutlinedButton(
                            onClick = {
                                viewModel.setUserXp(memberId, amount.toLongOrNull() ?: 0L)
                                adjustingMember = null
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Set") }
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.resetUserXp(memberId, resetBonus = true)
                            adjustingMember = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Reset to zero", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { adjustingMember = null }) { Text("Done") }
            },
        )
    }

    pendingRemoveRole?.let { reward ->
        ConfirmDialog(
            title = "Remove role reward?",
            message = "@${reward.roleName ?: reward.roleId.orEmpty()} is no longer granted at " +
                "level ${reward.level}.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeRoleReward(reward.id) },
            onDismiss = { pendingRemoveRole = null },
        )
    }

    pendingRemoveCurrency?.let { reward ->
        ConfirmDialog(
            title = "Remove currency reward?",
            message = "${reward.amount} currency is no longer paid at level ${reward.level}.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeCurrencyReward(reward.id) },
            onDismiss = { pendingRemoveCurrency = null },
        )
    }
}
