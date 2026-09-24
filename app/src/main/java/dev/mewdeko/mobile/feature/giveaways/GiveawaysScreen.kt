package dev.mewdeko.mobile.feature.giveaways

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
import dev.mewdeko.mobile.util.shortDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

private val Tabs = listOf(
    SectionTab("active", "Active", Icons.Default.Schedule),
    SectionTab("ended", "Ended", Icons.Default.CheckCircle),
)

/** Prize draws for a guild. */
@Composable
fun GiveawaysScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: GiveawaysViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showCreate by remember { mutableStateOf(false) }
    var pendingEnd by remember { mutableStateOf<GiveawayRecord?>(null) }

    FeatureScaffold(
        title = "Giveaways",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            NewItemFab(label = "New giveaway", onClick = { showCreate = true })
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.CardGiftcard)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Active", "${state.active.size}", Modifier.weight(1f))
                StatTile("Ended", "${state.ended.size}", Modifier.weight(1f))
                StatTile("Total", "${state.giveaways.size}", Modifier.weight(1f))
            }
        }

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        val visible = if (state.section == "ended") state.ended else state.active
        if (visible.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = if (state.section == "ended") "No finished giveaways."
                    else "No giveaways running.",
                    icon = Icons.Default.CardGiftcard,
                    actionLabel = if (state.section == "ended") null else "New giveaway",
                    onAction = if (state.section == "ended") null else ({ showCreate = true }),
                )
            }
        } else {
            visible.forEach { giveaway ->
                SectionCard {
                    SectionCardHeader(
                        title = giveaway.item.orEmpty().ifBlank { "Giveaway #${giveaway.id}" },
                        icon = Icons.Default.CardGiftcard,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagChip("#${state.channelName(giveaway.channelId)}", icon = Icons.Default.Tag)
                        TagChip(
                            "${giveaway.winners} winner${if (giveaway.winners == 1) "" else "s"}"
                        )
                        if (giveaway.useCaptcha) TagChip("Captcha")
                        if (!giveaway.useButton) TagChip("Reaction entry")
                    }
                    giveaway.`when`?.let { endsAt ->
                        Text(
                            text = if (giveaway.isEnded) "Ended ${endsAt.relativeToNow()}"
                            else "Ends ${endsAt.relativeToNow()} (${endsAt.shortDateTime()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (giveaway.messageCountReq > 0) {
                        Text(
                            text = "Requires ${giveaway.messageCountReq} messages to enter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (giveaway.restrictedRoleIds.isNotEmpty()) {
                        Text(
                            text = "Restricted to " +
                                giveaway.restrictedRoleIds.joinToString { "@${state.roleName(it)}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!giveaway.isEnded) {
                        OutlinedButton(
                            onClick = { pendingEnd = giveaway },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("End now and draw winners") }
                    }
                }
            }
        }
    }

    if (showCreate) {
        val emojiOptions = remember(state.availableEmojiGuilds) {
            buildList {
                add(SelectorOption(id = "🎉", name = "🎉 Party popper (default)"))
                state.availableEmojiGuilds.forEach { guildEmojis ->
                    guildEmojis.emojis.forEach { emoji ->
                        add(
                            SelectorOption(
                                id = emoji.formatted,
                                name = ":${emoji.name}:",
                                subtitle = guildEmojis.guild.name,
                            )
                        )
                    }
                }
            }
        }
        CreateGiveawayEditor(
            channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) },
            roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) },
            emojiOptions = emojiOptions,
            onDismiss = { showCreate = false },
            onCreate = { item, channelId, endsAt, winners, useButton, useCaptcha, msgReq, emote, roles ->
                viewModel.create(
                    item = item,
                    channelId = channelId,
                    endsAt = endsAt,
                    winners = winners,
                    useButton = useButton,
                    useCaptcha = useCaptcha,
                    messageCountReq = msgReq,
                    emote = emote,
                    restrictRoles = roles,
                )
                showCreate = false
            },
        )
    }

    pendingEnd?.let { giveaway ->
        ConfirmDialog(
            title = "End giveaway?",
            message = "Winners are drawn immediately for " +
                "\"${giveaway.item.orEmpty().ifBlank { "this giveaway" }}\".",
            confirmLabel = "End now",
            destructive = false,
            onConfirm = { viewModel.end(giveaway.id) },
            onDismiss = { pendingEnd = null },
        )
    }
}

/**
 * The full screen form for starting a giveaway. It holds a date and time
 * picker and a role list, so it is a pushed editor rather than a sheet.
 */
