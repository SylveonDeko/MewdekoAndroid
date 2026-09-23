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
import dev.mewdeko.mobile.core.model.GuildMember
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

/** How the item list within a list is ordered. */
enum class TodoSortBy(val id: String, val label: String) {
    PRIORITY("priority", "Priority"),
    DUE_DATE("dueDate", "Due Date"),
    CREATED("createdAt", "Created"),
    TITLE("title", "Title");

    companion object {
        /** Maps a wire id onto a sort field, defaulting to [PRIORITY]. */
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: PRIORITY
    }
}

/** Ascending or descending item ordering. */
enum class TodoSortOrder(val id: String, val label: String) {
    DESC("desc", "Descending"),
    ASC("asc", "Ascending");

    companion object {
        /** Maps a wire id onto a sort order, defaulting to [DESC]. */
        fun from(id: String?) = entries.firstOrNull { it.id == id } ?: DESC
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

    /** True when the item has a due date in the past and is not yet complete. */
    val isOverdue: Boolean get() = dueDate != null && !isCompleted && dueDate.isBefore(Instant.now())
}

/** A user's granted access to a todo list, as returned by the permissions endpoint. */
@Serializable
data class TodoListPermissionModel(
    val id: Int = 0,
    val todoListId: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val canView: Boolean = false,
    val canAdd: Boolean = false,
    val canEdit: Boolean = false,
    val canComplete: Boolean = false,
    val canDelete: Boolean = false,
    val canManageList: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val grantedBy: Snowflake? = null,
    @Serializable(with = InstantSerializer::class) val grantedAt: Instant? = null,
) {
    /** Coarse role label matching the dashboard's permission manager. */
    val roleLabel: String
        get() = when {
            canManageList -> "Manager"
            canEdit -> "Editor"
            canAdd -> "Contributor"
            else -> "Viewer"
        }
}

/** The signed-in user's effective permissions over one todo list. */
data class TodoUserPermissions(
    val canView: Boolean = false,
    val canAdd: Boolean = false,
    val canEdit: Boolean = false,
    val canComplete: Boolean = false,
    val canDelete: Boolean = false,
    val canManage: Boolean = false,
) {
    companion object {
        /** Full access, granted to a list's owner. */
        val Owner = TodoUserPermissions(
            canView = true, canAdd = true, canEdit = true,
            canComplete = true, canDelete = true, canManage = true,
        )

        /** Default read-only access to a public server list with no explicit grant. */
        val ServerViewOnly = TodoUserPermissions(canView = true)

        /** No access at all. */
        val None = TodoUserPermissions()
    }
}

/** Aggregate counts shown on a list's card. */
data class TodoListStats(
    val total: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val overdue: Int = 0,
    val completionRate: Int = 0,
)

/** Todo screen state. */
data class TodoState(
    val lists: List<TodoListModel> = emptyList(),
    val itemsByList: Map<Int, List<TodoItemModel>> = emptyMap(),
    val permissionsByList: Map<Int, List<TodoListPermissionModel>> = emptyMap(),
    val members: List<GuildMember> = emptyList(),
    val includeCompleted: Boolean = true,
    val searchQuery: String = "",
    val sortBy: TodoSortBy = TodoSortBy.PRIORITY,
    val sortOrder: TodoSortOrder = TodoSortOrder.DESC,
) {
    /** Every item belonging to one list, as loaded from the server. */
    fun items(listId: Int): List<TodoItemModel> = itemsByList[listId].orEmpty()

    /** Lists matching the current search query, by name or description. */
    fun visibleLists(): List<TodoListModel> {
        val query = searchQuery.trim()
        if (query.isEmpty()) return lists
        return lists.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.description?.contains(query, ignoreCase = true) == true)
        }
    }

    /** A list's items filtered by [includeCompleted] and sorted by [sortBy]/[sortOrder]. */
    fun visibleItems(listId: Int): List<TodoItemModel> {
        val all = items(listId)
        val filtered = if (includeCompleted) all else all.filterNot { it.isCompleted }
        val order = if (sortOrder == TodoSortOrder.ASC) 1 else -1
        return filtered.sortedWith { a, b ->
            when (sortBy) {
                TodoSortBy.PRIORITY -> (a.priority - b.priority) * order
                TodoSortBy.DUE_DATE -> compareNullableLast(a.dueDate, b.dueDate, order)
                TodoSortBy.CREATED -> compareNullableLast(a.createdAt, b.createdAt, order)
                TodoSortBy.TITLE -> a.title.compareTo(b.title, ignoreCase = true) * order
            }
        }
    }

    /** Completion and overdue counts for one list, independent of [includeCompleted]. */
    fun stats(listId: Int): TodoListStats {
        val all = items(listId)
        val total = all.size
        val completed = all.count { it.isCompleted }
        val pending = total - completed
        val overdue = all.count { it.isOverdue }
        val rate = if (total > 0) (completed * 100) / total else 0
        return TodoListStats(total, completed, pending, overdue, rate)
    }

    /** The signed-in user's effective permissions over [list]. */
    fun permissionsFor(list: TodoListModel, userId: Snowflake): TodoUserPermissions {
        if (list.ownerId != null && list.ownerId == userId) return TodoUserPermissions.Owner
        val grant = permissionsByList[list.id]?.firstOrNull { it.userId == userId }
        if (grant != null) {
            return TodoUserPermissions(
                canView = grant.canView,
                canAdd = grant.canAdd,
                canEdit = grant.canEdit,
                canComplete = grant.canComplete,
                canDelete = grant.canDelete,
                canManage = grant.canManageList,
            )
        }
        return if (list.isServerList) TodoUserPermissions.ServerViewOnly else TodoUserPermissions.None
    }

    /** How many tasks are still open across every list. */
    val openCount: Int get() = itemsByList.values.flatten().count { !it.isCompleted }

    /** How many tasks are done across every list. */
    val doneCount: Int get() = itemsByList.values.flatten().count { it.isCompleted }
}

