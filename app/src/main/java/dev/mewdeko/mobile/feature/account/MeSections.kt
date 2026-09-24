package dev.mewdeko.mobile.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.model.Guild
import dev.mewdeko.mobile.core.model.MyGiveawayEntry
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.GlyphOrb
import dev.mewdeko.mobile.core.ui.LocalSheetDismiss
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.OrbSize
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.feature.guilddetail.home.isLargeFont
import dev.mewdeko.mobile.feature.guilddetail.home.rememberHomeRoles
import dev.mewdeko.mobile.feature.guilddetail.home.skeleton
import dev.mewdeko.mobile.feature.guilddetail.home.tabular
import dev.mewdeko.mobile.util.calendarDate
import dev.mewdeko.mobile.util.compact
import dev.mewdeko.mobile.util.shortDate
import dev.mewdeko.mobile.util.shortDateTime
import dev.mewdeko.mobile.util.withSeparators
import kotlin.math.roundToInt

/** Whether a section with no data yet is still loading rather than failed. */
private fun MeState.loading(section: MeSection): Boolean = !hasFailed(section)

/** One tile of the numbers grid. */
private data class NumberTile(
    val label: String,
    val value: String,
    val loading: Boolean,
    val icon: ImageVector,
    val tint: Color? = null,
)

/** A count for a stat tile: separated up to 99,999, compact above. */
private fun Long.tileText(): String = if (this > 99_999) compact() else withSeparators()

/** A tile value: the number, a skeleton placeholder while loading, or [NotSet]. */
private fun tileValue(value: Long?, loading: Boolean): String =
    value?.tileText() ?: if (loading) "1,234" else NotSet

