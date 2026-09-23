package dev.mewdeko.mobile.feature.invites

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
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
import dev.mewdeko.mobile.util.withSeparators

private val Tabs = listOf(
    SectionTab("overview", "Overview", Icons.Default.Groups),
    SectionTab("leaderboard", "Leaders", Icons.Default.Leaderboard),
    SectionTab("members", "Members", Icons.Default.PersonOff),
    SectionTab("codes", "Codes", Icons.Default.Link),
    SectionTab("settings", "Settings", Icons.Default.Tune),
)

private val RangeOptions = InviteStatsRange.entries.map { SelectorOption(it.value.toString(), it.label) }

/** Invite tracking: growth analytics, leaderboard, invited members, codes/labels, and settings. */
@Composable
fun InvitesScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: InvitesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var pendingReset by remember { mutableStateOf<InviteResetScope?>(null) }
    var pendingDeleteCode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.pendingExport) {
        val export = state.pendingExport ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, export.filename)
            putExtra(Intent.EXTRA_TEXT, export.content)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${export.filename}"))
        viewModel.clearPendingExport()
    }

    FeatureScaffold(
        title = "Invites",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
    ) {
        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "overview" -> OverviewSection(state, viewModel)
            "leaderboard" -> LeaderboardSection(state, viewModel)
            "members" -> MembersSection(state, viewModel)
            "codes" -> CodesSection(state, viewModel, onDeleteCode = { pendingDeleteCode = it })
            "settings" -> SettingsSection(
                state = state,
                viewModel = viewModel,
                onResetAll = { pendingReset = it },
            )
        }
    }

    pendingReset?.let { scope ->
        ConfirmDialog(
            title = if (scope == InviteResetScope.SERVER) "Reset every invite?" else "Reset invites of members who left?",
            message = if (scope == InviteResetScope.SERVER) {
                "This clears every inviter's tally and the join history for this server. It cannot be undone."
            } else {
                "This clears the tally of every inviter who is no longer in the server."
            },
            confirmLabel = "Reset",
            onConfirm = { viewModel.resetAll(scope) },
            onDismiss = { pendingReset = null },
        )
    }

    pendingDeleteCode?.let { code ->
        ConfirmDialog(
            title = "Delete invite $code?",
            message = "Anyone holding the link will no longer be able to use it.",
            confirmLabel = "Delete",
            onConfirm = { viewModel.deleteCode(code) },
            onDismiss = { pendingDeleteCode = null },
        )
    }
}

// region Overview

