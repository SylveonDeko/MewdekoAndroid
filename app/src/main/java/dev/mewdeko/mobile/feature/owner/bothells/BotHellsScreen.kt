package dev.mewdeko.mobile.feature.owner.bothells

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.MonospaceStyle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.GuildCard
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.guildBorder
import dev.mewdeko.mobile.core.ui.guildWash
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.core.ui.toneWash
import dev.mewdeko.mobile.feature.guilddetail.home.skeleton
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Width from which the stat tiles sit four across and the threshold fields three across. */
private val WideWidth = 600.dp

/** Padding around the page's single lazy list. */
private val ListPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 80.dp)

/** Local date formatter for the joined date. */
private val JoinedFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())

/**
 * Servers littered with bots, with bulk leave, mirroring the dashboard's
 * `/owner/bot-hells`. Fleet level: acts on the selected bot instance.
 *
 * One lazy list carries the whole page, since an instance can hold thousands
 * of servers: the tabs, then either the servers tab (stats, threshold
 * summary, toolbar, last leave banner, and one card per listed server) or
 * the settings tab (thresholds, report channel, and auto leave).
 */
@Composable
fun BotHellsScreen(
    onBack: () -> Unit,
    viewModel: BotHellsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val visible = remember(state.entries, state.flaggedOnly, state.search) { state.visibleEntries() }

    FeatureScaffold(
        title = "Bot Hells",
        subtitle = viewModel.botName,
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        scrollable = false,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = ListPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro", contentType = "intro") {
                Text(
                    text = "Servers littered with bots, and a way out of them",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "tabs", contentType = "tabs") {
                SectionTabs(
                    tabs = BotHellsTabs,
                    selectedId = state.tab,
                    onSelect = viewModel::setTab,
                )
            }
            if (state.tab == BotHellsTab.SETTINGS) {
                settingsTab(state, viewModel)
            } else {
                serversTab(state, visible, viewModel)
            }
        }
    }

    if (state.confirmingLeave) {
        LeaveConfirmation(
            targets = state.leaveTargets,
            botId = viewModel.botId,
            onConfirm = viewModel::confirmLeave,
            onDismiss = viewModel::cancelLeave,
        )
    }
}

/** The two tabs, matching the dashboard's ids and labels. */
private val BotHellsTabs = listOf(
    SectionTab(BotHellsTab.SERVERS, "Servers", Icons.Default.SmartToy),
    SectionTab(BotHellsTab.SETTINGS, "Settings", Icons.Default.Settings),
)

