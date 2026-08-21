package dev.mewdeko.mobile.feature.todo

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** How urgent a todo item is. */
enum class TodoPriority(val raw: Int, val label: String, val icon: ImageVector) {
    LOW(1, "Low", Icons.Default.Remove),
    MEDIUM(2, "Medium", Icons.Default.KeyboardArrowUp),
    HIGH(3, "High", Icons.Default.PriorityHigh),
    URGENT(4, "Urgent", Icons.Default.Warning);

    companion object {
        /** Maps a wire value onto a priority, defaulting to [MEDIUM]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: MEDIUM
    }
}

/** A todo list, either personal or shared with the server. */
@Serializable
data class TodoListModel(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val ownerId: Snowflake? = null,
    val isServerList: Boolean = false,
    val isPublic: Boolean = false,
    val color: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant? = null,
)

/** A single task within a todo list. */
@Serializable
data class TodoItemModel(
    val id: Int = 0,
    val todoListId: Int = 0,
    val title: String = "",
    val description: String? = null,
    val isCompleted: Boolean = false,
    val priority: Int = 1,
    @Serializable(with = InstantSerializer::class) val dueDate: Instant? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val completedBy: Snowflake? = null,
    val tags: List<String> = emptyList(),
    val position: Int = 0,
) {
    /** The typed form of [priority]. */
    val priorityType: TodoPriority get() = TodoPriority.from(priority)
}

/** Todo screen state. */
data class TodoState(
    val lists: List<TodoListModel> = emptyList(),
    val itemsByList: Map<Int, List<TodoItemModel>> = emptyMap(),
    val includeCompleted: Boolean = true,
) {
    /** Items belonging to one list, in display order. */
    fun items(listId: Int): List<TodoItemModel> = itemsByList[listId].orEmpty()

    /** How many tasks are still open across every list. */
    val openCount: Int get() = itemsByList.values.flatten().count { !it.isCompleted }

    /** How many tasks are done across every list. */
    val doneCount: Int get() = itemsByList.values.flatten().count { it.isCompleted }
}

/** Personal and shared task lists. */
@HiltViewModel
class TodoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(TodoState())

    /** Observable screen state. */
    val state: StateFlow<TodoState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads every list and its items. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val lists = api.send(
            Endpoint("api/Todo/$guildId/lists/$userId"),
            ListSerializer(TodoListModel.serializer()),
        ).sortedWith(
            compareByDescending<TodoListModel> { it.isServerList }.thenBy { it.name.lowercase() }
        )
        _state.update { it.copy(lists = lists) }
        lists.forEach { list -> loadItems(list.id) }
    }

    /** Reloads one list's items. */
    fun loadItems(listId: Int) = viewModelScope.launch {
        val include = _state.value.includeCompleted
        val items = runCatching {
            api.send(
                Endpoint("api/Todo/$guildId/lists/$listId/items/$userId?includeCompleted=$include"),
                ListSerializer(TodoItemModel.serializer()),
            )
        }.getOrDefault(emptyList())

        val sorted = items.sortedWith(
            compareBy<TodoItemModel> { it.isCompleted }
                .thenByDescending { it.priority }
                .thenBy { it.position }
        )
        _state.update { it.copy(itemsByList = it.itemsByList + (listId to sorted)) }
    }

    /** Shows or hides completed tasks, reloading every list. */
    fun setIncludeCompleted(value: Boolean) = viewModelScope.launch {
        _state.update { it.copy(includeCompleted = value) }
        _state.value.lists.forEach { loadItems(it.id) }
    }

    /** Creates a list. */
    fun createList(name: String, description: String?, isServerList: Boolean) =
        launchAction("Failed to create list.") {
            api.send(
                Endpoint(
                    "api/Todo/$guildId/lists",
                    HttpMethod.POST,
                    jsonBody(
                        "userId" to (userId.toLongOrNull() ?: 0L),
                        "name" to name,
                        "description" to description.orEmpty(),
                        "isServerList" to isServerList,
                    ),
                ),
                TodoListModel.serializer(),
            )
            postSuccess("List created.")
            load(refreshing = true)
        }

    /** Deletes a list and its tasks. */
    fun deleteList(listId: Int) = launchAction("Failed to delete list.") {
        api.sendIgnoringBody(
            Endpoint("api/Todo/$guildId/lists/$listId/$userId", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(
                lists = it.lists.filterNot { list -> list.id == listId },
                itemsByList = it.itemsByList - listId,
            )
        }
        postSuccess("List deleted.")
    }

    /** Adds a task to a list. */
    fun addItem(
        listId: Int,
        title: String,
        description: String?,
        priority: TodoPriority,
        dueDate: Instant?,
    ) = launchAction("Failed to add item.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Todo/$guildId/lists/$listId/items",
                HttpMethod.POST,
                jsonBody(
                    "userId" to (userId.toLongOrNull() ?: 0L),
                    "title" to title,
                    "description" to description,
                    "priority" to priority.raw,
                    "dueDate" to dueDate?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                ),
            )
        )
        loadItems(listId)
    }

    /** Marks a task complete. Completed tasks cannot be reopened by the API. */
    fun complete(item: TodoItemModel) = launchAction("Failed to mark complete.") {
        if (item.isCompleted) return@launchAction
        api.sendIgnoringBody(
            Endpoint("api/Todo/$guildId/items/${item.id}/complete/$userId", HttpMethod.PUT)
        )
        loadItems(item.todoListId)
    }

    /** Deletes a task. */
    fun deleteItem(item: TodoItemModel) = launchAction("Failed to delete item.") {
        api.sendIgnoringBody(
            Endpoint("api/Todo/$guildId/items/${item.id}/$userId", HttpMethod.DELETE)
        )
        _state.update { current ->
            current.copy(
                itemsByList = current.itemsByList + (
                    item.todoListId to current.items(item.todoListId)
                        .filterNot { it.id == item.id }
                    ),
            )
        }
    }

    /** Renames or re-describes a task. */
    fun updateItem(item: TodoItemModel, title: String, description: String?) =
        launchAction("Failed to update item.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Todo/$guildId/items/${item.id}",
                    HttpMethod.PUT,
                    jsonBody(
                        "userId" to (userId.toLongOrNull() ?: 0L),
                        "title" to title,
                        "description" to description,
                    ),
                )
            )
            loadItems(item.todoListId)
        }

    /** Sets or clears a task's due date. */
    fun setDueDate(item: TodoItemModel, dueDate: Instant?) =
        launchAction("Failed to set due date.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Todo/$guildId/items/${item.id}/duedate",
                    HttpMethod.PUT,
                    jsonBody(
                        "userId" to (userId.toLongOrNull() ?: 0L),
                        "dueDate" to dueDate?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                    ),
                )
            )
            loadItems(item.todoListId)
        }

    /** Adds a tag to a task. */
    fun addTag(item: TodoItemModel, tag: String) = launchAction("Failed to add tag.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Todo/$guildId/items/${item.id}/tags",
                HttpMethod.POST,
                jsonBody("userId" to (userId.toLongOrNull() ?: 0L), "tag" to tag),
            )
        )
        loadItems(item.todoListId)
    }

    /** Removes a tag from a task. */
    fun removeTag(item: TodoItemModel, tag: String) = launchAction("Failed to remove tag.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Todo/$guildId/items/${item.id}/tags",
                HttpMethod.DELETE,
                jsonBody("userId" to (userId.toLongOrNull() ?: 0L), "tag" to tag),
            )
        )
        loadItems(item.todoListId)
    }
}
