package dev.mewdeko.mobile.feature.todo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mewdeko.mobile.core.ui.ConfirmDialog
import dev.mewdeko.mobile.core.ui.DiscordSelectorSingle
import dev.mewdeko.mobile.core.ui.EmptyState
import dev.mewdeko.mobile.core.ui.FeatureScaffold
import dev.mewdeko.mobile.core.ui.MewdekoTextField
import dev.mewdeko.mobile.core.ui.SectionCard
import dev.mewdeko.mobile.core.ui.SectionCardHeader
import dev.mewdeko.mobile.core.ui.SelectorKind
import dev.mewdeko.mobile.core.ui.SelectorOption
import dev.mewdeko.mobile.core.ui.StatTile
import dev.mewdeko.mobile.core.ui.SwitchRow
import dev.mewdeko.mobile.core.ui.TagChip
import dev.mewdeko.mobile.navigation.GuildRouteArgs
import dev.mewdeko.mobile.util.relativeToNow
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
    var addingItemTo by remember { mutableStateOf<TodoListModel?>(null) }
    var editingItem by remember { mutableStateOf<TodoItemModel?>(null) }
    var taggingItem by remember { mutableStateOf<TodoItemModel?>(null) }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateList = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
    ) {
        SectionCard {
            SectionCardHeader("Overview", Icons.Default.Checklist)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Lists", "${state.lists.size}", Modifier.weight(1f))
                StatTile("Open", "${state.openCount}", Modifier.weight(1f))
                StatTile("Done", "${state.doneCount}", Modifier.weight(1f))
            }
            SwitchRow(
                title = "Show completed",
                checked = state.includeCompleted,
                onCheckedChange = viewModel::setIncludeCompleted,
            )
        }

        if (state.lists.isEmpty()) {
            SectionCard {
                EmptyState(
                    message = "No lists yet. Create one to start tracking tasks.",
                    icon = Icons.Default.Checklist,
                )
            }
        } else {
            state.lists.forEach { list ->
                val items = state.items(list.id)
                SectionCard {
                    SectionCardHeader(
                        title = list.name,
                        icon = if (list.isServerList) Icons.Default.Groups
                        else Icons.Default.Checklist,
                        trailing = {
                            Row {
                                IconButton(onClick = { addingItemTo = list }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add task")
                                }
                                IconButton(onClick = { pendingDeleteList = list }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete list",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
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
                        TagChip("${items.count { !it.isCompleted }} open")
                    }

                    if (items.isEmpty()) {
                        EmptyState("Nothing on this list yet.")
                    } else {
                        items.forEach { item ->
                            ListItem(
                                leadingContent = {
                                    Checkbox(
                                        checked = item.isCompleted,
                                        onCheckedChange = { viewModel.complete(item) },
                                        enabled = !item.isCompleted,
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
                                            Text(
                                                text = it,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            TagChip(
                                                label = item.priorityType.label,
                                                icon = item.priorityType.icon,
                                            )
                                            item.dueDate?.let {
                                                TagChip(
                                                    label = "Due ${it.shortDate()}",
                                                    icon = Icons.Default.Schedule,
                                                )
                                            }
                                            item.tags.forEach { tag -> TagChip(tag) }
                                            item.completedAt?.let {
                                                TagChip("Done ${it.relativeToNow()}")
                                            }
                                        }
                                    }
                                },
                                trailingContent = {
                                    Row {
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
                                        IconButton(onClick = { pendingDeleteItem = item }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Delete task",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
        var isServerList by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showCreateList = false },
            title = { Text("New list") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MewdekoTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Name",
                    )
                    MewdekoTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description (optional)",
                    )
                    SwitchRow(
                        title = "Server list",
                        subtitle = "Visible to everyone in this server",
                        checked = isServerList,
                        onCheckedChange = { isServerList = it },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createList(
                            name.trim(),
                            description.takeIf { it.isNotBlank() },
                            isServerList,
                        )
                        showCreateList = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateList = false }) { Text("Cancel") }
            },
        )
    }

    addingItemTo?.let { list ->
        var title by remember(list.id) { mutableStateOf("") }
        var description by remember(list.id) { mutableStateOf("") }
        var priority by remember(list.id) { mutableStateOf(TodoPriority.MEDIUM) }
        var dueInDays by remember(list.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingItemTo = null },
            title = { Text("Add task to ${list.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MewdekoTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Title",
                    )
                    MewdekoTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description (optional)",
                        singleLine = false,
                        minLines = 2,
                    )
                    DiscordSelectorSingle(
                        kind = SelectorKind.Custom(Icons.Default.Checklist),
                        options = TodoPriority.entries.map {
                            SelectorOption(it.raw.toString(), it.label)
                        },
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
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    enabled = title.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingItemTo = null }) { Text("Cancel") } },
        )
    }

    editingItem?.let { item ->
        var title by remember(item.id) { mutableStateOf(item.title) }
        var description by remember(item.id) { mutableStateOf(item.description.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Edit task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MewdekoTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = "Title",
                    )
                    MewdekoTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = "Description",
                        singleLine = false,
                        minLines = 2,
                    )
                    if (item.dueDate != null) {
                        TextButton(onClick = { viewModel.setDueDate(item, null) }) {
                            Text("Clear due date")
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
                                        Icon(Icons.Default.Close, contentDescription = null)
                                    },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateItem(
                            item,
                            title.trim(),
                            description.takeIf { it.isNotBlank() },
                        )
                        editingItem = null
                    },
                    enabled = title.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingItem = null }) { Text("Cancel") } },
        )
    }

    taggingItem?.let { item ->
        var tag by remember(item.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { taggingItem = null },
            title = { Text("Add tag") },
            text = {
                MewdekoTextField(value = tag, onValueChange = { tag = it }, label = "Tag")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.addTag(item, tag.trim()); taggingItem = null },
                    enabled = tag.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { taggingItem = null }) { Text("Cancel") } },
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
