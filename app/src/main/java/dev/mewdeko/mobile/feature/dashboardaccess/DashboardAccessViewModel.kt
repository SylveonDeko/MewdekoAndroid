package dev.mewdeko.mobile.feature.dashboardaccess

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.firstArrayOrNull
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** The pages of the Dashboard Access screen. */
enum class AccessPage(val id: String, val title: String) {
    GRANTS("grants", "Grants"),
    EDITOR("editor", "Grant"),
    DELEGATION("delegation", "Managers");

    companion object {
        /** Resolves a page from its tab id, defaulting to [GRANTS]. */
        fun from(id: String): AccessPage = entries.firstOrNull { it.id == id } ?: GRANTS
    }
}

/** The grant being created or edited. */
data class GrantDraft(
    val editingId: Int? = null,
    val targetType: AccessTargetType = AccessTargetType.USER,
    val targetId: Snowflake? = null,
    val levels: Map<String, AccessLevel> = emptyMap(),
) {
    /** Whether an existing grant is loaded, which locks the target. */
    val isEditing: Boolean get() = editingId != null

    /** The level stored for one controller section. */
    fun level(section: String): AccessLevel = levels[section] ?: AccessLevel.NONE

    /**
     * The lowest level across a feature's sections, so a mixed grant never
     * looks more permissive than it is.
     */
    fun groupLevel(sections: List<String>): AccessLevel =
        sections.minOfOrNull { level(it).value }?.let { AccessLevel.from(it) } ?: AccessLevel.NONE

    /** The sections that will be sent, which excludes anything at [AccessLevel.NONE]. */
    val grantedSections: Map<String, AccessLevel>
        get() = levels.filterValues { it != AccessLevel.NONE }
}

/** Dashboard Access screen state. */
data class DashboardAccessState(
    val settings: DashboardAccessSettings? = null,
    val managers: List<DashboardAccessManager> = emptyList(),
    val managersError: String? = null,
    val grants: List<DashboardAccessGrant> = emptyList(),
    val grantsError: String? = null,
    val roles: List<GuildRole> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val page: AccessPage = AccessPage.GRANTS,
    val managerTargetType: AccessTargetType = AccessTargetType.USER,
    val managerTargetId: Snowflake? = null,
    val isAddingManager: Boolean = false,
    val isUpdatingToggle: Boolean = false,
    val draft: GrantDraft = GrantDraft(),
    val isSavingGrant: Boolean = false,
) {
    /** Whether the signed-in user may grant or revoke access at all. */
    val canManageAccess: Boolean get() = settings?.canManageAccess == true

    /** Whether the signed-in user owns the guild and may change delegation. */
    val isGuildOwner: Boolean get() = settings?.isGuildOwner == true

    /** Resolves a user or role id to its display name, falling back to `User <id>` or `Role <id>`. */
    fun targetName(type: AccessTargetType, id: Snowflake): String = when (type) {
        AccessTargetType.ROLE -> roles.firstOrNull { it.id == id }?.name?.takeIf { it.isNotBlank() }
            ?: "Role $id"

        AccessTargetType.USER -> members.firstOrNull { it.id == id }
            ?.let { member -> member.displayName.ifBlank { member.username } }
            ?.takeIf { it.isNotBlank() }
            ?: "User $id"
    }
}

/**
 * Loads and edits restricted dashboard access for a guild: the owner-only
 * delegation toggle and access managers, plus per-section grants for users
 * and roles.
 */
