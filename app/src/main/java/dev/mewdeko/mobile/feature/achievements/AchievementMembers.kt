package dev.mewdeko.mobile.feature.achievements

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.ui.Avatar
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FaIcon
import dev.mewdeko.mobile.core.ui.LoadingState
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.StatePill
import java.time.Duration
import java.time.Instant

/** Members ranked by achievements, with search, sorting, and paging. */
@Composable
internal fun AchievementMembers(state: AchievementsState, viewModel: AchievementsViewModel) {
    SearchField(value = state.memberSearch, onValueChange = viewModel::setMemberSearch, placeholder = "Find a member", fontAwesome = true)
    EnumPicker(
        label = "Sort",
        options = listOf(EnumOption(0, "Most points"), EnumOption(1, "Most unlocked"), EnumOption(2, "Latest unlock")),
        selected = state.memberSort,
        onSelect = viewModel::setMemberSort,
        showDescription = false,
    )

    if (state.members.isEmpty()) {
        if (state.loadingMembers) {
            LoadingState()
        } else {
            EmptyState(
                message = if (state.memberSearch.isBlank()) "Members show up here once they unlock something." else "No members match.",
                glyph = FaGlyph.Users,
            )
        }
        return
    }

    SectionCard {
        SectionCardHeader("%,d members".format(state.memberTotal), FaGlyph.Users)
        state.members.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable { viewModel.openMember(member.userId) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (member.rank > 0) "#${member.rank}" else "", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Avatar(url = member.avatarUrl, contentDescription = null, size = 36)
                Column(Modifier.weight(1f)) {
                    Text(
                        if (member.inServer) member.displayName else "${member.displayName} (left)",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "@${member.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${member.unlocked} unlocked · ${ago(member.lastUnlockAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%,d".format(member.points), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    StatePill(member.tier, member.tierGrade?.let { state.grade(it).uiColor } ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    val pages = maxOf(1, (state.memberTotal + viewModel.memberPageSize - 1) / viewModel.memberPageSize)
    if (pages > 1) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.loadMembers(state.memberPage - 1) }, enabled = state.memberPage > 0 && !state.loadingMembers) {
                Text("Previous")
            }
            Text("Page ${state.memberPage + 1} of $pages", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { viewModel.loadMembers(state.memberPage + 1) }, enabled = state.memberPage < pages - 1 && !state.loadingMembers) {
                Text("Next")
            }
        }
    }
}

/** "3h ago" for an instant, or "never". */
private fun ago(instant: Instant?): String {
    instant ?: return "never"
    val seconds = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86400}d ago"
    }
}