@Composable
private fun OverviewSection(state: InvitesState, viewModel: InvitesViewModel) {
    val analytics = state.analytics

    SectionCard {
        SectionCardHeader("Growth", Icons.Default.Groups)
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Schedule),
            options = RangeOptions,
            placeholder = "All time",
            label = "Window",
            selectedId = state.overviewRange.value.toString(),
            onSelect = { viewModel.setOverviewRange(InviteStatsRange.from(it?.toIntOrNull() ?: 3)) },
        )

        if (analytics == null) {
            EmptyState("No joins recorded yet.", icon = Icons.Default.Groups)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Joins", analytics.joins.withSeparators(), Modifier.weight(1f))
                StatTile("Leaves", analytics.leaves.withSeparators(), Modifier.weight(1f))
                StatTile("Net growth", formatSigned(analytics.netGrowth), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Retention", formatPercent(analytics.retention), Modifier.weight(1f))
                StatTile("Fake joins", analytics.fakeJoins.withSeparators(), Modifier.weight(1f))
                StatTile("Stayed", analytics.stayed.withSeparators(), Modifier.weight(1f))
            }
            Text(
                text = "Via invites ${analytics.sources.invite} · vanity ${analytics.sources.vanity} · " +
                    "bots ${analytics.sources.bot} · unknown ${analytics.sources.unknown}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (analytics != null && analytics.series.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Joins and leaves", Icons.Default.Leaderboard)
            GrowthChart(analytics.series, modifier = Modifier.fillMaxWidth().height(160.dp))
        }
    }

    if (analytics != null) {
        SectionCard {
            SectionCardHeader("Top inviters", Icons.Default.Leaderboard)
            if (analytics.topInviters.isEmpty()) {
                EmptyState("Nobody has invited anyone in this window.")
            } else {
                analytics.topInviters.forEachIndexed { index, entry ->
                    ListItem(
                        leadingContent = { RankBadge(index + 1) },
                        headlineContent = { Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text("${entry.regular} regular · ${entry.left} left · ${entry.fake} fake")
                        },
                        trailingContent = { Text(entry.total.withSeparators(), style = MaterialTheme.typography.titleMedium) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { viewModel.openDetail(entry.userId); viewModel.setSection("leaderboard") },
                    )
                }
            }
        }

        SectionCard {
            SectionCardHeader("Top invite codes", Icons.Default.Link)
            if (analytics.topCodes.isEmpty()) {
                EmptyState("No invite codes were used in this window.")
            } else {
                analytics.topCodes.forEach { code ->
                    ListItem(
                        headlineContent = { Text(code.code, style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = code.label?.let { { Text(it) } },
                        trailingContent = { Text(code.joins.withSeparators(), style = MaterialTheme.typography.titleSmall) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }
}

@Composable
private fun GrowthChart(series: List<GrowthPoint>, modifier: Modifier = Modifier) {
    val joinColor = MaterialTheme.colorScheme.primary
    val leaveColor = MaterialTheme.colorScheme.error
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val peak = maxOf(series.maxOfOrNull { it.joins } ?: 0, series.maxOfOrNull { it.leaves } ?: 0, 1)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                repeat(4) { step ->
                    val y = size.height * step / 3f
                    drawLine(gridColor.copy(alpha = 0.4f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                drawGrowthSeries(series.map { it.joins }, peak, joinColor, fill = true)
                drawGrowthSeries(series.map { it.leaves }, peak, leaveColor, fill = false)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot("Joins", joinColor)
            LegendDot("Leaves", leaveColor)
            Text("Peak $peak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.drawGrowthSeries(values: List<Int>, peak: Int, color: Color, fill: Boolean) {
    if (values.size < 2) return
    val stepX = size.width / (values.size - 1).toFloat()
    fun pointAt(index: Int): Offset {
        val ratio = values[index].toFloat() / peak.toFloat()
        return Offset(index * stepX, size.height - (ratio * size.height))
    }
    val line = Path().apply {
        moveTo(pointAt(0).x, pointAt(0).y)
        for (index in 1 until values.size) lineTo(pointAt(index).x, pointAt(index).y)
    }
    if (fill) {
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(area, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
    }
    drawPath(line, color = color, style = Stroke(width = 2.5f))
}

// endregion

// region Leaderboard

@Composable
private fun LeaderboardSection(state: InvitesState, viewModel: InvitesViewModel) {
    SectionCard {
        SectionCardHeader("Leaderboard", Icons.Default.Leaderboard)
        Text(
            text = "Net total is regular minus left minus fake, plus bonus.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Custom(Icons.Default.Schedule),
            options = RangeOptions,
            placeholder = "All time",
            label = "Window",
            selectedId = state.boardRange.value.toString(),
            onSelect = { viewModel.setBoardRange(InviteStatsRange.from(it?.toIntOrNull() ?: 0)) },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.roles.map { SelectorOption(it.id, it.name) },
            placeholder = "Any role",
            label = "Role filter",
            selectedId = state.boardRoleId,
            onSelect = viewModel::setBoardRole,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::exportLeaderboard, enabled = !state.boardExporting) {
                Icon(Icons.Default.Download, contentDescription = null)
                Text("  Export CSV")
            }
        }
    }

    SectionCard {
        if (state.leaderboard.isEmpty()) {
            EmptyState("No invites recorded for this window.", icon = Icons.Default.Groups)
        } else {
            state.leaderboard.forEach { entry ->
                ListItem(
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RankBadge(entry.rank)
                            Avatar(entry.avatarUrl, contentDescription = entry.username, size = 32)
                        }
                    },
                    headlineContent = { Text(entry.username, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            "${entry.regular} regular · ${entry.left} left · ${entry.fake} fake · " +
                                "${entry.bonus} bonus" +
                                (entry.retention?.let { " · ${formatPercent(it)} retained" } ?: ""),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = { Text(entry.total.withSeparators(), style = MaterialTheme.typography.titleMedium) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { viewModel.openDetail(entry.userId) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::boardPreviousPage, enabled = state.boardPage > 1) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
                }
                Text("Page ${state.boardPage}", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = viewModel::boardNextPage, enabled = state.hasNextBoardPage) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
                }
            }
        }
    }

    SectionCard {
        SectionCardHeader("Member lookup", Icons.Default.Groups)
        DiscordSelectorSingle(
            kind = SelectorKind.User,
            options = state.guildMembers.map { SelectorOption(it.id, it.displayName) },
            placeholder = "Pick a member",
            selectedId = state.detailUserId,
            onSelect = { it?.let(viewModel::openDetail) },
        )

        if (state.detailLoading) {
            Text("Loading…", style = MaterialTheme.typography.bodySmall)
        } else {
            val detail = state.detailBreakdown
            val inviter = state.detailInviter
            if (state.detailUserId != null && detail != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Total", detail.total.withSeparators(), Modifier.weight(1f), tint = MaterialTheme.colorScheme.primary)
                    StatTile("Rank", detail.rank?.let { "#$it" } ?: "Unranked", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Regular", detail.regular.withSeparators(), Modifier.weight(1f))
                    StatTile("Left", detail.left.withSeparators(), Modifier.weight(1f))
                    StatTile("Fake", detail.fake.withSeparators(), Modifier.weight(1f))
                    StatTile("Bonus", detail.bonus.withSeparators(), Modifier.weight(1f))
                }
                InfoRow(
                    label = "Invited by",
                    value = inviter?.inviter?.username ?: (inviter?.joinType ?: "Unknown"),
                )
                inviter?.inviteCode?.let { InfoRow("Via code", it) }
                if (state.detailInvited.isNotEmpty()) {
                    InfoRow(
                        label = "Invited (${state.detailInvited.size})",
                        value = state.detailInvited.take(5).joinToString { it.username },
                    )
                }

                HorizontalDivider()
                Text("Adjust", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MewdekoTextField(
                        value = state.adjustRegular.toString(),
                        onValueChange = { viewModel.setAdjustRegular(it.toIntOrNull() ?: 0) },
                        label = "Regular",
                        modifier = Modifier.weight(1f),
                    )
                    MewdekoTextField(
                        value = state.adjustBonus.toString(),
                        onValueChange = { viewModel.setAdjustBonus(it.toIntOrNull() ?: 0) },
                        label = "Bonus",
                        modifier = Modifier.weight(1f),
                    )
                    MewdekoTextField(
                        value = state.adjustFake.toString(),
                        onValueChange = { viewModel.setAdjustFake(it.toIntOrNull() ?: 0) },
                        label = "Fake",
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::applyAdjust, enabled = !state.adjusting, modifier = Modifier.weight(1f)) {
                        Text("Apply")
                    }
                    var pendingReset by remember { mutableStateOf(false) }
                    OutlinedButton(onClick = { pendingReset = true }) { Text("Reset") }
                    if (pendingReset) {
                        ConfirmDialog(
                            title = "Reset this member's invites?",
                            message = "This cannot be undone.",
                            confirmLabel = "Reset",
                            onConfirm = { viewModel.resetMember() },
                            onDismiss = { pendingReset = false },
                        )
                    }
                }
            } else {
                EmptyState("Pick a member, or tap a leaderboard row, to see their breakdown.")
            }
        }
    }
}

@Composable
private fun RankBadge(rank: Int) {
    Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Text(
            text = if (rank > 0) "$rank" else "-",
            style = MaterialTheme.typography.labelLarge,
            color = when (rank) {
                1 -> MaterialTheme.colorScheme.primary
                2, 3 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

// endregion

// region Members

@Composable
private fun MembersSection(state: InvitesState, viewModel: InvitesViewModel) {
    SectionCard {
        SectionCardHeader("Invited members", Icons.Default.PersonOff)
        Text(
            "Every witnessed join, filtered by inviter, code or label.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.User,
            options = state.guildMembers.map { SelectorOption(it.id, it.displayName) },
            placeholder = "Any inviter",
            label = "Inviter",
            selectedId = state.memberFilterInviter,
            onSelect = viewModel::setMemberFilterInviter,
        )
        MewdekoTextField(
            value = state.memberFilterCode,
            onValueChange = viewModel::setMemberFilterCode,
            label = "Invite code",
            placeholder = "Optional",
        )
        MewdekoTextField(
            value = state.memberFilterLabel,
            onValueChange = viewModel::setMemberFilterLabel,
            label = "Label",
            placeholder = "Optional",
        )
        Button(onClick = viewModel::applyMemberFilters, modifier = Modifier.fillMaxWidth()) {
            Text("Apply filters")
        }
        SwitchRow(
            title = "Include members who left",
            subtitle = "Show joins whose member has since left",
            checked = state.memberIncludeLeft,
            onCheckedChange = viewModel::setMemberIncludeLeft,
        )
        OutlinedButton(onClick = viewModel::exportInvited, enabled = !state.membersExporting) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text("  Export CSV")
        }
    }

    SectionCard {
        val page = state.invitedPage
        if (page == null || page.items.isEmpty()) {
            EmptyState("No joins match these filters.", icon = Icons.Default.PersonOff)
        } else {
            page.items.forEach { item -> InvitedRow(item, state.guildMembers) }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::memberPreviousPage, enabled = state.memberPage > 1) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous page")
                }
                Text(
                    "Page ${state.memberPage} of ${state.memberPageCount} · ${page.total.withSeparators()} joins",
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = viewModel::memberNextPage, enabled = state.memberPage < state.memberPageCount) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next page")
                }
            }
        }
    }
}

@Composable
private fun InvitedRow(item: InvitedRecord, guildMembers: List<InviteMemberLite>) {
    val inviterName = item.inviterId.takeIf { it.isNotEmpty() && it != "0" }
        ?.let { id -> guildMembers.find { it.id == id }?.displayName ?: id }
        ?: "-"
    ListItem(
        leadingContent = { Avatar(item.avatarUrl, contentDescription = item.username, size = 32) },
        headlineContent = {
            Text(item.username ?: item.userId, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = "Inviter: $inviterName · ${item.inviteCode ?: "-"} · ${item.joinType.humanize()} · " +
                    (item.joinedAt?.relativeToNow() ?: "unknown time"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            when {
                item.isFake -> TagChip("Fake: ${item.fakeReason.humanize()}")
                item.leftAt != null -> TagChip("Left")
                else -> TagChip("Present")
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

// endregion

// region Codes

@Composable
private fun CodesSection(state: InvitesState, viewModel: InvitesViewModel, onDeleteCode: (String) -> Unit) {
    SectionCard {
        SectionCardHeader("Invite codes", Icons.Default.Link)
        Text(
            "Name codes so they show up in stats, and grant a role to everyone who joins through one.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (state.codes.isEmpty()) {
        SectionCard { EmptyState("This server has no invite codes.", icon = Icons.Default.Link) }
    } else {
        state.codes.forEach { code -> CodeCard(code, state, viewModel, onDeleteCode) }
    }

    val orphans = state.labels.filter { label -> state.codes.none { it.code == label.inviteCode } }
    if (orphans.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Labels for codes that no longer exist", Icons.Default.Link)
            orphans.forEach { label ->
                ListItem(
                    headlineContent = { Text("${label.inviteCode} · ${label.label}") },
                    trailingContent = {
                        TextButton(onClick = { viewModel.removeOrphanLabel(label.inviteCode) }) { Text("Remove") }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun CodeCard(
    code: GuildInviteCode,
    state: InvitesState,
    viewModel: InvitesViewModel,
    onDeleteCode: (String) -> Unit,
) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(code.code, style = MaterialTheme.typography.titleSmall)
                val owner = code.inviterName ?: code.ownerUserId?.let { id ->
                    state.guildMembers.find { it.id == id }?.displayName
                } ?: "unknown"
                val channel = state.channels.find { it.id == code.channelId }?.name ?: code.channelId
                Text(
                    text = "$owner · #$channel · ${code.uses} uses" +
                        (code.maxUses?.let { " of $it" } ?: "") +
                        (if (code.maxAge != null) " · expires" else "") +
                        (if (code.isTemporary) " · temporary" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            code.ownerUserId?.let { ownerId ->
                TagChip("credits ${state.guildMembers.find { it.id == ownerId }?.displayName ?: ownerId}")
            }
            IconButton(onClick = { onDeleteCode(code.code) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete ${code.code}")
            }
        }

        val draft = state.labelDrafts[code.code] ?: LabelDraft()
        MewdekoTextField(
            value = draft.label,
            onValueChange = { viewModel.setLabelDraftText(code.code, it.take(64)) },
            label = "Label",
            placeholder = "e.g. Twitter campaign",
            supportingText = "Shown in stats, exports and greet placeholders. Clearing removes the label.",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = state.roles.map { SelectorOption(it.id, it.name) },
            placeholder = "No role",
            label = "Role on join",
            selectedId = draft.roleId,
            onSelect = { viewModel.setLabelDraftRole(code.code, it) },
        )
        Button(onClick = { viewModel.saveLabel(code.code) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Save, contentDescription = null)
            Text("  Save")
        }
    }
}

// endregion

// region Settings

@Composable
private fun SettingsSection(
    state: InvitesState,
    viewModel: InvitesViewModel,
    onResetAll: (InviteResetScope) -> Unit,
) {
    val settings = state.settings

    SectionCard {
        SectionCardHeader("Invite tracking", Icons.Default.Tune)
        SwitchRow(
            title = "Track invites",
            subtitle = "Attribute every join to an invite, the vanity URL or an app",
            checked = settings?.isEnabled == true,
            onCheckedChange = viewModel::setEnabled,
        )
        SwitchRow(
            title = "Remove credit when members leave",
            subtitle = "The inviter's left count goes up and their total goes down",
            checked = settings?.removeInviteOnLeave == true,
            onCheckedChange = viewModel::setRemoveOnLeave,
        )
        SwitchRow(
            title = "Count rejoins",
            subtitle = "Off flags members who have joined before as fake invites",
            checked = settings?.countRejoins == true,
            onCheckedChange = viewModel::setCountRejoins,
        )
        SwitchRow(
            title = "Flag members without an avatar",
            subtitle = "Joins from accounts with no avatar count as fake",
            checked = settings?.fakeOnNoAvatar == true,
            onCheckedChange = viewModel::setFakeOnNoAvatar,
        )
    }

    SectionCard {
        SectionCardHeader("Minimum account age", Icons.Default.Schedule)
        Text(
            "Accounts younger than this are flagged as fake. 0 disables the check.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MewdekoTextField(
            value = state.minAgeDays.toString(),
            onValueChange = { viewModel.setMinAgeDays(it.toIntOrNull() ?: 0) },
            label = "Days",
            numeric = true,
        )
        Button(onClick = viewModel::saveMinAge, modifier = Modifier.fillMaxWidth()) { Text("Save") }
    }

    SectionCard {
        SectionCardHeader("Channels", Icons.Default.Link)
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.channels.map { SelectorOption(it.id, it.name) },
            placeholder = "System channel",
            label = "Personal link channel",
            selectedId = settings?.linkChannelId,
            onSelect = viewModel::setLinkChannel,
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = state.channels.map { SelectorOption(it.id, it.name) },
            placeholder = "Disabled",
            label = "Join and leave log channel",
            selectedId = settings?.logChannelId,
            onSelect = viewModel::setLogChannel,
        )
    }

    ExclusionCard(
        title = "Blacklisted inviters",
        note = InviteExclusionKind.BLACKLISTED_USER.note,
        icon = Icons.Default.Block,
        options = state.guildMembers.map { SelectorOption(it.id, it.displayName) },
        selectorKind = SelectorKind.User,
        ids = state.blacklistedUsers,
        nameFor = { id -> state.guildMembers.find { it.id == id }?.displayName ?: id },
        onAdd = { viewModel.addExclusion(InviteExclusionKind.BLACKLISTED_USER, it) },
        onRemove = { viewModel.removeExclusion(InviteExclusionKind.BLACKLISTED_USER, it) },
    )
    ExclusionCard(
        title = "Blacklisted roles",
        note = InviteExclusionKind.BLACKLISTED_ROLE.note,
        icon = Icons.Default.Block,
        options = state.roles.map { SelectorOption(it.id, it.name) },
        selectorKind = SelectorKind.Role,
        ids = state.blacklistedRoles,
        nameFor = { id -> "@" + (state.roles.find { it.id == id }?.name ?: id) },
        onAdd = { viewModel.addExclusion(InviteExclusionKind.BLACKLISTED_ROLE, it) },
        onRemove = { viewModel.removeExclusion(InviteExclusionKind.BLACKLISTED_ROLE, it) },
    )
    ExclusionCard(
        title = "Hidden from leaderboard",
        note = InviteExclusionKind.HIDDEN_USER.note,
        icon = Icons.Default.VisibilityOff,
        options = state.guildMembers.map { SelectorOption(it.id, it.displayName) },
        selectorKind = SelectorKind.User,
        ids = state.hiddenUsers,
        nameFor = { id -> state.guildMembers.find { it.id == id }?.displayName ?: id },
        onAdd = { viewModel.addExclusion(InviteExclusionKind.HIDDEN_USER, it) },
        onRemove = { viewModel.removeExclusion(InviteExclusionKind.HIDDEN_USER, it) },
    )

    SectionCard {
        SectionCardHeader("Maintenance", Icons.Default.Sync)
        Text(
            "Importing raises each inviter's regular total to the sum of uses across their codes and never " +
                "lowers it, so it is safe to run again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = viewModel::syncInvites, enabled = !state.syncing, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Sync, contentDescription = null)
            Text("  Import uses from Discord")
        }
        OutlinedButton(onClick = { onResetAll(InviteResetScope.LEFT_MEMBERS) }, modifier = Modifier.fillMaxWidth()) {
            Text("Reset inviters who left")
        }
        OutlinedButton(onClick = { onResetAll(InviteResetScope.SERVER) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Warning, contentDescription = null)
            Text("  Reset everything")
        }
    }
}

@Composable
private fun ExclusionCard(
    title: String,
    note: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<SelectorOption>,
    selectorKind: SelectorKind,
    ids: List<Snowflake>,
    nameFor: (Snowflake) -> String,
    onAdd: (Snowflake?) -> Unit,
    onRemove: (Snowflake) -> Unit,
) {
    var pending by remember { mutableStateOf<Snowflake?>(null) }
    SectionCard {
        SectionCardHeader(title, icon)
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                DiscordSelectorSingle(
                    kind = selectorKind,
                    options = options,
                    placeholder = "Add",
                    selectedId = pending,
                    onSelect = { pending = it },
                )
            }
            Button(onClick = { onAdd(pending); pending = null }, enabled = pending != null) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
        if (ids.isEmpty()) {
            EmptyState("None yet.")
        } else {
            ids.forEach { id ->
                ListItem(
                    headlineContent = { Text(nameFor(id), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingContent = {
                        IconButton(onClick = { onRemove(id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

// endregion

private fun formatPercent(value: Double?): String = if (value == null) "-" else "%.0f%%".format(value * 100)

private fun formatSigned(value: Int): String = if (value >= 0) "+$value" else "$value"

private fun String.humanize(): String = replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), " ")
