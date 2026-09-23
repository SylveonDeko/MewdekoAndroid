package dev.mewdeko.mobile.feature.votes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.theme.MonospaceStyle
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
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.formatDuration
import dev.mewdeko.mobile.util.relativeToNow

/** A unit a vote reward role duration can be entered in, seconds up to days. */
private enum class VoteDurationUnit(val id: String, val label: String, val seconds: Long) {
    SECONDS("seconds", "Seconds", 1L),
    MINUTES("minutes", "Minutes", 60L),
    HOURS("hours", "Hours", 3600L),
    DAYS("days", "Days", 86400L);

    companion object {
        /** Resolves a stored id, defaulting to hours. */
        fun from(id: String?): VoteDurationUnit = entries.firstOrNull { it.id == id } ?: HOURS

        /** Picks the largest unit that evenly divides [totalSeconds], for prefilling an edit dialog. */
        fun bestFit(totalSeconds: Int): VoteDurationUnit {
            if (totalSeconds <= 0) return HOURS
            return entries.sortedByDescending { it.seconds }
                .firstOrNull { totalSeconds.toLong() % it.seconds == 0L } ?: SECONDS
        }
    }
}

private val VoteDurationUnitOptions = VoteDurationUnit.entries.map { SelectorOption(it.id, it.label) }

/** The four vote announcement placeholders the dashboard documents. */
private val VoteMessagePlaceholders = listOf(
    "%user.mention%",
    "%user.name%",
    "%user.id%",
    "%server.name%",
)

