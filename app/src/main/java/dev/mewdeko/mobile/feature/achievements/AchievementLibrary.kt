package dev.mewdeko.mobile.feature.achievements

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FaIcon
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.StatePill

/**
 * The achievement library: category chips, search, filter and sort menus, a
 * select mode for turning many on or off, and one card per achievement with
 * its switch. Tapping a card opens the editor.
 */
@Composable
internal fun AchievementLibrary(state: AchievementsState, viewModel: AchievementsViewModel) {
    val catalog = state.catalog ?: return
    val shown = state.shown

    CategoryChips(state, viewModel::setCategoryFilter)

    SearchField(value = state.search, onValueChange = viewModel::setSearch, placeholder = "Search achievements", fontAwesome = true)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuButton(
            label = state.filter.title,
            glyph = FaGlyph.Filter,
            options = AchievementFilter.entries.map { it.title to { viewModel.setFilter(it) } },
        )
        MenuButton(
            label = state.sort.title,
            glyph = FaGlyph.ArrowUpArrowDown,
            options = AchievementSort.entries.map { it.title to { viewModel.setSort(it) } },
        )
        Box(Modifier.weight(1f))
        TextButton(onClick = { viewModel.setSelecting(!state.selecting) }) {
            FaIcon(FaGlyph.CircleCheck, size = 16.dp)
            Text(if (state.selecting) " Done" else " Select")
        }
    }

    if (state.selecting) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                if (state.selectedKeys.isEmpty()) "Tap to pick" else "${state.selectedKeys.size} picked",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::selectAllShown) { Text("All") }
            TextButton(onClick = { viewModel.bulkSet(true) }, enabled = state.selectedKeys.isNotEmpty() && !state.isSaving) {
                Text("Activate")
            }
            TextButton(onClick = { viewModel.bulkSet(false) }, enabled = state.selectedKeys.isNotEmpty() && !state.isSaving) {
                Text("Deactivate")
            }
        }
    }

    state.category(state.categoryFilter)?.takeIf { !it.enabled }?.let { category ->
        Text(
            "The ${category.name} category is inactive. Activate it under Categories.",
            style = MaterialTheme.typography.bodySmall,
            color = LocalGuildPalette.current.accent.color,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }

    if (shown.isEmpty()) {
        val noCustom = state.categoryFilter == "custom" && state.search.isBlank() && state.filter == AchievementFilter.ALL
        EmptyState(
            message = if (noCustom) "No server achievements yet. Make your own: hit a number, say a phrase, react with an emoji, or something staff hand out."
            else "Nothing matches. Try another filter or search.",
            glyph = FaGlyph.Crown,
            actionLabel = "Make an achievement",
            onAction = viewModel::startNew,
        )
        return
    }

    if (state.categoryFilter == "all" && state.sort == AchievementSort.ORDER) {
        catalog.categories.forEach { category ->
            val items = shown.filter { it.categoryKey == category.key }
            if (items.isNotEmpty()) {
                SectionCard {
                    SectionCardHeader(category.name, headerIcon(category.icon))
                    items.forEach { AchievementRow(it, state, viewModel) }
                }
            }
        }
    } else {
        SectionCard {
            SectionCardHeader("${shown.size} achievements", FaGlyph.Crown)
            shown.forEach { AchievementRow(it, state, viewModel) }
        }
    }
}

/** The category filter chips. */
@Composable
private fun CategoryChips(state: AchievementsState, onSelect: (String) -> Unit) {
    val catalog = state.catalog ?: return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("fa:layer-group", null, "Everything", "${catalog.achievements.size}", state.categoryFilter == "all", true) { onSelect("all") }
        catalog.categories
            .filter { it.achievementCount > 0 || !it.isBuiltIn || it.key == "custom" }
            .forEach { category ->
                Chip(
                    category.icon,
                    state.iconImageUrl(category.iconUrl),
                    category.name,
                    if (category.enabled) "${category.enabledCount}/${category.achievementCount}" else "inactive",
                    state.categoryFilter == category.key,
                    category.enabled,
                ) { onSelect(category.key) }
            }
    }
}

