package dev.mewdeko.mobile.feature.rolegreets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
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
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add greet") },
            )
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
                                TagChip("#${state.channelName(greet.channelId)}", icon = Icons.Default.Tag)
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
                        SliderRow(
                            label = "Auto-delete after",
                            value = greet.deleteTime.toFloat(),
                            onValueChange = { },
                            onValueChangeFinished = { },
                            valueRange = 0f..600f,
                            valueLabel = if (greet.deleteTime == 0) "Never" else "${greet.deleteTime}s",
                        )
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
                        )
                        if (!greet.webhookUrl.isNullOrBlank()) {
                            Text(
                                text = "Posted through a webhook.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        var roleId by remember { mutableStateOf<String?>(null) }
        var channelId by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add role greet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val role = roleId
                        val channel = channelId
                        if (role != null && channel != null) viewModel.add(role, channel)
                        showAdd = false
                    },
                    enabled = roleId != null && channelId != null,
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}
