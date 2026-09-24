package dev.mewdeko.mobile.feature.twitch

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TabLevel
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val MainTabs = listOf(
    SectionTab(TwitchSections.SETUP, "Setup", Icons.Default.Power),
    SectionTab(TwitchSections.COMMANDS, "Commands", Icons.Default.Terminal),
    SectionTab(TwitchSections.EVENTS, "Events", Icons.Default.Bolt),
    SectionTab(TwitchSections.LINKS, "Links", Icons.Default.Link),
)

private val SubTabs = mapOf(
    TwitchSections.SETUP to listOf(
        SectionTab(TwitchSections.CONNECT, "Connect", Icons.Default.Key),
        SectionTab(TwitchSections.BOT_SETTINGS, "Bot Settings", Icons.Default.ChatBubble),
    ),
    TwitchSections.COMMANDS to listOf(
        SectionTab(TwitchSections.CUSTOM, "Custom", Icons.Default.Terminal),
        SectionTab(TwitchSections.TIMERS, "Timers", Icons.Default.Refresh),
        SectionTab(TwitchSections.QUOTES, "Quotes", Icons.Default.ChatBubble),
    ),
    TwitchSections.EVENTS to listOf(
        SectionTab(TwitchSections.ALERTS, "Alerts", Icons.Default.Notifications),
        SectionTab(TwitchSections.LIVE, "Live Tools", Icons.Default.Settings),
    ),
)

/**
 * Twitch Bot: OAuth connection and health, chat bot settings, custom
 * commands, timers, quotes, alerts, channel point actions, live tools, and
 * Discord to Twitch account links.
 */
@Composable
fun TwitchScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: TwitchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    var pendingConfirm by remember { mutableStateOf<TwitchConfirmation?>(null) }

    LaunchedEffect(state.pendingAuthUrl) {
        val url = state.pendingAuthUrl ?: return@LaunchedEffect
        runCatching {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
                .launchUrl(context, url.toUri())
        }.onFailure {
            runCatching { uriHandler.openUri(url) }
        }
        viewModel.authUrlOpened()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onResumed()
    }

    val showSaveFab = state.hasUnsavedSettings &&
        (state.subSection == TwitchSections.BOT_SETTINGS || state.subSection == TwitchSections.ALERTS)

    FeatureScaffold(
        title = "Twitch Bot",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { viewModel.load(refreshing = true) }, enabled = !loadState.isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        },
        floatingActionButton = {
            val newAction = when {
                state.section != TwitchSections.COMMANDS -> null
                state.subSection == TwitchSections.TIMERS -> "New timer" to TwitchEditor.TIMER
                state.subSection == TwitchSections.QUOTES -> "Add quote" to TwitchEditor.QUOTE
                else -> "New command" to TwitchEditor.COMMAND
            }
            if (newAction != null) {
                NewItemFab(label = newAction.first, onClick = { viewModel.startNew(newAction.second) })
            } else if (showSaveFab) {
                ExtendedFloatingActionButton(
                    onClick = { if (!state.isSaving) viewModel.saveSettings() },
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    text = { Text(if (state.isSaving) "Saving..." else "Save changes") },
                )
            }
        },
    ) {
        StatusHeader(state, onConnect = viewModel::connect)

        SectionTabs(tabs = MainTabs, selectedId = state.section, onSelect = viewModel::setSection)
        SubTabs[state.section]?.let { tabs ->
            SectionTabs(
                tabs = tabs,
                selectedId = state.subSection,
                onSelect = viewModel::setSubSection,
                level = TabLevel.Secondary,
            )
        }

        val confirm: (TwitchConfirmation) -> Unit = { pendingConfirm = it }
        when (state.section) {
            TwitchSections.SETUP -> when (state.subSection) {
                TwitchSections.BOT_SETTINGS -> BotSettingsSection(state, viewModel, confirm)
                else -> ConnectSection(state, viewModel)
            }

            TwitchSections.COMMANDS -> when (state.subSection) {
                TwitchSections.TIMERS -> TimersSection(state, viewModel, confirm)
                TwitchSections.QUOTES -> QuotesSection(state, viewModel, confirm)
                else -> CustomCommandsSection(state, viewModel, confirm)
            }

            TwitchSections.EVENTS -> when (state.subSection) {
                TwitchSections.LIVE -> LiveToolsSection(state, viewModel, confirm)
                else -> AlertsSection(state, viewModel, confirm)
            }

            TwitchSections.LINKS -> LinksSection(state, viewModel, confirm)
        }
    }

    when (state.openEditor) {
        TwitchEditor.COMMAND -> TwitchCommandEditor(state, viewModel)
        TwitchEditor.TIMER -> TwitchTimerEditor(state, viewModel)
        TwitchEditor.QUOTE -> TwitchQuoteSheet(state, viewModel)
        TwitchEditor.REDEMPTION -> TwitchRedemptionEditor(state, viewModel)
        null -> Unit
    }

    pendingConfirm?.let { pending ->
        ConfirmDialog(
            title = pending.title,
            message = pending.message,
            confirmLabel = pending.confirmLabel,
            onConfirm = {
                pendingConfirm = null
                pending.onConfirm()
            },
            onDismiss = { pendingConfirm = null },
        )
    }
}

