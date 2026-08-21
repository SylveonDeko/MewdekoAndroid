package dev.mewdeko.mobile.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Checkbox
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
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.clickableRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("types", "Log types", Icons.Default.ManageSearch),
    SectionTab("ignored", "Ignored", Icons.Default.Block),
)

/** Per-event audit logging destinations and the ignore list. */
@Composable
fun LoggingScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: LoggingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDisableAll by remember { mutableStateOf(false) }
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Logging",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { pendingDisableAll = true }) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = "Disable all logging",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Configured",
                    value = "${state.configuredCount}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Log types",
                    value = "${LogType.entries.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Ignored",
                    value = "${state.ignoredChannels.size}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.section == "ignored") {
            SectionCard {
                SectionCardHeader("Ignored channels", Icons.Default.Block)
                Text(
                    text = "Events in these channels are never logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.availableChannels.isEmpty()) {
                    EmptyState("No channels found.", icon = Icons.Default.Tag)
                } else {
                    state.availableChannels.forEach { channel ->
                        val ignored = channel.id in state.ignoredChannels
                        ListItem(
                            headlineContent = { Text("#${channel.name}") },
                            trailingContent = {
                                Checkbox(checked = ignored, onCheckedChange = null)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickableRow { viewModel.toggleIgnored(channel.id) },
                        )
                    }
                }
            }
        } else {
            SectionCard {
                SectionCardHeader("Log destinations", Icons.Default.ManageSearch)
                Text(
                    text = "Pick the channel each event type is written to. Leaving one unset " +
                        "disables logging for that event.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LogType.entries.forEach { type ->
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = channelOptions,
                        placeholder = "Not logged",
                        label = type.label,
                        selectedId = state.channelFor(type),
                        onSelect = { viewModel.setChannel(type, it) },
                    )
                }
            }
        }
    }

    if (pendingDisableAll) {
        ConfirmDialog(
            title = "Disable all logging?",
            message = "Every log type loses its destination channel. The ignore list is kept.",
            confirmLabel = "Disable all",
            onConfirm = viewModel::disableAll,
            onDismiss = { pendingDisableAll = false },
        )
    }
}
