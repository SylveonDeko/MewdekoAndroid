package dev.mewdeko.mobile.feature.achievements

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import dev.mewdeko.mobile.core.ui.ReorderableColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelector
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FaIcon
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SectionTab
import dev.mewdeko.mobile.core.ui.SectionTabs
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.StatePill
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor
import dev.mewdeko.mobile.feature.embed.Placeholder
import dev.mewdeko.mobile.navigation.GuildRouteArgs

/** Subtitle shared with the dashboard and iOS. */
private const val FeatureSubtitle = "Milestones, badges, and ranks members earn by taking part"

/** Solid green of an on state. */
internal val OnGreen = Color(0xFF10B981)

/** Solid red of destructive actions. */
internal val DangerRed = Color(0xFFEF4444)

private val Tabs = listOf(
    SectionTab("overview", "Overview", glyph = FaGlyph.Gauge),
    SectionTab("library", "Achievements", glyph = FaGlyph.Crown),
    SectionTab("categories", "Categories", glyph = FaGlyph.LayerGroup),
    SectionTab("announce", "Announce", glyph = FaGlyph.Bell),
    SectionTab("cards", "Cards", glyph = FaGlyph.IdCard),
    SectionTab("members", "Members", glyph = FaGlyph.Users),
    SectionTab("settings", "Settings", glyph = FaGlyph.Sliders),
)

/**
 * Achievements: milestones, badges, and ranks members earn. Overview shows
 * whether it is on and what feeds it; Achievements organizes the library;
 * Categories orders and switches categories; Announce and Settings hold the
 * server settings; Members ranks members and opens their progress.
 */
@Composable
fun AchievementsScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    var categorySheet by remember { mutableStateOf<CategorySheetTarget?>(null) }
    var pendingCategoryDelete by remember { mutableStateOf<AchievementCategoryItem?>(null) }
    var pendingResetAll by remember { mutableStateOf(false) }

    FeatureScaffold(
        title = "Achievements",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        floatingActionButton = {
            val settingsTab = state.section == "announce" || state.section == "settings"
            if (settingsTab && state.settingsDirty) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::saveSettings,
                    icon = { FaIcon(FaGlyph.FloppyDisk) },
                    text = { Text(if (state.isSaving) "Saving…" else "Save changes") },
                )
            } else if (state.section == "library" && !state.editorOpen) {
                NewItemFab(label = "New achievement", onClick = viewModel::startNew, glyph = FaGlyph.CirclePlus)
            }
        },
    ) {
        Text(
            text = FeatureSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        SectionTabs(tabs = Tabs, selectedId = state.section, onSelect = viewModel::setSection)

        when (state.section) {
            "overview" -> OverviewSection(state, viewModel)
            "library" -> AchievementLibrary(state, viewModel)
            "categories" -> CategoriesSection(
                state = state,
                viewModel = viewModel,
                onNew = { categorySheet = CategorySheetTarget(null) },
                onEdit = { categorySheet = CategorySheetTarget(it) },
                onDelete = { pendingCategoryDelete = it },
            )
            "announce" -> AnnounceSection(state, viewModel)
            "cards" -> CardsSection(state, viewModel)
            "members" -> AchievementMembers(state, viewModel)
            "settings" -> SettingsSection(state, viewModel, onResetAll = { pendingResetAll = true })
        }
    }

    if (state.editorOpen) {
        AchievementEditor(state = state, viewModel = viewModel)
    }

    if (state.memberOpen) {
        AchievementMemberSheet(state = state, viewModel = viewModel)
    }

    if (state.designer != null) {
        CardDesigner(state = state, viewModel = viewModel)
    }

    categorySheet?.let { target ->
        CategoryForm(
            state = state,
            viewModel = viewModel,
            category = target.category,
            onSave = { name, icon, description ->
                viewModel.saveCategory(target.category?.id, name, icon, description) { categorySheet = null }
            },
            onDismiss = { categorySheet = null },
        )
    }

    pendingCategoryDelete?.let { category ->
        ConfirmDialog(
            title = "Delete this category?",
            message = "Its achievements move to the Server category. Nobody loses anything.",
            confirmLabel = "Delete category",
            onConfirm = {
                pendingCategoryDelete = null
                category.id?.let(viewModel::deleteCategory)
            },
            onDismiss = { pendingCategoryDelete = null },
        )
    }

    if (pendingResetAll) {
        ConfirmDialog(
            title = "Reset every achievement?",
            message = "Every member loses every achievement and point in this server. Tracked activity stays, so anything still qualified for comes back quietly.",
            confirmLabel = "Reset everything",
            onConfirm = {
                pendingResetAll = false
                viewModel.resetAll()
            },
            onDismiss = { pendingResetAll = false },
        )
    }
}

