package dev.mewdeko.mobile.feature.birthday

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.GuildRole
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
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** Birthday screen state. */
data class BirthdayState(
    val channelId: Snowflake? = null,
    val roleId: Snowflake? = null,
    val pingRoleId: Snowflake? = null,
    val timezone: String = "UTC",
    val reminderDays: Int = 1,
    val message: EmbedMessage = EmbedMessage(),
    val enabledFeatures: Int = 0,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val allUsers: List<BirthdayUserDetail> = emptyList(),
    val todays: List<BirthdayUserDetail> = emptyList(),
    val upcoming: List<BirthdayUserDetail> = emptyList(),
    val upcomingDays: Int = 7,
    val stats: BirthdayStats? = null,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
)

/** Loads and edits the guild's birthday configuration and member records. */
@HiltViewModel
class BirthdayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(BirthdayState())

    /** Observable screen state. */
    val state: StateFlow<BirthdayState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads the configuration, stats, and every member birthday record. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val days = _state.value.upcomingDays
        coroutineScope {
            val config = async {
                runCatching {
                    api.send(Endpoint("api/birthday/$guildId/config"), BirthdayConfig.serializer())
                }.getOrNull()
            }
            val users = async { users("users") }
            val today = async { users("today") }
            val upcoming = async { users("upcoming?days=$days") }
            val stats = async {
                runCatching {
                    api.send(Endpoint("api/birthday/$guildId/stats"), BirthdayStats.serializer())
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
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val cfg = config.await()
            _state.update {
                it.copy(
                    channelId = cfg?.birthdayChannelId?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    roleId = cfg?.birthdayRoleId?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    pingRoleId = cfg?.birthdayPingRoleId?.takeIf { id -> id.isNotEmpty() && id != "0" },
                    timezone = cfg?.defaultTimezone ?: "UTC",
                    reminderDays = cfg?.birthdayReminderDays ?: 1,
                    message = EmbedMessage.parse(cfg?.birthdayMessage),
                    enabledFeatures = cfg?.enabledFeatures ?: 0,
                    availableChannels = channels.await().sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await().sortedBy { role -> role.name.lowercase() },
                    allUsers = users.await(),
                    todays = today.await(),
                    upcoming = upcoming.await(),
                    stats = stats.await(),
                    hasUnsavedChanges = false,
                )
            }
        }
    }

    /** Sets the announcement channel. */
    fun setChannel(id: Snowflake?) = edit { it.copy(channelId = id) }

    /** Sets the temporary birthday role. */
    fun setRole(id: Snowflake?) = edit { it.copy(roleId = id) }

    /** Sets the role mentioned alongside announcements. */
    fun setPingRole(id: Snowflake?) = edit { it.copy(pingRoleId = id) }

    /** Sets the guild's default timezone. */
    fun setTimezone(value: String) = edit { it.copy(timezone = value) }

    /** Sets how many days ahead reminder DMs are sent. */
    fun setReminderDays(value: Int) = edit { it.copy(reminderDays = value) }

    /** Sets the announcement message template. */
    fun setMessage(value: EmbedMessage) = edit { it.copy(message = value) }

    /** Changes the upcoming-birthday window and reloads that list. */
    fun setUpcomingDays(days: Int) = viewModelScope.launch {
        _state.update { it.copy(upcomingDays = days) }
        val result = users("upcoming?days=$days")
        _state.update { it.copy(upcoming = result) }
    }

    /** Writes the pending configuration edits. */
    fun save() = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(isSaving = true) }
        val body = buildJsonObject {
            put("birthdayChannelId", current.channelId.asJson())
            put("birthdayRoleId", current.roleId.asJson())
            put("birthdayPingRoleId", current.pingRoleId.asJson())
            put("birthdayMessage", JsonPrimitive(current.message.serialize()))
            put("birthdayReminderDays", JsonPrimitive(current.reminderDays))
            put("defaultTimezone", JsonPrimitive(current.timezone))
        }
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint(
                    "api/birthday/$guildId/config",
                    HttpMethod.PUT,
                    MewdekoJson.encodeToString(JsonObject.serializer(), body),
                )
            )
        }.isSuccess
        _state.update { it.copy(isSaving = false, hasUnsavedChanges = !ok) }
        if (ok) postSuccess("Birthday settings saved.") else postError("Failed to save settings.")
    }

    /** Restores the bot's default birthday configuration. */
    fun reset() = viewModelScope.launch {
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("api/birthday/$guildId/config/reset", HttpMethod.POST)
            )
        }.isSuccess
        if (ok) {
            postSuccess("Birthday settings reset.")
            load(refreshing = true)
        } else {
            postError("Failed to reset settings.")
        }
    }

    /** Enables or disables one birthday subsystem. */
    fun toggleFeature(feature: BirthdayFeature) = viewModelScope.launch {
        val enabled = feature.isEnabled(_state.value.enabledFeatures)
        val action = if (enabled) "disable" else "enable"
        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint("api/birthday/$guildId/features/${feature.bit}/$action", HttpMethod.POST)
            )
        }.isSuccess
        if (!ok) {
            postError("Failed to ${action} ${feature.label}.")
            return@launch
        }
        _state.update {
            it.copy(
                enabledFeatures = if (enabled) it.enabledFeatures and feature.bit.inv()
                else it.enabledFeatures or feature.bit,
            )
        }
    }

    private suspend fun users(tail: String): List<BirthdayUserDetail> = runCatching {
        api.send(
            Endpoint("api/birthday/$guildId/$tail"),
            ListSerializer(BirthdayUserDetail.serializer()),
        )
    }.getOrDefault(emptyList())

    private fun edit(transform: (BirthdayState) -> BirthdayState) {
        _state.update { transform(it).copy(hasUnsavedChanges = true) }
    }

    private fun Snowflake?.asJson() =
        if (isNullOrEmpty()) JsonNull else JsonPrimitive(this)
}