/** "Your numbers": a tile per headline number and two caption lines. */
@Composable
internal fun MeNumbersSection(state: MeState, modifier: Modifier = Modifier) {
    val rep = state.reputation
    val rank = rep?.rank
    val podium = rank != null && rank in 1..3
    val repLoading = rep == null && state.loading(MeSection.Reputation)
    val tiles = listOf(
        NumberTile("Reputation", tileValue(rep?.totalRep?.toLong(), repLoading), repLoading, Icons.Default.Star),
        NumberTile(
            label = "Rank",
            value = when {
                rank == null -> if (repLoading) "#0" else NotSet
                rank > 0 -> "#$rank"
                else -> "Unranked"
            },
            loading = repLoading,
            icon = if (podium) Icons.Default.MilitaryTech else Icons.Default.EmojiEvents,
            tint = if (podium) MaterialTheme.colorScheme.tertiary else null,
        ),
        NumberTile(
            "Day streak",
            tileValue(rep?.currentStreak?.toLong(), repLoading),
            repLoading,
            Icons.Default.LocalFireDepartment,
        ),
        numberTile("Balance", state.currency?.balance, state.loading(MeSection.Currency), Icons.Default.Paid),
        numberTile("Messages", state.messages?.totalMessages, state.loading(MeSection.Messages), Icons.Default.Forum),
        numberTile(
            "Invites",
            state.invites?.inviteCount?.toLong(),
            state.loading(MeSection.Invites),
            Icons.Default.PersonAddAlt,
        ),
        numberTile(
            "Stars received",
            state.starboard?.stats?.starsReceived?.toLong(),
            state.starboard == null && state.loading(MeSection.Starboard),
            Icons.Default.Star,
        ),
    )
    val columns = if (isLargeFont()) 2 else 3

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Your numbers", Icons.Default.BarChart)
        tiles.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { tile ->
                    StatTile(
                        label = tile.label,
                        value = tile.value,
                        icon = tile.icon,
                        tint = tile.tint,
                        valueModifier = Modifier.skeleton(visible = tile.loading),
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (rep != null) {
            MeCaption(
                "Given ${rep.totalGiven.withSeparators()}, received ${rep.totalReceived.withSeparators()}, " +
                    "longest streak ${rep.longestStreak.withSeparators()} days."
            )
        }
        state.currency?.recentTransactions?.takeIf { it.isNotEmpty() }?.let {
            MeCaption("${it.size} recent transactions.")
        }
    }
}

/** A stat tile for an optional count. */
private fun numberTile(
    label: String,
    value: Long?,
    sectionLoading: Boolean,
    icon: ImageVector,
): NumberTile {
    val loading = value == null && sectionLoading
    return NumberTile(label, tileValue(value, loading), loading, icon)
}

/** A caption under a grid or card. */
@Composable
private fun MeCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** "Where you talk": a share strip and the busiest channels, in the secondary. */
@Composable
internal fun MeChannelsSection(state: MeState, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.secondary
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Where you talk", Icons.Default.Forum, tint = secondary)
        MeCard(tint = secondary) {
            val messages = state.messages
            when {
                messages == null -> MeStatusLine(loading = state.loading(MeSection.Messages))
                else -> {
                    if (!messages.enabled) {
                        MeNotice(
                            text = "Message tracking is off for you in this server.",
                            tint = secondary,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                    if (messages.channelBreakdown.isEmpty()) {
                        EmptyState(
                            message = "No tracked messages. Your busiest channels show up here once you start talking.",
                            icon = Icons.Default.Forum,
                        )
                    } else {
                        val top = messages.channelBreakdown.sortedByDescending { it.count }.take(6)
                        val total = top.sumOf { it.count }.coerceAtLeast(1L)
                        ChannelShareStrip(
                            counts = top.take(5).map { it.count },
                            tint = secondary,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                        top.forEachIndexed { index, channel ->
                            if (index > 0) MeDivider()
                            ChannelRow(
                                name = channel.channelName,
                                percent = (channel.count.toDouble() / total * 100).roundToInt(),
                                count = channel.count,
                                tint = secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One channel of the breakdown: a hash glyph, the name, its share and count. */
@Composable
private fun ChannelRow(name: String, percent: Int, count: Long, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeRowGlyph(Icons.Default.Tag, readableInk(tint))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = count.withSeparators(),
            style = MaterialTheme.typography.titleSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** An inline informational note on a tinted capsule. */
@Composable
internal fun MeNotice(text: String, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tint.copy(alpha = DashAlpha.Hex10),
        border = guildBorder(tint),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** "AFK": the away state, the message, and the set, update and clear actions. */
@Composable
internal fun MeAfkSection(
    state: MeState,
    onEdit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalGuildPalette.current.muted.color
    val afk = state.afk
    val away = afk?.isAfk == true
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("AFK", Icons.Default.Bedtime, tint = scheme.tertiary)
        MeCard(tint = if (away) scheme.tertiary else scheme.primary) {
            if (afk == null) {
                MeStatusLine(loading = state.loading(MeSection.Afk))
                return@MeCard
            }
            Column(
                modifier = Modifier.padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    GlyphOrb(
                        icon = if (away) Icons.Default.Bedtime else Icons.Default.DarkMode,
                        tint = if (away) scheme.tertiary else muted,
                        size = OrbSize.Large,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (away) "You're away" else "You're around",
                            style = MaterialTheme.typography.titleMedium,
                            color = scheme.onSurface,
                        )
                        val detail = when {
                            away -> afk.`when`?.let { "Since ${it.shortDateTime()}" }
                            else -> "Set a message for members who mention you."
                        }
                        if (detail != null) {
                            Text(detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                if (afk.message.isNotBlank()) {
                    Text(
                        text = "“${afk.message}”",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = scheme.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onEdit) { Text(if (away) "Update" else "Set AFK") }
                    if (away) {
                        OutlinedButton(onClick = onClear, border = guildBorder()) { Text("Clear") }
                    }
                }
            }
        }
    }
}

/**
 * The AFK message editor: a multiline field with the bot's rejection, such
 * as the guild's length limit, shown in place of the hint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AfkEditorSheet(
    initial: String,
    onSave: (String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    MewdekoBottomSheet(onDismissRequest = onDismiss, title = "Set AFK") {
        val dismissSheet = LocalSheetDismiss.current
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MewdekoTextField(
                value = draft,
                onValueChange = { draft = it; error = null },
                label = "Message",
                placeholder = "I'll be back later…",
                singleLine = false,
                minLines = 2,
                isError = error != null,
                supportingText = error ?: "Members who mention you see this while you are away.",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = dismissSheet) { Text("Cancel") }
                Button(
                    enabled = !saving,
                    onClick = {
                        saving = true
                        onSave(draft.trim()) { failure ->
                            saving = false
                            if (failure == null) dismissSheet() else error = failure
                        }
                    },
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save")
                    }
                }
            }
        }
    }
}

/** "Watching": highlight words and settings, then reminders. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeWatchingSection(
    state: MeState,
    onToggleHighlights: (Boolean) -> Unit,
    onAddHighlight: (String, (Boolean) -> Unit) -> Unit,
    onRemoveHighlight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    var draft by rememberSaveable { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    val submit = {
        val word = draft.trim()
        if (word.isNotEmpty() && !adding) {
            adding = true
            onAddHighlight(word) { saved ->
                adding = false
                if (saved) draft = ""
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Watching", Icons.Default.Visibility)
        MeCard(overline = "Highlights") {
            state.highlightSettings?.let { settings ->
                SwitchRow(
                    title = "Highlight pings",
                    subtitle = "A DM when someone uses one of your words",
                    icon = Icons.Default.DocumentScanner,
                    checked = settings.highlightsEnabled,
                    onCheckedChange = onToggleHighlights,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                MeDivider()
            }
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Add a word") },
                    singleLine = true,
                    enabled = state.selectedGuildId != null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit(); focus.clearFocus() }),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = { submit() },
                    enabled = draft.isNotBlank() && !adding,
                ) {
                    if (adding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = scheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "Add highlight")
                    }
                }
            }
            val highlights = state.highlights
            when {
                highlights == null -> MeStatusLine(loading = state.loading(MeSection.Highlights))
                highlights.isEmpty() -> MeEmptyLine("No highlight words yet.")
                else -> FlowRow(
                    modifier = Modifier.padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    highlights.forEach { highlight ->
                        HighlightChip(word = highlight.word, onRemove = { onRemoveHighlight(highlight.id) })
                    }
                }
            }
        }
        MeRemindersCard(state)
    }
}

/** Upcoming and expired reminders, across every server. */
@Composable
private fun MeRemindersCard(state: MeState) {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalGuildPalette.current.muted.color
    MeCard(overline = "Reminders") {
        val reminders = state.reminders
        when {
            reminders == null -> MeStatusLine(loading = state.loading(MeSection.Reminders))
            reminders.isEmpty() -> MeEmptyLine("No upcoming reminders.")
            else -> reminders.take(8).forEachIndexed { index, reminder ->
                if (index > 0) MeDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MeRowGlyph(
                        icon = if (reminder.isExpired) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                        tint = if (reminder.isExpired) muted else scheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = reminder.message?.takeIf { it.isNotBlank() } ?: "No message",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val caption = listOfNotNull(
                            reminder.`when`?.shortDateTime(),
                            reminderServer(reminder.serverId, state)?.let { "in $it" },
                        ).joinToString(" ")
                        if (caption.isNotEmpty()) {
                            Text(caption, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                        }
                    }
                    if (reminder.isExpired) {
                        StatePill("Expired", tone = scheme.error, icon = Icons.Default.Timer)
                    }
                }
            }
        }
    }
}

/**
 * The server a reminder belongs to, named only when it is not the selected
 * one, since the bot returns reminders from every server.
 */
private fun reminderServer(serverId: Snowflake?, state: MeState): String? {
    val id = serverId?.takeIf { it.isNotEmpty() && it != "0" } ?: return null
    if (id == state.selectedGuildId) return null
    return state.guilds?.firstOrNull { it.id == id }?.name
}

/** "Activity": suggestions, giveaways, starboard, invites, and levels across servers. */
@Composable
internal fun MeActivitySection(state: MeState, modifier: Modifier = Modifier) {
    val automation = rememberHomeRoles().automation.color
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Activity", Icons.Default.AutoAwesome, tint = automation)
        MeSuggestionsCard(state)
        MeGiveawaysCard(state)
        MeStarboardCard(state)
        MeInvitesCard(state)
        MeCrossServerCard(state)
    }
}

@Composable
private fun MeSuggestionsCard(state: MeState) {
    val scheme = MaterialTheme.colorScheme
    MeCard(overline = "Suggestions") {
        val suggestions = state.suggestions
        when {
            suggestions == null -> MeStatusLine(loading = state.loading(MeSection.Suggestions))
            suggestions.isEmpty() -> MeEmptyLine("No suggestions submitted here.")
            else -> suggestions.take(6).forEachIndexed { index, suggestion ->
                if (index > 0) MeDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${suggestion.suggestionId ?: suggestion.id.toLong()}",
                            style = MaterialTheme.typography.titleSmall.tabular(),
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        StatePill(
                            text = suggestion.stateName.ifBlank { "Pending" },
                            tone = scheme.secondary,
                            icon = Icons.Default.Lightbulb,
                        )
                    }
                    Text(
                        text = suggestion.suggestion1?.takeIf { it.isNotBlank() } ?: "No text",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeGiveawaysCard(state: MeState) {
    val scheme = MaterialTheme.colorScheme
    val muted = LocalGuildPalette.current.muted.color
    MeCard(overline = "Giveaways") {
        val giveaways = state.giveaways
        when {
            giveaways == null -> MeStatusLine(loading = state.loading(MeSection.Giveaways))
            giveaways.isEmpty() -> MeEmptyLine("No giveaway entries.")
            else -> giveaways.take(6).forEachIndexed { index, entry ->
                if (index > 0) MeDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MeRowGlyph(Icons.Default.CardGiftcard, if (entry.isEnded) muted else scheme.primary)
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = entry.item?.takeIf { it.isNotBlank() } ?: "Untitled prize",
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = giveawayLine(entry),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (entry.isEnded) {
                        StatePill("Ended", tone = muted, icon = Icons.Default.Flag)
                    } else {
                        StatePill("Entered", tone = scheme.primary, icon = Icons.Default.ConfirmationNumber)
                    }
                }
            }
        }
    }
}

/** "12 Mar 2026, 2 winners" style detail line for a giveaway. */
private fun giveawayLine(entry: MyGiveawayEntry): String {
    val winners = "${entry.winnerCount} winner${if (entry.winnerCount == 1) "" else "s"}"
    val date = entry.`when`?.shortDate() ?: return winners
    return "$date, $winners"
}

@Composable
private fun MeStarboardCard(state: MeState) {
    MeCard(overline = "Starboard") {
        val result = state.starboard
        val stats = result?.stats
        when {
            result == null -> MeStatusLine(loading = state.loading(MeSection.Starboard))
            stats == null -> MeEmptyLine("Starboard isn't set up in this server.")
            else -> {
                MeValueRow("Stars given", stats.starsGiven.withSeparators(), icon = Icons.Default.StarOutline)
                MeDivider()
                MeValueRow("Stars received", stats.starsReceived.withSeparators(), icon = Icons.Default.Star)
                MeDivider()
                MeValueRow("Posts on the board", stats.messagesStarred.withSeparators(), icon = Icons.Default.Forum)
                stats.topStarredPosts.maxOfOrNull { it.starCount }?.let { best ->
                    MeDivider()
                    MeValueRow(
                        "Best post",
                        "${best.withSeparators()} star${if (best == 1) "" else "s"}",
                        icon = Icons.Default.MilitaryTech,
                    )
                }
            }
        }
    }
}

@Composable
private fun MeInvitesCard(state: MeState) {
    val scheme = MaterialTheme.colorScheme
    MeCard(overline = "Invites") {
        val invites = state.invites
        if (invites == null) {
            MeStatusLine(loading = state.loading(MeSection.Invites))
            return@MeCard
        }
        MeValueRow("Total invites", invites.inviteCount.withSeparators(), icon = Icons.Default.PersonAddAlt)
        if (invites.invitedUsers.isNotEmpty()) {
            MeDivider()
            MeOverline("Latest invitees", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            invites.invitedUsers.take(5).forEach { invited ->
                val name = invited.displayName.ifBlank { invited.username }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(url = null, contentDescription = null, size = 32, fallbackText = name)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (invited.username.isNotBlank()) {
                            Text(
                                text = "@${invited.username}",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(4.dp))
        }
    }
}

@Composable
private fun MeCrossServerCard(state: MeState) {
    MeCard(overline = "Across servers") {
        val analytics = state.analytics
        if (analytics == null) {
            MeStatusLine(loading = state.loading(MeSection.Analytics))
            return@MeCard
        }
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniStat(analytics.totalServers.withSeparators(), "Servers")
            MiniStat(analytics.globalBalance.withSeparators(), "Global balance")
            MiniStat(analytics.totalSuggestions.withSeparators(), "Suggestions")
        }
        val top = analytics.xpData.sortedByDescending { it.totalXp }.take(5)
        val best = top.firstOrNull()
        if (best != null) {
            MeDivider()
            MeOverline("Top XP servers", modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
            top.forEach { entry -> XpLevelRow(entry = entry, maxXp = best.totalXp) }
            Spacer(Modifier.size(4.dp))
        }
    }
}

/** "Profile": bio, pronouns, zodiac, friend code, and birthday, read-only. */
@Composable
internal fun MeProfileSection(state: MeState, modifier: Modifier = Modifier) {
    val secondary = MaterialTheme.colorScheme.secondary
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Profile", Icons.Default.Badge, tint = secondary)
        MeCard(tint = secondary) {
            val profile = state.profile
            if (profile == null) {
                MeStatusLine(loading = state.loading(MeSection.Profile))
                return@MeCard
            }
            if (profile.bio.isNotBlank()) {
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                MeDivider()
            }
            MeValueRow("Pronouns", profile.pronouns.ifBlank { NotSet }, icon = Icons.Default.Person)
            MeDivider()
            MeValueRow("Zodiac", profile.zodiacSign.ifBlank { NotSet }, icon = Icons.Default.AutoAwesome)
            MeDivider()
            MeValueRow(
                "Switch friend code",
                profile.switchFriendCode.ifBlank { NotSet },
                icon = Icons.Default.SportsEsports,
            )
            MeDivider()
            MeValueRow(
                label = "Birthday",
                value = profile.birthday?.calendarDate() ?: NotSet,
                subtitle = profile.birthdayTimezone.takeIf { profile.birthday != null && it.isNotBlank() },
                icon = Icons.Default.Cake,
            )
        }
    }
}

/** The six preference switches and the setup wizard state. */
internal class PreferenceActions(
    val levelUpPings: () -> Unit,
    val pronouns: () -> Unit,
    val guidedSetup: () -> Unit,
    val greetDms: () -> Unit,
    val stats: () -> Unit,
    val birthdayAnnouncements: () -> Unit,
)

/** "Preferences": every per-user switch, each saved the moment it flips. */
@Composable
internal fun MePreferencesSection(
    state: MeState,
    actions: PreferenceActions,
    modifier: Modifier = Modifier,
) {
    val muted = LocalGuildPalette.current.muted.color
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MeSectionHeader("Preferences", Icons.Default.Tune, tint = muted)
        MeCard(tint = muted) {
            val prefs = state.preferences
            val profile = state.profile
            if (prefs == null && profile == null) {
                MeStatusLine(
                    loading = state.loading(MeSection.Preferences) || state.loading(MeSection.Profile),
                )
                return@MeCard
            }
            Column(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (prefs != null) {
                    SwitchRow(
                        title = "Level-up pings",
                        subtitle = "Mention me when I level up",
                        icon = Icons.Default.ArrowCircleUp,
                        checked = !prefs.levelUpPingsDisabled,
                        onCheckedChange = { actions.levelUpPings() },
                    )
                    SwitchRow(
                        title = "Pronoun lookups",
                        subtitle = "Let the bot show my pronouns",
                        icon = Icons.Default.PersonSearch,
                        checked = !prefs.pronounsDisabled,
                        onCheckedChange = { actions.pronouns() },
                    )
                    SwitchRow(
                        title = "Prefer guided setup",
                        subtitle = "Start features with a wizard on the dashboard",
                        icon = Icons.Default.AutoFixHigh,
                        checked = prefs.prefersGuidedSetup,
                        onCheckedChange = { actions.guidedSetup() },
                    )
                }
                if (profile != null) {
                    SwitchRow(
                        title = "Greet DMs",
                        subtitle = "Receive welcome messages by DM",
                        icon = Icons.Default.Mail,
                        checked = !profile.greetDmsOptOut,
                        onCheckedChange = { actions.greetDms() },
                    )
                    SwitchRow(
                        title = "Stat tracking",
                        subtitle = "Count my messages and activity",
                        icon = Icons.Default.BarChart,
                        checked = !profile.statsOptOut,
                        onCheckedChange = { actions.stats() },
                    )
                    SwitchRow(
                        title = "Birthday announcements",
                        subtitle = "Celebrate my birthday in this server",
                        icon = Icons.Default.Cake,
                        checked = profile.birthdayAnnouncementsEnabled,
                        onCheckedChange = { actions.birthdayAnnouncements() },
                    )
                }
            }
            if (prefs != null) {
                MeDivider()
                MeValueRow(
                    label = "Setup wizard",
                    value = if (prefs.hasCompletedAnyWizard) "Completed" else "Not completed",
                    icon = if (prefs.hasCompletedAnyWizard) Icons.Default.Verified else Icons.Outlined.Verified,
                )
            }
        }
    }
}

/** "Your stats in" line under the server picker, with an owner pill. */
@Composable
internal fun SelectedGuildLine(guild: Guild, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Your stats in",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = guild.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (guild.owner) {
            StatePill("Owner", tone = MaterialTheme.colorScheme.primary, icon = Icons.Default.WorkspacePremium)
        }
    }
}
