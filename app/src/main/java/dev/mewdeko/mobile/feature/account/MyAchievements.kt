package dev.mewdeko.mobile.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FaIcon
import dev.mewdeko.mobile.core.ui.MewdekoBottomSheet
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TabLevel
import dev.mewdeko.mobile.feature.achievements.AchievementBadgeItem
import dev.mewdeko.mobile.feature.achievements.AchievementCategoryItem
import dev.mewdeko.mobile.feature.achievements.AchievementGradeInfo
import dev.mewdeko.mobile.feature.achievements.AchievementIconTile
import dev.mewdeko.mobile.feature.achievements.AchievementItem
import dev.mewdeko.mobile.feature.achievements.AchievementMemberRow
import dev.mewdeko.mobile.feature.achievements.AchievementProgressItem
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents

/** The member's privacy and notification choices for achievements. */
@Serializable
data class MyAchievementSettings(
    /** 0 lets everyone see the profile card and stats, 1 keeps them private. */
    val profileVisibility: Int = 0,
    /** 0 lets everyone see unlocked achievements, 1 keeps them private. */
    val achievementsVisibility: Int = 0,
    /** 0 lets everyone see badges, 1 keeps them private. */
    val badgesVisibility: Int = 0,
    val hideFromLeaderboards: Boolean = false,
    /** 0 follows the server, 1 always DMs unlocks, 2 never does. */
    val dmUnlocks: Int = 0,
    val showInLog: Boolean = true,
    val mentionMe: Boolean = true,
)

/** The signed in member's own achievements in a server. */
@Serializable
data class MyAchievementsData(
    val enabled: Boolean = false,
    val member: AchievementMemberRow = AchievementMemberRow(),
    val total: Int = 0,
    val tierPoints: Int = 0,
    val nextTier: String? = null,
    val nextTierPoints: Int? = null,
    val categories: List<AchievementCategoryItem> = emptyList(),
    val achievements: List<AchievementItem> = emptyList(),
    val progress: List<AchievementProgressItem> = emptyList(),
    val badges: List<AchievementBadgeItem> = emptyList(),
    val equipped: List<String?> = emptyList(),
    val settings: MyAchievementSettings = MyAchievementSettings(),
    val globalPoints: Long = 0,
    val globalUnlocked: Long = 0,
    val globalServers: Int = 0,
    val grades: List<AchievementGradeInfo> = emptyList(),
) {
    /** How far along the member is toward the next rank, from 0 to 1. */
    val tierFraction: Float
        get() {
            val next = nextTierPoints ?: return 1f
            if (next <= tierPoints) return 1f
            return ((member.points - tierPoints).toFloat() / (next - tierPoints)).coerceIn(0f, 1f)
        }

    /** A grade's color. */
    fun gradeColor(grade: Int?): Color? = grade?.let { g -> grades.firstOrNull { it.value == g }?.uiColor }
}

/** The badge slot response. */
@Serializable
data class MyAchievementBadgesResponse(val equipped: List<String?> = emptyList())

/** The badge slot request. */
@Serializable
data class MyAchievementBadgesRequest(val slots: List<String?>)

/** The selector kind for achievement pickers; each call passes a Font Awesome glyph for the orb. */
private val AchievementSelector = SelectorKind.Custom(Icons.Default.EmojiEvents)

/**
 * "Achievements": rank and progress, the four badge slots, privacy and DM choices, and a way into every
 * achievement in the server.
 */
