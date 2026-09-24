package dev.mewdeko.mobile.feature.statroles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
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
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** Stat Roles screen state. */
data class StatRolesState(
    /** True while the create or edit form is open over the list. */
    val editorOpen: Boolean = false,
    /** Why the last save attempt failed, shown inside the open editor. */
    val saveError: String? = null,
    val roles: List<StatRole> = emptyList(),
    val guildRoles: List<GuildRole> = emptyList(),
    val textChannels: List<TextChannelLite> = emptyList(),
    val activityChannels: List<TextChannelLite> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val results: Map<Int, StatRoleResultPanel> = emptyMap(),
    val busyId: Int? = null,
    val draft: StatRoleDraft = StatRoleDraft(),
    val isSaving: Boolean = false,
) {
    /** Display name for a guild role id, falling back to the id itself. */
    fun roleName(id: Snowflake): String = guildRoles.firstOrNull { it.id == id }?.name ?: id

    /** Display name for a text channel id, if the channel is known. */
    fun channelName(id: Snowflake): String? = textChannels.firstOrNull { it.id == id }?.name
}

/**
 * Lists, previews, runs, and edits the guild's stat roles: roles granted and
 * removed on a schedule based on member activity.
 */
@HiltViewModel
class StatRolesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(StatRolesState())

    /** Observable screen state. */
    val state: StateFlow<StatRolesState> = _state.asStateFlow()

    private val base = "api/StatRoles/$guildId"

    init {
        load()
    }

    /** Loads the stat roles plus the guild's roles, channels, and members used by the pickers. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val roles = async { fetchRoles() }
            val guildRoles = async {
                runCatching {
                    api.send(Endpoint("api/ClientOperations/roles/$guildId"), ListSerializer(GuildRole.serializer()))
                }.getOrDefault(emptyList())
            }
            val textChannels = async { channels("api/ClientOperations/textchannels/$guildId") }
            val textAndVoice = async { channels("api/ClientOperations/channels/$guildId/0") }
            val voice = async { channels("api/ClientOperations/channels/$guildId/1") }
            val members = async {
                runCatching {
                    api.send(Endpoint("api/ClientOperations/members/$guildId"), ListSerializer(GuildMember.serializer()))
                }.getOrDefault(emptyList())
            }

            val loaded = roles.await()
            _state.update { s ->
                s.copy(
                    roles = loaded,
                    guildRoles = guildRoles.await()
                        .filter { it.id != guildId }
                        .sortedBy { it.name.lowercase() },
                    textChannels = textChannels.await().sortedBy { it.name.lowercase() },
                    activityChannels = (textAndVoice.await() + voice.await())
                        .distinctBy { it.id }
                        .sortedBy { it.name.lowercase() },
                    members = members.await().sortedBy { it.label.lowercase() },
                    results = s.results.filterKeys { id -> loaded.any { it.id == id } },
                )
            }
        }
    }

    /** Opens the editor on a blank stat role with the dashboard's defaults. */
    fun startNew() = _state.update { it.copy(draft = StatRoleDraft(), editorOpen = true, saveError = null) }

    /** Opens the editor on an existing stat role. */
    fun edit(role: StatRole) = _state.update {
        it.copy(draft = StatRoleDraft.from(role), editorOpen = true, saveError = null)
    }

    /** Discards the draft and closes the editor. */
    fun cancelEdit() = _state.update { it.copy(draft = StatRoleDraft(), editorOpen = false, saveError = null) }

    /** Applies an edit to the draft without saving it. */
    fun updateDraft(transform: (StatRoleDraft) -> StatRoleDraft) = _state.update { it.copy(draft = transform(it.draft)) }

    /** Changes how members qualify; daily streaks need a window of at least one day. */
    fun setLimit(limit: StatRoleLimit) = updateDraft {
        it.copy(
            limitType = limit,
            lookbackDays = if (limit == StatRoleLimit.DAILY_STREAK) it.lookbackDays.coerceAtLeast(1) else it.lookbackDays,
        )
    }

    /** Flips a stat role on or off. */
    fun toggleEnabled(role: StatRole) = viewModelScope.launch {
        val target = !role.enabled
        replaceRole(role.copy(enabled = target))
        val body = buildJsonObject { put("enabled", JsonPrimitive(target)) }
        runCatching {
            api.send(Endpoint("$base/${role.id}", HttpMethod.PUT, encode(body)), StatRole.serializer())
        }.onSuccess { updated ->
            replaceRole(updated)
        }.onFailure {
            replaceRole(role)
            postError("Failed to update the stat role. ${it.userFacingMessage}")
        }
    }

    /** Deletes a stat role. Members keep whatever they currently hold. */
    fun delete(role: StatRole) = viewModelScope.launch {
        runCatching {
            api.sendIgnoringBody(Endpoint("$base/${role.id}", HttpMethod.DELETE))
        }.onSuccess {
            _state.update { s ->
                s.copy(
                    roles = s.roles.filterNot { it.id == role.id },
                    results = s.results - role.id,
                    draft = if (s.draft.id == role.id) StatRoleDraft() else s.draft,
                )
            }
        }.onFailure {
            postError("Failed to delete the stat role. ${it.userFacingMessage}")
        }
    }

    /** Shows who would gain and lose the role without changing anything. */
    fun preview(role: StatRole) = evaluate(role, preview = true)

    /** Evaluates the role and applies the changes right now, then reloads the list. */
    fun run(role: StatRole) = evaluate(role, preview = false)

    /** Hides a pinned preview or run result. */
    fun dismissResult(id: Int) = _state.update { it.copy(results = it.results - id) }

    /** Creates or updates the stat role in the editor. */
    fun save() = viewModelScope.launch {
        val current = _state.value
        val draft = current.draft
        if (draft.roleId.isNullOrEmpty()) {
            _state.update { it.copy(saveError = "Pick the role to manage.") }
            return@launch
        }
        if (draft.streakInvalid) {
            _state.update {
                it.copy(saveError = "Daily streaks only work with messages, voice minutes or minutes in a game.")
            }
            return@launch
        }
        _state.update { it.copy(isSaving = true, saveError = null) }
        val body = encode(requestBody(draft))
        val endpoint = if (draft.id == null) {
            Endpoint(base, HttpMethod.POST, body)
        } else {
            Endpoint("$base/${draft.id}", HttpMethod.PUT, body)
        }
        val result = runCatching { api.send(endpoint, StatRole.serializer()) }
        result.onSuccess {
            val refreshed = runCatching { fetchRoles() }.getOrNull()
            _state.update { s ->
                s.copy(
                    roles = refreshed ?: s.roles,
                    draft = StatRoleDraft(),
                    editorOpen = false,
                    isSaving = false,
                )
            }
        }.onFailure {
            _state.update { s ->
                s.copy(isSaving = false, saveError = "Failed to save the stat role. ${it.userFacingMessage}")
            }
        }
    }

    private fun evaluate(role: StatRole, preview: Boolean) = viewModelScope.launch {
        _state.update { it.copy(busyId = role.id) }
        val action = if (preview) "preview" else "run"
        val result = runCatching {
            api.send(Endpoint("$base/${role.id}/$action", HttpMethod.POST), StatRoleRunResult.serializer())
        }
        result.onSuccess { outcome ->
            _state.update { s ->
                s.copy(results = s.results + (role.id to StatRoleResultPanel(outcome, preview)))
            }
            if (!preview) {
                runCatching { fetchRoles() }.onSuccess { list -> _state.update { it.copy(roles = list) } }
            }
        }.onFailure {
            val verb = if (preview) "preview" else "run"
            postError("Failed to $verb the stat role. ${it.userFacingMessage}")
        }
        _state.update { it.copy(busyId = null) }
    }

    /** Builds the create or update body the same way the dashboard does. */
    private fun requestBody(draft: StatRoleDraft): JsonObject {
        val streak = draft.limitType == StatRoleLimit.DAILY_STREAK
        val maximum = draft.maximum.trim().toLongOrNull()
        val lookback = draft.lookbackDays.coerceIn(if (streak) 1 else 0, StatRoleDraft.MAX_LOOKBACK)
        val topEndCap = if (draft.limitType == StatRoleLimit.TOP_PERCENT) 100 else Int.MAX_VALUE
        val notifyChannel = draft.notifyChannelId?.toLongOrNull()
        return buildJsonObject {
            draft.roleId?.toLongOrNull()?.let { put("roleId", JsonPrimitive(it)) }
            draft.name.trim().takeIf { it.isNotEmpty() }?.let { put("name", JsonPrimitive(it.take(StatRoleDraft.NAME_MAX))) }
            put("statType", JsonPrimitive(draft.statType.value))
            put("limitType", JsonPrimitive(draft.limitType.value))
            put("minimum", JsonPrimitive(draft.minimum.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0L))
            if (maximum != null) put("maximum", JsonPrimitive(maximum.coerceAtLeast(0)))
            put("clearMaximum", JsonPrimitive(maximum == null))
            put("lookbackDays", JsonPrimitive(lookback))
            put("topStart", JsonPrimitive(draft.topStart.trim().toIntOrNull()?.coerceIn(1, topEndCap) ?: 1))
            put("topEnd", JsonPrimitive(draft.topEnd.trim().toIntOrNull()?.coerceIn(1, topEndCap) ?: 10))
            put("requiredDays", JsonPrimitive(draft.requiredDays.trim().toIntOrNull()?.coerceAtLeast(1) ?: 1))
            put("permanent", JsonPrimitive(draft.permanent))
            put("invert", JsonPrimitive(draft.invert))
            put("applyToBots", JsonPrimitive(draft.applyToBots))
            put("groupName", JsonPrimitive(draft.groupName.trim().take(StatRoleDraft.GROUP_MAX)))
            put("activityName", JsonPrimitive(draft.activityName.trim().take(StatRoleDraft.ACTIVITY_MAX)))
            put("channelFilter", idArray(draft.channelFilter))
            put("roleWhitelist", idArray(draft.roleWhitelist))
            put("roleBlacklist", idArray(draft.roleBlacklist))
            put("ignoredUsers", idArray(draft.ignoredUsers))
            if (notifyChannel != null) put("notifyChannelId", JsonPrimitive(notifyChannel))
            put("clearNotifyChannel", JsonPrimitive(notifyChannel == null))
            put("notifyDm", JsonPrimitive(draft.notifyDm))
            put("notifyMessage", JsonPrimitive(draft.notifyMessage.trim()))
            put(
                "intervalMinutes",
                JsonPrimitive(
                    draft.intervalMinutes.trim().toIntOrNull()?.coerceAtLeast(StatRoleDraft.MIN_INTERVAL) ?: 180,
                ),
            )
        }
    }

    private fun idArray(ids: List<Snowflake>): JsonArray =
        JsonArray(ids.mapNotNull { it.toLongOrNull() }.map { JsonPrimitive(it) })

    private fun encode(body: JsonObject): String = MewdekoJson.encodeToString(JsonObject.serializer(), body)

    private fun replaceRole(role: StatRole) = _state.update { s ->
        s.copy(roles = s.roles.map { if (it.id == role.id) role else it })
    }

    private suspend fun fetchRoles(): List<StatRole> =
        api.send(Endpoint(base), ListSerializer(StatRole.serializer()))

    private suspend fun channels(path: String): List<TextChannelLite> = runCatching {
        api.send(Endpoint(path), ListSerializer(TextChannelLite.serializer()))
    }.getOrDefault(emptyList())
}

/** The best display name for a member picker row. */
internal val GuildMember.label: String
    get() = displayName.ifBlank { username.ifBlank { id } }
