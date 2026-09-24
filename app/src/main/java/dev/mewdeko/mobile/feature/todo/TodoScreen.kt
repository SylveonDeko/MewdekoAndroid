package dev.mewdeko.mobile.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.FormSheet
import dev.mewdeko.mobile.core.ui.FullScreenEditor
import dev.mewdeko.mobile.core.ui.NewItemFab
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SearchField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.shortDate
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Personal and shared task lists. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodoScreen(
    guild: GuildRouteArgs,
    onBack: () -> Unit,
    viewModel: TodoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()

    var showCreateList by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var addingItemTo by remember { mutableStateOf<TodoListModel?>(null) }
    var editingItem by remember { mutableStateOf<TodoItemModel?>(null) }
    var taggingItem by remember { mutableStateOf<TodoItemModel?>(null) }
    var permissionsForList by remember { mutableStateOf<TodoListModel?>(null) }
    var pendingDeleteList by remember { mutableStateOf<TodoListModel?>(null) }
    var pendingDeleteItem by remember { mutableStateOf<TodoItemModel?>(null) }

    FeatureScaffold(
        title = "Todo Lists",
        subtitle = guild.name.takeIf { it.isNotEmpty() },
        onBack = onBack,
        loadState = loadState,
        status = status,
        onStatusShown = viewModel::clearStatus,
        onRefresh = { viewModel.load(refreshing = true) },
        onRetry = { viewModel.load() },
        actions = {
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filters")
            }
        },
        floatingActionButton = {
            NewItemFab(label = "New list", onClick = { showCreateList = true })
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Checklist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Lists", "${state.lists.size}", Modifier.weight(1f))
                StatTile("Open", "${state.openCount}", Modifier.weight(1f))
                StatTile("Done", "${state.doneCount}", Modifier.weight(1f))
            }
        }

        SearchField(
            value = state.searchQuery,
            onValueChange = viewModel::setSearchQuery,
            placeholder = "Search todo lists...",
        )

        if (showFilters) {
            SectionCard {
                SectionCardHeader("Filters", Icons.AutoMirrored.Filled.Sort)
                SwitchRow(
                    title = "Show completed items",
                    checked = state.includeCompleted,
                    onCheckedChange = viewModel::setIncludeCompleted,
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.Sort),
                    options = TodoSortBy.entries.map { SelectorOption(it.id, it.label) },
                    placeholder = "Sort by",
                    label = "Sort by",
                    selectedId = state.sortBy.id,
                    onSelect = { viewModel.setSortBy(TodoSortBy.from(it)) },
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.AutoMirrored.Filled.Sort),
                    options = TodoSortOrder.entries.map { SelectorOption(it.id, it.label) },
                    placeholder = "Order",
                    label = "Order",
                    selectedId = state.sortOrder.id,
                    onSelect = { viewModel.setSortOrder(TodoSortOrder.from(it)) },
                )
            }
        }

        val visibleLists = state.visibleLists()
        if (visibleLists.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = if (state.searchQuery.isBlank()) {
                        "No lists yet. Create one to start tracking tasks."
                    } else {
                        "No lists match \"${state.searchQuery}\"."
                    },
                    icon = Icons.Default.Checklist,
                    actionLabel = if (state.searchQuery.isBlank()) "New list" else null,
                    onAction = if (state.searchQuery.isBlank()) ({ showCreateList = true }) else null,
                )
            }
        } else {
            visibleLists.forEach { list ->
                val perms = state.permissionsFor(list, viewModel.userId)
                val stats = state.stats(list.id)
                val items = state.visibleItems(list.id)

                SectionCard {
                    SectionCardHeader(
                        title = list.name,
                        icon = if (list.isServerList) Icons.Default.Groups else Icons.Default.Checklist,
                        trailing = {
                            Row {
                                if (perms.canAdd) {
                                    IconButton(onClick = { addingItemTo = list }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add task")
                                    }
                                }
                                if (perms.canView) {
                                    IconButton(onClick = { permissionsForList = list }) {
                                        Icon(Icons.Default.Shield, contentDescription = "Manage permissions")
                                    }
                                }
                                if (perms.canManage) {
                                    IconButton(onClick = { pendingDeleteList = list }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete list",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        },
                    )
                    list.description?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (list.isServerList) TagChip("Server list")
                        if (list.isPublic) TagChip("Public")
                    }

                    ListStatsRow(stats)

                    if (items.isEmpty()) {
                        val listIsEmpty = state.items(list.id).isEmpty()
                        EmptyState(
                            message = if (listIsEmpty) "Nothing on this list yet."
                            else "No items match the current filters.",
                            actionLabel = if (listIsEmpty && perms.canAdd) "Add task" else null,
                            onAction = if (listIsEmpty && perms.canAdd) ({ addingItemTo = list }) else null,
                        )
                    } else {
                        items.forEach { item ->
                            ListItem(
                                leadingContent = {
                                    Checkbox(
                                        checked = item.isCompleted,
                                        onCheckedChange = { viewModel.complete(item) },
                                        enabled = !item.isCompleted && (perms.canComplete || perms.canEdit),
                                    )
                                },
                                headlineContent = {
                                    Text(
                                        text = item.title,
                                        textDecoration = if (item.isCompleted) {
                                            TextDecoration.LineThrough
                                        } else {
                                            TextDecoration.None
                                        },
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        item.description?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TagChip(
                                                label = item.priorityType.label,
                                                icon = item.priorityType.icon,
                                            )
                                            item.dueDate?.let {
                                                DueDateChip(dueDate = it, overdue = item.isOverdue)
                                            }
                                            item.tags.forEach { tag -> TagChip(tag) }
                                            item.completedAt?.let { TagChip("Done ${it.shortDate()}") }
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
                                        if (perms.canEdit) {
                                            IconButton(onClick = { taggingItem = item }) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    contentDescription = "Add tag",
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                            IconButton(onClick = { editingItem = item }) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit task",
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                        if (perms.canDelete) {
                                            IconButton(onClick = { pendingDeleteItem = item }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Delete task",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = Color.Transparent,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateList) {
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        FormSheet(
            title = "New list",
            confirmLabel = "Create",
            confirmEnabled = name.isNotBlank(),
            onConfirm = {
                viewModel.createList(name.trim(), description.takeIf { it.isNotBlank() })
                showCreateList = false
            },
            onDismiss = { showCreateList = false },
        ) {
            MewdekoTextField(value = name, onValueChange = { name = it }, label = "Name")
            MewdekoTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description (optional)",
            )
            Text(
                text = "Server lists are visible to everyone in this server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    addingItemTo?.let { list ->
        var title by remember(list.id) { mutableStateOf("") }
        var description by remember(list.id) { mutableStateOf("") }
        var priority by remember(list.id) { mutableStateOf(TodoPriority.MEDIUM) }
        var dueInDays by remember(list.id) { mutableStateOf("") }
        FormSheet(
            title = "Add task to ${list.name}",
            confirmLabel = "Add",
            confirmEnabled = title.isNotBlank(),
            onConfirm = {
                viewModel.addItem(
                    listId = list.id,
                    title = title.trim(),
                    description = description.takeIf { it.isNotBlank() },
                    priority = priority,
                    dueDate = dueInDays.toLongOrNull()
                        ?.let { Instant.now().plus(it, ChronoUnit.DAYS) },
                )
                addingItemTo = null
            },
            onDismiss = { addingItemTo = null },
        ) {
            MewdekoTextField(value = title, onValueChange = { title = it }, label = "Title")
            MewdekoTextField(
                value = description,
                onValueChange = { description = it },
                label = "Description (optional)",
                singleLine = false,
                minLines = 2,
            )
            DiscordSelectorSingle(
                kind = SelectorKind.Custom(Icons.Default.Checklist),
                options = TodoPriority.entries.map { SelectorOption(it.raw.toString(), it.label) },
                placeholder = "Medium",
                label = "Priority",
                selectedId = priority.raw.toString(),
                onSelect = { priority = TodoPriority.from(it?.toIntOrNull() ?: 2) },
            )
            MewdekoTextField(
                value = dueInDays,
                onValueChange = { dueInDays = it.filter(Char::isDigit) },
                label = "Due in days (optional)",
                numeric = true,
            )
        }
    }

    editingItem?.let { item ->
        var title by remember(item.id) { mutableStateOf(item.title) }
        var description by remember(item.id) { mutableStateOf(item.description.orEmpty()) }
        var priority by remember(item.id) { mutableStateOf(item.priorityType) }
        var dueDate by remember(item.id) { mutableStateOf(item.dueDate) }
        var showDatePicker by remember(item.id) { mutableStateOf(false) }

        FullScreenEditor(
            title = "Edit task",
            onClose = { editingItem = null },
            confirmLabel = "Save",
            confirmEnabled = title.isNotBlank(),
            onConfirm = {
                viewModel.updateItem(
                    item,
                    title.trim(),
                    description.takeIf { it.isNotBlank() },
                    priority,
                    dueDate,
                )
                editingItem = null
            },
            hasUnsavedChanges = title != item.title || description != item.description.orEmpty() ||
                priority != item.priorityType || dueDate != item.dueDate,
        ) {
            SectionCard {
                MewdekoTextField(value = title, onValueChange = { title = it }, label = "Title")
                MewdekoTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    singleLine = false,
                    minLines = 2,
                )
                DiscordSelectorSingle(
                    kind = SelectorKind.Custom(Icons.Default.Checklist),
                    options = TodoPriority.entries.map { SelectorOption(it.raw.toString(), it.label) },
                    placeholder = "Priority",
                    label = "Priority",
                    selectedId = priority.raw.toString(),
                    onSelect = { priority = TodoPriority.from(it?.toIntOrNull() ?: priority.raw) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.Schedule, contentDescription = null)
                        Text("  " + (dueDate?.shortDate() ?: "Set due date"))
                    }
                    if (dueDate != null) {
                        TextButton(onClick = { dueDate = null }) { Text("Clear") }
                    }
                }
                if (item.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.removeTag(item, tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove tag $tag")
                                },
                            )
                        }
                    }
                }
            }

            if (showDatePicker) {
                DueDatePickerDialog(
                    initial = dueDate,
                    onConfirm = { dueDate = it; showDatePicker = false },
                    onDismiss = { showDatePicker = false },
                )
            }
        }
    }

    taggingItem?.let { item ->
        var tag by remember(item.id) { mutableStateOf("") }
        FormSheet(
            title = "Add tag",
            confirmLabel = "Add",
            confirmEnabled = tag.isNotBlank(),
            onConfirm = { viewModel.addTag(item, tag.trim()); taggingItem = null },
            onDismiss = { taggingItem = null },
        ) {
            MewdekoTextField(value = tag, onValueChange = { tag = it }, label = "Tag")
        }
    }

    permissionsForList?.let { list ->
        TodoPermissionsDialog(
            list = list,
            permissions = state.permissionsByList[list.id].orEmpty(),
            members = state.members,
            onGrant = { target, canView, canEdit, canManage ->
                viewModel.grantPermission(list.id, target, canView, canEdit, canManage)
            },
            onRevoke = { target -> viewModel.revokePermission(list.id, target) },
            onDismiss = { permissionsForList = null },
        )
    }

    pendingDeleteList?.let { list ->
        ConfirmDialog(
            title = "Delete list?",
            message = "\"${list.name}\" and all of its tasks are removed.",
            onConfirm = { viewModel.deleteList(list.id) },
            onDismiss = { pendingDeleteList = null },
        )
    }

    pendingDeleteItem?.let { item ->
        ConfirmDialog(
            title = "Delete task?",
            message = "\"${item.title}\" is removed from the list.",
            onConfirm = { viewModel.deleteItem(item) },
            onDismiss = { pendingDeleteItem = null },
        )
    }
}

/** Pending, completed, overdue counts and a completion-rate progress bar for one list. */
@Composable
private fun ListStatsRow(stats: TodoListStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${stats.pending} pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${stats.completed} done",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.overdue > 0) {
                    Text(
                        "${stats.overdue} overdue",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                "${stats.total} item${if (stats.total == 1) "" else "s"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (stats.total > 0) {
            LinearProgressIndicator(
                progress = { stats.completionRate / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Due-date chip that turns error-colored when the item is overdue. */
@Composable
private fun DueDateChip(dueDate: Instant, overdue: Boolean) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text("Due ${dueDate.shortDate()}", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
        colors = if (overdue) {
            AssistChipDefaults.assistChipColors(
                disabledContainerColor = MaterialTheme.colorScheme.errorContainer,
                disabledLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                disabledLeadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            AssistChipDefaults.assistChipColors()
        },
    )
}

/** A Material date picker constrained to setting a task's due date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(
    initial: Instant?,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: Instant.now()).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton
                    onConfirm(Instant.ofEpochMilli(millis).plus(23, ChronoUnit.HOURS).plusSeconds(59 * 60 + 59))
                },
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}
