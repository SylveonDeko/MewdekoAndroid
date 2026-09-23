package dev.mewdeko.mobile.feature.repeaters

import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.normalizeKeys
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Editable state backing the create and edit repeater form. */
data class RepeaterDraft(
    val channelId: Snowflake = "",
    val message: EmbedMessage = EmbedMessage(),
    val triggerMode: StickyTriggerMode = StickyTriggerMode.TIME_INTERVAL,
    val interval: String = "00:05:00",
    val startTimeOfDay: String = "",
    val activityThreshold: Int = 5,
    val activityTimeWindow: String = "00:05:00",
    val conversationDetection: Boolean = false,
    val conversationThreshold: Int = 3,
    val priority: Int = 50,
    val queuePosition: Int = 0,
    val noRedundant: Boolean = false,
    val allowMentions: Boolean = false,
    val suppressNotifications: Boolean = false,
    val timeSchedulePreset: TimeSchedulePreset = TimeSchedulePreset.NONE,
    val timeConditions: String = "",
    val maxAge: String = "",
    val maxTriggers: String = "",
    val threadAutoSticky: Boolean = false,
    val threadOnlyMode: Boolean = false,
    val forumRequiredTags: List<Snowflake> = emptyList(),
    val forumExcludedTags: List<Snowflake> = emptyList(),
) {
    /** Encodes the required/excluded forum tag picks, or `null` when neither is set. */
    fun forumTagConditionsJson(): String? {
        if (forumRequiredTags.isEmpty() && forumExcludedTags.isEmpty()) return null
        val obj = buildJsonObject {
            if (forumRequiredTags.isNotEmpty()) {
                put("requiredTags", buildJsonArray { forumRequiredTags.forEach { add(JsonPrimitive(it)) } })
            }
            if (forumExcludedTags.isNotEmpty()) {
                put("excludedTags", buildJsonArray { forumExcludedTags.forEach { add(JsonPrimitive(it)) } })
            }
        }
        return MewdekoJson.encodeToString(JsonObject.serializer(), obj)
    }

    companion object {
        /** Builds a draft from an existing repeater, for the edit form. */
        fun from(repeater: RepeaterEntry): RepeaterDraft {
            val (required, excluded) = parseForumTagConditions(repeater.forumTagConditions)
            return RepeaterDraft(
                channelId = repeater.channelId,
                message = EmbedMessage.parse(repeater.message),
                triggerMode = repeater.trigger,
                interval = repeater.interval,
                startTimeOfDay = repeater.startTimeOfDay.orEmpty(),
                activityThreshold = repeater.activityThreshold,
                activityTimeWindow = repeater.activityTimeWindow,
                conversationDetection = repeater.conversationDetection,
                conversationThreshold = repeater.conversationThreshold,
                priority = repeater.priority,
                queuePosition = repeater.queuePosition,
                noRedundant = repeater.noRedundant,
                allowMentions = false,
                suppressNotifications = repeater.suppressNotifications,
                timeSchedulePreset = if (repeater.timeConditions.isNullOrBlank()) {
                    TimeSchedulePreset.NONE
                } else {
                    TimeSchedulePreset.CUSTOM
                },
                timeConditions = repeater.timeConditions.orEmpty(),
                maxAge = repeater.maxAge.orEmpty(),
                maxTriggers = repeater.maxTriggers?.toString().orEmpty(),
                threadAutoSticky = repeater.threadAutoSticky,
                threadOnlyMode = repeater.threadOnlyMode,
                forumRequiredTags = required,
                forumExcludedTags = excluded,
            )
        }

        /** Parses a repeater's `forumTagConditions` JSON into required/excluded id lists. */
        fun parseForumTagConditions(json: String?): Pair<List<Snowflake>, List<Snowflake>> {
            val empty = emptyList<Snowflake>() to emptyList<Snowflake>()
            if (json.isNullOrBlank()) return empty
            return runCatching {
                val root = MewdekoJson.parseToJsonElement(json).normalizeKeys() as JsonObject
                val required = (root["requiredTags"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                val excluded = (root["excludedTags"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
                required to excluded
            }.getOrDefault(empty)
        }
    }
}
