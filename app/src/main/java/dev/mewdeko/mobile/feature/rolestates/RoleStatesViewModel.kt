package dev.mewdeko.mobile.feature.rolestates

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import dev.mewdeko.mobile.core.ui.SelectorOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/** Guild-wide role-state configuration. */
@Serializable
data class RoleStateSettings(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "0",
    val enabled: Boolean = false,
    val clearOnBan: Boolean = false,
    val ignoreBots: Boolean = false,
    val deniedRoles: String? = null,
    val deniedUsers: String? = null,
    val skipAutoAssignRoles: Boolean = false,
)

/** One member's saved roles, exactly as the API returns them. */
@Serializable
data class UserRoleStateRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val userName: String? = null,
    val savedRoles: String? = null,
)

/** Result of snapshotting every member's roles at once. */
@Serializable
data class SaveAllResponse(
    val savedCount: Int = 0,
    val errorMessage: String? = null,
)

/**
 * One saved member, parsed once off the main thread.
 *
 * [rolePreview] already holds the first few role names, so a list row never
 * splits strings or looks up roles while it composes. [searchKey] is the
 * lowercased name and id the search filter matches against.
 */
@Immutable
data class RoleStateRow(
    val userId: Snowflake,
    val name: String,
    val roleIds: List<Snowflake>,
    val roleCount: Int,
    val rolePreview: String,
    val searchKey: String,
)

/**
 * Role states screen state.
 *
 * Everything the screen reads is precomputed by the view model: the parsed
 * [rows], the [filtered] search result, the id lists behind the exclusion
 * selectors, and every selector's option list. Recomposing the screen, such
 * as on each keystroke in the search field, therefore does no parsing,
 * filtering, or list building.
 */
@Immutable
data class RoleStatesState(
    val settings: RoleStateSettings = RoleStateSettings(),
    val loadedSettings: RoleStateSettings = RoleStateSettings(),
    val deniedRoleIds: List<Snowflake> = emptyList(),
    val deniedUserIds: List<Snowflake> = emptyList(),
    val rows: List<RoleStateRow> = emptyList(),
    val totalSavedRoles: Int = 0,
    val roleNameById: Map<Snowflake, String> = emptyMap(),
    val roleOptions: List<SelectorOption> = emptyList(),
    val memberOptions: List<SelectorOption> = emptyList(),
    val savedStateOptions: List<SelectorOption> = emptyList(),
    val query: String = "",
    val appliedQuery: String = "",
    val filtered: List<RoleStateRow> = emptyList(),
    val visibleLimit: Int = RoleStatesViewModel.PAGE_SIZE,
) {
    /** Whether any editable setting differs from what the server has. */
    val hasUnsavedSettings: Boolean
        get() = settings.deniedRoles != loadedSettings.deniedRoles ||
            settings.deniedUsers != loadedSettings.deniedUsers ||
            settings.skipAutoAssignRoles != loadedSettings.skipAutoAssignRoles
}

