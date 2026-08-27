package dev.mewdeko.mobile.feature.statchannels

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extra configuration a stat type needs before it can resolve.
 * Mirrors StatChannelRequirement on the bot.
 */
enum class StatRequirement(val raw: Int) {
    NONE(0),
    ROLE(1),
    DATE(2),
    GOAL(3),
    COUNTER_NAME(4),
    COUNTING_CHANNEL(5),
    MINECRAFT_SERVER(6);

    companion object {
        /** Maps a wire value onto a requirement, defaulting to [NONE]. */
        fun from(raw: Int): StatRequirement = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/**
 * How a stat channel pushes updates to Discord. Mirrors StatChannelUpdateMechanism on the bot.
 *
 * Discord permits two channel name edits per ten minutes per channel, which is why [RENAME] cannot
 * refresh faster than every five minutes while [RECREATE] uses the far more permissive guild bucket.
 */
enum class StatMechanism(val raw: Int, val label: String, val blurb: String, val caution: String?) {
    RENAME(
        0,
        "Rename only",
        "Edits the existing channel name. The channel keeps its ID, position and permissions.",
        "Discord allows two name edits per 10 minutes per channel, so this cannot refresh faster " +
            "than every 5 minutes.",
    ),
    RECREATE(
        1,
        "Delete and recreate",
        "Recreates the channel each update using the far more permissive guild channel bucket, so " +
            "it can refresh every minute.",
        "The channel ID changes on every update and each refresh writes two audit log entries.",
    ),
    AUTO(
        2,
        "Auto",
        "Renames while the per-channel budget allows it, and only falls back to recreating when " +
            "you have asked for a faster refresh than a rename can deliver.",
        null,
    );

    companion object {
        /** Maps a wire value onto a mechanism, defaulting to [AUTO]. */
        fun from(raw: Int): StatMechanism = entries.firstOrNull { it.raw == raw } ?: AUTO
    }
}

/** A stat type as described by the bot, including a worked example of how it renders. */
@Serializable
data class StatTypeDefinition(
    val type: Int = 0,
    val name: String = "",
    val category: String = "",
    val description: String = "",
    val defaultTemplate: String = "%count%",
    val placeholders: List<String> = emptyList(),
    val valueKind: Int = 0,
    val valueKindName: String = "Number",
    val requirement: Int = 0,
    val requirementName: String = "None",
    val example: String = "",
    val recommendedStyle: Int = 1,
    val realtime: Boolean = false,
) {
    /** The typed form of [requirement]. */
    val needs: StatRequirement get() = StatRequirement.from(requirement)

    /** An icon that suits this stat's category. */
    val icon: ImageVector
        get() = when (category) {
            "Members", "AFK" -> Icons.Default.Groups
            "Server" -> Icons.Default.Tag
            "Twitch" -> Icons.Default.Videocam
            "Counting", "XP", "Currency" -> Icons.Default.Analytics
            else -> Icons.Default.Equalizer
        }
}

/** A counter style with an example of how it renders 1,234 against a target of 2,000. */
@Serializable
data class StatStyleDefinition(
    val style: Int = 0,
    val name: String = "",
    val example: String = "",
)

/** An update mechanism and the shortest refresh interval it can sustain. */
@Serializable
data class StatMechanismDefinition(
    val mechanism: Int = 0,
    val name: String = "",
    val minimumIntervalMinutes: Int = 1,
)

/** The catalogue of stat types, styles and mechanisms the bot supports. */
@Serializable
data class StatChannelMetadata(
    val commonPlaceholders: List<String> = emptyList(),
    val statTypes: List<StatTypeDefinition> = emptyList(),
    val displayStyles: List<StatStyleDefinition> = emptyList(),
    val mechanisms: List<StatMechanismDefinition> = emptyList(),
)

/** A counting channel a stat channel can be pointed at. */
@Serializable
data class CountingChannelLite(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "0",
    val channelName: String? = null,
    val currentNumber: Long = 0,
)

/** A watched Minecraft server a stat channel can be pointed at. */
@Serializable
data class MinecraftServerLite(
    val id: Int = 0,
    val name: String = "",
    val address: String = "",
    val isDefault: Boolean = false,
)

/** The rendered result of a template, used for live previews. */
@Serializable
data class StatChannelPreview(val rendered: String = "")

/** The guild wide defaults applied to newly created stat channels. */
@Serializable
data class StatChannelSettings(
    val defaultMechanism: Int = 2,
    val defaultIntervalMinutes: Int = 5,
    val defaultDisplayStyle: Int = 1,
)

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
    val displayStyle: Int = 1,
    val styleName: String? = null,
    val updateMechanism: Int = 2,
    val mechanismName: String? = null,
    val updateIntervalMinutes: Int = 5,
    @Serializable(with = SnowflakeSerializer::class) val targetId: Snowflake? = null,
    val targetName: String? = null,
    @Serializable(with = InstantSerializer::class) val lastUpdateAt: Instant? = null,
    val currentValue: String? = null,
    @Serializable(with = InstantSerializer::class) val dateAdded: Instant? = null,
) {
    /** The typed form of [updateMechanism]. */
    val mechanism: StatMechanism get() = StatMechanism.from(updateMechanism)

    /** An icon suiting this channel's stat, resolved from the catalogue when available. */
    fun icon(metadata: StatChannelMetadata?): ImageVector =
        metadata?.statTypes?.firstOrNull { it.type == statType }?.icon
            ?: when (statType) {
                4 -> Icons.Default.Person
                6 -> Icons.AutoMirrored.Filled.Label
                10 -> Icons.Default.AccessTime
                11 -> Icons.Default.Flag
                3 -> Icons.Default.Circle
                else -> Icons.Default.Equalizer
            }
}

/** Stat channels screen state. */
data class StatChannelsState(
    val channels: List<StatChannel> = emptyList(),
    val availableVoiceChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val countingChannels: List<CountingChannelLite> = emptyList(),
    val minecraftServers: List<MinecraftServerLite> = emptyList(),
    val metadata: StatChannelMetadata? = null,
    val settings: StatChannelSettings = StatChannelSettings(),
    val preview: String = "",
    val previewPending: Boolean = false,
)

/** Voice channels whose names carry live server statistics. */
@HiltViewModel
class StatChannelsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(StatChannelsState())
    private var previewJob: Job? = null