/** One category chip. */
@Composable
private fun Chip(icon: String, imageUrl: String?, title: String, count: String, active: Boolean, enabled: Boolean,
                 onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = primary.copy(alpha = if (active) DashAlpha.Hex20 else DashAlpha.Hex08),
        border = if (active) BorderStroke(1.dp, primary.copy(alpha = DashAlpha.Hex30)) else null,
        modifier = Modifier.heightIn(min = 40.dp).alpha(if (enabled) 1f else 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AchievementIconTile(icon, imageUrl, primary, size = 15, bare = true)
            Text(title, fontWeight = FontWeight.SemiBold, color = if (active) primary else MaterialTheme.colorScheme.onSurface)
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A text button that opens a menu of choices. */
@Composable
private fun MenuButton(label: String, glyph: FaGlyph, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) {
            FaIcon(glyph, size = 16.dp)
            Text(" $label", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (title, action) ->
                DropdownMenuItem(text = { Text(title) }, onClick = {
                    open = false
                    action()
                })
            }
        }
    }
}

/** One achievement card. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AchievementRow(item: AchievementItem, state: AchievementsState, viewModel: AchievementsViewModel) {
    val grade = state.grade(item.grade)
    val picked = item.key in state.selectedKeys
    val rewards = state.rewardLabels(item)
    var menuOpen by remember { mutableStateOf(false) }
    val accent = LocalGuildPalette.current.accent.color
    val secondary = LocalGuildPalette.current.secondary.color

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.enabled) 1f else 0.6f)
            .clickable {
                if (state.selecting) {
                    if (!item.isGlobal) viewModel.toggleSelected(item.key)
                } else {
                    viewModel.startEdit(item)
                }
            }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.selecting) {
            Checkbox(checked = picked, onCheckedChange = { if (!item.isGlobal) viewModel.toggleSelected(item.key) }, enabled = !item.isGlobal)
        }
        AchievementIconTile(item.icon, state.iconImageUrl(item.iconUrl), grade.uiColor, size = 44)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StatePill("${grade.name} · ${item.points}", grade.uiColor)
                if (item.hidden) StatePill("Secret", secondary)
                if (item.isCustom) StatePill("Made here", accent)
                if (item.isOverridden) StatePill("Customized", MaterialTheme.colorScheme.primary)
            }
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${state.criteria(item)} · ${"%,d".format(item.unlockCount)} unlocked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rewards.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FaIcon(FaGlyph.Gift, size = 14.dp, tint = MaterialTheme.colorScheme.primary)
                    Text(rewards.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (!item.isGlobal && !state.selecting) {
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = item.selfEnabled,
                    onCheckedChange = { viewModel.toggle(item) },
                    enabled = item.key !in state.busyKeys,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        FaIcon(FaGlyph.Ellipsis, size = 18.dp, contentDescription = "Actions for ${item.name}")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            leadingIcon = { FaIcon(FaGlyph.Pen) },
                            onClick = {
                                menuOpen = false
                                viewModel.startEdit(item)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(if (item.selfEnabled) "Deactivate" else "Activate") },
                            leadingIcon = {
                                FaIcon(if (item.selfEnabled) FaGlyph.Pause else FaGlyph.Play)
                            },
                            onClick = {
                                menuOpen = false
                                viewModel.toggle(item)
                            },
                        )
                        if (item.isCustom && state.sort == AchievementSort.ORDER) {
                            val siblings = state.customOrder.filter { it.categoryKey == item.categoryKey }
                            val index = siblings.indexOfFirst { it.key == item.key }
                            if (index > 0) {
                                DropdownMenuItem(
                                    text = { Text("Move up") },
                                    leadingIcon = { FaIcon(FaGlyph.ArrowUp) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.move(item, -1)
                                    },
                                )
                            }
                            if (index in 0 until siblings.lastIndex) {
                                DropdownMenuItem(
                                    text = { Text("Move down") },
                                    leadingIcon = { FaIcon(FaGlyph.ArrowDown) },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.move(item, 1)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
