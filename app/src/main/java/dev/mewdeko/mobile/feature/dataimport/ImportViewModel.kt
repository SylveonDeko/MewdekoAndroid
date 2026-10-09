package dev.mewdeko.mobile.feature.dataimport

import android.util.Base64
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Import screen state. */
data class ImportState(
    val source: ImportSource? = null,
    val apiKey: String = "",
    val fileName: String? = null,
    val packedFile: String? = null,
    val preview: ImportPreview? = null,
    val result: ImportResult? = null,
    val settingsResult: ImportSettingsResult? = null,
    val chosenSections: Set<String> = emptySet(),
    val history: List<ImportHistoryEntry> = emptyList(),
    val isReading: Boolean = false,
    val isWriting: Boolean = false,
    val undoingId: Int? = null,
    val mergeMode: ImportMergeMode = ImportMergeMode.REPLACE,
    val minimumLevel: Int = 0,
    val useSourceCurve: Boolean = true,
    val importRoleRewards: Boolean = true,
    val syncRoles: Boolean = false,
) {
    /** Whether the chosen source has what it needs to be read. */
    val canRead: Boolean
        get() = source != null && !isReading && when (source.input) {
            ImportSource.Input.NONE -> true
            ImportSource.Input.KEY -> apiKey.isNotBlank()
            ImportSource.Input.FILE -> packedFile != null
        }

    /** Level roles in the preview whose roles still exist. */
    val readyRewardCount: Int get() = preview?.roleRewards?.count { it.exists } ?: 0
}

