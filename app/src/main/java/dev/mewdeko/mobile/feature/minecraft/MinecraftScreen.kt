package dev.mewdeko.mobile.feature.minecraft

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
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
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs

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

    var showAdd by remember { mutableStateOf(false) }
    var showRcon by remember { mutableStateOf(false) }
    var showRconSettings by remember { mutableStateOf<MinecraftServer?>(null) }
    var pendingRemove by remember { mutableStateOf<MinecraftServer?>(null) }

    val selected = state.servers.firstOrNull { it.name == state.selectedServer }
    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Minecraft",
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
                text = { Text("Add server") },
            )
        },
    ) {
        if (state.servers.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No Minecraft servers tracked yet.",
                    icon = Icons.Default.Widgets,
                )
            }
            return@FeatureScaffold
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

        selected?.let { server ->
            val live = state.status(server.name)

            SectionCard {
                SectionCardHeader(
                    title = server.name,
                    icon = Icons.Default.Widgets,
                    trailing = {
                        Row {
                            IconButton(onClick = { viewModel.refreshStatus(server.name) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Ping server")
                            }
                            IconButton(onClick = { pendingRemove = server }) {
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
                    if (server.isDefault) TagChip("Default")
                    if (server.rconEnabled) TagChip("RCON")
                    if (server.hasPluginKey) TagChip("Plugin linked")
                }

                if (live == null) {
                    EmptyState("No status yet. Tap refresh to ping the server.")
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
                    if (live.playerList.isNotEmpty()) {
                        Text(
                            text = "Online now",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            live.playerList.forEach { TagChip(it) }
                        }
                    }
                }
            }

            SectionCard {
                SectionCardHeader("Status watching", Icons.Default.Tune)
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Not watched",
                    label = "Post status in",
                    selectedId = server.watchChannelId,
                    onSelect = { viewModel.setWatch(server.name, it, null, null) },
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
                            onClick = { viewModel.setWatch(server.name, null, null, mode.raw) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                SliderRow(
                    label = "Refresh interval",
                    value = server.watchInterval.toFloat(),
                    onValueChange = { },
                    onValueChangeFinished = { },
                    valueRange = 1f..60f,
                    valueLabel = "${server.watchInterval}m",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 5, 15, 30).forEach { minutes ->
                        TextButton(
                            onClick = { viewModel.setWatch(server.name, null, minutes, null) },
                        ) { Text("${minutes}m") }
                    }
                }
            }

            SectionCard {
                SectionCardHeader("Event relays", Icons.Default.Tune)
                Text(
                    text = "These require the companion plugin installed on the server.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Not relayed",
                    label = "In-game chat",
                    selectedId = server.chatChannelId,
                    onSelect = { viewModel.updateServer(server.name, chatChannelId = it) },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Not relayed",
                    label = "Joins and leaves",
                    selectedId = server.joinLeaveChannelId,
                    onSelect = { viewModel.updateServer(server.name, joinLeaveChannelId = it) },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Not relayed",
                    label = "Deaths",
                    selectedId = server.deathChannelId,
                    onSelect = { viewModel.updateServer(server.name, deathChannelId = it) },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    options = channelOptions,
                    placeholder = "Not relayed",
                    label = "Advancements",
                    selectedId = server.advancementChannelId,
                    onSelect = { viewModel.updateServer(server.name, advancementChannelId = it) },
                )
                SwitchRow(
                    title = "Default server",
                    subtitle = "Used when a command does not name a server",
                    checked = server.isDefault,
                    onCheckedChange = { viewModel.updateServer(server.name, isDefault = it) },
                )
                OutlinedButton(
                    onClick = { viewModel.generatePluginKey(server.name) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Key, contentDescription = null)
                    Text(
                        text = if (server.hasPluginKey) "Regenerate plugin key"
                        else "Generate plugin key",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }

            SectionCard {
                SectionCardHeader("RCON", Icons.Default.Terminal)
                SwitchRow(
                    title = "RCON enabled",
                    checked = server.rconEnabled,
                    onCheckedChange = { showRconSettings = server },
                )
                if (server.rconEnabled) {
                    Text(
                        text = "Port ${server.rconPort}" +
                            if (server.hasRconPassword) " · password set" else " · no password",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { showRcon = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null)
                        Text("Run a command", modifier = Modifier.padding(start = 6.dp))
                    }
                }
                OutlinedButton(
                    onClick = { showRconSettings = server },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("RCON settings") }
            }
        }
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var address by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(McServerType.JAVA) }
        var port by remember { mutableStateOf(McServerType.JAVA.defaultPort.toString()) }
        var queryPort by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add Minecraft server") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MewdekoTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                        placeholder = "survival",
                    )
                    MewdekoTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Address",
                        placeholder = "mc.example.com",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        McServerType.entries.forEach { entry ->
                            FilterChip(
                                selected = type == entry,
                                onClick = {
                                    type = entry
                                    port = entry.defaultPort.toString()
                                },
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
                        label = "Query port (optional)",
                        numeric = true,
                        supportingText = "Leave at 0 unless the server enables the query protocol.",
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addServer(
                            name = name.trim(),
                            address = address.trim(),
                            port = port.toIntOrNull() ?: type.defaultPort,
                            type = type,
                            queryPort = queryPort.toIntOrNull() ?: 0,
                        )
                        showAdd = false
                    },
                    enabled = name.isNotBlank() && address.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
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

    if (showRcon && selected != null) {
        var command by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showRcon = false; viewModel.clearRconOutput() },
            title = { Text("RCON on ${selected.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MewdekoTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = "Command",
                        placeholder = "list",
                    )
                    state.rconOutput?.let { output ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = output,
                                style = MonospaceStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 12,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.sendRcon(selected.name, command.trim()) },
                    enabled = command.isNotBlank(),
                ) { Text("Run") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRcon = false; viewModel.clearRconOutput() },
                ) { Text("Close") }
            },
        )
    }

    state.pluginKey?.let { key ->
        AlertDialog(
            onDismissRequest = viewModel::clearPluginKey,
            title = { Text("Plugin key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Paste this into the companion plugin's config. It is shown once.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = key,
                            style = MonospaceStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(8.dp),
                        )
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