/** Converts a raw amount string plus its unit into whole seconds, 0 or blank meaning permanent. */
private fun VoteDurationUnit.toSeconds(amount: String): Int {
    val value = amount.toLongOrNull() ?: 0L
    if (value <= 0L) return 0
    return (value * seconds).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** A numeric amount field paired with a duration unit selector, any number of seconds up to days. */
@Composable
private fun DurationAmountUnitFields(
    amount: String,
    onAmountChange: (String) -> Unit,
    unit: VoteDurationUnit,
    onUnitChange: (VoteDurationUnit) -> Unit,
) {
    MewdekoTextField(
        value = amount,
        onValueChange = { value -> onAmountChange(value.filter(Char::isDigit)) },
        label = "Duration",
        numeric = true,
        supportingText = "0 or blank means permanent",
    )
    DiscordSelectorSingle(
        kind = SelectorKind.Custom(Icons.Default.HourglassBottom),
        options = VoteDurationUnitOptions,
        placeholder = "Unit",
        label = "Unit",
        selectedId = unit.id,
        onSelect = { onUnitChange(VoteDurationUnit.from(it)) },
    )
}

private val Tabs = listOf(
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("history", "History", Icons.Default.History),
)

/** Vote tracking, reward roles, and the vote leaderboard. */
@Composable
fun VotesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: VotesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAddRole by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<VoteRoleEntry?>(null) }

    FeatureScaffold(
        title = "Votes",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.hasUnsavedConfig) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveConfig,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save settings") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.ThumbUp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Votes", "${state.votes.size}", Modifier.weight(1f))
                StatTile("Reward roles", "${state.voteRoles.size}", Modifier.weight(1f))
                StatTile(
                    "Top voter",
                    "${state.leaderboard.firstOrNull()?.voteCount ?: 0}",
                    Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "leaderboard" -> SectionCard {
                SectionCardHeader("Top voters", Icons.Default.Leaderboard)
                if (state.leaderboard.isEmpty()) {
                    EmptyState("No votes recorded yet.", icon = Icons.Default.ThumbUp)
                } else {
                    state.leaderboard.forEachIndexed { index, entry ->
                        ListItem(
                            leadingContent = {
                                Box(
                                    modifier = Modifier.width(28.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (index == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            },
                            headlineContent = {
                                Text(
                                    text = entry.userId ?: "Unknown",
                                    style = MonospaceStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Text(
                                    text = "${entry.voteCount}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            "history" -> SectionCard {
                SectionCardHeader("Recent votes", Icons.Default.History)
                if (state.votes.isEmpty()) {
                    EmptyState("No votes recorded yet.", icon = Icons.Default.History)
                } else {
                    state.votes.take(50).forEach { vote ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = vote.userId ?: "Unknown",
                                    style = MonospaceStyle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = vote.dateAdded?.let {
                                { Text(it.relativeToNow()) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            else -> {
                SectionCard {
                    SectionCardHeader("Announcement", Icons.Default.Tune)
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "No announcement channel",
                        label = "Announce votes in",
                        selectedId = state.channelId,
                        onSelect = viewModel::setChannel,
                    )
                    EmbedMessageEditor(
                        message = state.message,
                        onMessageChange = viewModel::setMessage,
                    )
                    PlaceholderHints(
                        onInsert = { token ->
                            viewModel.setMessage(state.message.copy(content = state.message.content + token))
                        },
                    )
                    VotesPasswordField(
                        value = state.password,
                        onValueChange = viewModel::setPassword,
                        label = "Webhook password",
                        supportingText = "Shared secret the vote site sends back to the bot.",
                    )
                }

                SectionCard {
                    SectionCardHeader(
                        title = "Reward roles",
                        icon = Icons.Default.ThumbUp,
                        trailing = {
                            if (state.voteRoles.isNotEmpty()) {
                                IconButton(onClick = { pendingClear = true }) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Clear all",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    )
                    if (state.voteRoles.isEmpty()) {
                        EmptyState("No reward roles configured.")
                    } else {
                        state.voteRoles.forEach { entry ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "@${state.roleName(entry.roleId)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = if (entry.timer <= 0) "Permanent"
                                    else formatDuration(entry.timer.toLong()),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                IconButton(onClick = { editingRole = entry }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit duration")
                                }
                                IconButton(
                                    onClick = { viewModel.removeVoteRole(entry.roleId) },
                                ) {
                                    Icon(
                                        Icons.Default.DeleteSweep,
                                        contentDescription = "Remove role",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showAddRole = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add reward role", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }

    if (showAddRole) {
        var roleId by remember { mutableStateOf<String?>(null) }
        var amount by remember { mutableStateOf("12") }
        var unit by remember { mutableStateOf(VoteDurationUnit.HOURS) }
        AlertDialog(
            onDismissRequest = { showAddRole = false },
            title = { Text("Add reward role") },
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
                    DurationAmountUnitFields(
                        amount = amount,
                        onAmountChange = { amount = it },
                        unit = unit,
                        onUnitChange = { unit = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        roleId?.let { viewModel.addVoteRole(it, unit.toSeconds(amount)) }
                        showAddRole = false
                    },
                    enabled = roleId != null,
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddRole = false }) { Text("Cancel") } },
        )
    }

    editingRole?.let { role ->
        val initialUnit = VoteDurationUnit.bestFit(role.timer)
        var amount by remember(role.id) {
            mutableStateOf(if (role.timer <= 0) "0" else (role.timer / initialUnit.seconds).toString())
        }
        var unit by remember(role.id) { mutableStateOf(initialUnit) }
        AlertDialog(
            onDismissRequest = { editingRole = null },
            title = { Text("Edit duration") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "@${state.roleName(role.roleId)}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    DurationAmountUnitFields(
                        amount = amount,
                        onAmountChange = { amount = it },
                        unit = unit,
                        onUnitChange = { unit = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTimer(role.roleId, unit.toSeconds(amount))
                        editingRole = null
                    },
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingRole = null }) { Text("Cancel") } },
        )
    }

    if (pendingClear) {
        ConfirmDialog(
            title = "Clear all reward roles?",
            message = "Every configured vote reward role is removed.",
            confirmLabel = "Clear all",
            onConfirm = viewModel::clearAllRoles,
            onDismiss = { pendingClear = false },
        )
    }
}

/** A single-line text field that masks its value with a trailing show/hide toggle. */
@Composable
private fun VotesPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A row of tappable chips that insert a vote announcement placeholder into the message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceholderHints(onInsert: (String) -> Unit) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = "Tap to insert a placeholder",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            VoteMessagePlaceholders.forEach { token ->
                TagChip(label = token, onClick = { onInsert(token) })
            }
        }
    }
}
