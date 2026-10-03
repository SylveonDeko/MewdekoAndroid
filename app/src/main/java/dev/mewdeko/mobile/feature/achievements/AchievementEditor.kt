package dev.mewdeko.mobile.feature.achievements

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.delay
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FaGlyph
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.InfoRow
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow

/**
 * The create and edit form for an achievement. Built in achievements edit
 * their name, look, points, and rewards; server achievements also pick a
 * category, a grade, and what unlocks them. A preview shows the unlock message.
 */
@Composable
internal fun AchievementEditor(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.draft
    val item = state.editingItem
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val reason = state.invalidReason

    FullScreenEditor(
        title = if (draft.isNew) "New achievement" else item?.name ?: "Achievement",
        onClose = viewModel::closeEditor,
        confirmLabel = if (draft.isNew) "Create" else "Save",
        confirmEnabled = item?.isGlobal != true && reason == null && (state.draftDirty || draft.isNew) && !state.isSaving,
        onConfirm = viewModel::save,
        hasUnsavedChanges = state.draftDirty,
    ) {
        if (item?.isGlobal == true) {
            Text(
                "Global achievements are earned across every server and can't be changed per server.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@FullScreenEditor
        }

        Preview(state, viewModel)
        BasicsSection(state, viewModel)
        if (!draft.isBuiltIn) UnlockSection(state, viewModel)
        PointsSection(state, viewModel)
        RewardsSection(state, viewModel)

        if (reason != null && state.draftDirty) {
            Text(reason, style = MaterialTheme.typography.bodySmall, color = LocalGuildPalette.current.accent.color)
        }

        if (item != null && !draft.isNew) {
            SectionCard {
                InfoRow("Unlocked by", "%,d members".format(item.unlockCount))
                InfoRow("Key", item.key)
                if (item.isCustom) {
                    OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete achievement", color = DangerRed) }
                } else if (item.isOverridden) {
                    OutlinedButton(onClick = { confirmReset = true }) { Text("Reset to default") }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this achievement?",
            message = if ((item?.unlockCount ?: 0) > 0) "${item?.unlockCount} members unlocked it and lose it, along with its points."
            else "Nobody has unlocked it yet.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                viewModel.deleteDraft()
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset to default?",
            message = "Its name, description, icon, points, and rewards go back to how the bot ships them, and it becomes active again.",
            confirmLabel = "Reset",
            onConfirm = {
                confirmReset = false
                viewModel.resetToDefault()
            },
            onDismiss = { confirmReset = false },
        )
    }
}

/** The unlock image as it would be sent, drawn by the bot, redrawn a moment after each edit. */
@Composable
private fun Preview(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.draft
    var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    val key = listOf(draft.name, draft.description, draft.icon.orEmpty(), draft.categoryKey, draft.grade, state.draftPoints)

    LaunchedEffect(key) {
        delay(450)
        loading = true
        viewModel.previewImage()?.let { bytes ->
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { image = it.asImageBitmap() }
        }
        loading = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1200f / 420f)
                .background(Color(0xFF2B2D31), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            val current = image
            if (current != null) {
                Image(
                    bitmap = current,
                    contentDescription = "Unlock image for ${draft.name.ifBlank { "this achievement" }}",
                    modifier = Modifier.fillMaxWidth().alpha(if (loading) 0.6f else 1f),
                )
            } else {
                Text(if (loading) "Drawing" else "The image appears here.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Attached to unlock messages while unlock images are on.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Name, description, emoji, category, and switches. */
@Composable
private fun BasicsSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.draft
    val item = state.editingItem
    val limits = state.catalog?.limits ?: AchievementLimits()
    SectionCard {
        SectionCardHeader("Basics", FaGlyph.CircleInfo)
        MewdekoTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.editDraft { it.copy(name = value.take(limits.nameLength)) } },
            label = "Name",
            placeholder = if (draft.isBuiltIn) item?.defaultName else "Night Shift",
            supportingText = if (draft.isBuiltIn) "Leave blank to keep the default." else null,
        )
        MewdekoTextField(
            value = draft.description,
            onValueChange = { value -> viewModel.editDraft { it.copy(description = value.take(limits.descriptionLength)) } },
            label = "Description",
            placeholder = state.autoDescription,
            singleLine = false,
            minLines = 2,
            supportingText = "Leave blank to describe the goal automatically.",
        )
        val category = state.category(draft.categoryKey)
        AchievementIconField(
            state = state,
            viewModel = viewModel,
            icon = draft.icon,
            onChange = { icon -> viewModel.editDraft { it.copy(icon = icon) } },
            defaultIcon = category?.icon,
            defaultImageUrl = state.iconImageUrl(category?.iconUrl),
            defaultLabel = "the category's icon",
            color = state.grade(if (draft.isBuiltIn) item?.grade ?: 0 else draft.grade).uiColor,
        )
        if (!draft.isBuiltIn) {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.MilitaryTech),
                glyph = FaGlyph.LayerGroup,
                options = state.catalog?.categories.orEmpty()
                    .filter { it.key != "global" && it.key != "prestige" }
                    .map { SelectorOption(it.key, it.name) },
                placeholder = "Pick a category",
                selectedId = draft.categoryKey,
                onSelect = { id -> viewModel.editDraft { it.copy(categoryKey = id ?: "custom") } },
                label = "Category",
            )
        }
        SwitchRow(
            glyph = FaGlyph.CircleCheck,
            title = "Active",
            subtitle = if (draft.isNew) "Members can start earning it as soon as it's saved."
            else "Deactivating it keeps it for everyone who already unlocked it.",
            checked = draft.enabled,
            onCheckedChange = { value -> viewModel.editDraft { it.copy(enabled = value) } },
        )
        SwitchRow(
            glyph = FaGlyph.EyeSlash,
            title = "Secret until unlocked",
            subtitle = "Shown as a hidden achievement until someone finds it.",
            checked = draft.hidden,
            onCheckedChange = { value -> viewModel.editDraft { it.copy(hidden = value) } },
        )
    }
}

/** What unlocks a server achievement. */
@Composable
private fun UnlockSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.draft
    val limits = state.catalog?.limits ?: AchievementLimits()
    SectionCard {
        SectionCardHeader("How it unlocks", FaGlyph.Unlock)
        EnumPicker(
            label = "Unlocks when",
            options = AchievementTriggerKind.customChoices.map { EnumOption(it, it.title, it.blurb) },
            selected = draft.trigger,
            onSelect = { kind -> viewModel.editDraft { it.copy(trigger = kind) } },
        )
        when (draft.trigger) {
            AchievementTriggerKind.METRIC -> {
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.TrackChanges),
                    glyph = FaGlyph.ChartSimple,
                    options = state.catalog?.metrics.orEmpty().filter { it.allowCustom }
                        .map { SelectorOption(it.value.toString(), it.label, it.description) },
                    placeholder = "Pick what to count",
                    selectedId = draft.metric.toString(),
                    onSelect = { id -> viewModel.editDraft { it.copy(metric = id?.toIntOrNull() ?: it.metric) } },
                    label = "What to count",
                )
                MewdekoTextField(
                    value = draft.threshold.toString(),
                    onValueChange = { text -> viewModel.editDraft { it.copy(threshold = text.filter(Char::isDigit).take(12).toLongOrNull() ?: 0) } },
                    label = "Goal",
                    numeric = true,
                )
            }
            AchievementTriggerKind.KEYWORD, AchievementTriggerKind.REACTION -> {
                val keyword = draft.trigger == AchievementTriggerKind.KEYWORD
                MewdekoTextField(
                    value = draft.keyword,
                    onValueChange = { value -> viewModel.editDraft { it.copy(keyword = value.take(limits.keywordLength)) } },
                    label = if (keyword) "Phrase" else "Emoji",
                    placeholder = if (keyword) "good morning" else "Paste an emoji or <:name:id>",
                    supportingText = if (keyword) "Matched anywhere in a message, ignoring case." else "A unicode emoji or <:name:id>.",
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Channel,
                    glyph = FaGlyph.Comments,
                    options = state.lookups?.channels.orEmpty().filter { it.type == 0 }
                        .map { SelectorOption(it.id, it.name, it.categoryName) },
                    placeholder = "Any channel",
                    selectedId = draft.channelId,
                    onSelect = { id -> viewModel.editDraft { it.copy(channelId = id) } },
                    label = "Only in channel",
                )
            }
            else -> Text(
                "Give it from Members or with the achgrant command.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Grade and points. */
@Composable
private fun PointsSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.draft
    SectionCard {
        SectionCardHeader(if (draft.isBuiltIn) "Points" else "Grade and points", FaGlyph.Trophy)
        if (!draft.isBuiltIn) {
            EnumPicker(
                label = "Grade",
                options = state.grades.map { EnumOption(it.value, it.name, "${it.points} points by default") },
                selected = draft.grade,
                onSelect = { grade -> viewModel.editDraft { it.copy(grade = grade) } },
            )
        }
        MewdekoTextField(
            value = draft.points?.toString().orEmpty(),
            onValueChange = { text -> viewModel.editDraft { it.copy(points = text.filter(Char::isDigit).take(6).toIntOrNull()) } },
            label = "Points",
            placeholder = state.draftPoints.toString(),
            numeric = true,
            supportingText = "Blank uses the default. Ranks: " + state.catalog?.tiers.orEmpty()
                .filter { it.minPoints > 0 }.joinToString(", ") { "${it.name} ${"%,d".format(it.minPoints)}" },
        )
    }
}

/** Role, currency, and XP rewards. */
@Composable
private fun RewardsSection(state: AchievementsState, viewModel: AchievementsViewModel) {
    val draft = state.draft
    SectionCard {
        SectionCardHeader("Rewards", FaGlyph.Gift)
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            glyph = FaGlyph.User,
            options = state.lookups?.roles.orEmpty().map {
                SelectorOption(it.id, it.name, if (it.assignable) null else "The bot can't give this out", colorHex = it.color.toInt())
            },
            placeholder = "No role",
            selectedId = draft.roleRewardId,
            onSelect = { id -> viewModel.editDraft { it.copy(roleRewardId = id) } },
            label = "Role",
        )
        MewdekoTextField(
            value = draft.currencyReward.toString(),
            onValueChange = { text -> viewModel.editDraft { it.copy(currencyReward = text.filter(Char::isDigit).take(12).toLongOrNull() ?: 0) } },
            label = "Currency",
            numeric = true,
        )
        MewdekoTextField(
            value = draft.xpReward.toString(),
            onValueChange = { text -> viewModel.editDraft { it.copy(xpReward = text.filter(Char::isDigit).take(9).toIntOrNull() ?: 0) } },
            label = "XP",
            numeric = true,
        )
    }
}