/** The servers tab: stats, summary, toolbar, banner, and the list in its current state. */
private fun LazyListScope.serversTab(
    state: BotHellsState,
    visible: List<BotHellEntry>,
    viewModel: BotHellsViewModel,
) {
    item(key = "stats", contentType = "stats") { StatGrid(state) }

    state.settings?.let { settings ->
        item(key = "summary", contentType = "summary") {
            Text(
                text = settings.thresholdSummary(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    item(key = "toolbar", contentType = "toolbar") {
        Toolbar(
            state = state,
            onSearch = viewModel::setSearch,
            onToggleFlagged = viewModel::toggleFlaggedOnly,
            onSelectFlagged = viewModel::selectAllFlagged,
            onClear = viewModel::clearSelection,
            onLeaveSelected = viewModel::requestLeaveSelected,
            onRefresh = { viewModel.load() },
        )
    }

    if (state.leaving) {
        item(key = "leaving", contentType = "banner") {
            Banner(tone = MaterialTheme.colorScheme.tertiary, progress = true) {
                val count = state.leavingCount
                "Leaving $count server${if (count == 1) "" else "s"}. The bot leaves them one at a time, " +
                    "so a long list can take a while."
            }
        }
    }

    state.banner?.let { banner ->
        item(key = "banner", contentType = "banner") {
            Banner(
                tone = if (banner.isError) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            ) { banner.text }
        }
    }

    if (state.loading && state.entries.isNotEmpty()) {
        item(key = "progress", contentType = "progress") {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }

    val error = state.error
    when {
        state.loading && state.entries.isEmpty() && error == null -> {
            items(SkeletonRows, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .skeleton(true, MaterialTheme.shapes.large),
                )
            }
        }

        error != null && state.entries.isEmpty() -> {
            item(key = "error", contentType = "message") {
                ListMessage(text = error, tone = MaterialTheme.colorScheme.tertiary, onRetry = { viewModel.load() })
            }
        }

        else -> {
            if (error != null) {
                item(key = "error", contentType = "banner") {
                    Banner(tone = MaterialTheme.colorScheme.tertiary) { "$error. Showing the last list read." }
                }
            }
            if (visible.isEmpty()) {
                item(key = "empty", contentType = "message") {
                    ListMessage(
                        text = if (state.flaggedOnly) {
                            "No servers meet the thresholds."
                        } else {
                            "No servers match the search."
                        },
                        tone = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item(key = "select-visible", contentType = "select") {
                    val allSelected = visible.all { it.guildId in state.selected }
                    SelectVisibleRow(
                        count = visible.size,
                        allSelected = allSelected,
                        onToggle = viewModel::toggleSelectAllVisible,
                    )
                }
                items(visible, key = { it.guildId }, contentType = { "server" }) { entry ->
                    ServerCard(
                        entry = entry,
                        selected = entry.guildId in state.selected,
                        checking = entry.guildId in state.checking,
                        leaving = state.leaving,
                        onToggle = { viewModel.toggleSelect(entry.guildId) },
                        onRecheck = { viewModel.recheck(entry) },
                        onLeave = { viewModel.requestLeave(listOf(entry)) },
                    )
                }
                item(key = "footer", contentType = "footer") {
                    Text(
                        text = "Showing ${visible.size} of ${state.entries.size} servers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Six placeholder rows, as the dashboard shows while scanning. */
private val SkeletonRows = List(6) { it }

/** The four stat tiles: two across on phones, four from [WideWidth]. */
@Composable
private fun StatGrid(state: BotHellsState) {
    val scheme = MaterialTheme.colorScheme
    val tiles = listOf(
        StatSpec("Servers", state.entries.size, Icons.Default.Dns, scheme.secondary),
        StatSpec("Flagged", state.flaggedCount, Icons.Default.Warning, scheme.tertiary),
        StatSpec("Selected", state.selected.size, Icons.Default.CheckBox, scheme.primary),
        StatSpec("Bots in flagged", state.botsInFlagged, Icons.Default.SmartToy, scheme.tertiary),
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= WideWidth) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile ->
                        StatTile(
                            label = tile.label,
                            value = tile.value.toString(),
                            tint = tile.tone,
                            icon = tile.icon,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** One stat tile's content and tone. */
private data class StatSpec(val label: String, val value: Int, val icon: ImageVector, val tone: Color)

/** Search plus the filter, selection, leave, and refresh actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Toolbar(
    state: BotHellsState,
    onSearch: (String) -> Unit,
    onToggleFlagged: () -> Unit,
    onSelectFlagged: () -> Unit,
    onClear: () -> Unit,
    onLeaveSelected: () -> Unit,
    onRefresh: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    SectionCard {
        SearchField(value = state.search, onValueChange = onSearch, placeholder = "Server name or ID")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToneButton(
                text = if (state.flaggedOnly) "Flagged only" else "All servers",
                tone = if (state.flaggedOnly) scheme.tertiary else scheme.primary,
                icon = Icons.Default.FilterList,
                onClick = onToggleFlagged,
            )
            ToneButton(
                text = "Select all flagged",
                tone = scheme.primary,
                onClick = onSelectFlagged,
                enabled = state.flaggedCount > 0,
            )
            ToneButton(
                text = "Clear",
                tone = scheme.primary,
                onClick = onClear,
                enabled = state.selected.isNotEmpty(),
            )
            ToneButton(
                text = if (state.leaving) "Leaving..." else "Leave selected (${state.selected.size})",
                tone = scheme.tertiary,
                icon = Icons.AutoMirrored.Filled.Logout,
                loading = state.leaving,
                onClick = onLeaveSelected,
                enabled = state.selected.isNotEmpty() && !state.leaving,
            )
            ToneButton(
                text = "Refresh",
                tone = scheme.primary,
                icon = Icons.Default.Refresh,
                onClick = onRefresh,
                enabled = !state.loading,
            )
        }
    }
}

/** A tinted notice line: the last leave's outcome, a load failure, or a leave in progress. */
@Composable
private fun Banner(tone: Color, progress: Boolean = false, text: () -> String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tone.copy(alpha = DashAlpha.Hex15),
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (progress) {
                CircularProgressIndicator(color = tone, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A centered message in place of the list, with an optional retry. */
@Composable
private fun ListMessage(text: String, tone: Color, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = readableInk(tone),
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            ToneButton(
                text = "Try again",
                tone = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.Refresh,
                onClick = onRetry,
            )
        }
    }
}

/** The dashboard table's header checkbox: selects or clears every listed server. */
@Composable
private fun SelectVisibleRow(count: Int, allSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex08))
            .padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = allSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.semantics { contentDescription = "Select every listed server" },
        )
        Text(
            text = if (allSelected) "All $count listed selected" else "Select all $count listed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * One server: selection, icon, name, id and joined date, counts, the trigger
 * chip, and its recheck and leave actions. Tapping the card toggles the
 * selection; a selected card carries a solid primary border over a primary wash.
 */
@Composable
private fun ServerCard(
    entry: BotHellEntry,
    selected: Boolean,
    checking: Boolean,
    leaving: Boolean,
    onToggle: () -> Unit,
    onRecheck: () -> Unit,
    onLeave: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val muted = scheme.onSurfaceVariant
    val accentInk = readableInk(scheme.tertiary)
    val joined = remember(entry.joinedAt) {
        entry.joinedInstant?.let(JoinedFormatter::format) ?: "Unknown"
    }
    GuildCard(
        modifier = Modifier.fillMaxWidth(),
        wash = if (selected) toneWash(scheme.primary) else guildWash(),
        border = if (selected) BorderStroke(1.dp, scheme.primary) else guildBorder(),
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.semantics { contentDescription = "Select ${entry.guildName}" },
                )
                ServerIcon(entry)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.guildName.ifBlank { "Unknown server" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${entry.guildId} · joined $joined",
                        style = MonospaceStyle,
                        color = muted,
                        maxLines = 2,
                    )
                }
                StatePill(
                    text = entry.triggerLabel,
                    tone = if (entry.isBotHell) scheme.tertiary else scheme.secondary,
                )
            }
            Text(
                text = buildAnnotatedString {
                    append("${entry.total} members · ${entry.humans} humans · ")
                    withStyle(
                        SpanStyle(
                            color = if (entry.isBotHell) accentInk else scheme.onSurface,
                            fontWeight = if (entry.isBotHell) FontWeight.SemiBold else null,
                        ),
                    ) { append("${entry.bots} bots") }
                    withStyle(SpanStyle(color = muted)) { append(" (${entry.percent}%)") }
                    if (!entry.complete) {
                        withStyle(SpanStyle(color = muted)) { append(" · partial") }
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurface,
                modifier = Modifier.padding(start = 12.dp),
            )
            Row(
                modifier = Modifier.padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToneButton(
                    text = if (checking) "Checking..." else "Recheck",
                    tone = scheme.primary,
                    icon = Icons.Default.Refresh,
                    loading = checking,
                    onClick = onRecheck,
                    enabled = !checking,
                    modifier = Modifier.weight(1f),
                )
                ToneButton(
                    text = "Leave",
                    tone = scheme.tertiary,
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = onLeave,
                    enabled = !leaving,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The server icon, or a rounded square with the first two letters of its name. */
@Composable
private fun ServerIcon(entry: BotHellEntry) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)
    var failed by remember(entry.iconUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(primary.copy(alpha = DashAlpha.Hex20), shape),
        contentAlignment = Alignment.Center,
    ) {
        val url = entry.iconUrl?.takeIf { it.isNotBlank() && !failed }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Text(
                text = entry.initials,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = readableInk(primary),
            )
        }
    }
}

/**
 * The leave confirmation, worded as the dashboard's. When the bot owns any
 * target it adds that those servers are deleted rather than left, which the
 * bot does for servers it owns.
 */
@Composable
private fun LeaveConfirmation(
    targets: List<BotHellEntry>,
    botId: Snowflake?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val owned = if (botId == null) 0 else targets.count { it.ownerId == botId }
    val single = targets.size == 1
    val title = if (single) "Leave server" else "Leave ${targets.size} servers"
    val base = if (single) {
        "Leave ${targets.first().guildName.ifBlank { "this server" }}? " +
            "The bot will have to be re-invited to come back."
    } else {
        "Leave ${targets.size} servers? The bot will have to be re-invited to come back to any of them."
    }
    val ownedNote = when {
        owned == 0 -> ""
        single -> " The bot owns this server, so it will be deleted rather than left."
        else -> " The bot owns $owned of them, and those will be deleted rather than left."
    }
    ConfirmDialog(
        title = title,
        message = base + ownedNote,
        confirmLabel = "Leave",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The settings tab: an error with retry when settings are missing, else the two cards. */
private fun LazyListScope.settingsTab(state: BotHellsState, viewModel: BotHellsViewModel) {
    val settings = state.settings
    if (settings == null) {
        if (state.loading || state.settingsLoading) {
            item(key = "settings-skeleton", contentType = "skeleton") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .skeleton(true, MaterialTheme.shapes.large),
                )
            }
        } else {
            item(key = "settings-error", contentType = "message") {
                ListMessage(
                    text = state.settingsLoadError ?: "Failed to load the settings",
                    tone = MaterialTheme.colorScheme.tertiary,
                    onRetry = viewModel::retrySettings,
                )
            }
        }
        return
    }

    item(key = "thresholds", contentType = "card") {
        ThresholdsCard(
            state = state,
            settings = settings,
            onMinMembers = viewModel::setMinMembers,
            onBotCount = viewModel::setBotCount,
            onBotPercent = viewModel::setBotPercent,
            onChannel = viewModel::setChannel,
            onSave = viewModel::saveThresholds,
        )
    }
    item(key = "on-join", contentType = "card") {
        SectionCard {
            SectionCardHeader("On join", Icons.AutoMirrored.Filled.Login)
            SwitchRow(
                title = "Leave flagged servers automatically",
                subtitle = "Every new server is checked after its member list downloads. " +
                    "Flagged ones are reported, and left when this is on.",
                checked = state.autoLeaveShown,
                onCheckedChange = viewModel::setAutoLeave,
                enabled = !state.savingSettings,
            )
        }
    }
}

/** Minimum members, bot count, bot percentage, the report channel, and save. */
@Composable
private fun ThresholdsCard(
    state: BotHellsState,
    settings: BotHellSettings,
    onMinMembers: (String) -> Unit,
    onBotCount: (String) -> Unit,
    onBotPercent: (String) -> Unit,
    onChannel: (String) -> Unit,
    onSave: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val editable = !state.savingSettings
    SectionCard {
        SectionCardHeader("Thresholds", Icons.Default.Tune)
        Text(
            text = "Applies to the whole bot, not one server",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            text = "A server is flagged when it has at least the minimum members and either the bot count " +
                "or the bot percentage is met. Set a threshold to 0 to turn that check off.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fields: List<@Composable (Modifier) -> Unit> = listOf(
                { m ->
                    MewdekoTextField(
                        value = state.minMembersInput,
                        onValueChange = onMinMembers,
                        label = "Minimum members",
                        numeric = true,
                        enabled = editable,
                        modifier = m,
                    )
                },
                { m ->
                    MewdekoTextField(
                        value = state.botCountInput,
                        onValueChange = onBotCount,
                        label = "Bot count",
                        numeric = true,
                        enabled = editable,
                        modifier = m,
                    )
                },
                { m ->
                    MewdekoTextField(
                        value = state.botPercentInput,
                        onValueChange = onBotPercent,
                        label = "Bot percentage",
                        numeric = true,
                        enabled = editable,
                        supportingText = "0 to 100",
                        modifier = m,
                    )
                },
            )
            if (maxWidth >= WideWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    fields.forEach { field -> field(Modifier.weight(1f)) }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    fields.forEach { field -> field(Modifier.fillMaxWidth()) }
                }
            }
        }
        ChannelField(value = state.channelInput, onValueChange = onChannel, enabled = editable)
        ChannelStatus(settings = settings, error = state.settingsError)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            ToneButton(
                text = if (state.savingSettings) "Saving..." else "Save thresholds",
                tone = scheme.primary,
                icon = Icons.Default.Save,
                loading = state.savingSettings,
                onClick = onSave,
                enabled = editable && state.thresholdsDirty,
            )
        }
    }
}

/** The report channel id: numeric keyboard, monospaced, empty for the join/leave fallback. */
@Composable
private fun ChannelField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Report channel ID") },
        placeholder = { Text("Leave empty to use the join/leave channel") },
        singleLine = true,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The one status line under the channel field: a save error first, then
 * whether detections have anywhere to go, then where they go.
 */
@Composable
private fun ChannelStatus(settings: BotHellSettings, error: String?) {
    val accentInk = readableInk(MaterialTheme.colorScheme.tertiary)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (text, color) = when {
        error != null -> error to accentInk
        settings.effectiveChannelId.isZeroSnowflake ->
            ("No channel is set and there is no join/leave channel to fall back to, " +
                "so join detections are not posted anywhere.") to accentInk

        !settings.reachable ->
            "The bot cannot see channel ${settings.effectiveChannelId}, so join detections will not be posted." to
                accentInk

        else -> buildString {
            append("Join detections go to #")
            append(settings.channelName ?: settings.effectiveChannelId)
            settings.guildName?.takeIf { it.isNotBlank() }?.let { append(" in ").append(it) }
            if (settings.usingFallback) append(" (from the join/leave channel, since no channel is set)")
        } to muted
    }
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
}

/**
 * A tonal button in one palette tone: the tone at the dashboard's `20` tint
 * with a `30` border and readable ink, fading when disabled. [loading]
 * swaps the icon for a small spinner.
 */
@Composable
private fun ToneButton(
    text: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        color = tone.copy(alpha = DashAlpha.Hex20),
        contentColor = readableInk(tone),
        border = BorderStroke(1.dp, tone.copy(alpha = DashAlpha.Hex30)),
        modifier = modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(color = tone, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
