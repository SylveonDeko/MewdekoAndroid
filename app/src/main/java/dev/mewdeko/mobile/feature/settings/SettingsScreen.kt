package dev.mewdeko.mobile.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/**
 * Locales the bot ships response strings for. The empty id means "bot default", mirroring
 * the dashboard's language selector.
 */
private val languageOptions = listOf(
    SelectorOption("", "Bot default"),
    SelectorOption("en-US", "English"),
    SelectorOption("ar", "العربية (Arabic)"),
    SelectorOption("cs-CZ", "Čeština (Czech)"),
    SelectorOption("da-DK", "Dansk (Danish)"),
    SelectorOption("de-DE", "Deutsch (German)"),
    SelectorOption("es-ES", "Español (Spanish)"),
    SelectorOption("fr-FR", "Français (French)"),
    SelectorOption("he-IL", "עברית (Hebrew)"),
    SelectorOption("hi-IN", "हिन्दी (Hindi)"),
    SelectorOption("hu-HU", "Magyar (Hungarian)"),
    SelectorOption("id-ID", "Bahasa Indonesia"),
    SelectorOption("it-IT", "Italiano (Italian)"),
    SelectorOption("ja-JP", "日本語 (Japanese)"),
    SelectorOption("ko-KR", "한국어 (Korean)"),
    SelectorOption("nb-NO", "Norsk (Norwegian)"),
    SelectorOption("nl-NL", "Nederlands (Dutch)"),
    SelectorOption("pl-PL", "Polski (Polish)"),
    SelectorOption("pt-BR", "Português (Brazil)"),
    SelectorOption("ro-RO", "Română (Romanian)"),
    SelectorOption("ru-RU", "Русский (Russian)"),
    SelectorOption("sr-RS", "Srpski (Serbian)"),
    SelectorOption("sv-SE", "Svenska (Swedish)"),
    SelectorOption("tr-TR", "Türkçe (Turkish)"),
    SelectorOption("uk-UA", "Українська (Ukrainian)"),
    SelectorOption("zh-CN", "简体中文 (Chinese, Simplified)"),
    SelectorOption("zh-TW", "繁體中文 (Chinese, Traditional)"),
    SelectorOption("owo", "OwO"),
)

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

    var showUnsavedDialog by remember { mutableStateOf(false) }
    var pendingBackAfterSave by remember { mutableStateOf(false) }

    val guardedBack: () -> Unit = {
        if (state.hasUnsaved) showUnsavedDialog = true else onBack()
    }

    BackHandler(enabled = state.hasUnsaved) { showUnsavedDialog = true }

    LaunchedEffect(state.isSaving, state.hasUnsaved, pendingBackAfterSave) {
        if (pendingBackAfterSave && !state.isSaving) {
            pendingBackAfterSave = false
            if (!state.hasUnsaved) onBack()
        }
    }

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes before leaving this screen?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    pendingBackAfterSave = true
                    viewModel.save()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedDialog = false
                    onBack()
                }) {
                    Text("Discard")
                }
            },
        )
    }

    FeatureScaffold(
        title = "Settings",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = guardedBack,
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
            MewdekoTextField(
                value = state.warnExpireHours.toString(),
                onValueChange = { raw ->
                    viewModel.setWarnExpireHours(raw.filter(Char::isDigit).take(9).toIntOrNull() ?: 0)
                },
                label = "Warning expiry (hours)",
                numeric = true,
                supportingText = "0 = never",
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Language),
                options = languageOptions,
                placeholder = "Bot default",
                label = "Language",
                selectedId = state.locale,
                onSelect = { viewModel.setLocale(it.orEmpty()) },
            )
        }

        SectionCard {
            SectionCardHeader("Mute role", Icons.AutoMirrored.Filled.VolumeOff)
            MewdekoTextField(
                value = state.muteRoleName,
                onValueChange = { viewModel.setMuteRoleName(it.take(100)) },
                label = "Mute role name",
                placeholder = "Muted",
                supportingText = "Applied by mute commands and punishments. Created automatically if missing.",
            )
            SwitchRow(
                title = "Remove all other roles while muted",
                checked = state.removeRolesOnMute,
                onCheckedChange = viewModel::setRemoveRolesOnMute,
            )
        }

        SectionCard {
            SectionCardHeader("Chat utilities", Icons.Default.Forum)
            SwitchRow(
                title = "Message sniping",
                subtitle = "Let members recover recently deleted or edited messages",
                checked = state.snipeset,
                onCheckedChange = viewModel::setSnipeset,
            )
            SwitchRow(
                title = "Message link previews",
                subtitle = "Show the contents of Discord message links when posted",
                checked = state.previewLinks,
                onCheckedChange = viewModel::setPreviewLinks,
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
