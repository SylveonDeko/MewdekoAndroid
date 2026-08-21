package dev.mewdeko.mobile.feature.starboard

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.StarboardHighlight
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.net.jsonInt
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** One configured starboard. */
@Serializable
data class StarboardConfig(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val starboardChannelId: Snowflake? = null,
    val emote: String = "⭐",
    val threshold: Int = 1,
    val checkedChannels: String? = null,
    val useBlacklist: Boolean = false,
    val allowBots: Boolean = false,
    val removeOnDelete: Boolean = false,
    val removeOnReactionsClear: Boolean = false,
    val removeOnBelowThreshold: Boolean = false,
    val repostThreshold: Int = 0,
) {
    /** The channels this starboard watches or ignores, per [useBlacklist]. */
    val checkedChannelIds: List<Snowflake>
        get() = checkedChannels.orEmpty()
            .split(' ', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** The reaction emoji that count toward this starboard. */
    val emotes: List<String> get() = emote.split(' ').filter { it.isNotBlank() }
}

/** The most-starred member. */
@Serializable
data class StarboardUserStats(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val totalStars: Int = 0,
    val messageCount: Int = 0,
)

/** The channel producing the most starred messages. */
@Serializable
data class StarboardChannelStats(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    val totalStars: Int = 0,
    val messageCount: Int = 0,
)

/** The member handing out the most stars. */
@Serializable
data class StarboardStarrerStats(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val starsGiven: Int = 0,
    val uniqueEmotesUsed: Int = 0,
)

/** Guild-wide starboard statistics. */
@Serializable
data class StarboardServerStats(
    val mostStarredUser: StarboardUserStats? = null,
    val mostActiveChannel: StarboardChannelStats? = null,
    val mostActiveStarrer: StarboardStarrerStats? = null,
    val totalStarredMessages: Int = 0,
    val totalStars: Int = 0,
)

/** Starboard screen state. */
data class StarboardState(
    val boards: List<StarboardConfig> = emptyList(),
    val stats: StarboardServerStats? = null,
    val highlights: List<StarboardHighlight> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val section: String = "boards",
) {
    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake?): String =
        id?.let { raw -> availableChannels.firstOrNull { it.id == raw }?.name ?: raw } ?: "unset"
}

/** Star-pinned message boards. */
@HiltViewModel
class StarboardViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(StarboardState())

    /** Observable screen state. */
    val state: StateFlow<StarboardState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads boards, stats, and highlights. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val boards = async {
                runCatching {
                    api.send(
                        Endpoint("api/Starboard/$guildId/all"),
                        ListSerializer(StarboardConfig.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val stats = async {
                runCatching {
                    api.send(
                        Endpoint("api/Starboard/$guildId/stats"),
                        StarboardServerStats.serializer(),
                    )
                }.getOrNull()
            }
            val highlights = async {
                runCatching {
                    api.send(
                        Endpoint("api/Starboard/$guildId/highlights"),
                        ListSerializer(StarboardHighlight.serializer()),
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
                    boards = boards.await(),
                    stats = stats.await(),
                    highlights = highlights.await().sortedByDescending { entry -> entry.starCount },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Creates a starboard. */
    fun create(channelId: Snowflake, emote: String, threshold: Int) =
        launchAction("Failed to create starboard.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Starboard/$guildId",
                    HttpMethod.POST,
                    jsonBody(
                        "channelId" to (channelId.toLongOrNull() ?: 0L),
                        "emote" to emote,
                        "threshold" to threshold,
                    ),
                )
            )
            postSuccess("Starboard created.")
            load(refreshing = true)
        }

    /** Deletes a starboard. */
    fun delete(boardId: Int) = launchAction("Failed to delete starboard.") {
        api.sendIgnoringBody(
            Endpoint("api/Starboard/$guildId/$boardId", HttpMethod.DELETE)
        )
        _state.update { it.copy(boards = it.boards.filterNot { board -> board.id == boardId }) }
        postSuccess("Starboard deleted.")
    }

    /** Sets whether bot messages can be starred. */
    fun setAllowBots(boardId: Int, value: Boolean) =
        setting(boardId, "allow-bots", jsonBool(value)) { it.copy(allowBots = value) }

    /** Sets whether the starboard post is removed when the original is deleted. */
    fun setRemoveOnDelete(boardId: Int, value: Boolean) =
        setting(boardId, "remove-on-delete", jsonBool(value)) { it.copy(removeOnDelete = value) }

    /** Sets whether the post is removed when reactions are cleared. */
    fun setRemoveOnClear(boardId: Int, value: Boolean) =
        setting(boardId, "remove-on-clear", jsonBool(value)) {
            it.copy(removeOnReactionsClear = value)
        }

    /** Sets whether the post is removed once stars fall below the threshold. */
    fun setRemoveBelowThreshold(boardId: Int, value: Boolean) =
        setting(boardId, "remove-below-threshold", jsonBool(value)) {
            it.copy(removeOnBelowThreshold = value)
        }

    /** Switches the channel list between allow-list and block-list behaviour. */
    fun setUseBlacklist(boardId: Int, value: Boolean) =
        setting(boardId, "use-blacklist", jsonBool(value)) { it.copy(useBlacklist = value) }

    /** Sets how many stars a message needs to be posted. */
    fun setThreshold(boardId: Int, value: Int) =
        setting(boardId, "star-threshold", jsonInt(value)) { it.copy(threshold = value) }

    /** Sets how far down the channel a post may drift before being reposted. */
    fun setRepostThreshold(boardId: Int, value: Int) =
        setting(boardId, "repost-threshold", jsonInt(value)) { it.copy(repostThreshold = value) }

    /** Adds or removes a channel from the board's watch list. */
    fun toggleChannel(boardId: Int, channelId: Snowflake) =
        launchAction("Failed to update channels.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Starboard/$guildId/$boardId/toggle-channel",
                    HttpMethod.POST,
                    (channelId.toLongOrNull() ?: 0L).toString(),
                )
            )
            load(refreshing = true)
        }

    /** Adds a reaction emoji that counts toward the board. */
    fun addEmote(boardId: Int, emote: String) = launchAction("Failed to add emote.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Starboard/$guildId/$boardId/add-emote",
                HttpMethod.POST,
                jsonString(emote),
            )
        )
        load(refreshing = true)
    }

    /** Removes a reaction emoji from the board. */
    fun removeEmote(boardId: Int, emote: String) = launchAction("Failed to remove emote.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Starboard/$guildId/$boardId/remove-emote",
                HttpMethod.POST,
                jsonString(emote),
            )
        )
        load(refreshing = true)
    }

    private fun setting(
        boardId: Int,
        tail: String,
        body: String,
        transform: (StarboardConfig) -> StarboardConfig,
    ) = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint("api/Starboard/$guildId/$boardId/$tail", HttpMethod.POST, body)
        )
        _state.update { current ->
            current.copy(
                boards = current.boards.map { if (it.id == boardId) transform(it) else it },
            )
        }
    }
}