/** Which category the form edits, or null for a new one. */
private data class CategorySheetTarget(val category: AchievementCategoryItem?)

/** The on switch, totals, data sources, recent unlocks, and rarity. */
@Composable
private fun OverviewSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val overview = state.overview ?: return
    val blocked = state.blockedSources

    SectionCard {
        SectionCardHeader("Achievements", FaGlyph.Crown)
        SwitchRow(
            glyph = FaGlyph.Crown,
            title = "Let members earn achievements",
            subtitle = if (overview.settings.enabled) {
                "Members are earning achievements, badges, and ranks."
            } else {
                "Earning is paused. Start it and members who already qualify get theirs quietly."
            },
            checked = overview.settings.enabled,
            onCheckedChange = viewModel::setServerEnabled,
            enabled = !state.isSaving,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Earning", "%,d".format(overview.members), Modifier.weight(1f), glyph = FaGlyph.Users)
            StatTile("Unlocks", "%,d".format(overview.unlocks), Modifier.weight(1f), glyph = FaGlyph.Unlock)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Achievements active", "${overview.earnable} / ${overview.total}", Modifier.weight(1f), glyph = FaGlyph.Crown)
            StatTile("Made here", "%,d".format(overview.customCount), Modifier.weight(1f), glyph = FaGlyph.WandMagicSparkles)
        }
    }

    SectionCard {
        SectionCardHeader("Data sources", FaGlyph.Link)
        Text(
            "Achievements read numbers other features already track. Anything not tracking means some achievements can't move.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DataSources.forEach { source ->
            val on = overview.dataSources[source.key] ?: true
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FaIcon(source.glyph, size = 18.dp, tint = if (on) MaterialTheme.colorScheme.primary else LocalGuildPalette.current.accent.color)
                Column(Modifier.weight(1f)) {
                    Text(source.label, style = MaterialTheme.typography.bodyLarge)
                    if (!on) {
                        Text(
                            if (source.key in blocked) source.off else "Not tracking, and no active achievement needs it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when {
                    on -> StatePill("Tracking", OnGreen)
                    source.key == "messages" -> OutlinedButton(onClick = viewModel::enableMessageCounting) { Text("Start tracking") }
                    else -> StatePill("Not tracking", LocalGuildPalette.current.accent.color)
                }
            }
        }
    }

    if (overview.recent.isNotEmpty()) {
        SectionCard {
            SectionCardHeader("Recent unlocks", FaGlyph.Clock)
            overview.recent.forEach { unlock ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncImage(
                        model = unlock.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = DashAlpha.Hex20), RoundedCornerShape(16.dp)),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(unlock.username, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            unlock.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = state.grade(unlock.grade).uiColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (overview.mostCommon.isNotEmpty()) {
        RaritySection("Most unlocked", FaGlyph.Fire, overview.mostCommon, state, viewModel)
        RaritySection("Rarest", FaGlyph.Sparkles, overview.rarest, state, viewModel)
    }
}

/** One data source with its help text. */
private data class DataSource(val key: String, val label: String, val glyph: FaGlyph, val off: String)

private val DataSources = listOf(
    DataSource("messages", "Message counting", FaGlyph.Comments, "Message achievements can't track until message counting is on."),
    DataSource("voice", "Voice tracking", FaGlyph.Microphone, "Voice hours and channels need voice tracking in Server Stats."),
    DataSource("invites", "Invite tracking", FaGlyph.Users, "Invite achievements need invite tracking."),
    DataSource("commands", "Command stats", FaGlyph.Code, "Command achievements need command stats, which this server opted out of."),
    DataSource("xp", "XP", FaGlyph.Star, "Level achievements need XP gain turned on."),
    DataSource("reputation", "Reputation", FaGlyph.Trophy, "Reputation achievements need the reputation system."),
)

/** Most unlocked or rarest achievements; tapping one opens it. */
@Composable
private fun RaritySection(
    title: String,
    icon: FaGlyph,
    items: List<AchievementRarity>,
    state: AchievementsState,
    viewModel: AchievementsViewModel,
) {
    SectionCard {
        SectionCardHeader(title, icon)
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable {
                        state.catalog?.achievements?.firstOrNull { it.key == item.key }?.let(viewModel::startEdit)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AchievementIconTile(item.icon, state.iconImageUrl(item.iconUrl), state.grade(item.grade).uiColor)
                Text(item.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                StatePill("%,d".format(item.count), MaterialTheme.colorScheme.primary)
            }
        }
    }
}


/** Order, on and off, and server categories. */
@Composable
private fun CategoriesSection(
    state: AchievementsState,
    viewModel: AchievementsViewModel,
    onNew: () -> Unit,
    onEdit: (AchievementCategoryItem) -> Unit,
    onDelete: (AchievementCategoryItem) -> Unit,
) {
    val categories = state.catalog?.categories ?: return
    SectionCard {
        SectionCardHeader("Order and visibility", FaGlyph.LayerGroup)
        Text(
            "Press and hold a category, then drag it to reorder. Members see categories in this order everywhere. " +
                "Deactivating one stops new unlocks in it; anything earned stays.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ReorderableColumn(
            items = categories,
            key = { it.key },
            onMove = viewModel::moveCategory,
            spacing = 4.dp,
        ) { category, dragModifier, dragging ->
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                    .then(dragModifier)
                    .alpha(if (category.enabled) 1f else 0.6f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FaIcon(FaGlyph.Bars, size = 14.dp, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = "Drag ${category.name} to reorder")
                AchievementIconTile(category.icon, state.iconImageUrl(category.iconUrl), MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (!category.isBuiltIn) StatePill("Made here", LocalGuildPalette.current.accent.color)
                    }
                    Text(
                        "${category.enabledCount} of ${category.achievementCount} active",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (category.key != "global") {
                    Switch(
                        checked = category.enabled,
                        onCheckedChange = { viewModel.setCategory(category, it) },
                        enabled = !state.isSaving,
                    )
                }
                if (!category.isBuiltIn) Box {
                    IconButton(onClick = { menuOpen = true }) {
                        FaIcon(FaGlyph.Ellipsis, size = 18.dp, contentDescription = "Actions for ${category.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        run {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { FaIcon(FaGlyph.Pen) },
                                onClick = {
                                    menuOpen = false
                                    onEdit(category)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = DangerRed) },
                                leadingIcon = { FaIcon(FaGlyph.Trash, tint = DangerRed) },
                                onClick = {
                                    menuOpen = false
                                    onDelete(category)
                                },
                            )
                        }
                    }
                }
            }
        }
        TextButton(onClick = onNew) { Text("New category") }
    }
}


/** Where unlocks go and the unlock message. */
@Composable
private fun AnnounceSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.settingsDraft
    val channels = state.lookups?.channels.orEmpty().filter { it.type == 0 }
        .map { SelectorOption(it.id, it.name, it.categoryName) }

    SectionCard {
        SectionCardHeader("Where unlocks go", FaGlyph.Bell)
        EnumPicker(
            label = "Announce",
            options = AchievementAnnounceMode.entries.map { EnumOption(it, it.title, it.blurb) },
            selected = draft.announceMode,
            onSelect = { mode -> viewModel.editSettings { it.copy(announceMode = mode) } },
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = channels,
            placeholder = "No log channel",
            selectedId = draft.logChannelId,
            onSelect = { id -> viewModel.editSettings { it.copy(logChannelId = id) } },
            label = "Log channel",
            glyph = FaGlyph.Comments,
        )
        if (draft.announceMode == AchievementAnnounceMode.LOG_CHANNEL && draft.logChannelId.isNullOrBlank()) {
            Text("Pick a channel, or nothing gets announced.", style = MaterialTheme.typography.bodySmall,
                color = LocalGuildPalette.current.accent.color)
        }
        SwitchRow(
            glyph = FaGlyph.Envelope,
            title = "DM members by default",
            subtitle = "Members who haven't chosen get unlocks in DMs too.",
            checked = draft.dmByDefault,
            onCheckedChange = { value -> viewModel.editSettings { it.copy(dmByDefault = value) } },
        )
        SwitchRow(
            glyph = FaGlyph.Bell,
            title = "Mention members",
            subtitle = "Ping members in unlock messages unless they turn it off.",
            checked = draft.mentionUsers,
            onCheckedChange = { value -> viewModel.editSettings { it.copy(mentionUsers = value) } },
        )
        SwitchRow(
            glyph = FaGlyph.Star,
            title = "Attach an unlock image",
            subtitle = "Each unlock message carries a drawn card of the achievement.",
            checked = draft.unlockImage,
            onCheckedChange = { value -> viewModel.editSettings { it.copy(unlockImage = value) } },
        )
        EnumPicker(
            label = "Delete unlock messages",
            options = listOf(
                EnumOption(0, "Never", "They stay in the channel"),
                EnumOption(5, "After 5 seconds", "The default"),
                EnumOption(15, "After 15 seconds"), EnumOption(30, "After 30 seconds"), EnumOption(60, "After 1 minute"),
                EnumOption(300, "After 5 minutes"), EnumOption(900, "After 15 minutes"), EnumOption(3600, "After 1 hour"),
                EnumOption(86400, "After 1 day"),
            ),
            selected = draft.deleteAfter,
            onSelect = { value -> viewModel.editSettings { it.copy(deleteAfter = value) } },
        )
    }

    SectionCard {
        SectionCardHeader("Keep unlocks out of some channels", FaGlyph.Filter)
        DiscordSelector(
            kind = SelectorKind.Channel,
            options = state.lookups?.channels.orEmpty().map {
                SelectorOption(it.id, it.name, it.categoryName, glyph = if (it.type == 2) FaGlyph.Microphone else FaGlyph.Comments)
            },
            placeholder = "No quiet channels",
            label = "Quiet channels",
            multiple = true,
            glyph = FaGlyph.Comments,
            selection = draft.quietChannelIds,
            onSelectionChange = { ids -> viewModel.editSettings { it.copy(quietChannelIds = ids) } },
        )
        SwitchRow(
            glyph = FaGlyph.Comments,
            title = "Only post where the member can talk",
            subtitle = "Skips channels the member can't send messages in, such as a read only channel they reacted in.",
            checked = draft.requireSendPermission,
            onCheckedChange = { value -> viewModel.editSettings { it.copy(requireSendPermission = value) } },
        )
        Text(
            "Members still earn achievements in quiet channels. The unlock is posted in the log channel instead, or not " +
                "at all when there isn't one. The log channel is never skipped.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard {
        SectionCardHeader("Unlock message", FaGlyph.EnvelopeOpen)
        EmbedMessageEditor(
            message = draft.message,
            onMessageChange = viewModel::setMessage,
            additionalPlaceholders = state.catalog?.placeholders.orEmpty()
                .map { Placeholder("Achievements", it.name, it.description) },
            allowComponents = false,
            allowSend = false,
        )
        Text(
            "Leave it empty for the default embed in the grade's color. %achievement.list% lists everything unlocked at once.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Exclusions, extras, and maintenance. */
@Composable
private fun SettingsSection(state: AchievementsState, viewModel: AchievementsViewModel, onResetAll: () -> Unit) {
    val draft = state.settingsDraft
    val roles = state.lookups?.roles.orEmpty().map { SelectorOption(it.id, it.name, colorHex = it.color.toInt()) }
    val channels = state.lookups?.channels.orEmpty().map {
        SelectorOption(it.id, it.name, it.categoryName, glyph = if (it.type == 2) FaGlyph.Microphone else FaGlyph.Comments)
    }

    SectionCard {
        SectionCardHeader("Who and where doesn't count", FaGlyph.Filter)
        DiscordSelector(
            kind = SelectorKind.Role,
            options = roles,
            placeholder = "No roles",
            label = "Roles",
            multiple = true,
            selection = draft.excludedRoleIds,
            glyph = FaGlyph.User,
            onSelectionChange = { ids -> viewModel.editSettings { it.copy(excludedRoleIds = ids) } },
        )
        DiscordSelector(
            kind = SelectorKind.Channel,
            options = channels,
            placeholder = "No channels",
            label = "Channels",
            multiple = true,
            glyph = FaGlyph.Comments,
            selection = draft.excludedChannelIds,
            onSelectionChange = { ids -> viewModel.editSettings { it.copy(excludedChannelIds = ids) } },
        )
        Text(
            "Members with these roles earn nothing, and activity in these channels doesn't count. Bots never earn.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionCard {
        SectionCardHeader("Extras", FaGlyph.Sliders)
        MewdekoTextField(
            value = draft.xpPerPoint.toString(),
            onValueChange = { text -> viewModel.editSettings { it.copy(xpPerPoint = text.filter(Char::isDigit).take(4).toIntOrNull()?.coerceIn(0, 1000) ?: 0) } },
            label = "XP per point",
            numeric = true,
            supportingText = "Extra XP for every achievement point. 0 turns it off.",
        )
        SwitchRow(
            glyph = FaGlyph.Eye,
            title = "Show secret achievements",
            subtitle = "Members see secret achievements before unlocking them.",
            checked = draft.revealHidden,
            onCheckedChange = { value -> viewModel.editSettings { it.copy(revealHidden = value) } },
        )
    }

    SectionCard {
        SectionCardHeader("Maintenance", FaGlyph.CircleExclamation)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::recheck, modifier = Modifier.weight(1f)) {
                FaIcon(FaGlyph.ArrowsRotate, size = 16.dp)
                Text(" Check everyone")
            }
            OutlinedButton(onClick = onResetAll, modifier = Modifier.weight(1f)) {
                FaIcon(FaGlyph.Trash, size = 16.dp, tint = DangerRed)
                Text(" Reset all", color = DangerRed)
            }
        }
        Spacer(Modifier.width(0.dp))
        Text(
            "Checking again is useful after turning on a data source. Resetting clears all unlocks and points here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
