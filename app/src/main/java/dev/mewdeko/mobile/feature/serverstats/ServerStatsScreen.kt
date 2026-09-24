package dev.mewdeko.mobile.feature.serverstats

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HeadsetOff
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
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
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.navigation.GuildRouteArgs

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.Insights),
    SectionTab("top", "Rankings", Icons.Default.Leaderboard),
    SectionTab("lookup", "Lookup", Icons.Default.PersonSearch),
    SectionTab("settings", "Settings", Icons.Default.Settings),
)

/** An exclusion or filter entry waiting on removal confirmation. */
private sealed interface PendingRemoval {
    /** A channel, role, or member exclusion. */
    data class Exclusion(val kind: StatsExclusionKind, val id: Snowflake, val label: String) : PendingRemoval

    /** A game name on the activity filter list. */
    data class FilterName(val name: String) : PendingRemoval
}

/** The icon shown for a [dev.mewdeko.mobile.core.model.GuildChannelLite.type] in the channel picker. */
private fun channelTypeIcon(type: String) = when (type) {
    "voice" -> Icons.AutoMirrored.Filled.VolumeUp
    "stage" -> Icons.Default.RecordVoiceOver
    "announcement" -> Icons.Default.Campaign
    else -> Icons.Default.Tag
}

/** Lookup tables for turning ids into names in the settings lists. */
private data class NameLookups(
    val channelOptions: List<SelectorOption>,
    val roleOptions: List<SelectorOption>,
    val memberOptions: List<SelectorOption>,
    val channelNames: Map<String, String>,
    val roleNames: Map<String, String>,
    val memberNames: Map<String, String>,
)

/** Activity Stats: messages, voice time, games, and member growth over any window, plus tracking settings. */
@Composable
fun ServerstatsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: ServerStatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var pendingRemoval by remember { mutableStateOf<PendingRemoval?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) {
            viewModel.cancelExport()
        } else {
            val resolver = context.contentResolver
            viewModel.export { bytes ->
                val stream = resolver.openOutputStream(uri) ?: error("Could not open the file.")
                stream.use { it.write(bytes) }
            }
        }
    }

    val lookups = remember(state.channels, state.roles, state.members) {
        NameLookups(
            channelOptions = state.channels.map {
                SelectorOption(
                    id = it.id,
                    name = it.name,
                    subtitle = it.categoryName,
                    icon = channelTypeIcon(it.type),
                )
            },
            roleOptions = state.roles.map { SelectorOption(it.id, it.name) },
            memberOptions = state.members.map {
                SelectorOption(it.id, it.displayName.ifEmpty { it.username }, subtitle = it.username)
            },
            channelNames = state.channels.associate { it.id to it.name },
            roleNames = state.roles.associate { it.id to it.name },
            memberNames = state.members.associate { it.id to it.displayName.ifEmpty { it.username } },
        )
    }

    FeatureScaffold(
        title = "Activity Stats",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        if (state.section != "settings") {
            LookbackPicker(selected = state.lookback, onSelect = viewModel::setLookback)
        }

        when (state.section) {
            "overview" -> OverviewSection(state, viewModel)
            "top" -> RankingsSection(
                state = state,
                viewModel = viewModel,
                onExport = { exportLauncher.launch(viewModel.prepareExport()) },
            )
            "lookup" -> LookupSection(state, viewModel, lookups)
            "settings" -> SettingsSection(
                state = state,
                viewModel = viewModel,
                lookups = lookups,
                onRemove = { pendingRemoval = it },
            )
        }
    }

    when (val removal = pendingRemoval) {
        is PendingRemoval.Exclusion -> ConfirmDialog(
            title = "Stop ignoring this ${removal.kind.label}?",
            message = "${removal.label} will be counted in activity stats again from now on.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeExclusion(removal.kind, removal.id) },
            onDismiss = { pendingRemoval = null },
        )

        is PendingRemoval.FilterName -> ConfirmDialog(
            title = "Remove ${removal.name}?",
            message = "This game is taken off the activity filter list.",
            confirmLabel = "Remove",
            onConfirm = { viewModel.removeFilterName(removal.name) },
            onDismiss = { pendingRemoval = null },
        )

        null -> Unit
    }
}

