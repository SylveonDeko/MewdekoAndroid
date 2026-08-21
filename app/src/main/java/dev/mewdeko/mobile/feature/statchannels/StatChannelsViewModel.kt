package dev.mewdeko.mobile.feature.statchannels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.ui.graphics.vector.ImageVector
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

/** What a stat channel's name displays. */
enum class StatChannelType(val raw: Int, val label: String, val icon: ImageVector) {
    TOTAL_MEMBERS(0, "Total Members", Icons.Default.Groups),
    HUMAN_MEMBERS(1, "Human Members", Icons.Default.Groups),
    BOT_MEMBERS(2, "Bot Members", Icons.Default.Groups),
    ONLINE_MEMBERS(3, "Online Members", Icons.Default.Circle),
    OFFLINE_MEMBERS(4, "Offline Members", Icons.Default.Circle),
    IDLE_MEMBERS(5, "Idle Members", Icons.Default.Circle),
    DND_MEMBERS(6, "DND Members", Icons.Default.Circle),
    CHANNEL_COUNT(7, "Channel Count", Icons.Default.Tag),
    ROLE_COUNT(8, "Role Count", Icons.Default.Label),
    ROLE_MEMBERS(9, "Role Member Count", Icons.Default.Person),
    COUNTDOWN(10, "Countdown", Icons.Default.AccessTime),
    GOAL(11, "Member Goal", Icons.Default.Flag);

    /** Whether this type needs a role to count against. */
    val requiresRole: Boolean get() = this == ROLE_MEMBERS

    /** Whether this type needs a target date. */
    val requiresCountdown: Boolean get() = this == COUNTDOWN

    /** Whether this type needs a numeric target. */
    val requiresGoal: Boolean get() = this == GOAL

    companion object {
        /** Maps a wire value onto a type, defaulting to [TOTAL_MEMBERS]. */
        fun from(raw: Int): StatChannelType = entries.firstOrNull { it.raw == raw } ?: TOTAL_MEMBERS
    }
}

/** A voice channel whose name the bot keeps updated with a live statistic. */
@Serializable
data class StatChannel(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val channelName: String = "Unknown",
    val statType: Int = 0,
    val typeName: String? = null,
    val template: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
    val roleName: String? = null,
    @Serializable(with = InstantSerializer::class) val countdownDate: Instant? = null,
    val goalTarget: Int? = null,
    @Serializable(with = SnowflakeSerializer::class) val currentValue: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
) {
    /** The typed form of [statType]. */
    val type: StatChannelType get() = StatChannelType.from(statType)
}

/** Stat channels screen state. */
data class StatChannelsState(
    val channels: List<StatChannel> = emptyList(),
    val availableVoiceChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
)

/** Voice channels whose names carry live server statistics. */
@HiltViewModel
class StatChannelsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(StatChannelsState())

    /** Observable screen state. */
    val state: StateFlow<StatChannelsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the stat channels plus voice channel and role options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val channels = async {
                runCatching {
                    api.send(
                        Endpoint("api/StatChannel/$guildId"),
                        ListSerializer(StatChannel.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val voice = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/channels/$guildId/2"),
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
                    channels = channels.await(),
                    availableVoiceChannels = voice.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Turns a voice channel into a live stat display. */
    fun add(
        channelId: Snowflake,
        type: StatChannelType,
        template: String,
        roleId: Snowflake?,
        countdownDate: Instant?,
        goalTarget: Int,
    ) = launchAction("Failed to add stat channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/StatChannel/$guildId",
                HttpMethod.POST,
                jsonBody(
                    "channelId" to (channelId.toLongOrNull() ?: 0L),
                    "statType" to type.raw,
                    "template" to template,
                    "roleId" to roleId?.toLongOrNull(),
                    "countdownDate" to countdownDate?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                    "goalTarget" to goalTarget,
                ),
            )
        )
        postSuccess("Stat channel added.")
        load(refreshing = true)
    }

    /** Changes the name template a stat channel renders. */
    fun updateTemplate(channelId: Snowflake, template: String) =
        launchAction("Failed to update template.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/StatChannel/$guildId/$channelId",
                    HttpMethod.PUT,
                    jsonBody("template" to template),
                )
            )
            _state.update { current ->
                current.copy(
                    channels = current.channels.map {
                        if (it.channelId == channelId) it.copy(template = template) else it
                    },
                )
            }
            postSuccess("Template updated.")
        }

    /** Stops updating a channel's name. */
    fun remove(channelId: Snowflake) = launchAction("Failed to remove stat channel.") {
        api.sendIgnoringBody(
            Endpoint("api/StatChannel/$guildId/$channelId", HttpMethod.DELETE)
        )
        _state.update {
            it.copy(channels = it.channels.filterNot { entry -> entry.channelId == channelId })
        }
        postSuccess("Stat channel removed.")
    }
}