    /** Observable screen state. */
    val state: StateFlow<StatChannelsState> = _state.asStateFlow()

    init {
        load()
    }

    /**
     * Reloads the stat channels plus the catalogue and picker options. The stat type catalogue comes
     * from the bot rather than a local list so new counters appear without an app update, and so the
     * wire values can never drift out of sync with the bot's enum.
     */
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
            val metadata = async {
                runCatching {
                    api.send(
                        Endpoint("api/StatChannel/$guildId/metadata"),
                        StatChannelMetadata.serializer(),
                    )
                }.getOrNull()
            }
            val settings = async {
                runCatching {
                    api.send(
                        Endpoint("api/StatChannel/$guildId/settings"),
                        StatChannelSettings.serializer(),
                    )
                }.getOrDefault(StatChannelSettings())
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
            val counting = async {
                runCatching {
                    api.send(
                        Endpoint("api/Counting/$guildId/channels"),
                        ListSerializer(CountingChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val minecraft = async {
                runCatching {
                    api.send(
                        Endpoint("api/Minecraft/$guildId/servers"),
                        ListSerializer(MinecraftServerLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    channels = channels.await(),
                    metadata = metadata.await(),
                    settings = settings.await(),
                    countingChannels = counting.await(),
                    minecraftServers = minecraft.await(),
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
        statType: Int,
        template: String,
        displayStyle: Int,
        mechanism: StatMechanism,
        intervalMinutes: Int,
        roleId: Snowflake?,
        countdownDate: Instant?,
        goalTarget: Int?,
        targetId: Long?,
        targetName: String?,
    ) = launchAction("Failed to add stat channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/StatChannel/$guildId",
                HttpMethod.POST,
                jsonBody(
                    "channelId" to (channelId.toLongOrNull() ?: 0L),
                    "statType" to statType,
                    "template" to template,
                    "displayStyle" to displayStyle,
                    "updateMechanism" to mechanism.raw,
                    "updateIntervalMinutes" to intervalMinutes,
                    "roleId" to roleId?.toLongOrNull(),
                    "countdownDate" to countdownDate?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                    "goalTarget" to goalTarget,
                    "targetId" to targetId,
                    "targetName" to targetName?.takeIf { it.isNotBlank() },
                ),
            )
        )
        postSuccess("Stat channel added.")
        load(refreshing = true)
    }

    /**
     * Renders the draft template against live guild data, debounced so typing does not spam the API.
     * The bot does the rendering so the preview matches exactly what the channel name will become.
     */
    fun refreshPreview(
        statType: Int,
        template: String,
        displayStyle: Int,
        roleId: Snowflake?,
        countdownDate: Instant?,
        goalTarget: Int?,
        targetId: Long?,
        targetName: String?,
    ) {
        previewJob?.cancel()
        if (template.isBlank()) {
            _state.update { it.copy(preview = "", previewPending = false) }
            return
        }

        previewJob = viewModelScope.launch {
            delay(PreviewDebounce)
            _state.update { it.copy(previewPending = true) }
            val rendered = runCatching {
                api.send(
                    Endpoint(
                        "api/StatChannel/$guildId/preview",
                        HttpMethod.POST,
                        jsonBody(
                            "statType" to statType,
                            "template" to template,
                            "displayStyle" to displayStyle,
                            "roleId" to roleId?.toLongOrNull(),
                            "countdownDate" to countdownDate?.let {
                                DateTimeFormatter.ISO_INSTANT.format(it)
                            },
                            "goalTarget" to goalTarget,
                            "targetId" to targetId,
                            "targetName" to targetName?.takeIf { it.isNotBlank() },
                        ),
                    ),
                    StatChannelPreview.serializer(),
                )
            }.getOrNull()

            _state.update {
                it.copy(preview = rendered?.rendered.orEmpty(), previewPending = false)
            }
        }
    }

    /** Drops any pending preview and clears the last rendered result. */
    fun clearPreview() {
        previewJob?.cancel()
        _state.update { it.copy(preview = "", previewPending = false) }
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

    /** Changes how a stat channel renders its number and pushes updates to Discord. */
    fun updateDelivery(
        channelId: Snowflake,
        displayStyle: Int,
        mechanism: StatMechanism,
        intervalMinutes: Int,
    ) = launchAction("Failed to update stat channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/StatChannel/$guildId/$channelId",
                HttpMethod.PUT,
                jsonBody(
                    "displayStyle" to displayStyle,
                    "updateMechanism" to mechanism.raw,
                    "updateIntervalMinutes" to intervalMinutes,
                ),
            )
        )
        postSuccess("Stat channel updated.")
        load(refreshing = true)
    }

    /** Changes the defaults applied to newly created stat channels. */
    fun updateSettings(settings: StatChannelSettings) =
        launchAction("Failed to save defaults.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/StatChannel/$guildId/settings",
                    HttpMethod.PUT,
                    jsonBody(
                        "defaultMechanism" to settings.defaultMechanism,
                        "defaultIntervalMinutes" to settings.defaultIntervalMinutes,
                        "defaultDisplayStyle" to settings.defaultDisplayStyle,
                    ),
                )
            )
            _state.update { it.copy(settings = settings) }
            postSuccess("Defaults saved.")
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

    /** Clamps a requested interval to what the chosen mechanism can sustain. */
    fun minimumInterval(mechanism: StatMechanism): Int =
        _state.value.metadata?.mechanisms
            ?.firstOrNull { it.mechanism == mechanism.raw }
            ?.minimumIntervalMinutes
            ?: if (mechanism == StatMechanism.RENAME) 5 else 1

    private companion object {
        val PreviewDebounce = 350.milliseconds
    }
}
