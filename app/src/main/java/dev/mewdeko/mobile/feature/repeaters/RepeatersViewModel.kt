package dev.mewdeko.mobile.feature.repeaters

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.InstantSerializer
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
import java.time.Instant
import javax.inject.Inject

/** What causes a sticky repeater to post again. */
enum class StickyTriggerMode(val raw: Int, val label: String, val blurb: String) {
    TIME_INTERVAL(0, "Interval", "Repost on a fixed timer"),
    ON_ACTIVITY(1, "Activity", "Repost once the channel gets busy enough"),
    IMMEDIATE(2, "Immediate", "Repost as soon as another message arrives"),
    SMART(3, "Smart", "Repost when the bot judges the conversation has moved on"),
    MANUAL(4, "Manual", "Only repost when triggered by hand");

    companion object {
        /** Maps a wire value onto a mode, defaulting to [TIME_INTERVAL]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: TIME_INTERVAL
    }
}

/** A recurring or sticky message. */
@Serializable
data class RepeaterEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val message: String = "",
    val interval: String = "00:05:00",
    val startTimeOfDay: String? = null,
    val noRedundant: Boolean = false,
    val isEnabled: Boolean = true,
    val triggerMode: Int = 0,
    val activityThreshold: Int = 5,
    val activityTimeWindow: String = "00:05:00",
    val conversationDetection: Boolean = false,
    val conversationThreshold: Int = 3,
    val priority: Int = 50,
    val queuePosition: Int = 0,
    val timeConditions: String? = null,
    val maxAge: String? = null,
    val maxTriggers: Int? = null,
    val threadAutoSticky: Boolean = false,
    val threadOnlyMode: Boolean = false,
    val suppressNotifications: Boolean = false,
    val displayCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastDisplayed: Instant? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    @Serializable(with = InstantSerializer::class) val nextExecution: Instant? = null,
    val guildTimezone: String = "UTC",
) {
    /** The typed form of [triggerMode]. */
    val trigger: StickyTriggerMode get() = StickyTriggerMode.from(triggerMode)
}

/** Repeaters screen state. */
data class RepeatersState(
    val repeaters: List<RepeaterEntry> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /** How many repeaters are currently running. */
    val activeCount: Int get() = repeaters.count { it.isEnabled }

    /** Total posts made across every repeater. */
    val totalPosts: Int get() = repeaters.sumOf { it.displayCount }

    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name ?: id
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

    /** Reloads the repeater list and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val repeaters = async {
                runCatching {
                    api.send(
                        Endpoint("api/Repeaters/$guildId"),
                        ListSerializer(RepeaterEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    repeaters = repeaters.await().sortedByDescending { entry -> entry.priority },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Creates a repeater. */
    fun create(
        channelId: Snowflake,
        message: String,
        interval: String,
        priority: Int,
        triggerMode: StickyTriggerMode,
        suppressNotifications: Boolean,
    ) = launchAction("Failed to create repeater.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Repeaters/$guildId",
                HttpMethod.POST,
                jsonBody(
                    "channelId" to (channelId.toLongOrNull() ?: 0L),
                    "message" to message,
                    "interval" to interval,
                    "priority" to priority,
                    "triggerMode" to triggerMode.raw,
                    "suppressNotifications" to suppressNotifications,
                    "noRedundant" to false,
                    "activityThreshold" to 5,
                    "activityTimeWindow" to "00:05:00",
                    "conversationDetection" to false,
                    "conversationThreshold" to 3,
                    "threadAutoSticky" to false,
                    "threadOnlyMode" to false,
                    "allowMentions" to false,
                ),
            )
        )
        postSuccess("Repeater created.")
        load(refreshing = true)
    }

    /** Patches whichever repeater fields are non-null. */
    fun update(
        id: Int,
        message: String? = null,
        channelId: Snowflake? = null,
        interval: String? = null,
        isEnabled: Boolean? = null,
        priority: Int? = null,
        triggerMode: Int? = null,
        suppressNotifications: Boolean? = null,
        noRedundant: Boolean? = null,
    ) = launchAction("Failed to update repeater.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Repeaters/$guildId/$id",
                HttpMethod.PATCH,
                jsonBody(
                    "message" to message,
                    "channelId" to channelId?.toLongOrNull(),
                    "interval" to interval,
                    "isEnabled" to isEnabled,
                    "priority" to priority,
                    "triggerMode" to triggerMode,
                    "suppressNotifications" to suppressNotifications,
                    "noRedundant" to noRedundant,
                ),
            )
        )
        _state.update { current ->
            current.copy(
                repeaters = current.repeaters.map { entry ->
                    if (entry.id != id) entry else entry.copy(
                        message = message ?: entry.message,
                        channelId = channelId ?: entry.channelId,
                        interval = interval ?: entry.interval,
                        isEnabled = isEnabled ?: entry.isEnabled,
                        priority = priority ?: entry.priority,
                        triggerMode = triggerMode ?: entry.triggerMode,
                        suppressNotifications = suppressNotifications ?: entry.suppressNotifications,
                        noRedundant = noRedundant ?: entry.noRedundant,
                    )
                },
            )
        }
    }

    /** Sets the repeater's message body. */
    fun setMessage(id: Int, message: EmbedMessage) = update(id, message = message.serialize())

    /** Deletes a repeater. */
    fun remove(id: Int) = launchAction("Failed to delete repeater.") {
        api.sendIgnoringBody(Endpoint("api/Repeaters/$guildId/$id", HttpMethod.DELETE))
        _state.update { it.copy(repeaters = it.repeaters.filterNot { entry -> entry.id == id }) }
        postSuccess("Repeater deleted.")
    }

    /** Posts the repeater immediately. */
    fun triggerNow(id: Int) = launchAction("Failed to trigger repeater.") {
        api.sendIgnoringBody(
            Endpoint("api/Repeaters/$guildId/$id/trigger", HttpMethod.POST)
        )
        postSuccess("Repeater posted.")
        load(refreshing = true)
    }
}
