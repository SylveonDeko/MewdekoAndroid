package dev.mewdeko.mobile.feature.moderation

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.model.WarningRecord
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
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

/** One rung of the automatic warning-punishment ladder. */
@Serializable
data class WarningPunishment(
    val id: Int = 0,
    val count: Int = 0,
    val punishment: Int = 0,
    val time: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
) {
    /** Human-readable name for the punishment code the bot stores. */
    val actionLabel: String
        get() = when (punishment) {
            1 -> "Mute"
            2 -> "Kick"
            3 -> "Ban"
            4 -> "Soft-ban"
            5 -> "Add role"
            6 -> "Voice mute"
            7 -> "Chat mute"
            8 -> "Timeout"
            9 -> "Warn"
            10 -> "Remove roles"
            else -> "Action #$punishment"
        }
}

/** The channel warnings are logged to, if configured. */
@Serializable
data class WarnLogChannelResponse(
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake? = null,
)

/** Moderation screen state. */
data class ModerationState(
    val warnings: List<WarningRecord> = emptyList(),
    val punishments: List<WarningPunishment> = emptyList(),
    val warnLogChannel: Snowflake? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val section: String = "overview",
    val filterText: String = "",
    val activeOnly: Boolean = false,
) {
    /** Warnings still counting against their member. */
    val activeCount: Int get() = warnings.count { !it.forgiven }

    /** Warnings that have been forgiven. */
    val forgivenCount: Int get() = warnings.count { it.forgiven }

    /** The warn-log channel's name, falling back to its raw id. */
    val warnLogChannelName: String?
        get() = warnLogChannel?.let { id ->
            availableChannels.firstOrNull { it.id == id }?.name ?: id
        }

    /** Warnings matching the current filter and active-only toggle. */
    val filteredWarnings: List<WarningRecord>
        get() {
            val query = filterText.trim().lowercase()
            return warnings
                .filter { !activeOnly || !it.forgiven }
                .filter { warning ->
                    query.isEmpty() ||
                        warning.userId?.contains(query) == true ||
                        warning.reason.orEmpty().lowercase().contains(query)
                }
        }
}

/** Loads the guild's warnings, punishment ladder, and warn-log destination. */
@HiltViewModel
class ModerationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ModerationState())

    /** Observable screen state. */
    val state: StateFlow<ModerationState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads warnings, punishments, and channel metadata. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val warnings = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/warnings"),
                        ListSerializer(WarningRecord.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val punishments = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/punishments"),
                        ListSerializer(WarningPunishment.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val logChannel = async {
                runCatching {
                    api.send(
                        Endpoint("api/Moderation/$guildId/warnlog-channel"),
                        WarnLogChannelResponse.serializer(),
                    )
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
                    warnings = warnings.await()
                        .sortedByDescending { warning -> warning.dateAdded ?: Instant.EPOCH },
                    punishments = punishments.await().sortedBy { punishment -> punishment.count },
                    warnLogChannel = logChannel.await()?.channelId
                        ?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Updates the warning search filter. */
    fun setFilter(text: String) = _state.update { it.copy(filterText = text) }

    /** Toggles hiding forgiven warnings. */
    fun setActiveOnly(value: Boolean) = _state.update { it.copy(activeOnly = value) }
}