/** Reads data from another bot, previews it, writes it, and lists past imports so they can be undone. */
@HiltViewModel
class ImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ImportState())

    /** Observable screen state. */
    val state: StateFlow<ImportState> = _state.asStateFlow()

    init {
        load()
    }

    /** Loads the server's past imports. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val history = runCatching {
            api.send(Endpoint("api/import/$guildId/history"), ListSerializer(ImportHistoryEntry.serializer()))
        }.getOrDefault(emptyList())
        _state.update { it.copy(history = history) }
    }

    /** Picks a source and clears anything read from the previous one. */
    fun choose(source: ImportSource) = _state.update {
        ImportState(source = source, history = it.history)
    }

    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value) }
    fun setMergeMode(value: ImportMergeMode) = _state.update { it.copy(mergeMode = value) }
    fun setMinimumLevel(value: Int) = _state.update { it.copy(minimumLevel = value.coerceAtLeast(0)) }
    fun setUseSourceCurve(value: Boolean) = _state.update { it.copy(useSourceCurve = value) }
    fun setImportRoleRewards(value: Boolean) = _state.update { it.copy(importRoleRewards = value) }
    fun setSyncRoles(value: Boolean) = _state.update { it.copy(syncRoles = value) }

    /** Adds or removes a settings section from the import. */
    fun setSection(key: String, on: Boolean) = _state.update {
        it.copy(chosenSections = if (on) it.chosenSections + key else it.chosenSections - key)
    }

    /** Gzips a picked file so large exports fit through the dashboard. */
    fun takeFile(name: String, bytes: ByteArray) = viewModelScope.launch {
        val packed = withContext(Dispatchers.Default) {
            val out = ByteArrayOutputStream()
            GZIPOutputStream(out).use { it.write(bytes) }
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }
        _state.update { it.copy(fileName = name, packedFile = packed) }
    }

    /** Starts the import and follows it until the data is read. */
    fun read() = viewModelScope.launch {
        val current = _state.value
        val source = current.source ?: return@launch
        if (!current.canRead) return@launch
        _state.update { it.copy(isReading = true, result = null) }
        try {
            val body = buildJsonObject {
                put("source", source.raw)
                if (source.input == ImportSource.Input.KEY) put("apiKey", current.apiKey.trim())
                if (source.input == ImportSource.Input.FILE) put("fileGzip", current.packedFile)
            }
            var preview = api.send(
                Endpoint("api/import/$guildId/start", HttpMethod.POST, body.toString()),
                ImportPreview.serializer(),
            )
            _state.update { it.copy(preview = preview) }
            while (preview.isReading) {
                delay(1000)
                preview = api.send(Endpoint("api/import/$guildId/jobs/${preview.jobId}"), ImportPreview.serializer())
                _state.update { it.copy(preview = preview) }
            }
            if (preview.isFailed) {
                _state.update { it.copy(preview = null) }
                postError(ERRORS[preview.error] ?: ERRORS.getValue("SourceFailed"))
            } else {
                _state.update {
                    it.copy(
                        useSourceCurve = preview.nativeCurve != null,
                        chosenSections = preview.sections.map { s -> s.key }.toSet(),
                        settingsResult = null,
                    )
                }
            }
        } catch (t: Throwable) {
            _state.update { it.copy(preview = null) }
            postError(describe(t))
        } finally {
            _state.update { it.copy(isReading = false) }
        }
    }

    /** Writes the previewed data. */
    fun write() = viewModelScope.launch {
        val current = _state.value
        val preview = current.preview ?: return@launch
        _state.update { it.copy(isWriting = true) }
        try {
            if (preview.isSettings) {
                val sections = ImportSettingsSection.ORDER.filter { it in current.chosenSections }
                val result = api.send(
                    Endpoint(
                        "api/import/$guildId/jobs/${preview.jobId}/settings",
                        HttpMethod.POST,
                        buildJsonObject { put("sections", JsonArray(sections.map { JsonPrimitive(it) })) }.toString(),
                    ),
                    ImportSettingsResult.serializer(),
                )
                _state.update { it.copy(settingsResult = result) }
                load(refreshing = true)
                return@launch
            }
            val result = if (preview.isCurrency) {
                api.send(
                    Endpoint(
                        "api/import/$guildId/jobs/${preview.jobId}/currency",
                        HttpMethod.POST,
                        buildJsonObject { put("mergeMode", current.mergeMode.raw) }.toString(),
                    ),
                    ImportResult.serializer(),
                )
            } else {
                api.send(
                    Endpoint(
                        "api/import/$guildId/jobs/${preview.jobId}/xp",
                        HttpMethod.POST,
                        buildJsonObject {
                            put("mergeMode", current.mergeMode.raw)
                            put("minimumLevel", current.minimumLevel)
                            put("useSourceCurve", current.useSourceCurve)
                            put("importRoleRewards", current.importRoleRewards)
                            put("syncRoles", current.syncRoles)
                        }.toString(),
                    ),
                    ImportResult.serializer(),
                )
            }
            _state.update { it.copy(result = result) }
            load(refreshing = true)
        } catch (t: Throwable) {
            postError(describe(t))
        } finally {
            _state.update { it.copy(isWriting = false) }
        }
    }

    /** Undoes a past import. */
    fun undo(entry: ImportHistoryEntry) = viewModelScope.launch {
        _state.update { it.copy(undoingId = entry.id) }
        try {
            api.sendIgnoringBody(Endpoint("api/import/$guildId/history/${entry.id}/undo", HttpMethod.POST))
            load(refreshing = true)
        } catch (t: Throwable) {
            postError(describe(t))
        } finally {
            _state.update { it.copy(undoingId = null) }
        }
    }

    /** Turns an API failure into words, using the bot's error code when it sent one. */
    private fun describe(t: Throwable): String {
        val code = (t as? ApiError.Http)?.body?.let { body ->
            runCatching { (MewdekoJson.parseToJsonElement(body) as? JsonObject)?.get("error")?.jsonPrimitive?.content }
                .getOrNull()
        }
        return ERRORS[code] ?: "Something went wrong. Try again in a moment."
    }

    private companion object {
        val ERRORS = mapOf(
            "SourceFailed" to "The other bot did not answer properly. Try again in a few minutes.",
            "LeaderboardPrivate" to "This server's MEE6 leaderboard is private. Make it public in MEE6's dashboard, then try again.",
            "NotFound" to "The other bot has no data for this server.",
            "BadKey" to "That key was rejected. Check it was copied in full.",
            "KeyRequired" to "Paste a key first.",
            "FileRequired" to "Choose a file first.",
            "RateLimited" to "The other bot is limiting requests right now. Try again in a few minutes.",
            "UnreadableFile" to "That file could not be read. Use a JSON or CSV export with a user ID and an XP or level for each member.",
            "Empty" to "No members were found in that data.",
            "GlobalCurrency" to "This bot shares one balance across every server, so balances cannot be imported into one server.",
            "Busy" to "Another import is already running on this server.",
            "JobMissing" to "That import expired. Read the data again.",
            "NotReady" to "That import is not ready yet.",
            "WrongKind" to "That data does not match. XP sources need XP or levels, and UnbelievaBoat needs balances.",
            "UndoExpired" to "That import can no longer be undone.",
            "UndoNotLatest" to "Undo the newer import first.",
            "WrongServer" to "That file was exported from a different server. Run the export on this server's MEE6 dashboard.",
        )
    }
}
