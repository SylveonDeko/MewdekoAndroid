package dev.mewdeko.mobile.feature.statusroles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Roles applied while a member's custom status contains a phrase. */
@Composable
fun StatusRolesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: StatusRolesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<StatusRoleConfig?>(null) }

    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Status Roles",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add rule") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.SentimentSatisfied)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Rules", "${state.configs.size}", Modifier.weight(1f))
                StatTile(
                    label = "Granting",
                    value = "${state.configs.sumOf { it.addRoleIds.size }}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Revoking",
                    value = "${state.configs.sumOf { it.removeRoleIds.size }}",
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "When a member's custom status contains the trigger text, the bot applies " +
                    "the configured role changes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.configs.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No status rules configured yet.",
                    icon = Icons.Default.SentimentSatisfied,
                )
            }
        } else {
            state.configs.forEach { config ->
                SectionCard {
                    SectionCardHeader(
                        title = config.status.orEmpty().ifBlank { "Rule #${config.id}" },
                        icon = Icons.Default.SentimentSatisfied,
                        trailing = {
                            IconButton(onClick = { pendingDelete = config }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove rule",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        },
                    )
                    DiscordSelector(
                        kind = SelectorKind.Role,
                        options = roleOptions,
                        placeholder = "No roles granted",
                        label = "Grant while matching",
                        multiple = true,
                        selection = config.addRoleIds,
                        onSelectionChange = { viewModel.setAddRoles(config.id, it) },
                    )
                    DiscordSelector(
                        kind = SelectorKind.Role,
                        options = roleOptions,
                        placeholder = "No roles revoked",
                        label = "Revoke while matching",
                        multiple = true,
                        selection = config.removeRoleIds,
                        onSelectionChange = { viewModel.setRemoveRoles(config.id, it) },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = channelOptions,
                        placeholder = "No announcement channel",
                        label = "Announce in",
                        selectedId = config.statusChannelId,
                        onSelect = { it?.let { id -> viewModel.setChannel(config.id, id) } },
                    )
                    SwitchRow(
                        title = "Revoke granted roles on change",
                        subtitle = "Take the granted roles back when the status no longer matches",
                        checked = config.removeAdded,
                        onCheckedChange = { viewModel.toggleRemoveAdded(config.id) },
                    )
                    SwitchRow(
                        title = "Restore revoked roles on change",
                        subtitle = "Give the revoked roles back when the status no longer matches",
                        checked = config.readdRemoved,
                        onCheckedChange = { viewModel.toggleReaddRemoved(config.id) },
                    )
                    EmbedMessageEditor(
                        message = EmbedMessage.parse(config.statusEmbed),
                        onMessageChange = { viewModel.setEmbed(config.id, it) },
                    )
                }
            }
        }
    }

    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add status rule") },
            text = {
                MewdekoTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = "Trigger text",
                    placeholder = "gg/myserver",
                    supportingText = "Matched against each member's custom status.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.add(text.trim()); showAdd = false },
                    enabled = text.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }

    pendingDelete?.let { config ->
        ConfirmDialog(
            title = "Remove status rule?",
            message = "The rule for \"${config.status.orEmpty()}\" is deleted.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.remove(config.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}