@HiltViewModel
class DashboardAccessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(DashboardAccessState())

    /** Observable screen state. */
    val state: StateFlow<DashboardAccessState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Loads settings, roles, and members, then grants when the user may manage
     * access and managers when the user owns the guild (the bot forbids
     * managers for everyone else).
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val settingsCall = async {
                api.send(Endpoint("api/DashboardAccess/$guildId/settings"), DashboardAccessSettings.serializer())
            }
            val rolesCall = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val membersCall = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val settings = settingsCall.await()
            val grantsCall = async {
                if (settings.canManageAccess) fetchGrants()
                else Result.success(emptyList<DashboardAccessGrant>())
            }
            val managersCall = async {
                if (settings.isGuildOwner) fetchManagers()
                else Result.success(emptyList<DashboardAccessManager>())
            }

            val grants = grantsCall.await()
            val managers = managersCall.await()
            val roles = rolesCall.await().sortedBy { it.name.lowercase() }
            val members = membersCall.await().sortedBy { member ->
                member.displayName.ifBlank { member.username }.lowercase()
            }

            _state.update {
                it.copy(
                    settings = settings,
                    roles = roles,
                    members = members,
                    grants = grants.getOrDefault(it.grants),
                    grantsError = grants.exceptionOrNull()?.userFacingMessage,
                    managers = managers.getOrDefault(it.managers),
                    managersError = managers.exceptionOrNull()?.userFacingMessage,
                    page = if (!settings.isGuildOwner && it.page == AccessPage.DELEGATION) {
                        AccessPage.GRANTS
                    } else {
                        it.page
                    },
                )
            }
        }
    }

    /** Switches the visible page. */
    fun selectPage(page: AccessPage) = _state.update { it.copy(page = page) }

    /** Flips the owner-only setting that lets Administrators and Manage Guild members manage access. */
    fun toggleAdminsCanManage() = viewModelScope.launch {
        val settings = _state.value.settings ?: return@launch
        if (_state.value.isUpdatingToggle) return@launch
        val newValue = !settings.adminsCanManageAccess
        _state.update { it.copy(isUpdatingToggle = true) }
        val body = buildJsonObject { put("adminsCanManageAccess", JsonPrimitive(newValue)) }
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint(
                    "api/DashboardAccess/$guildId/settings",
                    HttpMethod.PUT,
                    MewdekoJson.encodeToString(JsonObject.serializer(), body),
                )
            )
        }.isSuccess
        _state.update {
            it.copy(
                isUpdatingToggle = false,
                settings = if (ok) it.settings?.copy(adminsCanManageAccess = newValue) else it.settings,
            )
        }
        if (!ok) postError("Failed to update setting.")
    }

    /** Changes the manager form's target kind, which clears the chosen target. */
    fun setManagerTargetType(type: AccessTargetType) = _state.update {
        if (it.managerTargetType == type) it else it.copy(managerTargetType = type, managerTargetId = null)
    }

    /** Picks the user or role to appoint as a manager. */
    fun setManagerTarget(id: Snowflake?) = _state.update { it.copy(managerTargetId = id) }

    /** Appoints the selected user or role as an access-list manager. */
    fun addManager() = viewModelScope.launch {
        val current = _state.value
        val targetId = current.managerTargetId ?: return@launch
        if (current.isAddingManager) return@launch
        _state.update { it.copy(isAddingManager = true) }
        val body = targetBody(current.managerTargetType, targetId)
        val result = runCatching {
            api.send(
                Endpoint(
                    "api/DashboardAccess/$guildId/managers",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(JsonObject.serializer(), body),
                ),
                DashboardAccessManager.serializer(),
            )
        }
        val manager = result.getOrNull()
        _state.update {
            if (manager != null) {
                it.copy(
                    isAddingManager = false,
                    managers = it.managers.filterNot { existing -> existing.id == manager.id } + manager,
                    managerTargetId = null,
                )
            } else {
                it.copy(isAddingManager = false)
            }
        }
        if (manager == null) postError("Failed to add manager.")
    }

    /** Removes an access-list manager. */
    fun removeManager(manager: DashboardAccessManager) = viewModelScope.launch {
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("api/DashboardAccess/$guildId/managers/${manager.id}", HttpMethod.DELETE)
            )
        }.isSuccess
        if (ok) {
            _state.update { it.copy(managers = it.managers.filterNot { m -> m.id == manager.id }) }
        } else {
            postError("Failed to remove manager.")
        }
    }

    /** Clears the grant form and opens it for a new grant. */
    fun startNewGrant() = _state.update { it.copy(draft = GrantDraft(), page = AccessPage.EDITOR) }

    /** Discards the grant being edited and returns to the list. */
    fun cancelEdit() = _state.update { it.copy(draft = GrantDraft(), page = AccessPage.GRANTS) }

    /** Loads an existing grant into the form with its target locked. */
    fun editGrant(grant: DashboardAccessGrant) = _state.update {
        it.copy(
            draft = GrantDraft(
                editingId = grant.id,
                targetType = grant.type,
                targetId = grant.targetId,
                levels = grant.canonicalSections(),
            ),
            page = AccessPage.EDITOR,
        )
    }

    /** Changes the grant's target kind, which clears the chosen target. Ignored while editing. */
    fun setGrantTargetType(type: AccessTargetType) = _state.update {
        if (it.draft.isEditing || it.draft.targetType == type) it
        else it.copy(draft = it.draft.copy(targetType = type, targetId = null))
    }

    /** Picks the grant's user or role. Ignored while editing. */
    fun setGrantTarget(id: Snowflake?) = _state.update {
        if (it.draft.isEditing) it else it.copy(draft = it.draft.copy(targetId = id))
    }

    /** Sets every controller section behind a dashboard feature to [level]. */
    fun setGroupLevel(group: DashboardAccessGroup, level: AccessLevel) = _state.update {
        val levels = it.draft.levels.toMutableMap()
        group.sections.forEach { section -> levels[section] = level }
        it.copy(draft = it.draft.copy(levels = levels))
    }

    /** Creates or replaces the target's grant with every non-None section in the form. */
    fun saveGrant() = viewModelScope.launch {
        val current = _state.value
        if (current.isSavingGrant) return@launch
        val draft = current.draft
        val targetId = draft.targetId
        if (targetId.isNullOrBlank()) {
            postError("Select a user or role.")
            return@launch
        }
        val sections = draft.grantedSections
        if (sections.isEmpty()) {
            postError("Select at least one section.")
            return@launch
        }

        _state.update { it.copy(isSavingGrant = true) }
        val body = buildJsonObject {
            put("targetType", JsonPrimitive(draft.targetType.value))
            put("targetId", JsonPrimitive(targetId.asSnowflakeNumber()))
            put(
                "sections",
                buildJsonArray {
                    sections.forEach { (section, level) ->
                        add(
                            buildJsonObject {
                                put("section", JsonPrimitive(section))
                                put("level", JsonPrimitive(level.value))
                            }
                        )
                    }
                },
            )
        }
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint(
                    "api/DashboardAccess/$guildId/grants",
                    HttpMethod.PUT,
                    MewdekoJson.encodeToString(JsonObject.serializer(), body),
                )
            )
        }.isSuccess
        if (!ok) {
            _state.update { it.copy(isSavingGrant = false) }
            postError("Failed to save access grant.")
            return@launch
        }

        val refreshed = fetchGrants()
        _state.update {
            it.copy(
                isSavingGrant = false,
                grants = refreshed.getOrDefault(it.grants),
                grantsError = refreshed.exceptionOrNull()?.userFacingMessage,
                draft = GrantDraft(),
                page = AccessPage.GRANTS,
            )
        }
    }

    /** Removes a grant, clearing the form if that grant was being edited. */
    fun removeGrant(grant: DashboardAccessGrant) = viewModelScope.launch {
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("api/DashboardAccess/$guildId/grants/${grant.id}", HttpMethod.DELETE)
            )
        }.isSuccess
        if (!ok) {
            postError("Failed to remove access grant.")
            return@launch
        }
        _state.update {
            it.copy(
                grants = it.grants.filterNot { g -> g.id == grant.id },
                draft = if (it.draft.editingId == grant.id) GrantDraft() else it.draft,
            )
        }
    }

    /**
     * Fetches grants without key normalisation, because the section map is a
     * dictionary keyed by case-sensitive controller names.
     */
    private suspend fun fetchGrants(): Result<List<DashboardAccessGrant>> = try {
        val raw = api.sendRaw(Endpoint("api/DashboardAccess/$guildId/grants"))
        val array = raw.firstArrayOrNull() ?: JsonArray(emptyList())
        Result.success(
            MewdekoJson.decodeFromJsonElement(ListSerializer(DashboardAccessGrant.serializer()), array)
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private suspend fun fetchManagers(): Result<List<DashboardAccessManager>> = try {
        Result.success(
            api.send(
                Endpoint("api/DashboardAccess/$guildId/managers"),
                ListSerializer(DashboardAccessManager.serializer()),
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }

    private fun targetBody(type: AccessTargetType, id: Snowflake): JsonObject = buildJsonObject {
        put("targetType", JsonPrimitive(type.value))
        put("targetId", JsonPrimitive(id.asSnowflakeNumber()))
    }
}
