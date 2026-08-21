package dev.mewdeko.mobile.feature.customvoice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

private val Tabs = listOf(
    SectionTab("settings", "Settings", Icons.Default.Tune),
    SectionTab("channels", "Live", Icons.Default.VolumeUp),
)

/** User-owned temporary voice channels. */
@Composable
fun CustomVoiceScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: CustomVoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingDisable by remember { mutableStateOf(false) }
    var pendingCleanup by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<CustomVoiceChannel?>(null) }

    FeatureScaffold(
        title = "Custom Voice",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { pendingCleanup = true }) {
                Icon(Icons.Default.CleaningServices, contentDescription = "Clean up idle channels")
            }
        },
        floatingActionButton = {
            if (state.hasUnsavedConfig) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::save,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text("Save changes") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Mic)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(
                    label = "Channels",
                    value = "${state.statistics?.totalChannels ?: state.channels.size}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Active",
                    value = "${state.statistics?.activeChannels ?: 0}",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "Locked",
                    value = "${state.statistics?.lockedChannels ?: 0}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        if (state.section == "channels") {
            SectionCard {
                SectionCardHeader("Live channels", Icons.Default.VolumeUp)
                if (state.channels.isEmpty()) {
                    EmptyState("No temporary channels right now.", icon = Icons.Default.VolumeUp)
                } else {
                    state.channels.forEach { channel ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = channel.channelId,
                                        style = MonospaceStyle,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        channel.ownerId?.let { TagChip("Owner $it") }
                                        channel.lastActive?.let {
                                            TagChip("Active ${it.relativeToNow()}")
                                        }
                                        if (channel.allowedUsers.isNotEmpty()) {
                                            TagChip("${channel.allowedUsers.size} allowed")
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.updateChannel(
                                            channel.channelId,
                                            isLocked = !channel.isLocked,
                                        )
                                    },
                                ) {
                                    Icon(
                                        if (channel.isLocked) Icons.Default.Lock
                                        else Icons.Default.LockOpen,
                                        contentDescription = "Toggle lock",
                                        tint = if (channel.isLocked) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.updateChannel(
                                            channel.channelId,
                                            keepAlive = !channel.keepAlive,
                                        )
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.PushPin,
                                        contentDescription = "Toggle keep alive",
                                        tint = if (channel.keepAlive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                                IconButton(onClick = { pendingDelete = channel }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete channel",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return@FeatureScaffold
        }

        SectionCard {
            SectionCardHeader("Hub", Icons.Default.Mic)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.VolumeUp),
                options = state.voiceChannels.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick the hub channel",
                label = "Join-to-create channel",
                selectedId = state.config.hubVoiceChannelId.takeIf { it != "0" },
                onSelect = { id -> viewModel.edit { it.copy(hubVoiceChannelId = id ?: "0") } },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.VolumeUp),
                options = state.categories.map { SelectorOption(it.id, it.name) },
                placeholder = "Same category as hub",
                label = "Create channels in",
                selectedId = state.config.channelCategoryId,
                onSelect = { id -> viewModel.edit { it.copy(channelCategoryId = id) } },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.availableRoles.map { SelectorOption(it.id, it.name) },
                placeholder = "No admin role",
                label = "Voice admin role",
                selectedId = state.config.customVoiceAdminRoleId,
                onSelect = { id -> viewModel.edit { it.copy(customVoiceAdminRoleId = id) } },
            )
        }

        SectionCard {
            SectionCardHeader("Defaults", Icons.Default.Tune)
            MewdekoTextField(
                value = state.config.defaultNameFormat,
                onValueChange = { value -> viewModel.edit { it.copy(defaultNameFormat = value) } },
                label = "Name format",
                supportingText = "Use {username} for the owner's name.",
            )
            SliderRow(
                label = "Default user limit",
                value = state.config.defaultUserLimit.toFloat(),
                onValueChange = { value ->
                    viewModel.edit { it.copy(defaultUserLimit = value.toInt()) }
                },
                valueRange = 0f..99f,
                valueLabel = if (state.config.defaultUserLimit == 0) "Unlimited"
                else "${state.config.defaultUserLimit}",
            )
            SliderRow(
                label = "Default bitrate",
                value = state.config.defaultBitrate.toFloat(),
                onValueChange = { value ->
                    viewModel.edit { it.copy(defaultBitrate = (value / 1000).toInt() * 1000) }
                },
                valueRange = 8000f..384000f,
                valueLabel = "${state.config.defaultBitrate / 1000} kbps",
            )
        }

        SectionCard {
            SectionCardHeader("Lifecycle", Icons.Default.Tune)
            SwitchRow(
                title = "Delete when empty",
                checked = state.config.deleteWhenEmpty,
                onCheckedChange = { value -> viewModel.edit { it.copy(deleteWhenEmpty = value) } },
            )
            SliderRow(
                label = "Empty timeout",
                value = state.config.emptyChannelTimeout.toFloat(),
                onValueChange = { value ->
                    viewModel.edit { it.copy(emptyChannelTimeout = value.toInt()) }
                },
                valueRange = 0f..60f,
                valueLabel = "${state.config.emptyChannelTimeout}m",
            )
            SwitchRow(
                title = "Allow multiple channels per member",
                checked = state.config.allowMultipleChannels,
                onCheckedChange = { value ->
                    viewModel.edit { it.copy(allowMultipleChannels = value) }
                },
            )
            SwitchRow(
                title = "Remember member preferences",
                checked = state.config.persistUserPreferences,
                onCheckedChange = { value ->
                    viewModel.edit { it.copy(persistUserPreferences = value) }
                },
            )
            SwitchRow(
                title = "Grant permissions automatically",
                checked = state.config.autoPermission,
                onCheckedChange = { value -> viewModel.edit { it.copy(autoPermission = value) } },
            )
        }

        SectionCard {
            SectionCardHeader("Member permissions", Icons.Default.Tune)
            SwitchRow(
                title = "Rename their channel",
                checked = state.config.allowNameCustomization,
                onCheckedChange = { value ->
                    viewModel.edit { it.copy(allowNameCustomization = value) }
                },
            )
            SwitchRow(
                title = "Change the user limit",
                checked = state.config.allowUserLimitCustomization,
                onCheckedChange = { value ->
                    viewModel.edit { it.copy(allowUserLimitCustomization = value) }
                },
            )
            SwitchRow(
                title = "Change the bitrate",
                checked = state.config.allowBitrateCustomization,
                onCheckedChange = { value ->
                    viewModel.edit { it.copy(allowBitrateCustomization = value) }
                },
            )
            SwitchRow(
                title = "Lock their channel",
                checked = state.config.allowLocking,
                onCheckedChange = { value -> viewModel.edit { it.copy(allowLocking = value) } },
            )
            SwitchRow(
                title = "Manage who can join",
                checked = state.config.allowUserManagement,
                onCheckedChange = { value ->
                    viewModel.edit { it.copy(allowUserManagement = value) }
                },
            )
            SliderRow(
                label = "Maximum user limit",
                value = state.config.maxUserLimit.toFloat(),
                onValueChange = { value -> viewModel.edit { it.copy(maxUserLimit = value.toInt()) } },
                valueRange = 1f..99f,
                valueLabel = "${state.config.maxUserLimit}",
            )
            SliderRow(
                label = "Maximum bitrate",
                value = state.config.maxBitrate.toFloat(),
                onValueChange = { value ->
                    viewModel.edit { it.copy(maxBitrate = (value / 1000).toInt() * 1000) }
                },
                valueRange = 8000f..384000f,
                valueLabel = "${state.config.maxBitrate / 1000} kbps",
            )
        }

        SectionCard {
            OutlinedButton(
                onClick = { pendingDisable = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Disable custom voice",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }

    if (pendingDisable) {
        ConfirmDialog(
            title = "Disable custom voice?",
            message = "The hub stops creating channels. Existing temporary channels are left alone.",
            confirmLabel = "Disable",
            onConfirm = viewModel::disable,
            onDismiss = { pendingDisable = false },
        )
    }

    if (pendingCleanup) {
        ConfirmDialog(
            title = "Clean up idle channels?",
            message = "Every temporary channel idle for more than 24 hours is deleted.",
            confirmLabel = "Clean up",
            onConfirm = { viewModel.cleanup(24) },
            onDismiss = { pendingCleanup = false },
        )
    }

    pendingDelete?.let { channel ->
        ConfirmDialog(
            title = "Delete channel?",
            message = "The temporary channel ${channel.channelId} is removed immediately.",
            onConfirm = { viewModel.deleteChannel(channel.channelId) },
            onDismiss = { pendingDelete = null },
        )
    }
}
