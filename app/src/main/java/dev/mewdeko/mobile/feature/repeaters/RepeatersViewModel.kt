package dev.mewdeko.mobile.feature.repeaters

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/**
 * Distinguishes an untouched PATCH field from one explicitly set to `null`.
 *
 * [dev.mewdeko.mobile.core.net.jsonBody] always omits `null` values, which
 * works for "leave alone" but cannot express "clear this field" for optional
 * server properties such as `maxAge` or `timeConditions`. Callers that need
 * to clear a field pass [Value] with a `null` payload instead.
 */
sealed interface Patch<out T> {
    /** The field is left as-is on the server. */
    data object Untouched : Patch<Nothing>

    /** The field is sent explicitly; a `null` payload clears it. */
    data class Value<T>(val value: T?) : Patch<T>
}

private fun JsonObjectBuilder.putIfPresent(key: String, value: String?) {
    if (value != null) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putIfPresent(key: String, value: Int?) {
    if (value != null) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putIfPresent(key: String, value: Boolean?) {
    if (value != null) put(key, JsonPrimitive(value))
}

private fun JsonObjectBuilder.putClearable(key: String, patch: Patch<String>) {
    if (patch is Patch.Value) put(key, patch.value?.let { JsonPrimitive(it) } ?: JsonNull)
}

private fun JsonObjectBuilder.putClearableInt(key: String, patch: Patch<Int>) {
    if (patch is Patch.Value) put(key, patch.value?.let { JsonPrimitive(it) } ?: JsonNull)
}

/**
 * Sets `startTimeOfDay` on a PATCH body. The bot treats a missing or `null` value as "keep",
 * so a [Patch.Value] with a `null` payload (the field was cleared in the UI) is sent as `""`,
 * which the bot recognizes as "clear this field".
 */
private fun JsonObjectBuilder.putStartTimeOfDayPatch(patch: Patch<String>) {
    if (patch is Patch.Value) put("startTimeOfDay", JsonPrimitive(patch.value.orEmpty()))
}

/**
 * Sets `maxAge` on a PATCH body. Since the bot cannot tell a `null` `maxAge` apart from a
 * missing field, clearing it back to unlimited is expressed with `clearMaxAge: true` instead.
 */
private fun JsonObjectBuilder.putMaxAgePatch(patch: Patch<String>) {
    if (patch !is Patch.Value) return
    val value = patch.value
    if (value.isNullOrBlank()) put("clearMaxAge", JsonPrimitive(true)) else put("maxAge", JsonPrimitive(value))
}

/**
 * Sets `maxTriggers` on a PATCH body. Since the bot cannot tell a `null` `maxTriggers` apart
 * from a missing field, clearing it back to unlimited is expressed with `clearMaxTriggers: true`
 * instead.
 */
private fun JsonObjectBuilder.putMaxTriggersPatch(patch: Patch<Int>) {
    if (patch !is Patch.Value) return
    val value = patch.value
    if (value == null) put("clearMaxTriggers", JsonPrimitive(true)) else put("maxTriggers", JsonPrimitive(value))
}

/** Screen state for the repeaters feature. */
data class RepeatersState(
    val repeaters: List<RepeaterEntry> = emptyList(),
    val stats: RepeaterStatsResponse? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val forumChannels: List<ForumChannelLite> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
) {
    /** Resolves a channel id to its display name, falling back to the raw id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name
            ?: forumChannels.firstOrNull { it.id == id }?.name
            ?: id

    /** Whether [id] refers to a forum channel rather than a text channel. */
    fun isForumChannel(id: Snowflake): Boolean = forumChannels.any { it.id == id }

    /** The forum tags available on the given channel; empty for text channels. */
    fun forumTags(id: Snowflake): List<ForumTagLite> =
        forumChannels.firstOrNull { it.id == id }?.tags.orEmpty()
}

/** Recurring and sticky messages. */
@HiltViewModel
class RepeatersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(RepeatersState())

    /** Observable screen state. */
    val state: StateFlow<RepeatersState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads repeaters, statistics, and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val repeatersDeferred = async {
                runCatching {
                    api.send(Endpoint("api/Repeaters/$guildId"), ListSerializer(RepeaterEntry.serializer()))
                }.getOrDefault(emptyList())
            }
            val statsDeferred = async {
                runCatching {
                    api.send(Endpoint("api/Repeaters/$guildId/statistics"), RepeaterStatsResponse.serializer())
                }.getOrNull()
            }
            val channelsDeferred = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val forumsDeferred = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/forumchannels/$guildId"),
                        ListSerializer(ForumChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val repeaters = repeatersDeferred.await()
            val liveIds = repeaters.map { it.id }.toSet()
            _state.update {
                it.copy(
                    repeaters = repeaters.sortedWith(
                        compareByDescending<RepeaterEntry> { r -> r.priority }.thenBy { r -> r.queuePosition },
                    ),
                    stats = statsDeferred.await(),
                    availableChannels = channelsDeferred.await().sortedBy { c -> c.name.lowercase() },
                    forumChannels = forumsDeferred.await().sortedBy { f -> f.name.lowercase() },
                    selectedIds = it.selectedIds.intersect(liveIds),
                )
            }
        }
    }

    /** Creates a repeater from a filled-in [RepeaterDraft]. */
    fun create(draft: RepeaterDraft) = launchAction("Failed to create repeater.") {
        val body = buildJsonObject {
            put("channelId", JsonPrimitive(draft.channelId.toLongOrNull() ?: 0L))
            put("message", JsonPrimitive(draft.message.serialize()))
            put("interval", JsonPrimitive(draft.interval))
            draft.startTimeOfDay.takeIf { it.isNotBlank() }?.let { put("startTimeOfDay", JsonPrimitive(it)) }
            put("noRedundant", JsonPrimitive(draft.noRedundant))
            put("allowMentions", JsonPrimitive(draft.allowMentions))
            put("triggerMode", JsonPrimitive(draft.triggerMode.raw))
            put("activityThreshold", JsonPrimitive(draft.activityThreshold))
            put("activityTimeWindow", JsonPrimitive(draft.activityTimeWindow))
            put("conversationDetection", JsonPrimitive(draft.conversationDetection))
            put("conversationThreshold", JsonPrimitive(draft.conversationThreshold))
            put("priority", JsonPrimitive(draft.priority))
            if (draft.timeSchedulePreset != TimeSchedulePreset.NONE) {
                put("timeSchedulePreset", JsonPrimitive(draft.timeSchedulePreset.raw))
            }
            if (draft.timeSchedulePreset == TimeSchedulePreset.CUSTOM) {
                draft.timeConditions.takeIf { it.isNotBlank() }?.let { put("timeConditions", JsonPrimitive(it)) }
            }
            draft.maxAge.takeIf { it.isNotBlank() }?.let { put("maxAge", JsonPrimitive(it)) }
            draft.maxTriggers.toIntOrNull()?.let { put("maxTriggers", JsonPrimitive(it)) }
            put("threadAutoSticky", JsonPrimitive(draft.threadAutoSticky))
            put("threadOnlyMode", JsonPrimitive(draft.threadOnlyMode))
            draft.forumTagConditionsJson()?.let { put("forumTagConditions", JsonPrimitive(it)) }
            put("suppressNotifications", JsonPrimitive(draft.suppressNotifications))
        }
        api.sendIgnoringBody(
            Endpoint("api/Repeaters/$guildId", HttpMethod.POST, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
        )
        load(refreshing = true)
    }

    /** Saves an edit as a diff-only PATCH against [original], matching the dashboard's edit form. */
    fun saveEdit(original: RepeaterEntry, draft: RepeaterDraft) = launchAction("Failed to update repeater.") {
        val newMessage = draft.message.serialize()

        /**
         * The bot never reports which preset produced [RepeaterEntry.timeConditions], so, like
         * the dashboard, the original preset is inferred as "custom" when raw conditions are
         * present and "none" otherwise; picking business/evening/weekend always counts as a
         * change even if the server-side JSON happened to already match that preset.
         */
        val originalPreset = if (original.timeConditions.isNullOrBlank()) TimeSchedulePreset.NONE else TimeSchedulePreset.CUSTOM
        val newTimeConditions = if (draft.timeSchedulePreset == TimeSchedulePreset.CUSTOM) {
            draft.timeConditions.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val originalTimeConditionsForCompare = if (originalPreset == TimeSchedulePreset.CUSTOM) original.timeConditions else null

        val newMaxAge = draft.maxAge.takeIf { it.isNotBlank() }
        val newMaxTriggers = draft.maxTriggers.toIntOrNull()
        val newForumTags = draft.forumTagConditionsJson()

        val body = buildJsonObject {
            if (newMessage != original.message) put("message", JsonPrimitive(newMessage))
            if (draft.channelId != original.channelId) {
                draft.channelId.takeIf { it.toLongOrNull() != null }?.let { put("channelId", JsonPrimitive(it)) }
            }
            if (draft.triggerMode.raw != original.triggerMode) put("triggerMode", JsonPrimitive(draft.triggerMode.raw))
            if (draft.interval != original.interval) put("interval", JsonPrimitive(draft.interval))
            val newStartTime = draft.startTimeOfDay.takeIf { it.isNotBlank() }
            if (newStartTime != original.startTimeOfDay) putStartTimeOfDayPatch(Patch.Value(newStartTime))
            if (draft.activityThreshold != original.activityThreshold ||
                draft.activityTimeWindow != original.activityTimeWindow
            ) {
                put("activityThreshold", JsonPrimitive(draft.activityThreshold))
                put("activityTimeWindow", JsonPrimitive(draft.activityTimeWindow))
            }
            if (draft.conversationDetection != original.conversationDetection) {
                put("conversationDetection", JsonPrimitive(draft.conversationDetection))
            }
            if (draft.conversationThreshold != original.conversationThreshold) {
                put("conversationThreshold", JsonPrimitive(draft.conversationThreshold))
            }
            if (draft.priority != original.priority) put("priority", JsonPrimitive(draft.priority))
            if (draft.queuePosition != original.queuePosition) put("queuePosition", JsonPrimitive(draft.queuePosition))
            if (draft.noRedundant != original.noRedundant) put("noRedundant", JsonPrimitive(draft.noRedundant))
            if (draft.allowMentions) put("allowMentions", JsonPrimitive(true))
            if (draft.timeSchedulePreset != originalPreset) {
                put("timeSchedulePreset", JsonPrimitive(draft.timeSchedulePreset.raw))
            }
            if (newTimeConditions != originalTimeConditionsForCompare) {
                newTimeConditions?.let { put("timeConditions", JsonPrimitive(it)) }
            }
            if (newMaxAge != original.maxAge) putMaxAgePatch(Patch.Value(newMaxAge))
            if (newMaxTriggers != original.maxTriggers) putMaxTriggersPatch(Patch.Value(newMaxTriggers))
            if (draft.threadAutoSticky != original.threadAutoSticky) {
                put("threadAutoSticky", JsonPrimitive(draft.threadAutoSticky))
            }
            if (draft.threadOnlyMode != original.threadOnlyMode) put("threadOnlyMode", JsonPrimitive(draft.threadOnlyMode))
            if (draft.suppressNotifications != original.suppressNotifications) {
                put("suppressNotifications", JsonPrimitive(draft.suppressNotifications))
            }
            if (newForumTags != original.forumTagConditions) putClearable("forumTagConditions", Patch.Value(newForumTags))
        }
        if (body.isEmpty()) return@launchAction
        api.sendIgnoringBody(
            Endpoint(
                "api/Repeaters/$guildId/${original.id}",
                HttpMethod.PATCH,
                MewdekoJson.encodeToString(JsonObject.serializer(), body),
            ),
        )
        load(refreshing = true)
    }

    /** Patches one or more fields on a single repeater, for the inline card controls. */
    fun update(
        id: Int,
        isEnabled: Boolean? = null,
        priority: Int? = null,
        queuePosition: Int? = null,
        noRedundant: Boolean? = null,
        conversationDetection: Boolean? = null,
        conversationThreshold: Int? = null,
        suppressNotifications: Boolean? = null,
        interval: String? = null,
        startTimeOfDay: Patch<String> = Patch.Untouched,
        maxAge: Patch<String> = Patch.Untouched,
        maxTriggers: Patch<Int> = Patch.Untouched,
    ) = launchAction("Failed to update repeater.") {
        val body = buildJsonObject {
            putIfPresent("isEnabled", isEnabled)
            putIfPresent("priority", priority)
            putIfPresent("queuePosition", queuePosition)
            putIfPresent("noRedundant", noRedundant)
            putIfPresent("conversationDetection", conversationDetection)
            putIfPresent("conversationThreshold", conversationThreshold)
            putIfPresent("suppressNotifications", suppressNotifications)
            putIfPresent("interval", interval)
            putStartTimeOfDayPatch(startTimeOfDay)
            putMaxAgePatch(maxAge)
            putMaxTriggersPatch(maxTriggers)
        }
        if (body.isEmpty()) return@launchAction
        api.sendIgnoringBody(
            Endpoint("api/Repeaters/$guildId/$id", HttpMethod.PATCH, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
        )
        load(refreshing = true)
    }

    /** Deletes a repeater. */
    fun remove(id: Int) = launchAction("Failed to delete repeater.") {
        api.sendIgnoringBody(Endpoint("api/Repeaters/$guildId/$id", HttpMethod.DELETE))
        _state.update {
            it.copy(
                repeaters = it.repeaters.filterNot { entry -> entry.id == id },
                selectedIds = it.selectedIds - id,
            )
        }
    }

    /** Posts the repeater immediately. */
    fun triggerNow(id: Int) = launchAction("Failed to trigger repeater.") {
        api.sendIgnoringBody(Endpoint("api/Repeaters/$guildId/$id/trigger", HttpMethod.POST))
        postSuccess("Repeater posted.")
        load(refreshing = true)
    }

    /** Moves a repeater one slot earlier in its priority's queue. */
    fun moveUp(id: Int) {
        val repeater = _state.value.repeaters.firstOrNull { it.id == id } ?: return
        if (repeater.queuePosition <= 1) return
        update(id, queuePosition = repeater.queuePosition - 1)
    }

    /** Moves a repeater one slot later in its priority's queue. */
    fun moveDown(id: Int) {
        val repeater = _state.value.repeaters.firstOrNull { it.id == id } ?: return
        update(id, queuePosition = repeater.queuePosition + 1)
    }

    /** Toggles whether [id] is part of the current bulk selection. */
    fun setSelected(id: Int, selected: Boolean) = _state.update { s ->
        s.copy(selectedIds = if (selected) s.selectedIds + id else s.selectedIds - id)
    }

    /** Selects every loaded repeater. */
    fun selectAll() = _state.update { it.copy(selectedIds = it.repeaters.map { r -> r.id }.toSet()) }

    /** Clears the bulk selection. */
    fun clearSelection() = _state.update { it.copy(selectedIds = emptySet()) }

    /** Enables or disables every selected repeater in one request. */
    fun bulkToggle(enable: Boolean) = launchAction("Failed to update repeaters.") {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return@launchAction
        val body = MewdekoJson.encodeToString(ListSerializer(Int.serializer()), ids)
        api.sendIgnoringBody(
            Endpoint("api/Repeaters/$guildId/bulk-toggle?enable=$enable", HttpMethod.PATCH, body),
        )
        clearSelection()
        load(refreshing = true)
    }
}
