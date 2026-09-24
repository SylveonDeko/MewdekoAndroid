package dev.mewdeko.mobile.feature.rolemenus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDownCircle
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mewdeko.mobile.core.theme.DashAlpha
import dev.mewdeko.mobile.core.theme.LocalGuildPalette
import dev.mewdeko.mobile.core.theme.Rgb
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EnumOption
import dev.mewdeko.mobile.core.ui.EnumPicker
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.readableInk
import dev.mewdeko.mobile.feature.embed.EmbedMessageEditor

/** The menu type choices, with the shared blurbs. */
internal val StyleOptions = listOf(
    EnumOption(RoleMenuStyle.DROPDOWN, RoleMenuStyle.DROPDOWN.label, RoleMenuStyle.DROPDOWN.blurb, Icons.Default.ArrowDropDownCircle),
    EnumOption(RoleMenuStyle.BUTTONS, RoleMenuStyle.BUTTONS.label, RoleMenuStyle.BUTTONS.blurb, Icons.Default.Apps),
)

private val ModeOptions = listOf(
    EnumOption(RoleMenuMode.PICK_ANY, RoleMenuMode.PICK_ANY.label, RoleMenuMode.PICK_ANY.blurb, Icons.AutoMirrored.Filled.PlaylistAddCheck),
    EnumOption(RoleMenuMode.PICK_ONE, RoleMenuMode.PICK_ONE.label, RoleMenuMode.PICK_ONE.blurb, Icons.Default.RadioButtonChecked),
)

private val ColorOptions = RoleMenuButtonColor.entries.map { EnumOption(it, it.label) }

/**
 * The full screen menu editor: Basics, Message, Menu type, Picking, Who can
 * use it, Confirmation, Options, Preview, and the save footer.
 */
