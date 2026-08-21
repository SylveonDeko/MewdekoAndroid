package dev.mewdeko.mobile.feature.afk

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.ScalarString
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonInt
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import dev.mewdeko.mobile.core.ui.StatusMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import javax.inject.Inject

/** Which settings the user has edited but not yet saved. */
enum class AfkSetting(val label: String) {
    DELETION("auto-deletion"),
    MAX_LENGTH("max length"),
    REMOVAL_TYPE("removal type"),
    TIMEOUT("timeout"),
    DISABLED_CHANNELS("disabled channels"),
    CUSTOM_MESSAGE("custom message"),
}

/** AFK screen state. */
data class AfkState(
    val deletionSeconds: Int = 0,
    val maxLength: Int = 250,
    val removalType: AfkRemovalType = AfkRemovalType.EITHER,
    val timeoutString: String = "0s",
    val disabledChannelIds: List<Snowflake> = emptyList(),
    val customMessage: EmbedMessage = EmbedMessage(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val afkUsers: List<UserWithAfk> = emptyList(),
    val changed: Set<AfkSetting> = emptySet(),
    val isSaving: Boolean = false,
) {
    /** Whether any edit is pending. */
    val hasUnsavedChanges: Boolean get() = changed.isNotEmpty()

    /** How many active AFK statuses were set with a timer. */
    val timedAfkCount: Int get() = afkUsers.count { it.afkStatus?.wasTimed == true }
}

/** Loads, edits, and persists AFK configuration plus the per-guild AFK user list. */
@HiltViewModel
class AfkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(AfkState())

    /** Observable screen state. */
    val state: StateFlow<AfkState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads every setting and the AFK user list in parallel. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val deletion = async { intOrNull("api/afk/$guildId/deletion") ?: 0 }
            val length = async { intOrNull("api/afk/$guildId/length") ?: 250 }
            val type = async { intOrNull("api/afk/$guildId/type") ?: 4 }
            val timeout = async { intOrNull("api/afk/$guildId/timeout") ?: 0 }
            val disabled = async { scalarOrNull("api/afk/$guildId/disabled-channels") }
            val custom = async { scalarOrNull("api/afk/$guildId/custom-message") }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val users = async {
                runCatching {
                    api.send(
                        Endpoint("api/afk/$guildId"),
                        ListSerializer(UserWithAfk.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val disabledRaw = disabled.await().orEmpty()
            _state.update {
                it.copy(
                    deletionSeconds = deletion.await(),
                    maxLength = length.await(),
                    removalType = AfkRemovalType.from(type.await()),
                    timeoutString = AfkTime.secondsToString(timeout.await()),
                    disabledChannelIds = if (disabledRaw.isEmpty() || disabledRaw == "0") emptyList()
                    else disabledRaw.split(',').map { id -> id.trim() }.filter { id -> id.isNotEmpty() },
                    customMessage = EmbedMessage.parse(custom.await()),
                    availableChannels = channels.await().sortedBy { channel -> channel.name.lowercase() },
                    afkUsers = users.await().filter { user -> user.hasActiveAfk },
                    changed = emptySet(),
                )
            }
        }
    }

    /** Updates the auto-deletion delay. */
    fun setDeletionSeconds(value: Int) = edit(AfkSetting.DELETION) { it.copy(deletionSeconds = value) }

    /** Updates the maximum AFK message length. */
    fun setMaxLength(value: Int) = edit(AfkSetting.MAX_LENGTH) { it.copy(maxLength = value) }

    /** Updates what clears a member's AFK status. */
    fun setRemovalType(value: AfkRemovalType) =
        edit(AfkSetting.REMOVAL_TYPE) { it.copy(removalType = value) }

    /** Updates the timed-AFK ceiling. */
    fun setTimeout(value: String) = edit(AfkSetting.TIMEOUT) { it.copy(timeoutString = value) }

    /** Updates the channels where AFK is suppressed. */
    fun setDisabledChannels(ids: List<Snowflake>) =
        edit(AfkSetting.DISABLED_CHANNELS) { it.copy(disabledChannelIds = ids) }

    /** Updates the custom AFK announcement message. */
    fun setCustomMessage(message: EmbedMessage) =
        edit(AfkSetting.CUSTOM_MESSAGE) { it.copy(customMessage = message) }

    /**
     * Writes every pending edit.
     *
     * Each setting has its own endpoint, so partial success is possible; the
     * status message names exactly which ones landed.
     */
    fun save() = viewModelScope.launch {
        val current = _state.value
        if (current.changed.isEmpty()) {
            postError("No changes to save.")
            return@launch
        }
        validationError(current)?.let {
            postError(it)
            return@launch
        }

        _state.update { it.copy(isSaving = true) }
        val saved = mutableListOf<String>()
        val failed = mutableListOf<String>()

        current.changed.forEach { setting ->
            val ok = runCatching {
                when (setting) {
                    AfkSetting.DELETION -> post("deletion", jsonInt(current.deletionSeconds))
                    AfkSetting.MAX_LENGTH -> post("length", jsonInt(current.maxLength))
                    AfkSetting.REMOVAL_TYPE -> post("type", jsonInt(current.removalType.raw))
                    AfkSetting.TIMEOUT -> post("timeout", jsonString(current.timeoutString))
                    AfkSetting.DISABLED_CHANNELS -> post(
                        "disabled-channels",
                        jsonString(current.disabledChannelIds.ifEmpty { listOf("0") }.joinToString(",")),
                    )

                    AfkSetting.CUSTOM_MESSAGE -> post(
                        "custom-message",
                        jsonString(current.customMessage.serialize()),
                    )
                }
            }.isSuccess
            if (ok) saved.add(setting.label) else failed.add(setting.label)
        }

        _state.update { it.copy(isSaving = false, changed = if (failed.isEmpty()) emptySet() else it.changed) }
        if (failed.isEmpty()) {
            postStatus(StatusMessage.success("Saved ${saved.joinToString(", ")}."))
        } else {
            postStatus(StatusMessage.error("Failed to save ${failed.joinToString(", ")}."))
        }
    }

    /** Clears one member's AFK status. */
    fun clearAfk(userId: Snowflake) = launchAction("Failed to clear AFK status.") {
        api.sendIgnoringBody(Endpoint("api/afk/$guildId/$userId", HttpMethod.DELETE))
        _state.update { it.copy(afkUsers = it.afkUsers.filterNot { user -> user.userId == userId }) }
        postSuccess("AFK cleared.")
    }

    /** Clears the AFK status of every currently-AFK member. */
    fun clearAll() = viewModelScope.launch {
        val ids = _state.value.afkUsers.map { it.userId }
        if (ids.isEmpty()) return@launch
        var cleared = 0
        ids.forEach { id ->
            val ok = runCatching {
                api.sendIgnoringBody(Endpoint("api/afk/$guildId/$id", HttpMethod.DELETE))
            }.isSuccess
            if (ok) cleared++
        }
        _state.update { it.copy(afkUsers = it.afkUsers.filterNot { user -> user.userId in ids }) }
        postStatus(
            if (cleared == ids.size) {
                StatusMessage.success(
                    "Cleared AFK for $cleared user${if (cleared == 1) "" else "s"}."
                )
            } else {
                StatusMessage.error("Cleared $cleared of ${ids.size}; some failed.")
            }
        )
    }

    private fun validationError(state: AfkState): String? = when {
        AfkSetting.MAX_LENGTH in state.changed && state.maxLength !in 1..4096 ->
            "Max length must be between 1 and 4096."

        AfkSetting.DELETION in state.changed && state.deletionSeconds < 0 ->
            "Deletion time cannot be negative."

        AfkSetting.TIMEOUT in state.changed &&
            AfkTime.stringToSeconds(state.timeoutString) !in 1..7200 ->
            "Timeout must be between 1 second and 2 hours."

        else -> null
    }

    private suspend fun post(path: String, body: String) =
        api.sendIgnoringBody(Endpoint("api/afk/$guildId/$path", HttpMethod.POST, body))

    private suspend fun intOrNull(path: String): Int? =
        runCatching { api.send(Endpoint(path), Int.serializer()) }.getOrNull()

    private suspend fun scalarOrNull(path: String): String? =
        runCatching { api.send(Endpoint(path), ScalarString.serializer()).value }.getOrNull()

    private fun edit(setting: AfkSetting, transform: (AfkState) -> AfkState) {
        _state.update { transform(it).copy(changed = it.changed + setting) }
    }
}
