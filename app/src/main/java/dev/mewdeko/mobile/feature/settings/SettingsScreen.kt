package dev.mewdeko.mobile.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Per-guild bot configuration. */
@Composable
fun SettingsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) }
    val roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) }

    FeatureScaffold(
        title = "Settings",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            if (state.hasUnsaved) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::save,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text(if (state.isSaving) "Saving…" else "Save changes") },
                )
            }
        },
    ) {
        SectionCard {
            SectionCardHeader("Bot configuration", Icons.Default.Tune)
            MewdekoTextField(
                value = state.prefix,
                onValueChange = viewModel::setPrefix,
                label = "Command prefix",
                placeholder = ".",
            )
            MewdekoTextField(
                value = state.currencyEmoji,
                onValueChange = viewModel::setCurrencyEmoji,
                label = "Currency emoji",
                placeholder = "💰",
            )
            SwitchRow(
                title = "Delete message after command",
                subtitle = "Removes the invoking message once a command runs",
                checked = state.deleteOnCommand,
                onCheckedChange = viewModel::setDeleteOnCommand,
            )
        }

        SectionCard {
            SectionCardHeader("Channels and roles", Icons.Default.Tag)
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "No channel",
                label = "Command log channel",
                selectedId = state.commandLogChannelId,
                onSelect = viewModel::setCommandLogChannel,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "No role",
                label = "Staff role",
                selectedId = state.staffRoleId,
                onSelect = viewModel::setStaffRole,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "No channel",
                label = "Warning log channel",
                selectedId = state.warningLogChannelId,
                onSelect = viewModel::setWarningLogChannel,
            )
            SliderRow(
                label = "Warning expiry",
                value = state.warnExpireHours.toFloat(),
                onValueChange = { viewModel.setWarnExpireHours((it / 24).toInt() * 24) },
                valueRange = 0f..8760f,
                valueLabel = if (state.warnExpireHours == 0) "Never"
                else "${state.warnExpireHours / 24}d",
            )
        }

        SectionCard {
            SectionCardHeader("Default messages", Icons.Default.ChatBubble)
            Text(
                text = "Default AFK message",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbedMessageEditor(
                message = state.afkMessage,
                onMessageChange = viewModel::setAfkMessage,
            )
            Text(
                text = "Stream notification template",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EmbedMessageEditor(
                message = state.streamMessage,
                onMessageChange = viewModel::setStreamMessage,
            )
        }
    }
}
