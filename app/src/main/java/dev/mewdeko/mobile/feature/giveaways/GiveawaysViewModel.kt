package dev.mewdeko.mobile.feature.giveaways

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
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** A prize draw stored by the bot. */
@Serializable
data class GiveawayRecord(
    val id: Int = 0,
    @Serializable(with = InstantSerializer::class) val `when`: Instant? = null,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val serverId: Snowflake? = null,
    val ended: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
    val winners: Int = 1,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val item: String? = null,
    val restrictTo: String? = null,
    val blacklistUsers: String? = null,
    val blacklistRoles: String? = null,
    val emote: String? = null,
    val useButton: Boolean = true,
    val useCaptcha: Boolean = false,
    val messageCountReq: Long = 0L,
) {
    /** Whether the draw has already been resolved. */
    val isEnded: Boolean get() = ended != 0

    /** The roles entry is restricted to, parsed from the space-delimited field. */
    val restrictedRoleIds: List<Snowflake>
        get() = restrictTo.orEmpty().split(' ').filter { it.isNotBlank() }
}

/** Giveaways screen state. */
data class GiveawaysState(
    val giveaways: List<GiveawayRecord> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val section: String = "active",
) {
    /** Draws still accepting entries. */
    val active: List<GiveawayRecord> get() = giveaways.filterNot { it.isEnded }

    /** Draws that have been resolved. */
    val ended: List<GiveawayRecord> get() = giveaways.filter { it.isEnded }

    /** Resolves a channel id to its name, falling back to the raw id. */
    fun channelName(id: Snowflake?): String =
        id?.let { raw -> availableChannels.firstOrNull { it.id == raw }?.name ?: raw } ?: "unknown"

    /** Resolves a role id to its name, falling back to the raw id. */
    fun roleName(id: Snowflake): String =
        availableRoles.firstOrNull { it.id == id }?.name ?: id
}

/** Prize draws for a guild. */
@HiltViewModel
class GiveawaysViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(GiveawaysState())

    /** Observable screen state. */
    val state: StateFlow<GiveawaysState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the draw list plus channel and role options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val giveaways = async {
                runCatching {
                    api.send(
                        Endpoint("api/Giveaways/guild/$guildId"),
                        ListSerializer(GiveawayRecord.serializer()),
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
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    giveaways = giveaways.await()
                        .sortedByDescending { entry -> entry.`when` ?: Instant.EPOCH },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Creates a new prize draw. */
    fun create(
        item: String,
        channelId: Snowflake,
        endsAt: Instant,
        winners: Int,
        useButton: Boolean,
        useCaptcha: Boolean,
        messageCountReq: Int,
        emote: String?,
        restrictRoles: List<Snowflake>,
    ) = launchAction("Failed to create giveaway.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Giveaways/$guildId",
                HttpMethod.POST,
                jsonBody(
                    "when" to DateTimeFormatter.ISO_INSTANT.format(endsAt),
                    "channelId" to (channelId.toLongOrNull() ?: 0L),
                    "serverId" to (guildId.toLongOrNull() ?: 0L),
                    "winners" to winners,
                    "userId" to (userId.toLongOrNull() ?: 0L),
                    "item" to item,
                    "useButton" to useButton,
                    "useCaptcha" to useCaptcha,
                    "messageCountReq" to messageCountReq.coerceAtLeast(0).toLong(),
                    "emote" to emote,
                    "restrictTo" to restrictRoles.takeIf { it.isNotEmpty() }?.joinToString(" "),
                ),
            )
        )
        postSuccess("Giveaway created.")
        load(refreshing = true)
    }

    /** Ends a running draw and picks its winners. */
    fun end(id: Int) = launchAction("Failed to end giveaway.") {
        api.sendIgnoringBody(Endpoint("api/Giveaways/$guildId/$id", HttpMethod.PATCH))
        postSuccess("Giveaway ended.")
        load(refreshing = true)
    }
}
