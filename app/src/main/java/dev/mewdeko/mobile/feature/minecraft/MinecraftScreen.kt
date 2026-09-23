package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** The synthetic channel option id used to clear a channel selection. */
private const val ClearChannelId = "0"

private val Tabs = listOf(
    SectionTab("servers", "Servers", Icons.Default.Widgets),
    SectionTab("history", "History", Icons.Default.History),
    SectionTab("console", "Console", Icons.Default.Terminal),
    SectionTab("add", "Add", Icons.Default.Add),
)

/** Minecraft server status tracking, event relays, and RCON. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MinecraftScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: MinecraftViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf("servers") }
    var showRconSettings by remember { mutableStateOf<MinecraftServer?>(null) }
    var pendingRemove by remember { mutableStateOf<MinecraftServer?>(null) }
    var pendingRevokeKey by remember { mutableStateOf<MinecraftServer?>(null) }
    var pendingRegenerateKey by remember { mutableStateOf<MinecraftServer?>(null) }

    val selected = state.servers.firstOrNull { it.name == state.selectedServer }
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }
    val clearableChannelOptions = listOf(SelectorOption(ClearChannelId, "None")) + channelOptions

    FeatureScaffold(
        title = "Minecraft",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.queryAll() }) {
                Icon(
                    Icons.Default.Sync,
                    contentDescription = "Query all servers",
                    tint = if (state.queryingAll) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        },
        floatingActionButton = {
            if (activeTab == "servers" && state.servers.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { activeTab = "add" },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add server") },
                )
            }
        },
    ) {
        SectionTabs(tabs = Tabs, selectedId = activeTab, onSelect = { activeTab = it })

        when (activeTab) {
            "history" -> MinecraftHistoryTab(
                servers = state.servers,
                state = state,
                onSelectServer = viewModel::selectHistoryServer,
                onSelectHours = { hours ->
                    state.historyServer?.let { viewModel.loadHistory(it, hours) }
                },
            )

            "console" -> MinecraftConsoleTab(
                servers = state.servers,
                state = state,
                onSelectServer = viewModel::selectConsoleServer,
                onSend = { command ->
                    state.consoleServer?.let { viewModel.sendRcon(it, command) }
                },
            )

            "add" -> MinecraftAddTab(
                channelOptions = channelOptions,
                onAdd = { name, address, port, type, queryPort, watchChannelId, watchInterval, watchMode, embed ->
                    viewModel.addServer(
                        name, address, port, type, queryPort,
                        watchChannelId, watchInterval, watchMode, embed,
                    )
                    activeTab = "servers"
                },
            )

            else -> MinecraftServersTab(
                state = state,
                selected = selected,
                clearableChannelOptions = clearableChannelOptions,
                viewModel = viewModel,
                onShowRconSettings = { showRconSettings = it },
                onRemove = { pendingRemove = it },
                onRevokeKey = { pendingRevokeKey = it },
                onRegenerateKey = { pendingRegenerateKey = it },
                onAddServer = { activeTab = "add" },
            )
        }
    }

    showRconSettings?.let { server ->
        var enabled by remember(server.name) { mutableStateOf(server.rconEnabled) }
        var rconPort by remember(server.name) { mutableIntStateOf(server.rconPort) }
        var password by remember(server.name) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRconSettings = null },
            title = { Text("RCON settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SwitchRow(
                        title = "RCON enabled",
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                    )
                    SliderRow(
                        label = "RCON port",
                        value = rconPort.toFloat(),
                        onValueChange = { rconPort = it.toInt() },
                        valueRange = 1024f..65535f,
                        valueLabel = "$rconPort",
                    )
                    MewdekoTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        supportingText = if (server.hasRconPassword) {
                            "Leave blank to keep the current password."
                        } else {
                            "Required to enable RCON."
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setRcon(
                            server.name,
                            enabled,
                            rconPort,
                            password.takeIf { it.isNotBlank() },
                        )
                        showRconSettings = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showRconSettings = null }) { Text("Cancel") }
            },
        )
    }

    pendingRegenerateKey?.let { server ->
        ConfirmDialog(
            title = if (server.hasPluginKey) "Replace plugin key?" else "Generate plugin key?",
            message = if (server.hasPluginKey) {
                "This replaces the existing key. The old key will stop working."
            } else {
                "The companion plugin will use this key to connect."
            },
            confirmLabel = if (server.hasPluginKey) "Replace" else "Generate",
            destructive = server.hasPluginKey,
            onConfirm = { viewModel.generatePluginKey(server.name) },
            onDismiss = { pendingRegenerateKey = null },
        )
    }

    pendingRevokeKey?.let { server ->
        ConfirmDialog(
            title = "Revoke plugin key?",
            message = "The companion plugin on ${server.name} will disconnect.",
            confirmLabel = "Revoke",
            onConfirm = { viewModel.revokePluginKey(server.name) },
            onDismiss = { pendingRevokeKey = null },
        )
    }

    state.pluginKey?.let { key ->
        val clipboard = LocalClipboardManager.current
        val wsUrl = state.pluginWsUrl ?: "ws://<your-dashboard-host>/api/mc-bridge/ws"
        AlertDialog(
            onDismissRequest = viewModel::clearPluginKey,
            title = { Text("Plugin key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste these into the companion plugin's config.yml. The key is shown once.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("api-key", style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = key,
                                style = MonospaceStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(key)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy key")
                        }
                    }
                    Text("WebSocket bridge URL", style = MaterialTheme.typography.labelMedium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = wsUrl,
                                style = MonospaceStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                        IconButton(onClick = { clipboard.setText(AnnotatedString(wsUrl)) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL")
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = viewModel::clearPluginKey) { Text("Done") }
            },
        )
    }

    pendingRemove?.let { server ->
        ConfirmDialog(
            title = "Remove ${server.name}?",
            message = "Status tracking and event relays for this server stop.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeServer(server.name) },
            onDismiss = { pendingRemove = null },
        )
    }
}

/** The servers list plus the detail sections for the selected server. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MinecraftServersTab(
    state: MinecraftState,
    selected: MinecraftServer?,
    clearableChannelOptions: List<SelectorOption>,
    viewModel: MinecraftViewModel,
    onShowRconSettings: (MinecraftServer) -> Unit,
    onRemove: (MinecraftServer) -> Unit,
    onRevokeKey: (MinecraftServer) -> Unit,
    onRegenerateKey: (MinecraftServer) -> Unit,
    onAddServer: () -> Unit,
) {
    if (state.servers.isEmpty()) {
        SectionCard {
            EmptyState(
                message = "No Minecraft servers tracked yet.",
                icon = Icons.Default.Widgets,
            )
            TextButton(onClick = onAddServer, modifier = Modifier.fillMaxWidth()) {
                Text("Add a server")
            }
        }
        return
    }

    SectionCard(contentPadding = 12) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.servers.forEach { server ->
                FilterChip(
                    selected = server.name == state.selectedServer,
                    onClick = { viewModel.selectServer(server.name) },
                    label = { Text(server.name) },
                    leadingIcon = {
                        val online = state.status(server.name)?.isOnline
                        Surface(
                            shape = CircleShape,
                            color = when (online) {
                                true -> MaterialTheme.colorScheme.primary
                                false -> MaterialTheme.colorScheme.error
                                null -> MaterialTheme.colorScheme.outline
                            },
                            modifier = Modifier.size(8.dp),
                        ) {}
                    },
                )
            }
        }
    }

    val server = selected ?: return
    val live = state.status(server.name)

    SectionCard {
        SectionCardHeader(
            title = server.name,
            icon = Icons.Default.Widgets,
            trailing = {
                Row {
                    IconButton(onClick = { viewModel.refreshStatus(server.name) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Query now")
                    }
                    IconButton(onClick = { onRemove(server) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove server",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
        )
        Text(
            text = "${server.address}:${server.port}",
            style = MonospaceStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TagChip(server.type.label)
            if (server.rconEnabled) TagChip("RCON")
            if (server.hasPluginKey) TagChip("Plugin linked")
        }
        SwitchRow(
            title = "Default server",
            subtitle = "Used when a command does not name a server",
            checked = server.isDefault,
            onCheckedChange = { viewModel.updateServer(server.name, isDefault = it) },
        )

        if (live == null) {
            EmptyState("No status yet. Tap the refresh icon to query the server.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Status",
                    value = if (live.isOnline) "Online" else "Offline",
                    tint = if (live.isOnline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Players",
                    value = "${live.playersOnline}/${live.playersMax}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Latency",
                    value = "${live.latency} ms",
                    modifier = Modifier.weight(1f),
                )
            }
            if (live.motd.isNotBlank()) {
                Text(live.motd, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                live.version.takeIf { it.isNotBlank() }?.let { TagChip(it) }
                live.software?.takeIf { it.isNotBlank() }?.let { TagChip(it) }
                live.map?.takeIf { it.isNotBlank() }?.let { TagChip("Map: $it") }
                live.gameMode?.takeIf { it.isNotBlank() }?.let { TagChip(it) }
            }
        }
    }

    MinecraftOnlinePlayersCard(
        players = live?.playerList.orEmpty(),
        rconEnabled = server.rconEnabled,
        whitelist = state.whitelist,
        onAddToWhitelist = { player -> viewModel.whitelistAdd(server.name, player) },
        onRemoveFromWhitelist = { player -> viewModel.whitelistRemove(server.name, player) },
    )

    if (server.rconEnabled) {
        MinecraftWhitelistCard(
            whitelist = state.whitelist,
            loading = state.whitelistLoading,
            onRefresh = { viewModel.loadWhitelist(server.name) },
            onAdd = { player -> viewModel.whitelistAdd(server.name, player) },
            onRemove = { player -> viewModel.whitelistRemove(server.name, player) },
        )
    }

    MinecraftPluginsCard(live?.plugins.orEmpty())

    SectionCard {
        SectionCardHeader("Configuration", Icons.Default.Settings)
        var address by remember(server.name) { mutableStateOf(server.address) }
        var port by remember(server.name) { mutableStateOf(server.port.toString()) }
        var type by remember(server.name) { mutableStateOf(server.type) }
        var queryPort by remember(server.name) { mutableStateOf(server.queryPort.toString()) }

        MewdekoTextField(value = address, onValueChange = { address = it }, label = "Address")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            McServerType.entries.forEach { entry ->
                FilterChip(
                    selected = type == entry,
                    onClick = { type = entry },
                    label = { Text(entry.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        MewdekoTextField(
            value = port,
            onValueChange = { port = it.filter(Char::isDigit) },
            label = "Port",
            numeric = true,
        )
        MewdekoTextField(
            value = queryPort,
            onValueChange = { queryPort = it.filter(Char::isDigit) },
            label = "Query port (0 = same as game port)",
            numeric = true,
        )
        Button(
            onClick = {
                viewModel.updateServer(
                    server.name,
                    address = address.trim(),
                    port = port.toIntOrNull() ?: server.port,
                    type = type.raw,
                    queryPort = queryPort.toIntOrNull() ?: 0,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save configuration") }
    }

    SectionCard {
        SectionCardHeader("Status watching", Icons.Default.Tune)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = clearableChannelOptions,
            placeholder = "Not watched",
            label = "Post status in",
            selectedId = server.watchChannelId ?: ClearChannelId,
            onSelect = { id ->
                viewModel.setWatch(
                    server.name,
                    id?.takeIf { it != ClearChannelId },
                    null,
                    null,
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            McWatchMode.entries.forEach { mode ->
                FilterChip(
                    selected = server.watch == mode,
                    onClick = { viewModel.setWatch(server.name, server.watchChannelId, null, mode.raw) },
                    label = { Text(mode.label) },
                )
            }
        }
        var pendingInterval by remember(server.name, server.watchInterval) {
            mutableIntStateOf(server.watchInterval)
        }
        SliderRow(
            label = "Refresh interval",
            value = pendingInterval.toFloat(),
            onValueChange = { pendingInterval = it.toInt() },
            onValueChangeFinished = {
                viewModel.setWatch(server.name, server.watchChannelId, pendingInterval, null)
            },
            valueRange = 1f..60f,
            valueLabel = "${pendingInterval}m",
        )
    }

    MinecraftCustomEmbedCard(server) { template -> viewModel.setCustomEmbed(server.name, template) }
    MinecraftAlertCard(
        title = "Server online alert",
        icon = Icons.Default.CheckCircle,
        template = server.customOnlineMessage,
        onSave = { template -> viewModel.setOnlineMessage(server.name, template) },
    )
    MinecraftAlertCard(
        title = "Server offline alert",
        icon = Icons.Default.CloudOff,
        template = server.customOfflineMessage,
        onSave = { template -> viewModel.setOfflineMessage(server.name, template) },
    )

    SectionCard {
        SectionCardHeader("Event relays", Icons.Default.Tune)
        Text(
            text = "These require the companion plugin installed on the server.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = clearableChannelOptions,
            placeholder = "Not relayed",
            label = "In-game chat",
            selectedId = server.chatChannelId ?: ClearChannelId,
            onSelect = { id -> viewModel.updateServer(server.name, chatChannelId = id) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = clearableChannelOptions,
            placeholder = "Not relayed",
            label = "Joins and leaves",
            selectedId = server.joinLeaveChannelId ?: ClearChannelId,
            onSelect = { id -> viewModel.updateServer(server.name, joinLeaveChannelId = id) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = clearableChannelOptions,
            placeholder = "Not relayed",
            label = "Deaths",
            selectedId = server.deathChannelId ?: ClearChannelId,
            onSelect = { id -> viewModel.updateServer(server.name, deathChannelId = id) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = clearableChannelOptions,
            placeholder = "Not relayed",
            label = "Advancements",
            selectedId = server.advancementChannelId ?: ClearChannelId,
            onSelect = { id -> viewModel.updateServer(server.name, advancementChannelId = id) },
        )
    }

    MinecraftEventTemplatesCard(server) { templates -> viewModel.setEventTemplates(server.name, templates) }

    SectionCard {
        SectionCardHeader("RCON", Icons.Default.Terminal)
        SwitchRow(
            title = "RCON enabled",
            checked = server.rconEnabled,
            onCheckedChange = { onShowRconSettings(server) },
        )
        if (server.rconEnabled) {
            Text(
                text = "Port ${server.rconPort}" +
                    if (server.hasRconPassword) " · password set" else " · no password",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(
            onClick = { onShowRconSettings(server) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("RCON settings") }
    }

    SectionCard {
        SectionCardHeader("Plugin API key", Icons.Default.Key)
        Text(
            text = "Lets the companion server plugin connect back to the bot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (server.hasPluginKey) "Key active" else "No key configured",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onRegenerateKey(server) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Key, contentDescription = null)
                Text(
                    text = if (server.hasPluginKey) "Regenerate" else "Generate",
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (server.hasPluginKey) {
                OutlinedButton(
                    onClick = { onRevokeKey(server) },
                    modifier = Modifier.weight(1f),
                ) { Text("Revoke") }
            }
        }
    }
}
