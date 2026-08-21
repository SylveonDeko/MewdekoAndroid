package dev.mewdeko.mobile.feature.patreon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.ui.Avatar
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
import dev.mewdeko.mobile.core.ui.SliderRow
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.Favorite),
    SectionTab("supporters", "Supporters", Icons.Default.Groups),
    SectionTab("settings", "Settings", Icons.Default.Tune),
)

/** Patreon supporter integration. */
@Composable
fun PatreonScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: PatreonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    var pendingDisconnect by remember { mutableStateOf(false) }

    LaunchedEffect(state.oauthUrl) {
        val url = state.oauthUrl ?: return@LaunchedEffect
        runCatching { uriHandler.openUri(url) }
        viewModel.clearOAuthUrl()
    }

    FeatureScaffold(
        title = "Patreon",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        if (!state.isLinked) {
            SectionCard {
                SectionCardHeader("Not connected", Icons.Default.Favorite)
                Text(
                    text = "Link a Patreon campaign to sync supporter roles and post " +
                        "announcements. You will be sent to Patreon to authorize access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = viewModel::requestOAuthUrl,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect Patreon") }
            }
            return@FeatureScaffold
        }

        state.creator?.let { creator ->
            SectionCard {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(
                        url = creator.imageUrl,
                        contentDescription = creator.fullName,
                        size = 56,
                        fallbackIcon = Icons.Default.Favorite,
                    )
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(creator.fullName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${creator.patronCount} patrons",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (creator.url.isNotBlank()) {
                        OutlinedButton(onClick = { uriHandler.openUri(creator.url) }) {
                            Text("Open")
                        }
                    }
                }
                state.status?.lastSync?.let {
                    Text(
                        text = "Last synced ${it.relativeToNow()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "supporters" -> {
                SectionCard {
                    SectionCardHeader("Supporters", Icons.Default.Groups)
                    if (state.supporters.isEmpty()) {
                        EmptyState("No supporters synced yet.", icon = Icons.Default.Favorite)
                    } else {
                        state.supporters.forEach { supporter ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = supporter.fullName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TagChip(
                                            if (supporter.isActive) "Active" else supporter.patronStatus
                                        )
                                        if (supporter.lifetimeAmountCents > 0) {
                                            TagChip(
                                                "Lifetime " +
                                                    "$${supporter.lifetimeAmountCents / 100}"
                                            )
                                        }
                                        supporter.lastChargeDate?.let {
                                            TagChip(it.relativeToNow())
                                        }
                                    }
                                },
                                trailingContent = {
                                    Text(
                                        text = "$${supporter.amountCents / 100}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }

                if (state.tiers.isNotEmpty()) {
                    SectionCard {
                        SectionCardHeader("Tiers", Icons.Default.WorkspacePremium)
                        state.tiers.forEach { tier ->
                            ListItem(
                                headlineContent = { Text(tier.title) },
                                supportingContent = tier.description?.let {
                                    { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                },
                                trailingContent = {
                                    androidx.compose.foundation.layout.Column(
                                        horizontalAlignment =
                                            androidx.compose.ui.Alignment.End,
                                    ) {
                                        Text(
                                            text = "$${tier.amountCents / 100}",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            text = "${tier.patronCount} patrons",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }

            "settings" -> {
                SectionCard {
                    SectionCardHeader("Announcements", Icons.Default.Tune)
                    SwitchRow(
                        title = "Announcements enabled",
                        checked = state.config.patreonEnabled,
                        onCheckedChange = { viewModel.saveConfig(toggleAnnouncements = true) },
                    )
                    SwitchRow(
                        title = "Sync supporter roles",
                        subtitle = "Grant Discord roles matching each supporter's tier",
                        checked = state.config.patreonRoleSync,
                        onCheckedChange = { viewModel.saveConfig(toggleRoleSync = true) },
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Channel,
                        options = state.availableChannels.map { SelectorOption(it.id, it.name) },
                        placeholder = "No announcement channel",
                        label = "Announce in",
                        selectedId = state.config.patreonChannelId,
                        onSelect = { id -> viewModel.edit { it.copy(patreonChannelId = id) } },
                    )
                    SliderRow(
                        label = "Announcement day of month",
                        value = state.config.patreonAnnouncementDay.toFloat(),
                        onValueChange = { value ->
                            viewModel.edit { it.copy(patreonAnnouncementDay = value.toInt()) }
                        },
                        valueRange = 1f..28f,
                        valueLabel = "${state.config.patreonAnnouncementDay}",
                    )
                    EmbedMessageEditor(
                        message = EmbedMessage.parse(state.config.patreonMessage),
                        onMessageChange = viewModel::setMessage,
                    )
                    Button(
                        onClick = { viewModel.saveConfig() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Save configuration") }
                }

                SectionCard {
                    SectionCardHeader("Maintenance", Icons.Default.Sync)
                    OutlinedButton(
                        onClick = { viewModel.runOperation("sync") },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Text("Sync supporters now", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { pendingDisconnect = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Disconnect Patreon",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            else -> {
                SectionCard {
                    SectionCardHeader("Revenue", Icons.Default.Payments)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Monthly",
                            value = "$${"%.0f".format(state.analytics?.totalMonthlyRevenue ?: 0.0)}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Average",
                            value = "$${"%.0f".format(state.analytics?.averageSupport ?: 0.0)}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Lifetime",
                            value = "$${"%.0f".format(state.analytics?.lifetimeRevenue ?: 0.0)}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SectionCard {
                    SectionCardHeader("Supporters", Icons.Default.Groups)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile(
                            label = "Active",
                            value = "${state.analytics?.activeSupporters ?: 0}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Linked",
                            value = "${state.analytics?.linkedSupporters ?: 0}",
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "New",
                            value = "${state.analytics?.newSupportersThisMonth ?: 0}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val top = state.analytics?.topSupporters.orEmpty()
                    if (top.isNotEmpty()) {
                        top.forEach { supporter ->
                            ListItem(
                                headlineContent = { Text(supporter.name) },
                                supportingContent = {
                                    if (!supporter.isLinked) Text("Discord account not linked")
                                },
                                trailingContent = {
                                    Text(
                                        text = "$${"%.0f".format(supporter.amount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }

    if (pendingDisconnect) {
        ConfirmDialog(
            title = "Disconnect Patreon?",
            message = "Supporter syncing and announcements stop until a campaign is linked again.",
            confirmLabel = "Disconnect",
            onConfirm = viewModel::disconnect,
            onDismiss = { pendingDisconnect = false },
        )
    }
}
