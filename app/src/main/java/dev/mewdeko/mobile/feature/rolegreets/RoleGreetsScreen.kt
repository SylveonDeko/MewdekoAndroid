package dev.mewdeko.mobile.feature.rolegreets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.feature.embed.Placeholder
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/**
 * Extra placeholders the bot resolves for a role greet, mirroring the
 * dashboard's `additionalPlaceholders` list passed to its embed builder.
 */
private val RoleGreetPlaceholders = listOf(
    Placeholder("Role Greet", "%user.username%", "The new member's username"),
    Placeholder("Role Greet", "%user.mention%", "Mention the new member"),
    Placeholder("Role Greet", "%server.name%", "The server's name"),
    Placeholder("Role Greet", "%role.name%", "The name of the role that was assigned"),
)

/** Greetings posted when a member gains a role. */
@Composable
fun RoleGreetsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: RoleGreetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RoleGreetEntry?>(null) }

    FeatureScaffold(
        title = "Role Greets",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            NewItemFab(label = "Add greet", onClick = { showAdd = true })
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.PersonAddAlt)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Greets", "${state.greets.size}", Modifier.weight(1f))
                StatTile("Active", "${state.activeCount}", Modifier.weight(1f))
            }
        }

        if (state.greets.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No role greets configured yet.",
                    icon = Icons.Default.PersonAddAlt,
                    actionLabel = "Add greet",
                    onAction = { showAdd = true },
                )
            }
        } else {
            state.greets.forEach { greet ->
                key(greet.id) {
                    SectionCard {
                        SectionCardHeader(
                            title = "@${state.roleName(greet.roleId)}",
                            icon = Icons.Default.PersonAddAlt,
                            trailing = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    TagChip("#${state.channelName(greet.channelId)}", icon = Icons.Default.Tag)
                                    IconButton(onClick = { pendingDelete = greet }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete greet",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                        )
                        SwitchRow(
                            title = "Enabled",
                            checked = !greet.disabled,
                            onCheckedChange = { viewModel.updateDisabled(greet.id, !it) },
                        )
                        SwitchRow(
                            title = "Greet bots",
                            subtitle = "Post this greeting for bot accounts too",
                            checked = greet.greetBots,
                            onCheckedChange = { viewModel.updateGreetBots(greet.id, it) },
                        )
                        var deleteTimeDraft by remember(greet.id, greet.deleteTime) {
                            mutableStateOf(greet.deleteTime.toString())
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MewdekoTextField(
                                value = deleteTimeDraft,
                                onValueChange = { deleteTimeDraft = it.filter { c -> c.isDigit() } },
                                label = "Delete after (seconds, 0 for never)",
                                numeric = true,
                                modifier = Modifier.weight(1f),
                            )
                            val deleteTimeValue = deleteTimeDraft.toIntOrNull()
                            Button(
                                onClick = {
                                    deleteTimeValue?.let { viewModel.updateDeleteTime(greet.id, it) }
                                },
                                enabled = deleteTimeValue != null &&
                                    deleteTimeValue >= 0 &&
                                    deleteTimeValue != greet.deleteTime,
                            ) { Text("Save") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0, 30, 60, 300).forEach { seconds ->
                                TextButton(onClick = { viewModel.updateDeleteTime(greet.id, seconds) }) {
                                    Text(if (seconds == 0) "Never" else "${seconds}s")
                                }
                            }
                        }
                        EmbedMessageEditor(
                            message = EmbedMessage.parse(greet.message),
                            onMessageChange = { viewModel.updateMessage(greet.id, it) },
                            additionalPlaceholders = RoleGreetPlaceholders,
                        )
                        var webhookDraft by remember(greet.id, greet.webhookUrl) {
                            mutableStateOf(greet.webhookUrl.orEmpty())
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MewdekoTextField(
                                value = webhookDraft,
                                onValueChange = { webhookDraft = it },
                                label = "Webhook URL (optional)",
                                placeholder = "https://discord.com/api/webhooks/...",
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = {
                                    viewModel.updateWebhook(greet.id, webhookDraft.trim().ifBlank { null })
                                },
                                enabled = webhookDraft.trim().ifBlank { null } != greet.webhookUrl,
                            ) { Text("Save") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var roleId by remember { mutableStateOf<String?>(null) }
        var channelId by remember { mutableStateOf<String?>(null) }
        FormSheet(
            title = "Add role greet",
            confirmLabel = "Add",
            confirmEnabled = roleId != null && channelId != null,
            onConfirm = {
                val role = roleId
                val channel = channelId
                if (role != null && channel != null) viewModel.add(role, channel)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        ) {
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a role",
                label = "Trigger role",
                selectedId = roleId,
                onSelect = { roleId = it },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a channel",
                label = "Post to",
                selectedId = channelId,
                onSelect = { channelId = it },
            )
        }
    }

    pendingDelete?.let { greet ->
        ConfirmDialog(
            title = "Delete role greet?",
            message = "The greeting for @${state.roleName(greet.roleId)} in " +
                "#${state.channelName(greet.channelId)} is removed.",
            onConfirm = { viewModel.delete(greet.id) },
            onDismiss = { pendingDelete = null },
        )
    }
}