@Composable
internal fun MeAchievementsSection(
    state: MeState,
    onSlot: (Int, String?) -> Unit,
    onPreference: (String, Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.achievements ?: return
    var showAll by remember { mutableStateOf(false) }
    val palette = LocalGuildPalette.current
    SectionCard(modifier = modifier) {
        SectionCardHeader("Achievements", FaGlyph.Trophy)
        val tierColor = data.gradeColor(data.member.tierGrade) ?: MaterialTheme.colorScheme.primary
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatePill(data.member.tier.ifEmpty { "Unranked" }, tierColor)
            Text("%,d points".format(data.member.points), fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "${data.member.unlocked} of ${data.total} unlocked" + if (data.member.rank > 0) " · #${data.member.rank}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (data.nextTier != null && data.nextTierPoints != null) {
            LinearProgressIndicator(progress = { data.tierFraction }, color = tierColor, modifier = Modifier.fillMaxWidth())
            Text("%,d points to %s".format(data.nextTierPoints - data.member.points, data.nextTier),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!data.enabled) {
            Text("Earning is paused in this server.", style = MaterialTheme.typography.bodySmall, color = palette.accent.color)
        }
        HorizontalDivider()
        Text("Badges on your profile", style = MaterialTheme.typography.titleSmall)
        if (data.badges.isEmpty()) {
            Text("Unlock achievements to earn badges.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val options = data.badges.map { SelectorOption(it.key, it.name, it.source) }
            (0 until 4).forEach { slot ->
                DiscordSelectorSingle(
                    kind = AchievementSelector,
                    glyph = FaGlyph.Trophy,
                    options = options,
                    placeholder = "Empty",
                    selectedId = data.equipped.getOrNull(slot),
                    onSelect = { onSlot(slot, it) },
                    label = "Slot ${slot + 1}",
                )
            }
        }
        HorizontalDivider()
        SwitchRow(glyph = FaGlyph.Eye, title = "Others can see my achievements",
            checked = data.settings.achievementsVisibility == 0,
            onCheckedChange = { onPreference("achievementsVisibility", if (it) 0 else 1) })
        SwitchRow(glyph = FaGlyph.Trophy, title = "Others can see my badges",
            checked = data.settings.badgesVisibility == 0,
            onCheckedChange = { onPreference("badgesVisibility", if (it) 0 else 1) })
        SwitchRow(glyph = FaGlyph.ChartSimple, title = "Show me on leaderboards",
            checked = !data.settings.hideFromLeaderboards,
            onCheckedChange = { onPreference("hideFromLeaderboards", !it) })
        SwitchRow(glyph = FaGlyph.Bell, title = "Mention me in unlock messages",
            checked = data.settings.mentionMe,
            onCheckedChange = { onPreference("mentionMe", it) })
        EnumPicker(
            label = "Unlocks in my DMs",
            options = listOf(EnumOption(0, "Server default"), EnumOption(1, "Always"), EnumOption(2, "Never")),
            selected = data.settings.dmUnlocks,
            onSelect = { onPreference("dmUnlocks", it) },
        )
        TextButton(onClick = { showAll = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            FaIcon(FaGlyph.Crown, size = 16.dp)
            Spacer(Modifier.padding(start = 8.dp))
            Text("See every achievement")
        }
    }
    if (showAll) {
        MyAchievementsSheet(data = data, iconUrl = { state.achievementIconUrl(it) }, onDismiss = { showAll = false })
    }
}

/** Every achievement in the server by category, unlocked or with the member's progress. */
@Composable
private fun MyAchievementsSheet(data: MyAchievementsData, iconUrl: (String?) -> String?, onDismiss: () -> Unit) {
    var filter by remember { mutableStateOf("all") }
    val progress = data.progress.associateBy { it.key }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()) }
    MewdekoBottomSheet(onDismissRequest = onDismiss, title = "My achievements") {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTabs(
                tabs = listOf(SectionTab("all", "All"), SectionTab("unlocked", "Unlocked"), SectionTab("locked", "Locked")),
                selectedId = filter,
                onSelect = { filter = it },
                level = TabLevel.Secondary,
            )
            data.categories.forEach { category ->
                val all = data.achievements.filter { it.categoryKey == category.key }
                val items = all.filter { item ->
                    val unlocked = progress[item.key]?.unlockedAt != null
                    when (filter) {
                        "unlocked" -> unlocked
                        "locked" -> !unlocked
                        else -> true
                    }
                }
                if (items.isEmpty()) return@forEach
                val done = all.count { progress[it.key]?.unlockedAt != null }
                Text("${category.name} · $done of ${all.size}", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                items.forEach { item ->
                    val entry = progress[item.key]
                    val unlocked = entry?.unlockedAt != null
                    val color = data.gradeColor(item.grade) ?: MaterialTheme.colorScheme.primary
                    Row(
                        modifier = Modifier.fillMaxWidth().alpha(if (unlocked) 1f else 0.75f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AchievementIconTile(item.icon, iconUrl(item.iconUrl),
                            if (unlocked) color else MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f, fill = false))
                                StatePill("${item.points}", color)
                            }
                            Text(item.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val unlockedAt = entry?.unlockedAt
                            val current = entry?.current
                            if (unlockedAt != null) {
                                Text("Unlocked ${dateFormat.format(unlockedAt)}", style = MaterialTheme.typography.bodySmall, color = color)
                            } else if (item.metric > 0 && item.threshold > 0 && current != null) {
                                LinearProgressIndicator(
                                    progress = { (current.toFloat() / item.threshold).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text("%,d / %,d".format(minOf(current, item.threshold), item.threshold),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
