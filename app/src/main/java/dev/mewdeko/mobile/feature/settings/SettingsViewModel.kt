package dev.mewdeko.mobile.feature.settings

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject

/** Guild settings screen state. */
data class SettingsState(
    val prefix: String = ".",
    val commandLogChannelId: Snowflake? = null,
    val staffRoleId: Snowflake? = null,
    val deleteOnCommand: Boolean = false,
    val currencyEmoji: String = "",
    val afkMessage: EmbedMessage = EmbedMessage(),
    val streamMessage: EmbedMessage = EmbedMessage(),
    val warningLogChannelId: Snowflake? = null,
    val warnExpireHours: Int = 0,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val hasUnsaved: Boolean = false,
    val isSaving: Boolean = false,
)

/**
 * Per-guild bot configuration.
 *
 * The bot's `GuildConfig` record has far more fields than this screen edits, so
 * the raw JSON object is kept verbatim and only the edited keys are merged back
 * on save. That fetch deliberately bypasses key normalisation, since the bot
 * round-trips these keys in PascalCase.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(SettingsState())

    /** Observable screen state. */
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var loadedConfig: JsonObject = JsonObject(emptyMap())

    init {
        load()
    }

    /** Reloads the guild config plus channel and role options. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val config = async {
                runCatching {
                    api.sendRaw(Endpoint("api/GuildConfig/$guildId")) as? JsonObject
                }.getOrNull() ?: JsonObject(emptyMap())
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

            loadedConfig = config.await()
            _state.update {
                it.copy(
                    prefix = loadedConfig.stringAt("Prefix") ?: ".",
                    commandLogChannelId = loadedConfig.snowflakeAt("CommandLogChannel"),
                    staffRoleId = loadedConfig.snowflakeAt("StaffRole"),
                    deleteOnCommand = loadedConfig.boolAt("DeleteMessageOnCommand"),
                    currencyEmoji = loadedConfig.stringAt("CurrencyEmoji").orEmpty(),
                    afkMessage = EmbedMessage.parse(loadedConfig.stringAt("AfkMessage")),
                    streamMessage = EmbedMessage.parse(loadedConfig.stringAt("StreamMessage")),
                    warningLogChannelId = loadedConfig.snowflakeAt("WarnlogChannelId"),
                    warnExpireHours = loadedConfig.intAt("WarnExpireHours") ?: 0,
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    hasUnsaved = false,
                )
            }
        }
    }

    /** Sets the guild's command prefix. */
    fun setPrefix(value: String) = edit { it.copy(prefix = value) }

    /** Sets the emoji used for the guild's currency. */
    fun setCurrencyEmoji(value: String) = edit { it.copy(currencyEmoji = value) }

    /** Sets whether the invoking message is deleted after a command runs. */
    fun setDeleteOnCommand(value: Boolean) = edit { it.copy(deleteOnCommand = value) }

    /** Sets the channel command usage is logged to. */
    fun setCommandLogChannel(id: Snowflake?) = edit { it.copy(commandLogChannelId = id) }

    /** Sets the role treated as staff. */
    fun setStaffRole(id: Snowflake?) = edit { it.copy(staffRoleId = id) }

    /** Sets the channel warnings are logged to. */
    fun setWarningLogChannel(id: Snowflake?) = edit { it.copy(warningLogChannelId = id) }

    /** Sets how long a warning stays active, in hours; zero means never. */
    fun setWarnExpireHours(value: Int) = edit { it.copy(warnExpireHours = value) }

    /** Sets the guild's default AFK message template. */
    fun setAfkMessage(value: EmbedMessage) = edit { it.copy(afkMessage = value) }

    /** Sets the stream notification template. */
    fun setStreamMessage(value: EmbedMessage) = edit { it.copy(streamMessage = value) }

    /** Merges the edited keys into the stored config and posts it back. */
    fun save() = viewModelScope.launch {
        val current = _state.value
        _state.update { it.copy(isSaving = true) }

        val merged = buildJsonObject {
            loadedConfig.forEach { (key, value) -> put(key, value) }
            put("Prefix", JsonPrimitive(current.prefix))
            put("CommandLogChannel", JsonPrimitive(current.commandLogChannelId.asId()))
            put("StaffRole", JsonPrimitive(current.staffRoleId.asId()))
            put("DeleteMessageOnCommand", JsonPrimitive(current.deleteOnCommand))
            put("CurrencyEmoji", JsonPrimitive(current.currencyEmoji))
            put("AfkMessage", JsonPrimitive(current.afkMessage.serialize()))
            put("StreamMessage", JsonPrimitive(current.streamMessage.serialize()))
            put("WarnlogChannelId", JsonPrimitive(current.warningLogChannelId.asId()))
            put("WarnExpireHours", JsonPrimitive(current.warnExpireHours))
        }

        val ok = runCatching {
            api.sendIgnoringBody(
                Endpoint(
                    "api/GuildConfig/$guildId",
                    HttpMethod.POST,
                    MewdekoJson.encodeToString(JsonObject.serializer(), merged),
                )
            )
        }.isSuccess

        if (ok) loadedConfig = merged
        _state.update { it.copy(isSaving = false, hasUnsaved = !ok) }
        if (ok) postSuccess("Settings saved.") else postError("Failed to save settings.")
    }

    private fun edit(transform: (SettingsState) -> SettingsState) {
        _state.update { transform(it).copy(hasUnsaved = true) }
    }

    private fun Snowflake?.asId(): Long = this?.toLongOrNull() ?: 0L

    private fun JsonObject.stringAt(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.intAt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }?.toInt()

    private fun JsonObject.boolAt(key: String): Boolean =
        (this[key] as? JsonPrimitive)?.booleanOrNull == true

    private fun JsonObject.snowflakeAt(key: String): Snowflake? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() && it != "0" }
}
