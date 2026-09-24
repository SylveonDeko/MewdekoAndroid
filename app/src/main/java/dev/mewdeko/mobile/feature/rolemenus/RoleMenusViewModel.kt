package dev.mewdeko.mobile.feature.rolemenus

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/** Error text used when the bot gives no message of its own. */
private const val SaveFallback = "Couldn't save the menu."

/** Role Menus screen state. */
data class RoleMenusState(
    val section: String = "menus",
    val menus: List<RoleMenu> = emptyList(),
    val lookups: RoleMenuLookups? = null,
    val importSources: List<RoleMenuImportSource> = emptyList(),
    val draft: RoleMenuDraft = RoleMenuDraft(),
    val editorOpen: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val busyMenuIds: Set<Int> = emptySet(),
    val busySourceIds: Set<Int> = emptySet(),
) {
    /** Channels the bot can post a menu in. */
    val postableChannels: List<LookupChannel>
        get() = lookups?.channels.orEmpty().filter { it.canPost }

    /** Every role the lookups offer, highest first. */
    val roles: List<LookupRole>
        get() = lookups?.roles.orEmpty()

    /** The lookup role with [id], if the bot reported it. */
    fun role(id: Snowflake?): LookupRole? = id?.let { wanted -> roles.firstOrNull { it.id == wanted } }

    /** The channel label "#name" for [id], from the lookups, or null when unknown. */
    fun channelLabel(id: Snowflake?): String? =
        id?.let { wanted -> lookups?.channels?.firstOrNull { it.id == wanted }?.let { "#${it.name}" } }

    /** Why [option] can't be given out right now, or null when it can. */
    fun problemFor(option: RoleMenuOptionDraft): String? {
        if (option.roleId.isBlank()) return null
        val role = role(option.roleId)
        return when {
            role != null && !role.assignable -> role.problem ?: option.problem
            role == null && lookups != null -> option.problem ?: "This role was deleted"
            else -> option.problem
        }
    }

    /** The draft's options as the preview shows them. */
    val previewOptions: List<RoleMenuPreviewOption>
        get() = draft.options.map { option ->
            RoleMenuPreviewOption(
                label = option.label.trim().ifEmpty { role(option.roleId)?.name.orEmpty() },
                emoji = option.emoji.trim(),
                description = option.description.trim(),
                buttonColor = RoleMenuButtonColor.from(option.buttonStyle),
            )
        }
}

/**
 * Loads and edits the guild's role menus: the list, the editor draft, and
 * moving older emoji role setups over.
 */