@Composable
fun RoleMenuEditor(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    val draft = state.draft
    val reason = draft.invalidReason
    var confirmingDiscard by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editingOption by remember { mutableStateOf<RoleMenuOptionDraft?>(null) }
    val confirmLabel = if (draft.isEditing) "Save changes" else "Post menu"
    val savedMenu = draft.id?.let { id -> state.menus.firstOrNull { it.id == id } }
    val requestClose = {
        if (state.hasUnsavedChanges) confirmingDiscard = true else viewModel.closeEditor()
    }

    FullScreenEditor(
        title = if (draft.isEditing) "Edit ${savedMenu?.name ?: draft.name.ifBlank { RoleMenuLimits.DefaultName }}" else "New menu",
        onClose = requestClose,
        confirmLabel = confirmLabel,
        confirmEnabled = reason == null && !state.isSaving,
        onConfirm = viewModel::save,
        hasUnsavedChanges = false,
    ) {
        BasicsSection(state, viewModel)
        MessageSection(state, viewModel)
        MenuTypeSection(state, viewModel)
        PickingSection(state, viewModel)
        AccessSection(state, viewModel)
        ConfirmationSection(state, viewModel)
        OptionsSection(
            state = state,
            onEdit = { editingOption = it },
            onAdd = { editingOption = RoleMenuOptionDraft() },
            onMove = viewModel::moveOption,
            onRemove = viewModel::removeOption,
        )

        SectionCard {
            SectionCardHeader("Preview", Icons.Default.Visibility)
            RoleMenuPreview(
                name = draft.name,
                message = draft.message,
                style = draft.style,
                mode = draft.mode,
                placeholder = draft.placeholder,
                options = state.previewOptions,
                paused = !draft.enabled,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RoleMenuActionButton(
                text = confirmLabel,
                icon = null,
                onClick = viewModel::save,
                enabled = reason == null,
                solid = true,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
            if (reason != null) {
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RoleMenuActionButton(
                text = "Cancel",
                icon = null,
                onClick = requestClose,
                tone = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            if (savedMenu != null) {
                RoleMenuActionButton(
                    text = "Delete menu",
                    icon = Icons.Default.Delete,
                    onClick = { confirmingDelete = true },
                    tone = ProblemRed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (confirmingDiscard) {
            ConfirmDialog(
                title = "Discard changes?",
                message = "Your edits to this menu haven't been saved.",
                confirmLabel = "Discard",
                onConfirm = viewModel::closeEditor,
                onDismiss = { confirmingDiscard = false },
            )
        }

        if (confirmingDelete && savedMenu != null) {
            DeleteMenuDialog(
                menu = savedMenu,
                onConfirm = { viewModel.delete(savedMenu) },
                onDismiss = { confirmingDelete = false },
            )
        }

        editingOption?.let { option ->
            OptionSheet(
                initial = option,
                isNew = draft.options.none { it.key == option.key },
                state = state,
                onConfirm = {
                    viewModel.upsertOption(it)
                    editingOption = null
                },
                onDismiss = { editingOption = null },
            )
        }
    }
}

/** Name and channel. */
@Composable
private fun BasicsSection(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    val draft = state.draft
    val postable = state.postableChannels
    SectionCard {
        SectionCardHeader("Basics", Icons.Default.Info)
        MewdekoTextField(
            value = draft.name,
            onValueChange = viewModel::setName,
            label = "Name",
            placeholder = "Pronouns",
            supportingText = "Only staff see this, and it titles the default message.",
        )
        DiscordSelectorSingle(
            kind = SelectorKind.Channel,
            options = postable.map { SelectorOption(it.id, it.name, subtitle = it.categoryName) },
            placeholder = "Pick a channel",
            label = "Channel",
            selectedId = draft.channelId,
            onSelect = viewModel::setChannel,
        )
        if (!draft.channelId.isNullOrBlank() && postable.none { it.id == draft.channelId }) {
            RoleMenuCallout(
                text = "The bot can't post in the selected channel. Pick another one or fix its permissions.",
                tone = LocalGuildPalette.current.accent.color,
                icon = Icons.Default.Warning,
            )
        }
    }
}

/** The message above the dropdown or buttons. */
@Composable
private fun MessageSection(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    SectionCard {
        SectionCardHeader("Message", Icons.Default.ChatBubble)
        EmbedMessageEditor(
            message = state.draft.message,
            onMessageChange = viewModel::setMessage,
            allowComponents = false,
            allowSend = false,
        )
        HelperText("Leave empty to post the menu's name and a list of its options.")
    }
}

/** Dropdown or buttons, and the dropdown hint text. */
@Composable
private fun MenuTypeSection(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    val draft = state.draft
    SectionCard {
        SectionCardHeader("Menu type", Icons.Default.Category)
        EnumPicker(
            label = "Menu type",
            options = StyleOptions,
            selected = draft.style,
            onSelect = viewModel::setStyle,
        )
        if (draft.style == RoleMenuStyle.DROPDOWN) {
            MewdekoTextField(
                value = draft.placeholder,
                onValueChange = viewModel::setPlaceholder,
                label = "Dropdown hint text",
                placeholder = RoleMenuLimits.defaultPlaceholder(draft.mode),
                supportingText = "Shown in the dropdown before anyone picks.",
            )
        }
    }
}

/** Pick any or pick one, with the limits that go with each. */
@Composable
private fun PickingSection(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    val draft = state.draft
    val ceiling = draft.options.size.coerceAtLeast(1)
    val minChoices = (0..ceiling).map { SelectorOption(it.toString(), if (it == 0) "None" else it.toString()) }
    val maxChoices = (0..ceiling).map { SelectorOption(it.toString(), if (it == 0) "No limit" else it.toString()) }
    SectionCard {
        SectionCardHeader("Picking", Icons.Default.PanTool)
        EnumPicker(
            label = "Picking",
            options = ModeOptions,
            selected = draft.mode,
            onSelect = viewModel::setMode,
        )
        if (draft.mode == RoleMenuMode.PICK_ANY) {
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Numbers),
                options = minChoices,
                placeholder = "None",
                label = "Must keep at least",
                selectedId = draft.minRoles.toString(),
                onSelect = { viewModel.setMinRoles(it?.toIntOrNull() ?: 0) },
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Numbers),
                options = maxChoices,
                placeholder = "No limit",
                label = "Can hold at most",
                selectedId = draft.maxRoles.toString(),
                onSelect = { viewModel.setMaxRoles(it?.toIntOrNull() ?: 0) },
            )
        } else {
            SwitchRow(
                title = "Keep one once chosen",
                subtitle = "Members can switch roles but can't clear their pick.",
                checked = draft.minRoles >= 1,
                onCheckedChange = viewModel::setKeepOne,
            )
        }
    }
}

/** The role needed to use the menu. */
@Composable
private fun AccessSection(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    val draft = state.draft
    SectionCard {
        SectionCardHeader("Who can use it", Icons.Default.Lock)
        Row(verticalAlignment = Alignment.Bottom) {
            DiscordSelectorSingle(
                kind = SelectorKind.Role,
                options = state.roles.map { SelectorOption(it.id, it.name, colorHex = it.color.toInt()) },
                placeholder = "Anyone",
                label = "Required role",
                selectedId = draft.requiredRoleId,
                onSelect = viewModel::setRequiredRole,
                modifier = Modifier.weight(1f),
            )
            if (draft.requiredRoleId != null) {
                IconButton(onClick = { viewModel.setRequiredRole(null) }) {
                    Icon(Icons.Default.Close, contentDescription = "Let anyone use it")
                }
            }
        }
        HelperText("Members without this role get a private note saying they need it.")
    }
}

/** Whether members get a private note listing what changed. */
@Composable
private fun ConfirmationSection(state: RoleMenusState, viewModel: RoleMenusViewModel) {
    val tell = state.draft.replyMode == RoleMenuReplyMode.PRIVATE
    SectionCard {
        SectionCardHeader("Confirmation", Icons.Default.Notifications)
        SwitchRow(
            title = "Tell members what changed",
            subtitle = "Sends a private note only they can see, listing the roles added and removed.",
            checked = tell,
            onCheckedChange = viewModel::setTellMembers,
        )
        if (!tell) {
            HelperText("Members still get a private note if something goes wrong.")
        }
    }
}

/** The option list with reordering, row labels for buttons, and the add button. */
@Composable
private fun OptionsSection(
    state: RoleMenusState,
    onEdit: (RoleMenuOptionDraft) -> Unit,
    onAdd: () -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    val draft = state.draft
    val options = draft.options
    val full = options.size >= RoleMenuLimits.MaxOptions
    SectionCard {
        SectionCardHeader(
            title = "Options",
            icon = Icons.Default.FormatListNumbered,
            trailing = {
                Text(
                    text = "${options.size} / ${RoleMenuLimits.MaxOptions}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        options.forEachIndexed { index, option ->
            if (draft.style == RoleMenuStyle.BUTTONS && index % RoleMenuLimits.ButtonsPerRow == 0) {
                Text(
                    text = "Row ${index / RoleMenuLimits.ButtonsPerRow + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp),
                )
            }
            OptionRow(
                position = index + 1,
                option = option,
                state = state,
                canMoveUp = index > 0,
                canMoveDown = index < options.lastIndex,
                onClick = { onEdit(option) },
                onMoveUp = { onMove(option.key, -1) },
                onMoveDown = { onMove(option.key, 1) },
                onRemove = { onRemove(option.key) },
            )
        }
        RoleMenuActionButton(
            text = "Add option",
            icon = Icons.Default.Add,
            onClick = onAdd,
            enabled = !full,
            modifier = Modifier.fillMaxWidth(),
        )
        if (full) {
            HelperText("25 is the most a menu can hold")
        }
    }
}

/** One option in the list: its number, emoji, name, role, color, and row actions. */
@Composable
private fun OptionRow(
    position: Int,
    option: RoleMenuOptionDraft,
    state: RoleMenusState,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val role = state.role(option.roleId)
    val roleName = role?.name.orEmpty()
    val label = option.label.ifBlank { roleName }.ifBlank { "Option $position" }
    val problem = state.problemFor(option)
    val isButtons = state.draft.style == RoleMenuStyle.BUTTONS
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = primary.copy(alpha = DashAlpha.Hex08),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                Text(
                    text = position.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = readableInk(primary),
                    modifier = Modifier.width(20.dp),
                )
                RoleMenuEmoji(option.emoji, size = 20.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        val dot = role?.color?.takeIf { it != 0L }?.let { Rgb.fromArgb(it.toInt()).color }
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(dot ?: MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                        )
                        Text(
                            text = if (option.roleId.isBlank()) "Pick a role" else "@" + roleName.ifBlank { "deleted role" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isButtons) {
                            val color = RoleMenuButtonColor.from(option.buttonStyle)
                            Box(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(width = 14.dp, height = 10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color.color),
                            )
                            Text(
                                text = color.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move $label up")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move $label down")
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove $label", tint = ProblemRed)
                }
            }
            if (problem != null) {
                ProblemNote(problem, modifier = Modifier.padding(end = 8.dp, bottom = 4.dp))
            }
        }
    }
}

/**
 * Adds or edits one option: Role, Name on the menu, Emoji, Description, and
 * Button color for buttons menus.
 */
@Composable
private fun OptionSheet(
    initial: RoleMenuOptionDraft,
    isNew: Boolean,
    state: RoleMenusState,
    onConfirm: (RoleMenuOptionDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var working by remember(initial.key) { mutableStateOf(initial) }
    val usedElsewhere = state.draft.options.filter { it.key != working.key }.map { it.roleId }.toSet()
    val roleOptions = state.roles
        .filter { (it.assignable || it.id == working.roleId) && it.id !in usedElsewhere }
        .map { SelectorOption(it.id, it.name, subtitle = it.problem, colorHex = it.color.toInt()) }
    val roleName = state.role(working.roleId)?.name.orEmpty()
    val problem = state.problemFor(working)
    val isButtons = state.draft.style == RoleMenuStyle.BUTTONS

    FormSheet(
        title = if (isNew) "Add option" else "Edit option",
        confirmLabel = if (isNew) "Add option" else "Done",
        confirmEnabled = working.roleId.isNotBlank(),
        onConfirm = { onConfirm(working) },
        onDismiss = onDismiss,
    ) {
        DiscordSelectorSingle(
            kind = SelectorKind.Role,
            options = roleOptions,
            placeholder = "Pick a role",
            label = "Role",
            selectedId = working.roleId.takeIf { it.isNotBlank() },
            onSelect = { id ->
                val picked = state.role(id)
                working = working.copy(
                    roleId = id.orEmpty(),
                    label = if (working.label.isBlank()) picked?.name.orEmpty().take(RoleMenuLimits.LabelLength) else working.label,
                    problem = if (id == initial.roleId) initial.problem else null,
                )
            },
        )
        if (problem != null) {
            ProblemNote(problem)
        }
        MewdekoTextField(
            value = working.label,
            onValueChange = { working = working.copy(label = it.take(RoleMenuLimits.LabelLength)) },
            label = "Name on the menu",
            placeholder = roleName.ifBlank { null },
        )
        RoleMenuEmojiField(
            value = working.emoji,
            emojis = state.lookups?.emojis.orEmpty(),
            onChange = { working = working.copy(emoji = it) },
        )
        MewdekoTextField(
            value = working.description,
            onValueChange = { working = working.copy(description = it.take(RoleMenuLimits.DescriptionLength)) },
            label = "Description",
            placeholder = "Optional",
            supportingText = if (isButtons) "Shown in the default message" else "Shown under the dropdown option",
        )
        if (isButtons) {
            EnumPicker(
                label = "Button color",
                options = ColorOptions,
                selected = RoleMenuButtonColor.from(working.buttonStyle),
                onSelect = { working = working.copy(buttonStyle = it.value) },
                showDescription = false,
            )
        }
    }
}

/**
 * The option emoji input: a text field for a unicode emoji or pasted custom
 * emoji text, plus a picker over this server's emojis that fills it in.
 */
@Composable
private fun RoleMenuEmojiField(
    value: String,
    emojis: List<LookupEmoji>,
    onChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MewdekoTextField(
            value = value,
            onValueChange = onChange,
            label = "Emoji",
            placeholder = "No emoji",
        )
        if (emojis.isNotEmpty()) {
            val selectedId = emojis.firstOrNull { it.formatted == value.trim() }?.id
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.EmojiEmotions),
                options = emojis.map { SelectorOption(it.id, it.name, imageUrl = it.url.ifBlank { null }) },
                placeholder = "Server emoji",
                label = "Server emoji",
                selectedId = selectedId,
                onSelect = { id -> onChange(emojis.firstOrNull { it.id == id }?.formatted.orEmpty()) },
            )
        }
    }
}

/** A red inline note naming what is wrong with an option's role. */
@Composable
private fun ProblemNote(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = ProblemRed.copy(alpha = DashAlpha.Hex20),
        border = BorderStroke(1.dp, ProblemRed.copy(alpha = DashAlpha.Hex30)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = ProblemRed, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = readableInk(ProblemRed))
        }
    }
}

/** A muted helper line under a field. */
@Composable
private fun HelperText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
