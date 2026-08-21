package dev.mewdeko.mobile.feature.feeds

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
import java.time.Instant
import javax.inject.Inject

/** One RSS or social feed the bot mirrors into a channel. */
@Serializable
data class FeedSubscription(
    val index: Int = 0,
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val url: String = "",
    val message: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
    val channelName: String? = null,
)

/** Feed counters returned by the stats endpoint. */
@Serializable
data class FeedStats(val totalFeeds: Int = 0)

/** Feeds screen state. */
data class FeedsState(
    val feeds: List<FeedSubscription> = emptyList(),
    val stats: FeedStats? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
) {
    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name ?: id
}

/** RSS and social feed subscriptions for a guild. */
@HiltViewModel
class FeedsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(FeedsState())

    /** Observable screen state. */
    val state: StateFlow<FeedsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the subscription list, stats, and channel options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val feeds = async {
                runCatching {
                    api.send(
                        Endpoint("api/Feeds/$guildId"),
                        ListSerializer(FeedSubscription.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val stats = async {
                runCatching {
                    api.send(Endpoint("api/Feeds/$guildId/stats"), FeedStats.serializer())
                }.getOrNull()
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
                    feeds = feeds.await(),
                    stats = stats.await(),
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Subscribes a channel to a feed URL. */
    fun add(channelId: Snowflake, url: String) = launchAction("Failed to add feed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Feeds/$guildId",
                HttpMethod.POST,
                jsonBody("channelId" to (channelId.toLongOrNull() ?: 0L), "url" to url),
            )
        )
        postSuccess("Feed added.")
        load(refreshing = true)
    }

    /** Removes a feed subscription. */
    fun remove(feed: FeedSubscription) = launchAction("Failed to remove feed.") {
        api.sendIgnoringBody(
            Endpoint("api/Feeds/$guildId/${feed.index}", HttpMethod.DELETE)
        )
        _state.update { it.copy(feeds = it.feeds.filterNot { entry -> entry.index == feed.index }) }
        postSuccess("Feed removed.")
    }

    /** Sets the announcement template for one feed. */
    fun setMessage(feed: FeedSubscription, message: EmbedMessage) =
        launchAction("Failed to update message.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Feeds/$guildId/${feed.index}/message",
                    HttpMethod.PUT,
                    jsonString(message.serialize()),
                )
            )
            postSuccess("Message updated.")
            load(refreshing = true)
        }
}
