package dev.mewdeko.mobile.feature.chattriggers

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import javax.inject.Inject

/**
 * How a trigger's prefix is resolved.
 *
 * Values mirror RequirePrefixType in the bot exactly. They previously did not, so "No prefix"
 * was sent as 1, which the bot reads as "requires the global prefix".
 */
enum class ChatTriggerPrefixType(val raw: Int, val label: String) {
    NONE(0, "No prefix"),
    GLOBAL(1, "Global prefix"),
    GUILD_OR_GLOBAL(2, "Server or global prefix"),
    GUILD_OR_NONE(3, "Server prefix if set"),
    CUSTOM(4, "Custom prefix");

    companion object {
        /** Maps a wire value onto a prefix type, defaulting to [NONE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** How a trigger picks between several responses. */
enum class ChatTriggerResponseMode(val raw: Int, val label: String) {
    SINGLE(0, "Always the first response"),
    RANDOM(1, "Random response"),
    ROUND_ROBIN(2, "In order, one per use"),
    ALL(3, "Send all of them");

    companion object {
        /** Maps a wire value onto a response mode, defaulting to [SINGLE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: SINGLE
    }
}

/** Who a trigger's own cooldown applies to. */
enum class ChatTriggerCooldownScope(val raw: Int, val label: String) {
    USER(0, "Each member separately"),
    CHANNEL(1, "Everyone in the channel"),
    GUILD(2, "The whole server");

    companion object {
        /** Maps a wire value onto a cooldown scope, defaulting to [USER]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: USER
    }
}

/** A bot event a trigger can respond to instead of a message. */
enum class ChatTriggerEventType(val raw: Int, val label: String) {
    NONE(0, "Not an event trigger"),
    XP_LEVEL_UP(1, "Member levels up"),
    XP_LEVEL_DOWN(2, "Member levels down"),
    MEMBER_JOIN(3, "Member joins the server"),
    MEMBER_LEAVE(4, "Member leaves the server"),
    VOICE_JOIN(5, "Member joins a voice channel"),
    VOICE_LEAVE(6, "Member leaves a voice channel"),
    BOOST(7, "Member starts boosting"),
    BOOST_END(8, "Member stops boosting"),
    TICKET_OPENED(9, "Ticket opened"),
    TICKET_CLOSED(10, "Ticket closed"),
    GIVEAWAY_WON(11, "Giveaway won");

    companion object {
        /** Maps a wire value onto an event type, defaulting to [NONE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** Who receives the roles a trigger grants or removes. */
enum class ChatTriggerRoleGrantType(val raw: Int, val label: String) {
    SENDER(0, "Sender"),
    MENTIONED(1, "Mentioned"),
    BOTH(2, "Both");

    companion object {
        /** Maps a wire value onto a grant type, defaulting to [SENDER]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: SENDER
    }
}

/**
 * One of the ways a trigger's [ChatTriggerModel.validTriggerTypes] bitmask can fire.
 *
 * Values mirror ChatTriggerType in the bot. Event (32) is left out since it is set through
 * [ChatTriggerEventType] rather than a toggle of its own.
 */
enum class ChatTriggerFireType(val raw: Int, val label: String) {
    MESSAGE(1, "Message"),
    INTERACTION(2, "Slash or context command"),
    BUTTON(4, "Button press"),
    REACTIONS(8, "Reaction added"),
    REACTIONS_REMOVED(16, "Reaction removed"),
}

/** Whether a trigger also registers as a Discord application command. */
enum class ChatTriggerApplicationCommandType(val raw: Int, val label: String) {
    NONE(0, "Not a command"),
    SLASH(1, "Slash command"),
    MESSAGE(2, "Message context menu"),
    USER(3, "User context menu");

    companion object {
        /** Maps a wire value onto a command type, defaulting to [NONE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** A custom keyword reaction. */
@Serializable
data class ChatTriggerModel(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    val trigger: String = "",
    val response: String = "",
    @Serializable(with = SnowflakeSerializer::class) val useCount: String = "0",
    val isRegex: Boolean = false,
    val ownerOnly: Boolean = false,
    val prefixType: Int = 0,
    val customPrefix: String? = null,
    val autoDeleteTrigger: Boolean = false,
    val reactToTrigger: Boolean = false,
    val noRespond: Boolean = false,
    val dmResponse: Boolean = false,
    val containsAnywhere: Boolean = false,
    val allowTarget: Boolean = false,
    val reactions: String? = null,
    val grantedRoles: String? = null,
    val removedRoles: String? = null,
    val roleGrantType: Int = 0,
    val validTriggerTypes: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val applicationCommandId: Snowflake? = null,
    val applicationCommandName: String? = null,
    val applicationCommandDescription: String? = null,
    val applicationCommandType: Int = 0,
    val ephemeralResponse: Boolean = false,
    @Serializable(with = SnowflakeSerializer::class) val crosspostingChannelId: Snowflake? = null,
    val crosspostingWebhookUrl: String? = null,
    val isDisabled: Boolean = false,
    val additionalResponses: String? = null,
    val responseMode: Int = 0,
    val roundRobinIndex: Int = 0,
    val currencyCost: Long = 0,
    val currencyReward: Long = 0,
    val xpReward: Int = 0,
    val requiredXpLevel: Int = 0,
    val requirementFailMessage: String? = null,
    val timeConditions: String? = null,
    val expiresAt: String? = null,
    val maxUses: Int? = null,
    val minAccountAgeMinutes: Int = 0,
    val minServerMembershipMinutes: Int = 0,
    val eventType: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val eventChannelId: Snowflake? = null,
    val allowBots: Boolean = false,
    val nextTriggerId: Int? = null,
    val replyToTrigger: Boolean = false,
    val deleteResponseAfter: Int = 0,
    val cooldownSeconds: Int = 0,
    val cooldownScope: Int = 0,
    val counterName: String? = null,
    val counterMin: Long? = null,
    val counterMax: Long? = null,
    val category: String? = null,
) {
    /** How often this trigger has fired. */
    val uses: Long get() = useCount.toLongOrNull() ?: 0L

    /** The typed form of [prefixType]. */
    val prefix: ChatTriggerPrefixType get() = ChatTriggerPrefixType.from(prefixType)

    /** The typed form of [roleGrantType]. */
    val grantType: ChatTriggerRoleGrantType get() = ChatTriggerRoleGrantType.from(roleGrantType)

    /** The typed form of [responseMode]. */
    val responses: ChatTriggerResponseMode get() = ChatTriggerResponseMode.from(responseMode)

    /** The typed form of [cooldownScope]. */
    val cooldownAppliesTo: ChatTriggerCooldownScope get() = ChatTriggerCooldownScope.from(cooldownScope)

    /** The typed form of [eventType]. */
    val event: ChatTriggerEventType get() = ChatTriggerEventType.from(eventType)

    /** The typed form of [applicationCommandType]. */
    val commandType: ChatTriggerApplicationCommandType
        get() = ChatTriggerApplicationCommandType.from(applicationCommandType)

    /** Whether [validTriggerTypes] includes [type]. */
    fun hasFireType(type: ChatTriggerFireType): Boolean = (validTriggerTypes and type.raw) != 0

    /** A copy with [type] turned on or off in [validTriggerTypes]. */
    fun withFireType(type: ChatTriggerFireType, enabled: Boolean): ChatTriggerModel =
        copy(
            validTriggerTypes = if (enabled) validTriggerTypes or type.raw
            else validTriggerTypes and type.raw.inv(),
        )

    /** Extra responses beyond the primary one. */
    val extraResponses: List<String>
        get() = additionalResponses.orEmpty().split("@@@").map { it.trim() }.filter { it.isNotEmpty() }

    /** Whether the trigger is limited to an active window. */
    val hasActiveHours: Boolean get() = !timeConditions.isNullOrBlank()

    /**
     * The trigger's active window, read from the condition format shared with sticky messages.
     * Only the first condition is surfaced; any extras the bot holds are left untouched.
     */
    val activeWindow: ActiveWindow?
        get() = timeConditions?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching {
                MewdekoJson.decodeFromString(ListSerializer(ActiveWindow.serializer()), raw).firstOrNull()
            }.getOrNull()
        }

    /** Roles granted when the trigger fires. */
    val grantedRoleIds: List<Snowflake>
        get() = grantedRoles.orEmpty().split(' ', '@').map { it.trim() }.filter { it.isNotEmpty() }

    /** Roles removed when the trigger fires. */
    val removedRoleIds: List<Snowflake>
        get() = removedRoles.orEmpty().split(' ', '@').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /**
         * A trigger with every field at its default, scoped to [guildId].
         *
         * Defaults [validTriggerTypes] to [ChatTriggerFireType.MESSAGE], matching the dashboard's
         * default, so a freshly created trigger can already fire before the editor forces it on.
         */
        fun blank(guildId: Snowflake) =
            ChatTriggerModel(guildId = guildId, validTriggerTypes = ChatTriggerFireType.MESSAGE.raw)
    }
}

/**
 * One active window for a trigger, matching the bot's TimeCondition shape.
 *
 * Property names are capitalised to match what the bot serializes, since both this and the
 * sticky message feature read the same stored format.
 */
@Serializable
data class ActiveWindow(
    @SerialName("StartTime") val startTime: String? = null,
    @SerialName("EndTime") val endTime: String? = null,
    @SerialName("DaysOfWeek") val daysOfWeek: List<Int>? = null,
    @SerialName("Enabled") val enabled: Boolean = true,
    @SerialName("Name") val name: String? = null,
) {
    /** A short readable summary, such as "09:00 to 17:00 on Mon, Tue". */
    val summary: String
        get() {
            val days = daysOfWeek
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString(", ") { DAY_NAMES.getOrElse(it) { "?" } }
                ?: "every day"
            return "${startTime.orEmpty()} to ${endTime.orEmpty()} on $days"
        }

    companion object {
        /** Day labels indexed the way the bot stores them, with Sunday first. */
        val DAY_NAMES = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    }
}

/** A named counter that trigger responses read and update. */
@Serializable
data class TriggerCounter(
    val id: Int = 0,
    val name: String = "",
    val value: Long = 0,
)

/** The outcome of a trigger dry run. */
@Serializable
data class TriggerTestResult(
    val matched: Boolean = false,
    val blocker: String? = null,
    val wouldFire: Boolean = false,
)

/** One recorded trigger fire. */
@Serializable
data class TriggerFire(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val dateAdded: String? = null,
)

/** A trigger's fire history. */
@Serializable
data class TriggerStats(
    val total: Int = 0,
    val recent: List<TriggerFire> = emptyList(),
)

/** Request body for setting a counter's value. */
@Serializable
private data class CounterRequest(val name: String, val value: Long)

/** Request body for a bulk category pause or resume. */
@Serializable
private data class CategoryToggleRequest(val category: String, val disabled: Boolean)

/** Request body for a trigger dry run. */
@Serializable
private data class TriggerTestRequest(
    val sample: String,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
)

/** Chat triggers screen state. */
data class ChatTriggersState(
    val triggers: List<ChatTriggerModel> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val counters: List<TriggerCounter> = emptyList(),
    val placeholders: List<String> = emptyList(),
    val testResults: Map<Int, TriggerTestResult> = emptyMap(),
    val stats: Map<Int, TriggerStats> = emptyMap(),
) {
    /** Triggers matching the current search query and category filter. */
    val filtered: List<ChatTriggerModel>
        get() {
            val q = query.trim().lowercase()
            return triggers
                .filter {
                    when (category) {
                        null -> true
                        UNGROUPED -> it.category.isNullOrBlank()
                        else -> it.category.orEmpty() == category
                    }
                }
                .filter {
                    q.isEmpty() ||
                        it.trigger.lowercase().contains(q) ||
                        it.response.lowercase().contains(q)
                }
        }

    /** Every category in use, for the filter chips. */
    val categories: List<String>
        get() = triggers.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()
            .sortedBy(String::lowercase)

    /** Whether any trigger has no category, so the "Ungrouped" filter chip is worth showing. */
    val hasUngrouped: Boolean get() = triggers.any { it.category.isNullOrBlank() }

    /** Total fires across every trigger. */
    val totalUses: Long get() = triggers.sumOf { it.uses }

    /** How many triggers are currently paused. */
    val pausedCount: Int get() = triggers.count { it.isDisabled }

    companion object {
        /**
         * Sentinel [category] value selecting triggers with no category.
         *
         * Kept distinct from any real category name (which cannot contain a NUL byte) and from
         * `null`, which means "no filter" rather than "no category".
         */
        const val UNGROUPED = "\u0000__ungrouped__"
    }
}

/** Custom keyword reactions. */
@HiltViewModel
class ChatTriggersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ChatTriggersState())

    /** Observable screen state. */
    val state: StateFlow<ChatTriggersState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads triggers plus role and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val triggers = async {
                runCatching {
                    api.send(
                        Endpoint("api/ChatTriggers/$guildId"),
                        ListSerializer(ChatTriggerModel.serializer()),
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
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/textchannels/$guildId"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val counters = async {
                runCatching {
                    api.send(
                        Endpoint("api/ChatTriggers/$guildId/counters"),
                        ListSerializer(TriggerCounter.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val placeholders = async {
                runCatching {
                    api.send(
                        Endpoint("api/ChatTriggers/$guildId/placeholders"),
                        ListSerializer(String.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    triggers = triggers.await().sortedBy { entry -> entry.trigger.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    counters = counters.await(),
                    placeholders = placeholders.await(),
                )
            }
        }
    }

    /** Updates the search query. */
    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    /** Filters the list to one category, or clears the filter when given null. */
    fun setCategory(value: String?) = _state.update { it.copy(category = value) }

    /** Pauses or resumes a trigger without deleting it. */
    fun setPaused(trigger: ChatTriggerModel, paused: Boolean) =
        update(trigger.copy(isDisabled = paused))

    /** Pauses or resumes every trigger in a category at once. */
    fun setCategoryPaused(category: String, paused: Boolean) =
        launchAction("Failed to update category.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/ChatTriggers/$guildId/category/toggle",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(CategoryToggleRequest(category, paused)),
                )
            )
            _state.update { current ->
                current.copy(
                    triggers = current.triggers.map {
                        if (it.category == category) it.copy(isDisabled = paused) else it
                    },
                )
            }
            postSuccess(if (paused) "Category paused." else "Category resumed.")
        }

    /** Sets a counter to an exact value, creating it when it does not exist. */
    fun setCounter(name: String, value: Long) = launchAction("Failed to save counter.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/ChatTriggers/$guildId/counters",
                HttpMethod.POST,
                MewdekoJson.encodeToString(CounterRequest(name.lowercase(), value)),
            )
        )
        loadCounters()
    }

    /** Deletes a counter along with every per-user value stored under its name. */
    fun deleteCounter(name: String) = launchAction("Failed to delete counter.") {
        api.sendIgnoringBody(
            Endpoint("api/ChatTriggers/$guildId/counters/${Uri.encode(name)}", HttpMethod.DELETE)
        )
        loadCounters()
    }

    /** Reports whether a sample message would fire a trigger, and what blocks it if not. */
    fun testTrigger(trigger: ChatTriggerModel, sample: String) =
        launchAction("Failed to test trigger.") {
            val result = api.send(
                Endpoint(
                    "api/ChatTriggers/$guildId/${trigger.id}/test",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(
                        TriggerTestRequest(sample, userId)
                    ),
                ),
                TriggerTestResult.serializer(),
            )
            _state.update { it.copy(testResults = it.testResults + (trigger.id to result)) }
        }

    /** Loads a trigger's fire history. */
    fun loadStats(trigger: ChatTriggerModel) = launchAction("Failed to load activity.") {
        val stats = api.send(
            Endpoint("api/ChatTriggers/$guildId/${trigger.id}/stats"),
            TriggerStats.serializer(),
        )
        _state.update { it.copy(stats = it.stats + (trigger.id to stats)) }
    }

    /** Reloads the guild's counters. */
    private suspend fun loadCounters() {
        val counters = runCatching {
            api.send(
                Endpoint("api/ChatTriggers/$guildId/counters"),
                ListSerializer(TriggerCounter.serializer()),
            )
        }.getOrDefault(emptyList())

        _state.update { it.copy(counters = counters) }
    }

    /** Creates a trigger. */
    fun add(trigger: ChatTriggerModel) = launchAction("Failed to add trigger.") {
        val added = api.send(
            Endpoint(
                "api/ChatTriggers/$guildId",
                HttpMethod.POST,
                MewdekoJson.encodeToString(trigger.copy(guildId = guildId)),
            ),
            ChatTriggerModel.serializer(),
        )
        _state.update {
            it.copy(
                triggers = (it.triggers + added).sortedBy { entry -> entry.trigger.lowercase() },
            )
        }
        postSuccess("Trigger added.")
    }

    /** Saves an edited trigger. */
    fun update(trigger: ChatTriggerModel) = launchAction("Failed to update trigger.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/ChatTriggers/$guildId",
                HttpMethod.PATCH,
                MewdekoJson.encodeToString(trigger.copy(guildId = guildId)),
            )
        )
        _state.update { current ->
            current.copy(
                triggers = current.triggers.map { if (it.id == trigger.id) trigger else it },
            )
        }
        postSuccess("Trigger saved.")
    }

    /** Deletes a trigger. */
    fun remove(id: Int) = launchAction("Failed to remove trigger.") {
        api.sendIgnoringBody(
            Endpoint("api/ChatTriggers/$guildId/$id", HttpMethod.DELETE)
        )
        _state.update { it.copy(triggers = it.triggers.filterNot { entry -> entry.id == id }) }
        postSuccess("Trigger removed.")
    }
}
