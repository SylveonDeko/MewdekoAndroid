package dev.mewdeko.mobile.feature.rolestates

import androidx.lifecycle.SavedStateHandle
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
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
) {
    /** Roles excluded from being saved, parsed from the space-delimited field. */
    val deniedRoleIds: List<Snowflake>
        get() = deniedRoles.orEmpty().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }

    /** Members excluded from role-state saving. */
    val deniedUserIds: List<Snowflake>
        get() = deniedUsers.orEmpty().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** One member's saved roles. */
@Serializable
data class UserRoleStateRecord(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val userName: String? = null,
    val savedRoles: String? = null,
) {
    /** The saved role ids, tolerating both space and comma delimiters. */
    val roleIds: List<Snowflake>
        get() = savedRoles.orEmpty().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
}

/** Result of snapshotting every member's roles at once. */
@Serializable
data class SaveAllResponse(
    val savedCount: Int = 0,
    val errorMessage: String? = null,
)

/** Role states screen state. */
data class RoleStatesState(
    val settings: RoleStateSettings = RoleStateSettings(),
    val loadedSettings: RoleStateSettings = RoleStateSettings(),
    val users: List<UserRoleStateRecord> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val query: String = "",
) {
    /** Whether any editable setting differs from what the server has. */
    val hasUnsavedSettings: Boolean
        get() = settings.deniedRoles != loadedSettings.deniedRoles ||
            settings.deniedUsers != loadedSettings.deniedUsers ||
            settings.skipAutoAssignRoles != loadedSettings.skipAutoAssignRoles

    /** Members matching the current search query. */
    val visibleUsers: List<UserRoleStateRecord>
        get() = if (query.isBlank()) users else users.filter { record ->
            record.userName.orEmpty().contains(query, ignoreCase = true) ||
                record.userId.orEmpty().contains(query)
        }

    /** Resolves a role id to its name, falling back to the raw id. */
    fun roleName(id: Snowflake): String =
        availableRoles.firstOrNull { it.id == id }?.name ?: id
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

    init {
        load()
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
            _state.update {
                it.copy(
                    settings = snapshot,
                    loadedSettings = snapshot,
                    users = users.await(),
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    members = members.await().sortedBy { member ->
                        member.displayName.ifBlank { member.username }.lowercase()
                    },
                )
            }
        }
    }

    /** Updates the member search query. */
    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    /**
     * Stages the roles excluded from being saved.
     *
     * The backend's `RoleStatesService` splits `DeniedRoles` on `,` with a
     * throwing `ulong.Parse`, so this must be comma-joined even though the
     * read side tolerates either delimiter.
     */
    fun setDeniedRoles(ids: List<Snowflake>) = _state.update {
        it.copy(settings = it.settings.copy(deniedRoles = ids.joinToString(",")))
    }

    /**
     * Stages the members excluded from being saved.
     *
     * Comma-joined for the same reason as [setDeniedRoles].
     */
    fun setDeniedUsers(ids: List<Snowflake>) = _state.update {
        it.copy(settings = it.settings.copy(deniedUsers = ids.joinToString(",")))
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

    /** Overwrites one member's saved roles. */
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
            postSuccess("Roles assigned.")
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

    /** Removes roles from a member's saved state. */
    fun removeRolesFromUser(userId: Snowflake, roleIds: List<Snowflake>) =
        launchAction("Failed to remove roles.") {
            val numeric = roleIds.mapNotNull { it.toLongOrNull() }
            api.sendIgnoringBody(
                Endpoint(
                    "api/RoleStates/$guildId/user/$userId/roles",
                    HttpMethod.DELETE,
                    MewdekoJson.encodeToString(numeric),
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

    /** Discards one member's saved roles. */
    fun clearUser(userId: Snowflake) = launchAction("Failed to clear role state.") {
        api.sendIgnoringBody(
            Endpoint("api/RoleStates/$guildId/user/$userId", HttpMethod.DELETE)
        )
        _state.update { it.copy(users = it.users.filterNot { user -> user.userId == userId }) }
        postSuccess("Role state cleared.")
    }
}