/**
 * The connection summary at the top of every Twitch tab: one card with the
 * overall state as a pill in the header, then one full-width row each for
 * the bot account and the broadcaster channel. Names wrap instead of being
 * cut off, and a missing identity offers its Connect action inline.
 */
@Composable
private fun StatusHeader(state: TwitchState, onConnect: (TwitchOAuthMode) -> Unit) {
    val status = state.status
    val scheme = MaterialTheme.colorScheme
    val (pillText, pillTone) = when {
        status == null -> "Checking" to scheme.onSurfaceVariant
        status.isConfigured -> status.connectionLabel to scheme.primary
        status.hasBotAccount || status.hasChannelAuthorization -> status.connectionLabel to scheme.tertiary
        else -> status.connectionLabel to scheme.error
    }
    SectionCard {
        SectionCardHeader(
            title = "Connection",
            icon = Icons.Default.SignalCellularAlt,
            trailing = { StatePill(text = pillText, tone = pillTone) },
        )
        ConnectionStatusRow(
            icon = Icons.Default.SmartToy,
            label = "Bot account",
            name = status?.botLabel,
            loading = status == null,
            actionLabel = when {
                !state.isBotOwner -> null
                state.connecting == TwitchOAuthMode.BOT -> "Opening..."
                else -> "Connect"
            },
            actionEnabled = state.connecting == null,
            onAction = { onConnect(TwitchOAuthMode.BOT) },
        )
        ConnectionStatusRow(
            icon = Icons.Default.Videocam,
            label = "Channel",
            name = status?.channelLabel,
            loading = status == null,
            actionLabel = if (state.connecting == TwitchOAuthMode.CHANNEL) "Opening..." else "Connect",
            actionEnabled = state.connecting == null,
            onAction = { onConnect(TwitchOAuthMode.CHANNEL) },
        )
        if (status != null && !status.isConfigured) {
            HelpText(
                if (!status.hasBotAccount && !state.isBotOwner) {
                    "The shared bot account is not connected yet. Only a bot owner can connect it; you can still " +
                        "connect your channel."
                } else {
                    "Connect both the bot account and the broadcaster channel to enable modern Twitch chat."
                }
            )
        }
    }
}

/**
 * One identity row in [StatusHeader]: a glyph, the label, and the connected
 * name on its own wrapping line. When [name] is missing the row reads "Not
 * connected" and shows [actionLabel] as a button, if one is given.
 */
