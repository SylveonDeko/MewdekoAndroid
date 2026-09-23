package dev.mewdeko.mobile.feature.logging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/**
 * A chip in the category filter row above the log-type list. `"popular"` and
 * `"all"` are client-side pseudo-categories; the rest map to [LogCategory].
 */
private data class TypeFilter(val id: String, val label: String)

private val TypeFilters = listOf(
    TypeFilter("popular", "Popular"),
    TypeFilter("users", "Users"),
    TypeFilter("messages", "Messages"),
    TypeFilter("moderation", "Moderation"),
    TypeFilter("server", "Server"),
    TypeFilter("channels", "Channels"),
    TypeFilter("roles", "Roles"),
    TypeFilter("threads", "Threads"),
    TypeFilter("voice", "Voice"),
    TypeFilter("all", "All Events"),
)

private fun logTypesFor(filter: String): List<LogType> = when (filter) {
    "all" -> LogType.entries.toList()
    "popular" -> LogType.entries.filter { it.raw in LogType.POPULAR_RAW }
    else -> LogType.entries.filter { it.category.id == filter }
}

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
    var pendingClearCategory by remember { mutableStateOf<LogCategory?>(null) }
    var typeFilter by remember { mutableStateOf("popular") }
    var channelSearch by remember { mutableStateOf("") }
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    val ignoredList = state.availableChannels.filter { it.id in state.ignoredChannels }
    val activeAll = state.availableChannels.filter { it.id !in state.ignoredChannels }
    val activeList = activeAll.filter { it.name.contains(channelSearch, ignoreCase = true) }

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
        SectionTabs(
            tabs = Tabs.map { tab ->
                if (tab.id == "ignored") tab.copy(title = "Ignored (${state.ignoredChannels.size})")
                else tab
            },
            selectedId = state.section,
            onSelect = viewModel::setSection,
        )

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
            IgnoredChannelsSection(
                search = channelSearch,
                onSearchChange = { channelSearch = it },
                ignored = ignoredList,
                activeTotal = activeAll.size,
                active = activeList,
                onToggle = viewModel::toggleIgnored,
            )
        } else {
            SectionCard {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TypeFilters, key = { it.id }) { filter ->
                        FilterChip(
                            selected = typeFilter == filter.id,
                            onClick = { typeFilter = filter.id },
                            label = { Text(filter.label) },
                            leadingIcon = if (filter.id == "popular") {
                                { Icon(Icons.Default.Star, contentDescription = null) }
                            } else null,
                        )
                    }
                }
                if (typeFilter != "popular" && typeFilter != "all") {
                    TextButton(
                        onClick = {
                            LogCategory.entries.find { it.id == typeFilter }
                                ?.let { pendingClearCategory = it }
                        },
                    ) {
                        Icon(
                            Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Clear category",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SectionCard {
                SectionCardHeader("Log destinations", Icons.Default.ManageSearch)
                Text(
                    text = "Pick the channel each event type is written to. Leaving one unset " +
                        "disables logging for that event.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val visibleTypes = logTypesFor(typeFilter)
                if (visibleTypes.isEmpty()) {
                    EmptyState("No event types in this category.", icon = Icons.Default.ManageSearch)
                } else {
                    visibleTypes.forEach { type ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    type.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = type.label,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                            Text(
                                text = type.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DiscordSelectorSingle(
                                kind = SelectorKind.Channel,
                                options = channelOptions,
                                placeholder = "Not logged",
                                selectedId = state.channelFor(type),
                                onSelect = { viewModel.setChannel(type, it) },
                            )
                        }
                    }
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

    pendingClearCategory?.let { category ->
        ConfirmDialog(
            title = "Clear ${category.label}?",
            message = "Every log type in this category loses its destination channel.",
            confirmLabel = "Clear category",
            onConfirm = { viewModel.clearCategory(category) },
            onDismiss = { pendingClearCategory = null },
        )
    }
}

/** Ignored and active channel lists, each with its own header, summary, and actions. */
@Composable
private fun IgnoredChannelsSection(
    search: String,
    onSearchChange: (String) -> Unit,
    ignored: List<TextChannelLite>,
    activeTotal: Int,
    active: List<TextChannelLite>,
    onToggle: (String) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Ignored channels", Icons.Default.Block)
        Text(
            text = "Events in these channels are never logged. " +
                "${ignored.size} ignored, $activeTotal logged.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ignored.isEmpty()) {
            EmptyState("No channels ignored.", icon = Icons.Default.Block)
        } else {
            ignored.forEach { channel ->
                ListItem(
                    headlineContent = { Text("#${channel.name}") },
                    trailingContent = {
                        TextButton(onClick = { onToggle(channel.id) }) {
                            Text("Log")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    SectionCard {
        SectionCardHeader("Active channels", Icons.Default.Tag)
        MewdekoTextField(
            value = search,
            onValueChange = onSearchChange,
            label = "Search channels",
            placeholder = "Channel name",
        )
        if (active.isEmpty()) {
            EmptyState("No matching channels.", icon = Icons.Default.Tag)
        } else {
            active.forEach { channel ->
                ListItem(
                    headlineContent = { Text("#${channel.name}") },
                    trailingContent = {
                        TextButton(onClick = { onToggle(channel.id) }) {
                            Text("Ignore")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
