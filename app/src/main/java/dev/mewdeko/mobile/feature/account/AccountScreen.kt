package dev.mewdeko.mobile.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TipsAndUpdates
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalUriHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.util.compact
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.shortDate
import dev.mewdeko.mobile.util.withSeparators

/** The signed-in user's cross-guild profile, stats, and preferences. */
@Composable
fun AccountScreen(
    user: MobileUser,
    instance: MobileInstance?,
    onSwitchInstance: () -> Unit,
    onSwitchServer: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteData: () -> Unit,
    viewModel: MeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var pendingSignOut by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var newHighlight by remember { mutableStateOf("") }
    var afkDraft by remember { mutableStateOf("") }

    FeatureScaffold(
        title = user.displayName,
        subtitle = "@${user.username}",
        onBack = null,
        loadState = state.load,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.loadGuilds() },
    ) {
        SectionCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Avatar(url = user.avatarUrl, contentDescription = user.displayName, size = 64)
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "@${user.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (instance != null) {
                        Text(
                            text = "Connected to ${instance.botName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            state.profile?.let { profile ->
                if (profile.bio.isNotBlank()) {
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    profile.pronouns.takeIf { it.isNotBlank() }?.let {
                        TagChip(it, icon = Icons.Default.AlternateEmail)
                    }
                    profile.zodiacSign.takeIf { it.isNotBlank() }?.let {
                        TagChip(it, icon = Icons.Default.Star)
                    }
                    profile.birthday?.let {
                        TagChip(it.shortDate(), icon = Icons.Default.Cake)
                    }
                }
            }
        }

        SectionCard {
            SectionCardHeader("Server context", Icons.Default.Groups)
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Groups),
                options = state.guilds.map { SelectorOption(it.id, it.name) },
                placeholder = "Pick a server",
                selectedId = state.selectedGuild?.id,
                onSelect = { id ->
                    state.guilds.firstOrNull { it.id == id }?.let(viewModel::selectGuild)
                },
            )
            Text(
                text = "Stats and preferences below are scoped to the selected server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.selectedGuild == null) {
            SectionCard {
                EmptyState(
                    message = "Choose a server above to view your stats and preferences.",
                    icon = Icons.Default.Groups,
                )
            }
        } else {
            AccountStats(state)
            AccountAfk(
                state = state,
                draft = afkDraft,
                onDraftChange = { afkDraft = it },
                onSet = { viewModel.setAfk(afkDraft); afkDraft = "" },
                onClear = viewModel::clearAfk,
            )
            AccountHighlights(
                state = state,
                draft = newHighlight,
                onDraftChange = { newHighlight = it },
                onAdd = { viewModel.addHighlight(newHighlight); newHighlight = "" },
                onRemove = viewModel::removeHighlight,
                onToggleEnabled = viewModel::setHighlightsEnabled,
            )
            AccountPreferences(state = state, viewModel = viewModel)
            AccountActivity(state)
        }

        SectionCard {
            SectionCardHeader("Session", Icons.Default.SmartToy)
            OutlinedButton(onClick = onSwitchInstance, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Switch bot instance", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onSwitchServer, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Switch dashboard", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = { pendingSignOut = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Sign out",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        SectionCard {
            SectionCardHeader("Privacy", Icons.Default.Shield)
            Text(
                text = "Mewdeko stores your Discord id, username, and avatar so the dashboard " +
                    "can identify you, and keeps your sign-in tokens encrypted on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { uriHandler.openUri(PrivacyPolicyUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Privacy policy") }
            OutlinedButton(
                onClick = { uriHandler.openUri(TermsUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Terms of service") }
            Button(
                onClick = { pendingDelete = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("Delete my data", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }

    if (pendingDelete) {
        ConfirmDialog(
            title = "Delete your data?",
            message = "Your session is revoked on the dashboard and this device forgets your " +
                "tokens, profile, and saved server. Your Discord account is not affected.",
            confirmLabel = "Delete",
            onConfirm = { pendingDelete = false; onDeleteData() },
            onDismiss = { pendingDelete = false },
        )
    }

    if (pendingSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "Your tokens for this dashboard will be removed from this device.",
            confirmLabel = "Sign out",
            onConfirm = onSignOut,
            onDismiss = { pendingSignOut = false },
        )
    }
}

@Composable
private fun AccountStats(state: MeState) {
    SectionCard {
        SectionCardHeader("Your stats", Icons.Default.EmojiEvents)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Reputation",
                value = state.reputation?.totalRep?.withSeparators() ?: "-",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Rank",
                value = state.reputation?.rank?.let { "#$it" } ?: "-",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Streak",
                value = state.reputation?.currentStreak?.toString() ?: "-",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Balance",
                value = state.currency?.balance?.compact() ?: "-",
                icon = Icons.Default.Paid,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Messages",
                value = state.messages?.totalMessages?.compact() ?: "-",
                icon = Icons.Default.MarkEmailUnread,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Invites",
                value = state.invites?.inviteCount?.toString() ?: "-",
                icon = Icons.Default.Groups,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Stars given",
                value = state.starboard?.starsGiven?.toString() ?: "-",
                icon = Icons.Default.Star,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Stars received",
                value = state.starboard?.starsReceived?.toString() ?: "-",
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Servers",
                value = state.analytics?.totalServers?.toString() ?: "-",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AccountAfk(
    state: MeState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSet: () -> Unit,
    onClear: () -> Unit,
) {
    SectionCard {
        SectionCardHeader("AFK", Icons.Default.DarkMode)
        val afk = state.afk
        if (afk?.isAfk == true) {
            Text(afk.message.ifBlank { "You are marked AFK." })
            afk.`when`?.let {
                Text(
                    text = "Since ${it.relativeToNow()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                Text("Clear AFK")
            }
        } else {
            MewdekoTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = "AFK message",
                placeholder = "Back in a bit",
            )
            OutlinedButton(
                onClick = onSet,
                enabled = draft.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Set AFK") }
        }
    }
}

@Composable
private fun AccountHighlights(
    state: MeState,
    draft: String,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
) {
    SectionCard {
        SectionCardHeader("Highlights", Icons.Default.NotificationsActive)
        SwitchRow(
            title = "Notify me",
            subtitle = "Ping me when one of my words is used",
            checked = state.highlightSettings?.highlightsEnabled == true,
            onCheckedChange = onToggleEnabled,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MewdekoTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = "New word",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAdd, enabled = draft.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "Add highlight")
            }
        }
        if (state.highlights.isEmpty()) {
            EmptyState("No highlight words yet.", icon = Icons.Default.NotificationsActive)
        } else {
            state.highlights.forEach { highlight ->
                ListItem(
                    headlineContent = { Text(highlight.word) },
                    supportingContent = highlight.dateAdded?.let {
                        { Text("Added ${it.relativeToNow()}") }
                    },
                    trailingContent = {
                        IconButton(onClick = { onRemove(highlight.id) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove ${highlight.word}",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun AccountPreferences(state: MeState, viewModel: MeViewModel) {
    SectionCard {
        SectionCardHeader("Preferences", Icons.Default.Notifications)
        SwitchRow(
            title = "Level-up pings",
            subtitle = "Get pinged when you level up",
            checked = state.preferences?.levelUpPingsDisabled == false,
            onCheckedChange = { viewModel.toggleLevelUpPings() },
        )
        SwitchRow(
            title = "Show pronouns",
            subtitle = "Display your pronouns on your profile",
            checked = state.preferences?.pronounsDisabled == false,
            onCheckedChange = { viewModel.togglePronouns() },
        )
        SwitchRow(
            title = "Guided setup",
            subtitle = "Prefer step-by-step configuration flows",
            checked = state.preferences?.prefersGuidedSetup == true,
            onCheckedChange = { viewModel.toggleGuidedSetup() },
        )
        SwitchRow(
            title = "Greet DMs",
            subtitle = "Receive welcome messages by direct message",
            checked = state.profile?.greetDmsOptOut == false,
            onCheckedChange = { viewModel.toggleGreetDms() },
        )
        SwitchRow(
            title = "Stats collection",
            subtitle = "Include my activity in server statistics",
            checked = state.profile?.statsOptOut == false,
            onCheckedChange = { viewModel.toggleStats() },
        )
        SwitchRow(
            title = "Birthday announcements",
            subtitle = "Let the bot announce my birthday",
            checked = state.profile?.birthdayAnnouncementsEnabled == true,
            onCheckedChange = { viewModel.toggleBirthdayAnnouncements() },
        )
    }
}

@Composable
private fun AccountActivity(state: MeState) {
    if (state.suggestions.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("My suggestions", Icons.Default.TipsAndUpdates)
            state.suggestions.take(10).forEach { suggestion ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = suggestion.suggestion1.orEmpty().ifBlank { "Suggestion" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            buildString {
                                append(suggestion.stateName.ifBlank { "Pending" })
                                suggestion.dateAdded?.let { append(" · ${it.relativeToNow()}") }
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (state.reminders.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Reminders", Icons.Default.Notifications)
            state.reminders.take(10).forEach { reminder ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = reminder.message.orEmpty().ifBlank { "Reminder" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = reminder.`when`?.let { { Text(it.relativeToNow()) } },
                    trailingContent = {
                        if (reminder.isExpired) TagChip("Expired")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    if (state.giveaways.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Giveaways entered", Icons.Default.CardGiftcard)
            state.giveaways.take(10).forEach { entry ->
                ListItem(
                    headlineContent = { Text(entry.item.orEmpty().ifBlank { "Giveaway" }) },
                    supportingContent = {
                        Text(
                            buildString {
                                append(if (entry.isEnded) "Ended" else "Running")
                                append(" · ${entry.winnerCount} winner")
                                if (entry.winnerCount != 1) append("s")
                            }
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }

    state.analytics?.xpData?.takeIf { it.isNotEmpty() }?.let { xp ->
        SectionCard {
            SectionCardHeader("XP across servers", Icons.Default.Star)
            xp.sortedByDescending { it.totalXp }.take(10).forEach { entry ->
                ListItem(
                    headlineContent = {
                        Text(entry.guildName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text("Level ${entry.level} · ${entry.totalXp.compact()} XP")
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

/** Where the published privacy policy lives. */
private const val PrivacyPolicyUrl = "https://mewdeko.tech/privacy"

/** Where the published terms of service live. */
private const val TermsUrl = "https://mewdeko.tech/terms"