@HiltViewModel
class RoleMenusViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(RoleMenusState())

    /** Observable screen state. */
    val state: StateFlow<RoleMenusState> = _state.asStateFlow()

    private val base = "api/rolemenus/$guildId"

    init {
        load()
    }

    /** Loads the menus, the editor lookups, and the older setups in parallel. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val menus = async {
                runCatching {
                    api.send(Endpoint(base), ListSerializer(RoleMenu.serializer()))
                }.getOrDefault(emptyList())
            }
            val lookups = async {
                runCatching { api.send(Endpoint("$base/lookups"), RoleMenuLookups.serializer()) }.getOrNull()
            }
            val sources = async {
                runCatching {
                    api.send(Endpoint("$base/import-sources"), ListSerializer(RoleMenuImportSource.serializer()))
                }.getOrDefault(emptyList())
            }
            val loadedMenus = menus.await()
            val loadedLookups = lookups.await()
            val loadedSources = sources.await()
            _state.update {
                it.copy(
                    menus = loadedMenus,
                    lookups = loadedLookups ?: it.lookups,
                    importSources = loadedSources,
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Opens the editor on a blank menu. */
    fun startNew() = _state.update {
        it.copy(draft = RoleMenuDraft(), editorOpen = true, hasUnsavedChanges = false, section = "menus")
    }

    /** Opens the editor on a saved menu. */
    fun startEdit(menu: RoleMenu) = _state.update {
        it.copy(draft = RoleMenuDraft.from(menu), editorOpen = true, hasUnsavedChanges = false)
    }

    /** Closes the editor and drops the draft. */
    fun closeEditor() = _state.update {
        it.copy(editorOpen = false, hasUnsavedChanges = false, draft = RoleMenuDraft())
    }

    /** Sets the menu name. */
    fun setName(value: String) = edit { it.copy(name = value.take(RoleMenuLimits.NameLength)) }

    /** Sets the channel the menu is posted in. */
    fun setChannel(id: Snowflake?) = edit { it.copy(channelId = id) }

    /** Sets the message shown above the dropdown or buttons. */
    fun setMessage(value: EmbedMessage) = edit { it.copy(message = value.copy(components = emptyList())) }

    /** Switches between a dropdown and buttons. */
    fun setStyle(value: RoleMenuStyle) = edit { it.copy(style = value) }

    /** Sets the dropdown hint text. */
    fun setPlaceholder(value: String) = edit { it.copy(placeholder = value.take(RoleMenuLimits.PlaceholderLength)) }

    /** Switches between pick any and pick one, resetting the limits to suit. */
    fun setMode(value: RoleMenuMode) = edit {
        if (value == it.mode) it
        else if (value == RoleMenuMode.PICK_ONE) it.copy(mode = value, maxRoles = 1).clamped()
        else it.copy(mode = value, maxRoles = 0).clamped()
    }

    /** Sets how many roles a pick any member must keep. */
    fun setMinRoles(value: Int) = edit { it.copy(minRoles = value).clamped() }

    /** Sets how many roles a pick any member can hold; 0 is no limit. */
    fun setMaxRoles(value: Int) = edit { it.copy(maxRoles = value).clamped() }

    /** For pick one menus, whether members must keep one role once they choose. */
    fun setKeepOne(value: Boolean) = edit { it.copy(minRoles = if (value) 1 else 0, maxRoles = 1).clamped() }

    /** Sets the role needed to use the menu; null is anyone. */
    fun setRequiredRole(id: Snowflake?) = edit { it.copy(requiredRoleId = id) }

    /** Whether members get a private note listing what changed. */
    fun setTellMembers(value: Boolean) = edit {
        it.copy(replyMode = if (value) RoleMenuReplyMode.PRIVATE else RoleMenuReplyMode.SILENT)
    }

    /** Adds [option], or replaces the option with the same key. */
    fun upsertOption(option: RoleMenuOptionDraft) = edit { draft ->
        val cleaned = option.copy(
            label = option.label.take(RoleMenuLimits.LabelLength),
            description = option.description.take(RoleMenuLimits.DescriptionLength),
        )
        val exists = draft.options.any { it.key == option.key }
        val options = if (exists) {
            draft.options.map { if (it.key == option.key) cleaned else it }
        } else if (draft.options.size < RoleMenuLimits.MaxOptions) {
            draft.options + cleaned
        } else {
            draft.options
        }
        draft.copy(options = options).clamped()
    }

    /** Removes the option with [key]. */
    fun removeOption(key: String) = edit { draft ->
        draft.copy(options = draft.options.filterNot { it.key == key }).clamped()
    }

    /** Moves the option with [key] up (negative [delta]) or down. */
    fun moveOption(key: String, delta: Int) = edit { draft ->
        val index = draft.options.indexOfFirst { it.key == key }
        val target = index + delta
        if (index < 0 || target !in draft.options.indices) return@edit draft
        val list = draft.options.toMutableList()
        val moved = list.removeAt(index)
        list.add(target, moved)
        draft.copy(options = list)
    }

    /** Posts a new menu or saves the edited one, then closes the editor. */
    fun save() = viewModelScope.launch {
        val current = _state.value
        val draft = current.draft.clamped()
        if (draft.invalidReason != null || current.isSaving) return@launch
        _state.update { it.copy(isSaving = true) }
        val body = MewdekoJson.encodeToString(JsonObject.serializer(), requestBody(draft, current))
        val endpoint = if (draft.id == null) {
            Endpoint(base, HttpMethod.POST, body)
        } else {
            Endpoint("$base/${draft.id}", HttpMethod.PUT, body)
        }
        runCatching { api.send(endpoint, RoleMenu.serializer()) }
            .onSuccess { saved ->
                _state.update {
                    it.copy(
                        menus = it.menus.replaceOrAdd(saved),
                        isSaving = false,
                        editorOpen = false,
                        hasUnsavedChanges = false,
                        draft = RoleMenuDraft(),
                    )
                }
                val channel = channelText(saved)
                postSuccess(if (draft.id == null) "Posted in $channel" else "Message updated in $channel")
            }
            .onFailure { error ->
                _state.update { it.copy(isSaving = false) }
                postError(error.botMessage())
            }
    }

    /** Deletes [menu] and its message. */
    fun delete(menu: RoleMenu) = viewModelScope.launch {
        markBusy(menu.id, true)
        runCatching { api.sendIgnoringBody(Endpoint("$base/${menu.id}", HttpMethod.DELETE)) }
            .onSuccess {
                _state.update { s ->
                    val closing = s.editorOpen && s.draft.id == menu.id
                    s.copy(
                        menus = s.menus.filterNot { it.id == menu.id },
                        busyMenuIds = s.busyMenuIds - menu.id,
                        editorOpen = s.editorOpen && !closing,
                        hasUnsavedChanges = if (closing) false else s.hasUnsavedChanges,
                        draft = if (closing) RoleMenuDraft() else s.draft,
                    )
                }
            }
            .onFailure { error ->
                markBusy(menu.id, false)
                postError(error.botMessage())
            }
    }

    /** Pauses or resumes [menu]. */
    fun setEnabled(menu: RoleMenu, enabled: Boolean) = viewModelScope.launch {
        markBusy(menu.id, true)
        val body = buildJsonObject { put("enabled", JsonPrimitive(enabled)) }
        runCatching {
            api.send(
                Endpoint(
                    "$base/${menu.id}/enabled",
                    HttpMethod.PUT,
                    MewdekoJson.encodeToString(JsonObject.serializer(), body),
                ),
                RoleMenu.serializer(),
            )
        }
            .onSuccess { saved ->
                _state.update { it.copy(menus = it.menus.replaceOrAdd(saved), busyMenuIds = it.busyMenuIds - menu.id) }
            }
            .onFailure { error ->
                markBusy(menu.id, false)
                postError(error.botMessage())
            }
    }

    /** Posts a fresh copy of [menu] in its current channel. */
    fun repost(menu: RoleMenu) = viewModelScope.launch {
        markBusy(menu.id, true)
        val body = buildJsonObject { put("channelId", JsonPrimitive(menu.channelId.ifBlank { "0" })) }
        runCatching {
            api.send(
                Endpoint(
                    "$base/${menu.id}/repost",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(JsonObject.serializer(), body),
                ),
                RoleMenu.serializer(),
            )
        }
            .onSuccess { saved ->
                _state.update { it.copy(menus = it.menus.replaceOrAdd(saved), busyMenuIds = it.busyMenuIds - menu.id) }
                postSuccess("Posted again in ${channelText(saved)}")
            }
            .onFailure { error ->
                markBusy(menu.id, false)
                postError(error.botMessage())
            }
    }

    /**
     * Moves an older emoji role setup into a new role menu, then shows it on
     * the Menus tab.
     */
    fun importSetup(
        source: RoleMenuImportSource,
        style: RoleMenuStyle,
        channelId: Snowflake?,
        name: String,
        copyMessage: Boolean,
        retireOriginal: Boolean,
    ) = viewModelScope.launch {
        if (source.id in _state.value.busySourceIds) return@launch
        _state.update { it.copy(busySourceIds = it.busySourceIds + source.id) }
        val body = buildJsonObject {
            put("sourceId", JsonPrimitive(source.id))
            put("style", JsonPrimitive(style.value))
            put("channelId", JsonPrimitive(channelId?.takeIf { it.isNotBlank() } ?: "0"))
            put("name", name.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
            put("copyMessage", JsonPrimitive(copyMessage))
            put("retireOriginal", JsonPrimitive(retireOriginal))
        }
        runCatching {
            api.send(
                Endpoint("$base/import", HttpMethod.POST, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
                RoleMenu.serializer(),
            )
        }
            .onSuccess { saved ->
                _state.update {
                    it.copy(
                        menus = it.menus.replaceOrAdd(saved),
                        importSources = it.importSources.filterNot { s -> s.id == source.id },
                        busySourceIds = it.busySourceIds - source.id,
                        section = "menus",
                    )
                }
                postSuccess("Moved to a role menu in ${channelText(saved)}")
            }
            .onFailure { error ->
                _state.update { it.copy(busySourceIds = it.busySourceIds - source.id) }
                postError(error.botMessage())
            }
    }

    private fun requestBody(draft: RoleMenuDraft, current: RoleMenusState): JsonObject = buildJsonObject {
        val message = draft.message.copy(components = emptyList()).serialize().let { if (it == "-") "" else it }
        put("name", JsonPrimitive(draft.name.trim()))
        put("channelId", JsonPrimitive(draft.channelId.orEmpty().ifBlank { "0" }))
        put("message", JsonPrimitive(message))
        put("style", JsonPrimitive(draft.style.value))
        put(
            "placeholder",
            draft.placeholder.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull,
        )
        put("mode", JsonPrimitive(draft.mode.value))
        put("minRoles", JsonPrimitive(draft.minRoles))
        put("maxRoles", JsonPrimitive(draft.maxRoles))
        put("requiredRoleId", JsonPrimitive(draft.requiredRoleId?.takeIf { it.isNotBlank() } ?: "0"))
        put("replyMode", JsonPrimitive(draft.replyMode.value))
        put("enabled", JsonPrimitive(draft.enabled))
        put("options", buildJsonArray {
            draft.options.forEach { option ->
                add(buildJsonObject {
                    put("id", option.id?.let(::JsonPrimitive) ?: JsonNull)
                    put("roleId", JsonPrimitive(option.roleId))
                    put(
                        "label",
                        JsonPrimitive(option.label.trim().ifEmpty { current.role(option.roleId)?.name.orEmpty() }),
                    )
                    put("emoji", option.emoji.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
                    put(
                        "description",
                        option.description.trim().takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull,
                    )
                    put("buttonStyle", JsonPrimitive(RoleMenuButtonColor.from(option.buttonStyle).value))
                })
            }
        })
    }

    private fun channelText(menu: RoleMenu): String =
        menu.channelName?.let { "#$it" } ?: _state.value.channelLabel(menu.channelId) ?: "the channel"

    private fun markBusy(id: Int, busy: Boolean) = _state.update {
        it.copy(busyMenuIds = if (busy) it.busyMenuIds + id else it.busyMenuIds - id)
    }

    private fun edit(transform: (RoleMenuDraft) -> RoleMenuDraft) = _state.update {
        val next = transform(it.draft)
        if (next == it.draft) it else it.copy(draft = next, hasUnsavedChanges = true)
    }

    private fun List<RoleMenu>.replaceOrAdd(menu: RoleMenu): List<RoleMenu> =
        if (any { it.id == menu.id }) map { if (it.id == menu.id) menu else it } else this + menu

    /**
     * The bot's plain text error, or the shared fallback when there is none.
     * A JSON error body contributes its message or title instead.
     */
    private fun Throwable.botMessage(): String {
        val body = (this as? ApiError.Http)?.body?.trim().orEmpty()
        if (body.isEmpty()) return SaveFallback
        if (body.startsWith("{")) {
            val obj = runCatching { MewdekoJson.parseToJsonElement(body).jsonObject }.getOrNull()
            val text = listOf("message", "Message", "title", "error")
                .firstNotNullOfOrNull { key -> (obj?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
            return text ?: SaveFallback
        }
        if (body.startsWith("<")) return SaveFallback
        return body.removeSurrounding("\"").take(300)
    }
}