@Composable
private fun CreateGiveawayEditor(
    channelOptions: List<SelectorOption>,
    roleOptions: List<SelectorOption>,
    emojiOptions: List<SelectorOption>,
    onDismiss: () -> Unit,
    onCreate: (
        item: String,
        channelId: String,
        endsAt: Instant,
        winners: Int,
        useButton: Boolean,
        useCaptcha: Boolean,
        messageCountReq: Int,
        emote: String?,
        restrictRoles: List<String>,
    ) -> Unit,
) {
    var item by remember { mutableStateOf("") }
    var channelId by remember { mutableStateOf<String?>(null) }
    var endsAt by remember { mutableStateOf(Instant.now().plus(1, ChronoUnit.DAYS)) }
    var winners by remember { mutableStateOf("1") }
    var useButton by remember { mutableStateOf(true) }
    var useCaptcha by remember { mutableStateOf(false) }
    var messageReq by remember { mutableStateOf("0") }
    var emote by remember { mutableStateOf("🎉") }
    var restrictRoles by remember { mutableStateOf(emptyList<String>()) }

    val edited = item.isNotEmpty() || channelId != null || winners != "1" || !useButton ||
        useCaptcha || messageReq != "0" || emote != "🎉" || restrictRoles.isNotEmpty()

    FullScreenEditor(
        title = "New giveaway",
        onClose = onDismiss,
        confirmLabel = "Create",
        confirmEnabled = item.isNotBlank() && channelId != null &&
            endsAt.isAfter(Instant.now()) && (winners.toIntOrNull() ?: 0) >= 1,
        onConfirm = {
            channelId?.let {
                onCreate(
                    item.trim(),
                    it,
                    endsAt,
                    winners.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                    useButton,
                    useCaptcha,
                    messageReq.toIntOrNull() ?: 0,
                    emote.takeIf { value -> value.isNotBlank() },
                    restrictRoles,
                )
            }
        },
        hasUnsavedChanges = edited,
    ) {
        SectionCard {
            SectionCardHeader("Prize", Icons.Default.CardGiftcard)
            MewdekoTextField(
                value = item,
                onValueChange = { item = it },
                label = "Prize",
                placeholder = "Nitro month",
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Channel,
                options = channelOptions,
                placeholder = "Pick a channel",
                label = "Post in",
                selectedId = channelId,
                onSelect = { channelId = it },
            )
            EndTimeField(value = endsAt, onChange = { endsAt = it })
            MewdekoTextField(
                value = winners,
                onValueChange = { winners = it.filter(Char::isDigit) },
                label = "Number of winners",
                numeric = true,
                supportingText = "At least 1",
                isError = (winners.toIntOrNull() ?: 0) < 1,
            )
        }
        SectionCard {
            SectionCardHeader("Entry", Icons.Default.EmojiEmotions)
            SwitchRow(
                title = "Button entry",
                subtitle = "Members join with a button instead of a reaction",
                checked = useButton,
                onCheckedChange = { useButton = it },
            )
            SwitchRow(
                title = "Require captcha",
                subtitle = "Adds a bot check before entry counts",
                checked = useCaptcha,
                onCheckedChange = { useCaptcha = it },
            )
            if (!useButton) {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.EmojiEmotions),
                    options = emojiOptions,
                    placeholder = "🎉 Party popper (default)",
                    label = "Reaction emoji",
                    selectedId = emote.takeIf { it.isNotBlank() },
                    onSelect = { emote = it.orEmpty() },
                )
                MewdekoTextField(
                    value = emote,
                    onValueChange = { emote = it },
                    label = "Or paste a custom emoji code",
                    placeholder = "🎉",
                )
            }
            MewdekoTextField(
                value = messageReq,
                onValueChange = { messageReq = it.filter(Char::isDigit) },
                label = "Minimum messages to enter",
                numeric = true,
            )
            DiscordSelector(
                kind = SelectorKind.Role,
                options = roleOptions,
                placeholder = "Anyone can enter",
                label = "Restrict to roles",
                multiple = true,
                selection = restrictRoles,
                onSelectionChange = { restrictRoles = it },
            )
        }
    }
}

/**
 * Picks the moment a giveaway ends via the platform date and time dialogs,
 * so runs can be any length instead of a bounded hour count.
 */
@Composable
private fun EndTimeField(value: Instant, onChange: (Instant) -> Unit) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Ends",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                val base = ZonedDateTime.ofInstant(value, ZoneId.systemDefault())
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                val zoned = ZonedDateTime.of(
                                    year, month + 1, day, hour, minute, 0, 0, ZoneId.systemDefault(),
                                )
                                onChange(zoned.toInstant())
                            },
                            base.hour,
                            base.minute,
                            false,
                        ).show()
                    },
                    base.year,
                    base.monthValue - 1,
                    base.dayOfMonth,
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = "  ${value.shortDateTime()} (${value.relativeToNow()})",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