@Composable
private fun ConnectionStatusRow(
    icon: ImageVector,
    label: String,
    name: String?,
    loading: Boolean,
    actionLabel: String?,
    actionEnabled: Boolean,
    onAction: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlyphOrb(icon = icon, tint = if (name != null) scheme.primary else scheme.onSurfaceVariant)
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Text(
                text = when {
                    name != null -> "@$name"
                    loading -> "Checking..."
                    else -> "Not connected"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (name != null) scheme.onSurface else scheme.onSurfaceVariant,
            )
        }
        if (name == null && !loading && actionLabel != null) {
            OutlinedButton(onClick = onAction, enabled = actionEnabled) {
                Text(actionLabel, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ConnectSection(state: TwitchState, viewModel: TwitchViewModel) {
    val status = state.status
    SectionCard {
        SectionCardHeader("OAuth setup", Icons.Default.Key)
        HelpText("Use Twitch authorization instead of hand-pasted chat tokens.")

        if (state.isBotOwner) {
            ConnectRow(
                title = "Bot account",
                description = "Grants user:read:chat, user:write:chat, and user:bot. This is a single shared " +
                    "identity used across every server, so only bot owners can (re)authorize it.",
                buttonLabel = when {
                    state.connecting == TwitchOAuthMode.BOT -> "Opening..."
                    status?.hasBotAccount == true -> "Reconnect bot"
                    else -> "Connect bot"
                },
                enabled = state.connecting == null,
                onClick = { viewModel.connect(TwitchOAuthMode.BOT) },
            )
            HorizontalDivider()
        }

        ConnectRow(
            title = "Broadcaster channel",
            description = "Grants channel:bot and event scopes for this server. Authorize this with your own " +
                "Twitch account.",
            buttonLabel = when {
                state.connecting == TwitchOAuthMode.CHANNEL -> "Opening..."
                status?.hasChannelAuthorization == true -> "Reconnect channel"
                else -> "Connect channel"
            },
            enabled = state.connecting == null,
            onClick = { viewModel.connect(TwitchOAuthMode.CHANNEL) },
        )
        HelpText(
            "Authorization opens Twitch in your browser and finishes on the web dashboard. This screen " +
                "refreshes when you come back."
        )
    }

    HealthCard(state, viewModel)
}

@Composable
private fun ConnectRow(
    title: String,
    description: String,
    buttonLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        HelpText(description)
        Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun HealthCard(state: TwitchState, viewModel: TwitchViewModel) {
    val status = state.status
    var expanded by remember { mutableStateOf(false) }
    SectionCard {
        SectionCardHeader("Health", Icons.Default.MonitorHeart)
        InfoRow("Transport", if (status?.useEventSub != false) "EventSub WebSocket" else "Legacy IRC")
        InfoRow("Last event", status?.lastEventAt.twitchDate())
        InfoRow("Bot token", status?.botTokenExpiry.twitchDate())
        InfoRow("Channel token", status?.channelTokenExpiry.twitchDate())

        TextButton(onClick = { expanded = !expanded }) {
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            Text("  Advanced diagnostics")
        }

        if (expanded) {
            val health = state.health
            when {
                TwitchList.HEALTH in state.failedLists && health == null -> ListError(
                    "Could not load Twitch diagnostics.",
                    onRetry = { viewModel.load(refreshing = true) },
                )

                health == null -> EmptyState("No diagnostics available yet.", icon = Icons.Default.MonitorHeart)

                else -> {
                    Text("Missing scopes", style = MaterialTheme.typography.titleSmall)
                    InfoRow("Bot", health.botMissingScopes.joinToString(", ").ifEmpty { "none" })
                    InfoRow("Channel", health.channelMissingScopes.joinToString(", ").ifEmpty { "none" })
                    Text("EventSub", style = MaterialTheme.typography.titleSmall)
                    if (health.subscriptions.isEmpty()) {
                        EmptyState("No stored subscriptions yet.", icon = Icons.Default.Bolt)
                    } else {
                        health.subscriptions.forEach { subscription ->
                            ListItem(
                                headlineContent = {
                                    Text(subscription.type, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(subscription.lastUpdatedAt.twitchDate("Never updated"))
                                },
                                trailingContent = { TagChip(subscription.status) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BotSettingsSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    val settings = state.settings
    val status = state.status
    SectionCard {
        SectionCardHeader("Chat bot", Icons.Default.ChatBubble)
        HelpText("Configure how Twitch chat commands behave in this channel.")
        MewdekoTextField(
            value = settings.commandPrefix,
            onValueChange = viewModel::setCommandPrefix,
            label = "Command prefix",
            supportingText = "Up to 8 characters.",
        )
        MewdekoTextField(
            value = settings.language,
            onValueChange = viewModel::setLanguage,
            label = "Language override",
            placeholder = "Use server default",
        )
        SwitchRow(
            title = "Enable Twitch chat bot for this server",
            checked = settings.enabled,
            onCheckedChange = viewModel::setEnabled,
        )
        SwitchRow(
            title = "Use EventSub for chat events",
            subtitle = "Turning this off falls back to legacy IRC.",
            checked = settings.useEventSub,
            onCheckedChange = viewModel::setUseEventSub,
        )
    }

    SectionCard {
        SectionCardHeader("Disconnect", Icons.Default.LinkOff)
        OutlinedButton(
            onClick = {
                confirm(
                    TwitchConfirmation(
                        title = "Disconnect the Twitch channel?",
                        message = "Removes the channel authorization for this server and disables the chat bot.",
                        confirmLabel = "Disconnect",
                        onConfirm = { viewModel.disconnect(TwitchOAuthMode.CHANNEL) },
                    )
                )
            },
            enabled = status?.hasChannelAuthorization == true && !state.disconnecting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Disconnect channel")
        }
        if (state.isBotOwner) {
            OutlinedButton(
                onClick = {
                    confirm(
                        TwitchConfirmation(
                            title = "Disconnect the Twitch bot account?",
                            message = "The bot account is shared by every server on this bot. Twitch chat stops " +
                                "everywhere until it is reconnected.",
                            confirmLabel = "Disconnect",
                            onConfirm = { viewModel.disconnect(TwitchOAuthMode.BOT) },
                        )
                    )
                },
                enabled = status?.hasBotAccount == true && !state.disconnecting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Disconnect bot account")
            }
        }
    }
}

@Composable
private fun LinksSection(
    state: TwitchState,
    viewModel: TwitchViewModel,
    confirm: (TwitchConfirmation) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Link an account", Icons.Default.Link)
        HelpText("Connect a Discord member to their Twitch username.")
        DiscordSelectorSingle(
            kind = SelectorKind.User,
            options = state.members.map { member ->
                SelectorOption(
                    id = member.id,
                    name = member.displayName.ifBlank { member.username },
                    subtitle = member.username.takeIf { it.isNotBlank() && it != member.displayName },
                )
            },
            placeholder = "Select a user",
            label = "Discord member",
            selectedId = state.linkUserId,
            onSelect = viewModel::setLinkUser,
        )
        MewdekoTextField(
            value = state.linkUsername,
            onValueChange = viewModel::setLinkUsername,
            label = "Twitch username",
            placeholder = "twitchlogin",
        )
        Button(
            onClick = viewModel::addLink,
            enabled = !state.linking && state.linkUserId != null && state.linkUsername.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.linking) "Linking..." else "Link account")
        }
    }

    SectionCard {
        SectionCardHeader("Linked accounts (${state.links.size})", Icons.Default.Person)
        when {
            TwitchList.LINKS in state.failedLists -> ListError(
                "Could not load account links.",
                onRetry = { viewModel.load(refreshing = true) },
            )

            state.links.isEmpty() -> EmptyState("No Twitch accounts linked yet.", icon = Icons.Default.Link)

            else -> state.links.forEach { link ->
                ListItem(
                    headlineContent = {
                        Text(state.memberName(link.discordUserId), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = { Text("twitch.tv/${link.twitchUsername}") },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                confirm(
                                    TwitchConfirmation(
                                        title = "Unlink ${state.memberName(link.discordUserId)}?",
                                        message = "Removes the link to ${link.twitchUsername}.",
                                        confirmLabel = "Unlink",
                                        onConfirm = { viewModel.removeLink(link) },
                                    )
                                )
                            },
                            enabled = !state.linking,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Unlink")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}