/** Orders two nullable instants with nulls always sorted last, regardless of [order]. */
private fun compareNullableLast(a: Instant?, b: Instant?, order: Int): Int = when {
    a == null && b == null -> 0
    a == null -> 1
    b == null -> -1
    else -> a.compareTo(b) * order
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

    /** Reloads every list, its items and permissions, and the guild's members. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val lists = api.send(
            Endpoint("api/Todo/$guildId/lists/$userId"),
            ListSerializer(TodoListModel.serializer()),
        ).sortedWith(
            compareByDescending<TodoListModel> { it.isServerList }.thenBy { it.name.lowercase() }
        )
        _state.update { it.copy(lists = lists) }

        val members = runCatching {
            api.send(
                Endpoint("api/ClientOperations/members/$guildId"),
                ListSerializer(GuildMember.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(members = members) }

        lists.forEach { list ->
            loadItems(list.id)
            loadPermissions(list.id)
        }
    }

    /** Reloads one list's items, always including completed ones so stats stay accurate. */
    fun loadItems(listId: Int) = viewModelScope.launch {
        val items = runCatching {
            api.send(
                Endpoint("api/Todo/$guildId/lists/$listId/items/$userId?includeCompleted=true"),
                ListSerializer(TodoItemModel.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(itemsByList = it.itemsByList + (listId to items)) }
    }

    /** Reloads one list's granted permissions. */
    fun loadPermissions(listId: Int) = viewModelScope.launch {
        val permissions = runCatching {
            api.send(
                Endpoint("api/Todo/$guildId/lists/$listId/permissions/$userId"),
                ListSerializer(TodoListPermissionModel.serializer()),
            )
        }.getOrDefault(emptyList())
        _state.update { it.copy(permissionsByList = it.permissionsByList + (listId to permissions)) }
    }

    /** Updates the list search query. */
    fun setSearchQuery(value: String) = _state.update { it.copy(searchQuery = value) }

    /** Shows or hides completed tasks. Filtered client-side, no reload needed. */
    fun setIncludeCompleted(value: Boolean) = _state.update { it.copy(includeCompleted = value) }

    /** Sets the item sort field. */
    fun setSortBy(value: TodoSortBy) = _state.update { it.copy(sortBy = value) }

    /** Sets the item sort order. */
    fun setSortOrder(value: TodoSortOrder) = _state.update { it.copy(sortOrder = value) }

    /** Creates a list. Dashboard parity: always a server list. */
    fun createList(name: String, description: String?) =
        launchAction("Failed to create list.") {
            api.send(
                Endpoint(
                    "api/Todo/$guildId/lists",
                    HttpMethod.POST,
                    jsonBody(
                        "userId" to (userId.toLongOrNull() ?: 0L),
                        "name" to name,
                        "description" to description.orEmpty(),
                        "isServerList" to true,
                    ),
                ),
                TodoListModel.serializer(),
            )
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
                permissionsByList = it.permissionsByList - listId,
            )
        }
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

    /**
     * Renames, re-describes, re-prioritizes, and re-dates a task in one save.
     *
     * The main update endpoint now accepts an optional priority and due
     * date, both saved in a single call. It cannot clear a due date though:
     * when [dueDate] is null and the item previously had one, the dedicated
     * due date endpoint is called afterward with a null date.
     */
    fun updateItem(
        item: TodoItemModel,
        title: String,
        description: String?,
        priority: TodoPriority,
        dueDate: Instant?,
    ) = launchAction("Failed to update item.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Todo/$guildId/items/${item.id}",
                HttpMethod.PUT,
                jsonBody(
                    "userId" to (userId.toLongOrNull() ?: 0L),
                    "title" to title,
                    "description" to description,
                    "priority" to priority.raw,
                    "dueDate" to dueDate?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                ),
            )
        )
        if (dueDate == null && item.dueDate != null) {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Todo/$guildId/items/${item.id}/duedate",
                    HttpMethod.PUT,
                    jsonBody(
                        "userId" to (userId.toLongOrNull() ?: 0L),
                        "dueDate" to null,
                    ),
                )
            )
        }
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

    /** Grants view/edit/manage permissions on a list to a guild member. */
    fun grantPermission(
        listId: Int,
        targetUserId: Snowflake,
        canView: Boolean,
        canEdit: Boolean,
        canManage: Boolean,
    ) = launchAction("Failed to grant permissions.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Todo/$guildId/lists/$listId/permissions",
                HttpMethod.POST,
                jsonBody(
                    "targetUserId" to (targetUserId.toLongOrNull() ?: 0L),
                    "requestingUserId" to (userId.toLongOrNull() ?: 0L),
                    "canView" to canView,
                    "canEdit" to canEdit,
                    "canManage" to canManage,
                ),
            )
        )
        loadPermissions(listId)
    }

    /** Revokes a user's access to a list. */
    fun revokePermission(listId: Int, targetUserId: Snowflake) =
        launchAction("Failed to revoke permissions.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Todo/$guildId/lists/$listId/permissions/$targetUserId/$userId",
                    HttpMethod.DELETE,
                )
            )
            loadPermissions(listId)
        }
}