/** One member's progress by category, with give, take away, and reset. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AchievementMemberSheet(state: AchievementsState, viewModel: AchievementsViewModel) {
    val detail = state.memberDetail
    var grantKey by remember { mutableStateOf<String?>(null) }
    var pendingRevoke by remember { mutableStateOf<AchievementItem?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    MewdekoBottomSheet(onDismissRequest = viewModel::closeMember, title = detail?.member?.displayName ?: "Member") {
        if (detail == null) {
            LoadingState()
            return@MewdekoBottomSheet
        }
        val member = detail.member
        val byKey = state.catalog?.achievements.orEmpty().associateBy { it.key }
        val progressByKey = detail.progress.associateBy { it.key }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(url = member.avatarUrl, contentDescription = null, size = 64)
                Column(Modifier.weight(1f)) {
                    Text(member.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("@${member.username}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    member.globalName?.takeIf { it != member.displayName }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        if (member.inServer) member.userId else "${member.userId} · left the server",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Rank", member.tier, Modifier.weight(1f), tint = member.tierGrade?.let { state.grade(it).uiColor }, glyph = FaGlyph.Trophy)
                StatTile("Points", "%,d".format(member.points), Modifier.weight(1f), glyph = FaGlyph.Star)
                StatTile("Unlocked", "${member.unlocked} / ${detail.total}", Modifier.weight(1f), glyph = FaGlyph.Unlock)
            }

            if (detail.badges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    detail.badges.forEach { badge ->
                        BadgeChip(
                            name = badge.name,
                            icon = badge.icon,
                            imageUrl = state.iconImageUrl(badge.iconUrl),
                            color = state.grade(badge.grade).uiColor,
                            equipped = badge.key in detail.equipped,
                        )
                    }
                }
            }

            if (member.inServer) {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Groups),
                    glyph = FaGlyph.Crown,
                    options = state.catalog?.achievements.orEmpty()
                        .filter { !it.isGlobal && progressByKey[it.key]?.unlockedAt == null }
                        .map { SelectorOption(it.key, it.name) },
                    placeholder = "Give an achievement",
                    selectedId = grantKey,
                    onSelect = { grantKey = it },
                    label = "Give an achievement",
                )
                OutlinedButton(
                    onClick = {
                        grantKey?.let(viewModel::grant)
                        grantKey = null
                    },
                    enabled = grantKey != null && !state.isSaving,
                ) { Text("Give") }
            }

            state.catalog?.categories.orEmpty().forEach { category ->
                val items = detail.progress.mapNotNull { p -> byKey[p.key]?.let { p to it } }.filter { it.second.categoryKey == category.key }
                if (items.isNotEmpty()) {
                    val done = items.count { it.first.unlockedAt != null }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AchievementIconTile(category.icon, state.iconImageUrl(category.iconUrl),
                            MaterialTheme.colorScheme.primary, size = 16, bare = true)
                        Text("${category.name} · $done of ${items.size}", fontWeight = FontWeight.SemiBold)
                    }
                    items.forEach { (progress, item) ->
                        val grade = state.grade(item.grade)
                        val unlocked = progress.unlockedAt != null
                        Row(
                            modifier = Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.6f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AchievementIconTile(item.icon, state.iconImageUrl(item.iconUrl), grade.uiColor)
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge)
                                val current = progress.current
                                when {
                                    unlocked -> Text("Unlocked ${ago(progress.unlockedAt)}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    current != null && item.threshold > 0 -> {
                                        LinearProgressIndicator(
                                            progress = { (minOf(current, item.threshold).toFloat() / item.threshold) },
                                            color = grade.uiColor,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Text("%,d / %,d".format(minOf(current, item.threshold), item.threshold),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    else -> Text(item.description, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (unlocked) {
                                IconButton(onClick = { pendingRevoke = item }, enabled = !state.isSaving) {
                                    FaIcon(FaGlyph.Xmark, tint = DangerRed, contentDescription = "Take away ${item.name}")
                                }
                            }
                        }
                    }
                }
            }

            OutlinedButton(onClick = { confirmReset = true }, enabled = member.unlocked > 0 && !state.isSaving) {
                Text("Reset member", color = DangerRed)
            }
        }
    }

    pendingRevoke?.let { item ->
        ConfirmDialog(
            title = "Take away ${item.name}?",
            message = "They lose its points. If it unlocks from activity they still qualify for, it comes back quietly later.",
            confirmLabel = "Take away",
            onConfirm = {
                pendingRevoke = null
                viewModel.revoke(item.key)
            },
            onDismiss = { pendingRevoke = null },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset this member?",
            message = "Every achievement they have here is cleared. Anything they still qualify for comes back quietly.",
            confirmLabel = "Reset member",
            onConfirm = {
                confirmReset = false
                viewModel.resetMember()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** A badge capsule in its grade color with its Font Awesome icon, and a star when it is equipped. */
@Composable
private fun BadgeChip(name: String, icon: String, imageUrl: String?, color: Color, equipped: Boolean) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = DashAlpha.Hex20), shape)
            .border(1.dp, color.copy(alpha = DashAlpha.Hex30), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        AchievementIconTile(icon, imageUrl, color, size = 13, bare = true)
        Text(name, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (equipped) FaIcon(FaGlyph.Star, size = 11.dp, tint = color)
    }
}