@Composable
private fun ColumnScope.OverviewSection(state: ServerStatsState, viewModel: ServerStatsViewModel) {
    val overview = state.overview

    SectionCard {
        SectionCardHeader("Server activity", Icons.Default.Insights)
        when {
            overview == null && state.overviewLoading -> InlineLoading()
            overview == null && state.overviewError != null ->
                InlineError(state.overviewError, onRetry = viewModel::loadOverview)
            overview == null -> EmptyState("No activity recorded yet.", icon = Icons.Default.Insights)
            else -> {
                if (state.overviewError != null) {
                    InlineError(state.overviewError, onRetry = viewModel::loadOverview)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile(
                        label = "Messages",
                        value = statsNumber(overview.messages),
                        sub = "${statsNumber(overview.messageContributors)} chatters",
                        modifier = Modifier.weight(1f),
                    )
                    StatsTile(
                        label = "Voice time",
                        value = statsDuration(overview.voiceSeconds),
                        sub = "${statsNumber(overview.voiceContributors)} in voice",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile("Joins", statsNumber(overview.joins), Modifier.weight(1f))
                    StatsTile("Leaves", statsNumber(overview.leaves), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile(
                        label = "Net growth",
                        value = statsSigned(overview.netGrowth),
                        tint = if (overview.netGrowth >= 0) StatsColors.Members else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    StatsTile(
                        label = "Members now",
                        value = statsNumber(overview.now.members),
                        sub = "${statsNumber(overview.now.online)} online, ${statsNumber(overview.now.inVoice)} in voice",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (overview == null) return

    SectionCard {
        SectionCardHeader("Highlights", Icons.Default.EmojiEvents)
        val topChatter = overview.topMessageUser
        val topVoice = overview.topVoiceUser
        val busiest = overview.topMessageChannel
        val topGame = state.topGames.firstOrNull()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsTile(
                label = "Top chatter",
                value = topChatter?.memberLabel ?: "-",
                sub = topChatter?.let { "${statsNumber(it.value)} messages" },
                onClick = topChatter?.let { entry -> { viewModel.openUser(entry.id) } },
                modifier = Modifier.weight(1f),
            )
            StatsTile(
                label = "Top voice",
                value = topVoice?.memberLabel ?: "-",
                sub = topVoice?.let { statsDuration(it.value) },
                onClick = topVoice?.let { entry -> { viewModel.openUser(entry.id) } },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsTile(
                label = "Busiest channel",
                value = busiest?.channelLabel ?: "-",
                sub = busiest?.let { "${statsNumber(it.value)} messages" },
                onClick = busiest?.let { entry -> { viewModel.openChannel(entry.id) } },
                modifier = Modifier.weight(1f),
            )
            StatsTile(
                label = "Most played",
                value = topGame?.name ?: if (state.tracksActivities) "-" else "Game tracking off",
                sub = topGame?.let { "${statsDuration(it.seconds)}, ${it.players} players" },
                onClick = topGame?.let { game -> { viewModel.openGame(game.name) } },
                modifier = Modifier.weight(1f),
            )
        }
    }

    val hourly = state.lookback == 1 || state.lookback == 2
    val bucketLabel: (java.time.Instant) -> String = { if (hourly) it.statsDayTime() else it.statsDay() }

    SectionCard {
        SectionCardHeader("Messages", Icons.Default.ChatBubble)
        StatsLineChart(
            lines = listOf(ChartLine("Messages", state.messageSeries.map { it.value }, MaterialTheme.colorScheme.primary)),
            startLabel = state.messageSeries.firstOrNull()?.bucket?.let(bucketLabel),
            endLabel = state.messageSeries.lastOrNull()?.bucket?.let(bucketLabel),
            emptyMessage = "No messages in this window",
        )
    }

    SectionCard {
        SectionCardHeader("Voice hours", Icons.Default.Mic)
        StatsLineChart(
            lines = listOf(ChartLine("Hours", state.voiceSeries.map { it.value }, StatsColors.Voice)),
            startLabel = state.voiceSeries.firstOrNull()?.bucket?.let(bucketLabel),
            endLabel = state.voiceSeries.lastOrNull()?.bucket?.let(bucketLabel),
            emptyMessage = "No voice time in this window",
            valueFormat = { "%,.1f".format(it) },
        )
    }

    val snapshots = state.snapshots
    val snapshotLabel: (java.time.Instant) -> String =
        { if (snapshots.size > 48) it.statsDay() else it.statsDayTime() }

    SectionCard {
        SectionCardHeader("Members", Icons.Default.Groups)
        StatsLineChart(
            lines = listOf(ChartLine("Members", snapshots.map { it.members.toDouble() }, StatsColors.Members)),
            startLabel = snapshots.firstOrNull()?.timestamp?.let(snapshotLabel),
            endLabel = snapshots.lastOrNull()?.timestamp?.let(snapshotLabel),
            emptyMessage = "Snapshots start an hour after tracking is enabled",
            beginAtZero = false,
        )
    }

    SectionCard {
        SectionCardHeader("Member status", Icons.Default.Person)
        StatsLineChart(
            lines = listOf(
                ChartLine("Online", snapshots.map { it.online.toDouble() }, StatsColors.Online),
                ChartLine("Idle", snapshots.map { it.idle.toDouble() }, StatsColors.Idle),
                ChartLine("DND", snapshots.map { it.dnd.toDouble() }, StatsColors.Dnd),
                ChartLine("In voice", snapshots.map { it.inVoice.toDouble() }, StatsColors.Voice),
            ),
            startLabel = snapshots.firstOrNull()?.timestamp?.let(snapshotLabel),
            endLabel = snapshots.lastOrNull()?.timestamp?.let(snapshotLabel),
            emptyMessage = "Snapshots start an hour after tracking is enabled",
        )
    }

    SectionCard {
        SectionCardHeader("Joins and leaves", Icons.Default.Timeline)
        val joins = state.joinLeave.joins
        val leaves = state.joinLeave.leaves
        val hasAny = joins.isNotEmpty() || leaves.isNotEmpty()
        StatsLineChart(
            lines = if (hasAny) {
                listOf(
                    ChartLine("Joins", joins.map { it.value }, StatsColors.Joins),
                    ChartLine("Leaves", leaves.map { it.value }, StatsColors.Leaves),
                )
            } else {
                emptyList()
            },
            startLabel = (joins.firstOrNull() ?: leaves.firstOrNull())?.bucket?.statsDay(),
            endLabel = (joins.lastOrNull() ?: leaves.lastOrNull())?.bucket?.statsDay(),
            emptyMessage = "No joins or leaves in this window",
        )
    }
}

@Composable
private fun ColumnScope.RankingsSection(
    state: ServerStatsState,
    viewModel: ServerStatsViewModel,
    onExport: () -> Unit,
) {
    val kind = state.rankKind

    SectionCard {
        SectionCardHeader(
            title = "Rankings",
            icon = Icons.Default.Leaderboard,
            trailing = {
                if (state.isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Default.Download, contentDescription = "Export as CSV")
                    }
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.rankKinds.forEach { option ->
                FilterChip(
                    selected = kind == option,
                    onClick = { viewModel.setRankKind(option) },
                    label = { Text(option.label) },
                )
            }
        }
    }

    val nothing = state.topUsers.isEmpty() && state.topActivities.isEmpty()
    when {
        state.topLoading && nothing -> SectionCard { InlineLoading() }
        state.topError != null -> SectionCard { InlineError(state.topError, onRetry = viewModel::loadTop) }
        nothing -> SectionCard {
            EmptyState("No activity recorded for this window", icon = Icons.Default.Leaderboard)
        }
        else -> {
            SectionCard {
                SectionCardHeader(
                    title = if (kind == StatKind.ACTIVITY) "Most time in games" else "Top members",
                    icon = Icons.Default.Groups,
                )
                if (state.topUsers.isEmpty()) {
                    EmptyState("No members ranked in this window.")
                } else {
                    state.topUsers.forEach { row ->
                        RankRow(
                            rank = row.rank,
                            title = row.entry.memberLabel,
                            value = statsValue(kind, row.entry.value),
                            avatarUrl = row.entry.avatarUrl,
                            showAvatar = true,
                            onClick = { viewModel.openUser(row.entry.id) },
                        )
                    }
                }
            }

            if (kind == StatKind.ACTIVITY) {
                SectionCard {
                    SectionCardHeader("Top games and apps", Icons.Default.SportsEsports)
                    if (state.topActivities.isEmpty()) {
                        EmptyState("No games or apps recorded in this window.")
                    } else {
                        state.topActivities.forEach { game ->
                            RankRow(
                                rank = game.rank,
                                title = game.name,
                                value = statsDuration(game.seconds),
                                detail = "${game.players} ${if (game.players == 1) "player" else "players"}, " +
                                    "${game.activeNow} now, ${game.type}",
                                onClick = { viewModel.openGame(game.name) },
                            )
                        }
                    }
                }
            } else {
                SectionCard {
                    SectionCardHeader("Top channels", Icons.Default.Tag)
                    if (state.topChannels.isEmpty()) {
                        EmptyState("No channel activity in this window.")
                    } else {
                        state.topChannels.forEach { row ->
                            RankRow(
                                rank = row.rank,
                                title = row.entry.channelLabel,
                                value = statsValue(kind, row.entry.value),
                                onClick = { viewModel.openChannel(row.entry.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.LookupSection(
    state: ServerStatsState,
    viewModel: ServerStatsViewModel,
    lookups: NameLookups,
) {
    SectionCard {
        SectionCardHeader("Member", Icons.Default.Person)
        DiscordSelectorSingle(
            kind = SelectorKind.User,
            options = lookups.memberOptions,
            placeholder = "Pick a member",
            selectedId = state.lookupUserId,
            onSelect = viewModel::selectUser,
        )
        val activity = state.userActivity
        when {
            state.userLoading && activity == null -> InlineLoading()
            state.userError != null -> InlineError(state.userError, onRetry = viewModel::loadUser)
            activity == null -> EmptyState("Pick a member to see their messages, voice time and games.")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile(
                        label = "Messages",
                        value = statsNumber(activity.messages),
                        sub = activity.messageRank?.let { "Rank #$it" },
                        modifier = Modifier.weight(1f),
                    )
                    StatsTile(
                        label = "Voice",
                        value = statsDuration(activity.voiceSeconds),
                        sub = activity.voiceRank?.let { "Rank #$it" },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile("Lifetime messages", statsNumber(activity.allTimeMessages), Modifier.weight(1f))
                    StatsTile("Lifetime voice", statsDuration(activity.allTimeVoiceSeconds), Modifier.weight(1f))
                }
                if (activity.topMessageChannels.isNotEmpty()) {
                    SubHeading("Chats most in")
                    activity.topMessageChannels.forEach {
                        ValueLine(it.channelLabel, statsNumber(it.value))
                    }
                }
                if (activity.topVoiceChannels.isNotEmpty()) {
                    SubHeading("Voice channels")
                    activity.topVoiceChannels.forEach {
                        ValueLine(it.channelLabel, statsDuration(it.value))
                    }
                }
                if (state.userGames.isNotEmpty()) {
                    SubHeading("Games and apps")
                    state.userGames.forEach { game ->
                        ValueLine(
                            label = game.name,
                            value = statsDuration(game.seconds),
                            onClick = { viewModel.openGame(game.name) },
                        )
                    }
                }
                if (activity.topMessageChannels.isEmpty() && activity.topVoiceChannels.isEmpty() &&
                    state.userGames.isEmpty()
                ) {
                    EmptyState("No channel or game activity in this window.")
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Channel", Icons.Default.Tag)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = lookups.channelOptions,
            placeholder = "Pick a channel",
            selectedId = state.lookupChannelId,
            onSelect = viewModel::selectChannel,
        )
        val activity = state.channelActivity
        when {
            state.channelLoading && activity == null -> InlineLoading()
            state.channelError != null -> InlineError(state.channelError, onRetry = viewModel::loadChannel)
            activity == null -> EmptyState("Pick a channel to see who is active in it.")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile("Messages", statsNumber(activity.messages), Modifier.weight(1f))
                    StatsTile("Voice", statsDuration(activity.voiceSeconds), Modifier.weight(1f))
                    StatsTile("People", statsNumber(activity.contributors), Modifier.weight(1f))
                }
                if (activity.topMessageUsers.isNotEmpty()) {
                    SubHeading("Top chatters")
                    activity.topMessageUsers.forEach { entry ->
                        ValueLine(
                            label = entry.memberLabel,
                            value = statsNumber(entry.value),
                            onClick = { viewModel.openUser(entry.id) },
                        )
                    }
                }
                if (activity.topVoiceUsers.isNotEmpty()) {
                    SubHeading("Top voice")
                    activity.topVoiceUsers.forEach { entry ->
                        ValueLine(
                            label = entry.memberLabel,
                            value = statsDuration(entry.value),
                            onClick = { viewModel.openUser(entry.id) },
                        )
                    }
                }
                if (activity.topMessageUsers.isEmpty() && activity.topVoiceUsers.isEmpty()) {
                    EmptyState("Nobody was active here in this window.")
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Who plays", Icons.Default.SportsEsports)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MewdekoTextField(
                value = state.gameQuery,
                onValueChange = viewModel::setGameQuery,
                label = "Game or app name",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::loadGame, enabled = state.gameQuery.isNotBlank()) {
                Icon(Icons.Default.Search, contentDescription = "Look up")
            }
        }
        val detail = state.gameDetail
        when {
            !state.tracksActivities ->
                EmptyState("Game tracking is off. Turn it on under Settings.", icon = Icons.Default.SportsEsports)
            state.gameLoading && detail == null -> InlineLoading()
            state.gameError != null -> InlineError(state.gameError, onRetry = viewModel::loadGame)
            detail == null -> EmptyState("Type a game to see who plays it and for how long.")
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatsTile("Playing now", statsNumber(detail.activeNow), Modifier.weight(1f))
                    StatsTile("Players", statsNumber(detail.players), Modifier.weight(1f))
                    StatsTile("Total time", statsDuration(detail.totalSeconds), Modifier.weight(1f))
                }
                if (detail.top.isEmpty()) {
                    EmptyState("Nobody has been seen in ${detail.name} in this window.")
                } else {
                    detail.top.forEach { row ->
                        RankRow(
                            rank = row.rank,
                            title = row.entry.memberLabel,
                            value = statsDuration(row.entry.value),
                            avatarUrl = row.entry.avatarUrl,
                            showAvatar = true,
                            onClick = { viewModel.openUser(row.entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsSection(
    state: ServerStatsState,
    viewModel: ServerStatsViewModel,
    lookups: NameLookups,
    onRemove: (PendingRemoval) -> Unit,
) {
    val settings = state.settings
    if (settings == null) {
        SectionCard { EmptyState("Settings are unavailable.", icon = Icons.Default.Settings) }
        return
    }

    SectionCard {
        SectionCardHeader("Tracking", Icons.Default.Tune)
        SwitchRow(
            title = "Track voice time",
            subtitle = "Seconds spent in voice channels, per member and channel",
            checked = settings.trackVoice,
            onCheckedChange = viewModel::setTrackVoice,
        )
        SwitchRow(
            title = "Hourly snapshots",
            subtitle = "Member and status counts every hour, for the member and status charts",
            checked = settings.trackSnapshots,
            onCheckedChange = viewModel::setTrackSnapshots,
        )
        SwitchRow(
            title = "Count bots",
            subtitle = "Include bot accounts in message and voice stats",
            checked = settings.countBots,
            onCheckedChange = viewModel::setCountBots,
        )
        SwitchRow(
            title = "Track games and apps",
            subtitle = "Time members spend playing, streaming, listening or watching. Off by default; " +
                "the heaviest tracker.",
            checked = settings.trackActivities,
            onCheckedChange = viewModel::setTrackActivities,
        )
        SwitchRow(
            title = "Verify activities",
            subtitle = "Only count activities backed by a Discord application, Spotify or a stream, which " +
                "blocks spoofed presences",
            checked = settings.verifyActivities,
            onCheckedChange = viewModel::setVerifyActivities,
            enabled = settings.trackActivities,
        )
    }

    SectionCard {
        SectionCardHeader("Windows and cooldown", Icons.Default.Timeline)
        val lookbackValue = state.lookbackDraft.toIntOrNull()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MewdekoTextField(
                value = state.lookbackDraft,
                onValueChange = viewModel::setLookbackDraft,
                label = "Default window (days)",
                numeric = true,
                isError = lookbackValue == null || lookbackValue !in 1..90,
                supportingText = "1 to 90. Used when no window is picked.",
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::saveLookbackDefault,
                enabled = lookbackValue != null && lookbackValue != settings.defaultLookbackDays,
            ) { Text("Save") }
        }
        val cooldownValue = state.cooldownDraft.toIntOrNull()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MewdekoTextField(
                value = state.cooldownDraft,
                onValueChange = viewModel::setCooldownDraft,
                label = "Message cooldown (seconds)",
                numeric = true,
                isError = cooldownValue == null || cooldownValue !in 0..300,
                supportingText = "0 to 300. 0 counts every message.",
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = viewModel::saveCooldown,
                enabled = cooldownValue != null && cooldownValue != settings.messageCooldownSeconds,
            ) { Text("Save") }
        }
    }

    SectionCard {
        SectionCardHeader("Voice states that do not count", Icons.Default.HeadsetOff)
        Text(
            text = "Toggle a state to leave that time out of voice stats.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VoiceStateFlag.entries.forEach { flag ->
            SwitchRow(
                title = flag.label,
                subtitle = flag.hint,
                checked = flag.isSet(settings.voiceStates),
                onCheckedChange = { viewModel.setVoiceStateIgnored(flag, it) },
            )
        }
    }

    ExclusionCard(
        title = "Ignored channels",
        note = "Messages and voice here are not counted.",
        kind = StatsExclusionKind.CHANNEL,
        selectorKind = SelectorKind.Channel,
        options = lookups.channelOptions,
        placeholder = "Add a channel",
        ids = state.exclusions.channels,
        nameOf = { id -> "#${lookups.channelNames[id] ?: id}" },
        viewModel = viewModel,
        onRemove = onRemove,
    )

    ExclusionCard(
        title = "Ignored roles",
        note = "Holders are not counted.",
        kind = StatsExclusionKind.ROLE,
        selectorKind = SelectorKind.Role,
        options = lookups.roleOptions,
        placeholder = "Add a role",
        ids = state.exclusions.roles,
        nameOf = { id -> "@${lookups.roleNames[id] ?: id}" },
        viewModel = viewModel,
        onRemove = onRemove,
    )

    ExclusionCard(
        title = "Ignored members",
        note = "Not counted in this server.",
        kind = StatsExclusionKind.USER,
        selectorKind = SelectorKind.User,
        options = lookups.memberOptions,
        placeholder = "Add a member",
        ids = state.exclusions.users,
        nameOf = { id -> lookups.memberNames[id] ?: id },
        viewModel = viewModel,
        onRemove = onRemove,
    )

    SectionCard {
        SectionCardHeader("Activity filter", Icons.Default.FilterAlt)
        val mode = ActivityFilterMode.entries.firstOrNull { it.value == settings.activityFilterMode }
            ?: ActivityFilterMode.BLACKLIST
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityFilterMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { if (mode != option) viewModel.setActivityFilterMode(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            text = mode.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MewdekoTextField(
                value = state.filterDraft,
                onValueChange = viewModel::setFilterDraft,
                label = "Game or app name",
                supportingText = "Up to 128 characters",
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::addFilterName, enabled = state.filterDraft.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = "Add game")
            }
        }
        val names = state.activityFilters.names
        if (names.isEmpty()) {
            EmptyState("No games listed.", icon = Icons.Default.SportsEsports)
        } else {
            names.forEachIndexed { index, name ->
                if (index > 0) HorizontalDivider()
                RemovableRow(
                    label = name,
                    contentDescription = "Remove $name",
                    onRemove = { onRemove(PendingRemoval.FilterName(name)) },
                )
            }
        }
    }
}

@Composable
private fun ExclusionCard(
    title: String,
    note: String,
    kind: StatsExclusionKind,
    selectorKind: SelectorKind,
    options: List<SelectorOption>,
    placeholder: String,
    ids: List<Snowflake>,
    nameOf: (Snowflake) -> String,
    viewModel: ServerStatsViewModel,
    onRemove: (PendingRemoval) -> Unit,
) {
    var draft by remember { mutableStateOf<Snowflake?>(null) }
    val available = remember(options, ids) { options.filter { it.id !in ids } }

    SectionCard {
        SectionCardHeader(title, Icons.Default.Block)
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DiscordSelectorSingle(
                kind = selectorKind,
                options = available,
                placeholder = placeholder,
                selectedId = draft,
                onSelect = { draft = it },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    viewModel.addExclusion(kind, draft)
                    draft = null
                },
                enabled = draft != null,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        if (ids.isEmpty()) {
            EmptyState("Nothing ignored.")
        } else {
            ids.forEachIndexed { index, id ->
                if (index > 0) HorizontalDivider()
                val label = nameOf(id)
                RemovableRow(
                    label = label,
                    contentDescription = "Remove $label",
                    onRemove = { onRemove(PendingRemoval.Exclusion(kind, id, label)) },
                )
            }
        }
    }
}

@Composable
private fun RemovableRow(label: String, contentDescription: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
