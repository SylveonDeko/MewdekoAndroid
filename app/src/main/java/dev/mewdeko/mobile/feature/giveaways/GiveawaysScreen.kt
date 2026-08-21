package dev.mewdeko.mobile.feature.giveaways

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
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
import dev.mewdeko.mobile.util.shortDateTime
import java.time.Instant
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
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New giveaway") },
            )
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
                    else "No giveaways running. Tap the button to start one.",
                    icon = Icons.Default.CardGiftcard,
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
        CreateGiveawayDialog(
            channelOptions = state.availableChannels.map { SelectorOption(it.id, it.name) },
            roleOptions = state.availableRoles.map { SelectorOption(it.id, it.name) },
            onDismiss = { showCreate = false },
            onCreate = { item, channelId, hours, winners, useButton, useCaptcha, msgReq, emote, roles ->
                viewModel.create(
                    item = item,
                    channelId = channelId,
                    endsAt = Instant.now().plus(hours.toLong(), ChronoUnit.HOURS),
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

@Composable
private fun CreateGiveawayDialog(
    channelOptions: List<SelectorOption>,
    roleOptions: List<SelectorOption>,
    onDismiss: () -> Unit,
    onCreate: (
        item: String,
        channelId: String,
        hours: Int,
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
    var hours by remember { mutableIntStateOf(24) }
    var winners by remember { mutableIntStateOf(1) }
    var useButton by remember { mutableStateOf(true) }
    var useCaptcha by remember { mutableStateOf(false) }
    var messageReq by remember { mutableStateOf("0") }
    var emote by remember { mutableStateOf("") }
    var restrictRoles by remember { mutableStateOf(emptyList<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New giveaway") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
                SliderRow(
                    label = "Runs for",
                    value = hours.toFloat(),
                    onValueChange = { hours = it.toInt().coerceAtLeast(1) },
                    valueRange = 1f..720f,
                    valueLabel = if (hours < 24) "${hours}h" else "${hours / 24}d ${hours % 24}h",
                )
                SliderRow(
                    label = "Winners",
                    value = winners.toFloat(),
                    onValueChange = { winners = it.toInt().coerceAtLeast(1) },
                    valueRange = 1f..25f,
                    valueLabel = "$winners",
                )
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
                    MewdekoTextField(
                        value = emote,
                        onValueChange = { emote = it },
                        label = "Entry emote",
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
        },
        confirmButton = {
            Button(
                onClick = {
                    channelId?.let {
                        onCreate(
                            item.trim(),
                            it,
                            hours,
                            winners,
                            useButton,
                            useCaptcha,
                            messageReq.toIntOrNull() ?: 0,
                            emote.takeIf { value -> value.isNotBlank() },
                            restrictRoles,
                        )
                    }
                },
                enabled = item.isNotBlank() && channelId != null,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