/** Saved member roles that survive a leave and rejoin. */
@HiltViewModel
class RoleStatesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(RoleStatesState())

    /** Observable screen state. */
    val state: StateFlow<RoleStatesState> = _state.asStateFlow()

    /** The parsed saved members, the source the search filter runs over. */
    private val rows = MutableStateFlow<List<RoleStateRow>>(emptyList())

    /** The raw search text, debounced before it filters [rows]. */
    private val query = MutableStateFlow("")

    init {
        observeSearch()
        load()
    }

    /**
     * Filters [rows] by [query] off the main thread.
     *
     * Typing is debounced by 200ms so a burst of keystrokes filters once;
     * clearing the field applies at once. A new query resets paging to the
     * first page, while a reload under the same query keeps what the user has
     * already paged in.
     */
    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            combine(rows, query.debounce { if (it.isBlank()) 0L else SEARCH_DEBOUNCE_MS }) { all, text ->
                text to filterRows(all, text)
            }
                .flowOn(Dispatchers.Default)
                .collect { (text, filtered) ->
                    _state.update {
                        it.copy(
                            appliedQuery = text,
                            filtered = filtered,
                            visibleLimit = if (text != it.appliedQuery) PAGE_SIZE else it.visibleLimit,
                        )
                    }
                }
        }
    }

    /** Reloads settings, saved states, and role options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val settings = async {
                runCatching {
                    api.send(
                        Endpoint("api/RoleStates/$guildId/settings"),
                        RoleStateSettings.serializer(),
                    )
                }.getOrDefault(RoleStateSettings())
            }
            val users = async {
                runCatching {
                    api.send(
                        Endpoint("api/RoleStates/$guildId/all"),
                        ListSerializer(UserRoleStateRecord.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val members = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val snapshot = settings.await()
            val records = users.await()
            val roleList = roles.await()
            val memberList = members.await()

            val prepared = withContext(Dispatchers.Default) {
                val sortedRoles = roleList
                    .filter { role -> role.id != guildId }
                    .sortedBy { role -> role.name.lowercase() }
                val roleNames = sortedRoles.associate { it.id to it.name }
                val parsed = buildRows(records, roleNames)
                Prepared(
                    roleNames = roleNames,
                    roleOptions = sortedRoles.map { SelectorOption(it.id, it.name) },
                    memberOptions = memberList
                        .sortedBy { member -> member.displayName.ifBlank { member.username }.lowercase() }
                        .map {
                            SelectorOption(
                                it.id,
                                it.displayName.ifBlank { it.username },
                                subtitle = it.username,
                            )
                        },
                    rows = parsed,
                    totalSavedRoles = parsed.sumOf { it.roleCount },
                    savedStateOptions = parsed.map { SelectorOption(it.userId, it.name) },
                    deniedRoleIds = splitIds(snapshot.deniedRoles),
                    deniedUserIds = splitIds(snapshot.deniedUsers),
                )
            }

            _state.update {
                it.copy(
                    settings = snapshot,
                    loadedSettings = snapshot,
                    deniedRoleIds = prepared.deniedRoleIds,
                    deniedUserIds = prepared.deniedUserIds,
                    rows = prepared.rows,
                    totalSavedRoles = prepared.totalSavedRoles,
                    roleNameById = prepared.roleNames,
                    roleOptions = prepared.roleOptions,
                    memberOptions = prepared.memberOptions,
                    savedStateOptions = prepared.savedStateOptions,
                )
            }
            rows.value = prepared.rows
        }
    }

    /** Updates the member search text; filtering follows after the debounce. */
    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        query.value = value
    }

    /** Reveals the next page of saved members. */
    fun showMore() = _state.update { it.copy(visibleLimit = it.visibleLimit + PAGE_SIZE) }

    /**
     * Stages the roles excluded from being saved.
     *
     * The backend's `RoleStatesService` splits `DeniedRoles` on `,` with a
     * throwing `ulong.Parse`, so this must be comma-joined even though the
     * read side tolerates either delimiter.
     */
    fun setDeniedRoles(ids: List<Snowflake>) = _state.update {
        it.copy(
            settings = it.settings.copy(deniedRoles = ids.joinToString(",")),
            deniedRoleIds = ids,
        )
    }

    /**
     * Stages the members excluded from being saved.
     *
     * Comma-joined for the same reason as [setDeniedRoles].
     */
    fun setDeniedUsers(ids: List<Snowflake>) = _state.update {
        it.copy(
            settings = it.settings.copy(deniedUsers = ids.joinToString(",")),
            deniedUserIds = ids,
        )
    }

    /** Stages whether auto-assign roles are skipped when restoring. */
    fun setSkipAutoAssign(value: Boolean) = _state.update {
        it.copy(settings = it.settings.copy(skipAutoAssignRoles = value))
    }

    /** Turns role-state saving on or off. */
    fun toggleEnabled() = launchAction("Failed to toggle role states.") {
        api.sendIgnoringBody(Endpoint("api/RoleStates/$guildId/toggle", HttpMethod.POST))
        load(refreshing = true)
    }

    /** Toggles clearing a member's saved roles when they are banned. */
    fun toggleClearOnBan() = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint("api/RoleStates/$guildId/clear-on-ban", HttpMethod.POST, "{}")
        )
        load(refreshing = true)
    }

    /** Toggles skipping bot accounts. */
    fun toggleIgnoreBots() = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint("api/RoleStates/$guildId/ignore-bots", HttpMethod.POST, "{}")
        )
        load(refreshing = true)
    }

    /**
     * Writes the staged settings.
     *
     * The controller's `UpdateSettings` action returns a bare `Ok()` with no
     * body, so the response must be ignored rather than decoded: decoding an
     * empty body as [RoleStateSettings] would silently yield all-default
     * values and overwrite the locally staged settings with them.
     */
    fun saveSettings() = launchAction("Failed to save settings.") {
        val payload = _state.value.settings.copy(guildId = guildId)
        api.sendIgnoringBody(
            Endpoint(
                "api/RoleStates/$guildId/settings",
                HttpMethod.POST,
                MewdekoJson.encodeToString(payload),
            )
        )
        _state.update { it.copy(settings = payload, loadedSettings = payload) }
    }

    /** Snapshots every current member's roles at once. */
    fun saveAll() = launchAction("Failed to snapshot roles.") {
        val result = api.send(
            Endpoint("api/RoleStates/$guildId/save-all", HttpMethod.POST),
            SaveAllResponse.serializer(),
        )
        if (!result.errorMessage.isNullOrBlank()) {
            postError(result.errorMessage)
        } else {
            postSuccess("Saved roles for ${result.savedCount} members.")
        }
        load(refreshing = true)
    }

    /**
     * Overwrites one member's saved roles.
     *
     * No success message: the member's row shows the new roles once the
     * reload lands.
     */
    fun setRoles(userId: Snowflake, roleIds: List<Snowflake>) =
        launchAction("Failed to set roles.") {
            val numeric = roleIds.mapNotNull { it.toLongOrNull() }
            api.sendIgnoringBody(
                Endpoint(
                    "api/RoleStates/$guildId/user/$userId/set-roles",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(numeric),
                )
            )
            load(refreshing = true)
        }

    /** Adds roles to a member's saved state without overwriting the rest. */
    fun addRolesToUser(userId: Snowflake, roleIds: List<Snowflake>) =
        launchAction("Failed to add roles.") {
            val numeric = roleIds.mapNotNull { it.toLongOrNull() }
            api.sendIgnoringBody(
                Endpoint(
                    "api/RoleStates/$guildId/user/$userId/roles",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(numeric),
                )
            )
            postSuccess("Roles added.")
            load(refreshing = true)
        }

    /**
     * Removes roles from a member's saved role state.
     *
     * The ids are sent as strings, not numbers: the dashboard proxy's DELETE handler parses the
     * body with plain JSON, which rounds an unquoted snowflake above 2^53. ASP.NET's Web JSON
     * defaults (AllowReadingFromString) accept quoted numbers just fine.
     */
    fun removeRolesFromUser(userId: Snowflake, roleIds: List<Snowflake>) =
        launchAction("Failed to remove roles.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/RoleStates/$guildId/user/$userId/roles",
                    HttpMethod.DELETE,
                    MewdekoJson.encodeToString(ListSerializer(String.serializer()), roleIds),
                )
            )
            postSuccess("Roles removed.")
            load(refreshing = true)
        }

    /** Copies one member's saved role state onto another member. */
    fun copyRoleState(from: Snowflake, to: Snowflake) =
        launchAction("Failed to copy role state.") {
            api.sendIgnoringBody(
                Endpoint("api/RoleStates/$guildId/user/$from/apply/$to", HttpMethod.POST)
            )
            postSuccess("Role state copied.")
            load(refreshing = true)
        }

    /**
     * Discards one member's saved roles.
     *
     * The row leaves the list, which is confirmation enough, so no success
     * message is posted.
     */
    fun clearUser(userId: Snowflake) = launchAction("Failed to clear role state.") {
        api.sendIgnoringBody(
            Endpoint("api/RoleStates/$guildId/user/$userId", HttpMethod.DELETE)
        )
        val remaining = withContext(Dispatchers.Default) {
            rows.value.filterNot { row -> row.userId == userId }
        }
        _state.update {
            it.copy(
                rows = remaining,
                totalSavedRoles = remaining.sumOf { row -> row.roleCount },
                savedStateOptions = it.savedStateOptions.filterNot { option -> option.id == userId },
            )
        }
        rows.value = remaining
    }

    /** The derived lists a load builds on [Dispatchers.Default] before publishing. */
    private class Prepared(
        val roleNames: Map<Snowflake, String>,
        val roleOptions: List<SelectorOption>,
        val memberOptions: List<SelectorOption>,
        val rows: List<RoleStateRow>,
        val totalSavedRoles: Int,
        val savedStateOptions: List<SelectorOption>,
        val deniedRoleIds: List<Snowflake>,
        val deniedUserIds: List<Snowflake>,
    )

    companion object {
        /** How many saved members the list shows per page. */
        const val PAGE_SIZE = 100

        /** How long typing must pause before the search filter runs. */
        private const val SEARCH_DEBOUNCE_MS = 200L

        /** How many role names a row previews before summarizing the rest. */
        private const val PREVIEW_ROLES = 3

        /** Splits a stored id list, tolerating both space and comma delimiters. */
        private fun splitIds(raw: String?): List<Snowflake> =
            raw.orEmpty().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }

        /** Parses every API record into a [RoleStateRow], dropping records without a member id. */
        private fun buildRows(
            records: List<UserRoleStateRecord>,
            roleNames: Map<Snowflake, String>,
        ): List<RoleStateRow> = records.mapNotNull { record ->
            val userId = record.userId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val name = record.userName?.takeIf { it.isNotBlank() } ?: userId
            val roleIds = splitIds(record.savedRoles)
            val shown = roleIds.take(PREVIEW_ROLES).joinToString(", ") { "@" + (roleNames[it] ?: it) }
            val extra = roleIds.size - PREVIEW_ROLES
            RoleStateRow(
                userId = userId,
                name = name,
                roleIds = roleIds,
                roleCount = roleIds.size,
                rolePreview = if (extra > 0) "$shown +$extra more" else shown,
                searchKey = "${name.lowercase()} $userId",
            )
        }

        /** The rows whose name or id contains [text], or all rows when it is blank. */
        private fun filterRows(all: List<RoleStateRow>, text: String): List<RoleStateRow> {
            val needle = text.trim().lowercase()
            return if (needle.isEmpty()) all else all.filter { it.searchKey.contains(needle) }
        }
    }
}
