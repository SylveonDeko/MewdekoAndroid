package dev.mewdeko.mobile.feature.achievements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.EmbedMessage
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder
import javax.inject.Inject

/** Achievements screen state. */
data class AchievementsState(
    val section: String = "overview",
    val overview: AchievementOverview? = null,
    val catalog: AchievementCatalog? = null,
    val lookups: AchievementLookups? = null,
    val busyKeys: Set<String> = emptySet(),
    val isSaving: Boolean = false,
    val categoryFilter: String = "all",
    val search: String = "",
    val filter: AchievementFilter = AchievementFilter.ALL,
    val sort: AchievementSort = AchievementSort.ORDER,
    val selecting: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    val draft: AchievementDraft = AchievementDraft(),
    val draftBaseline: AchievementDraft = AchievementDraft(),
    val editorOpen: Boolean = false,
    val settingsDraft: AchievementSettingsDraft = AchievementSettingsDraft(),
    val settingsBaseline: AchievementSettingsDraft = AchievementSettingsDraft(),
    val members: List<AchievementMemberRow> = emptyList(),
    val memberTotal: Int = 0,
    val memberPage: Int = 0,
    val memberSearch: String = "",
    val memberSort: Int = 0,
    val loadingMembers: Boolean = false,
    val memberDetail: AchievementMemberDetail? = null,
    val memberOpen: Boolean = false,
    /** The dashboard address, for uploaded icons served under /cdn. */
    val baseUrl: String? = null,
    /** Card designs and what the designer needs, once loaded. */
    val card: AchievementCardResponse? = null,
    /** Drawn previews of designs, by version key or builtin. */
    val cardThumbnails: Map<String, ByteArray> = emptyMap(),
    /** The design open in the designer, saved or not yet created. */
    val designer: CardEditTarget? = null,
    val cardsBusy: Boolean = false,
) {
    /**
     * Where to load an icon image from the API's iconUrl. Uploads served from the bot API come back as a
     * path, which the dashboard serves publicly under /cdn/achievement.
     */
    fun iconImageUrl(iconUrl: String?): String? {
        if (iconUrl.isNullOrBlank()) return null
        val upload = Regex("^achievements/(\\d+)/icons/(\\d+)$").find(iconUrl)
        if (upload != null) {
            val root = baseUrl?.trimEnd('/') ?: return null
            return "$root/cdn/achievement/${upload.groupValues[1]}/${upload.groupValues[2]}.png"
        }
        return iconUrl
    }

    /** The image of an icon value the editor holds before saving, or null for glyph icons. */
    fun draftIconUrl(icon: String?): String? {
        if (icon == null) return null
        catalog?.uploads?.firstOrNull { it.icon == icon }?.let { return iconImageUrl(it.url) }
        val emoji = Regex("^<(a?):[^:]+:(\\d+)>$").find(icon)
        if (emoji != null) {
            val ext = if (emoji.groupValues[1] == "a") "gif" else "png"
            return "https://cdn.discordapp.com/emojis/${emoji.groupValues[2]}.$ext?size=96"
        }
        return icon.takeIf { it.startsWith("https://") }
    }

    /** Grades from the catalog, or the bot's defaults. */
    val grades: List<AchievementGradeInfo> get() = catalog?.grades ?: AchievementGradeInfo.Fallback

    /** The grade facts for [value]. */
    fun grade(value: Int): AchievementGradeInfo = grades.firstOrNull { it.value == value } ?: grades.first()

    /** The category for [key]. */
    fun category(key: String): AchievementCategoryItem? = catalog?.categories?.firstOrNull { it.key == key }


    /** The metric for [value]. */
    fun metric(value: Int): AchievementMetricInfo? = catalog?.metrics?.firstOrNull { it.value == value }

    /** True when the editor draft differs from what was opened. */
    val draftDirty: Boolean get() = draft != draftBaseline

    /** True when the settings draft differs from what was loaded. */
    val settingsDirty: Boolean get() = settingsDraft != settingsBaseline

    /** The stored achievement being edited. */
    val editingItem: AchievementItem? get() = draft.key?.let { key -> catalog?.achievements?.firstOrNull { it.key == key } }

    /** Server achievements in stored order. */
    val customOrder: List<AchievementItem> get() = catalog?.achievements?.filter { it.isCustom }.orEmpty()

    /** Achievements matching the library filters. */
    val shown: List<AchievementItem>
        get() {
            val term = search.trim().lowercase()
            val items = catalog?.achievements.orEmpty().filter { a ->
                (categoryFilter == "all" || a.categoryKey == categoryFilter) &&
                    (term.isEmpty() || a.name.lowercase().contains(term) || a.description.lowercase().contains(term) ||
                        a.key.lowercase().contains(term)) &&
                    when (filter) {
                        AchievementFilter.ALL -> true
                        AchievementFilter.ON -> a.enabled
                        AchievementFilter.OFF -> !a.enabled
                        AchievementFilter.CUSTOM -> a.isCustom
                        AchievementFilter.CHANGED -> a.isOverridden
                        AchievementFilter.SECRET -> a.hidden
                        AchievementFilter.REWARDS -> a.hasRewards
                    }
            }
            return when (sort) {
                AchievementSort.ORDER -> items
                AchievementSort.NAME -> items.sortedBy { it.name.lowercase() }
                AchievementSort.POPULAR -> items.sortedByDescending { it.unlockCount }
                AchievementSort.RARE -> items.sortedBy { it.unlockCount }
                AchievementSort.POINTS -> items.sortedByDescending { it.points }
            }
        }

    /** Data sources that are off while an enabled achievement depends on them. */
    val blockedSources: Set<String>
        get() {
            val overview = overview ?: return emptySet()
            val catalog = catalog ?: return emptySet()
            return overview.dataSources.filterValues { !it }.keys.filter { source ->
                catalog.achievements.any { a ->
                    a.enabled && a.triggerKind == AchievementTriggerKind.METRIC && metric(a.metric)?.source == source
                }
            }.toSet()
        }

    /** A short line saying what unlocks [item]. */
    fun criteria(item: AchievementItem): String = when (item.triggerKind) {
        AchievementTriggerKind.METRIC -> {
            val info = metric(item.metric)
            if (info == null) "Reach ${"%,d".format(item.threshold)}"
            else "${info.label}: ${"%,d".format(item.threshold)} ${if (item.threshold == 1L) info.unit else info.unitPlural}"
        }
        AchievementTriggerKind.KEYWORD -> "Says \"${item.keyword.orEmpty()}\""
        AchievementTriggerKind.REACTION -> "Reacts with ${item.keyword.orEmpty()}"
        AchievementTriggerKind.MANUAL -> "Handed out by staff"
        AchievementTriggerKind.FEAT -> "A one time moment"
        AchievementTriggerKind.COMPLETION -> "Finish a category"
    }

    /** The rewards on [item] as short labels. */
    fun rewardLabels(item: AchievementItem): List<String> = buildList {
        if (item.roleRewardId != null) add("@${item.roleRewardName ?: "deleted role"}")
        if (item.currencyReward > 0) add("${"%,d".format(item.currencyReward)} currency")
        if (item.xpReward > 0) add("${"%,d".format(item.xpReward)} XP")
    }

    /** The description the bot writes for the draft when none is given. */
    val autoDescription: String
        get() {
            if (draft.isBuiltIn) return editingItem?.defaultDescription.orEmpty()
            return when (draft.trigger) {
                AchievementTriggerKind.KEYWORD ->
                    draft.keyword.trim().takeIf { it.isNotEmpty() }?.let { "Say \"$it\"" } ?: "Say a phrase"
                AchievementTriggerKind.REACTION ->
                    draft.keyword.trim().takeIf { it.isNotEmpty() }?.let { "React with $it" } ?: "React with an emoji"
                AchievementTriggerKind.MANUAL -> "Handed out by the staff"
                else -> {
                    val info = metric(draft.metric) ?: return ""
                    val unit = if (draft.threshold == 1L) info.unit else info.unitPlural
                    "Reach ${"%,d".format(draft.threshold)} $unit (${info.label.lowercase()})"
                }
            }
        }

    /** The points the draft gives. */
    val draftPoints: Int
        get() = draft.points
            ?: if (draft.isBuiltIn) editingItem?.defaultPoints ?: grade(draft.grade).points else grade(draft.grade).points

    /** The first rule the draft breaks, or null when it can be saved. */
    val invalidReason: String?
        get() {
            val limits = catalog?.limits ?: AchievementLimits()
            if (!draft.isBuiltIn && draft.name.isBlank()) return "Give it a name"
            if (draft.name.length > limits.nameLength) return "Names can be up to ${limits.nameLength} characters"
            if (draft.description.length > limits.descriptionLength) return "The description is too long"
            if (!draft.isBuiltIn && draft.trigger == AchievementTriggerKind.METRIC && draft.threshold < 1) {
                return "Set a goal of at least 1"
            }
            if (!draft.isBuiltIn && draft.watchesText && draft.keyword.isBlank()) {
                return if (draft.trigger == AchievementTriggerKind.KEYWORD) "Add the phrase to watch for" else "Add the emoji to watch for"
            }
            val points = draft.points
            if (points != null && (points < 0 || points > limits.maxPoints)) return "Points are out of range"
            if (draft.currencyReward < 0 || draft.xpReward < 0) return "Rewards can't be negative"
            val role = draft.roleRewardId?.let { id -> lookups?.roles?.firstOrNull { it.id == id } }
            if (role != null && !role.assignable) return "The bot can't give out that role"
            return null
        }
}

/** Loads and edits a server's achievements, categories, settings, and members. */
@HiltViewModel
class AchievementsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(AchievementsState())

    /** Observable screen state. */
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    private val base = "api/achievements/$guildId"
    private var memberSearchJob: Job? = null

    init {
        load()
    }

    /** Loads the overview, catalog, and lookups in parallel. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val overview = async { api.send(Endpoint(base), AchievementOverview.serializer()) }
            val catalog = async { api.send(Endpoint("$base/catalog"), AchievementCatalog.serializer()) }
            val lookups = async { runCatching { api.send(Endpoint("$base/lookups"), AchievementLookups.serializer()) }.getOrNull() }
            val loadedOverview = overview.await()
            val loadedCatalog = catalog.await()
            val loadedLookups = lookups.await()
            val settings = AchievementSettingsDraft.from(loadedOverview.settings)
            val baseUrl = runCatching { api.currentBaseUrl() }.getOrNull()
            _state.update {
                it.copy(
                    overview = loadedOverview,
                    catalog = loadedCatalog,
                    lookups = loadedLookups ?: it.lookups,
                    settingsDraft = settings,
                    settingsBaseline = settings,
                    baseUrl = baseUrl ?: it.baseUrl,
                )
            }
        }
    }

    private suspend fun reloadCatalog() {
        runCatching { api.send(Endpoint("$base/catalog"), AchievementCatalog.serializer()) }
            .onSuccess { catalog -> _state.update { it.copy(catalog = catalog) } }
    }

    private suspend fun reloadOverview() {
        runCatching { api.send(Endpoint(base), AchievementOverview.serializer()) }
            .onSuccess { overview -> _state.update { it.copy(overview = overview) } }
    }

    private fun json(body: JsonObject): String = MewdekoJson.encodeToString(JsonObject.serializer(), body)

    private fun text(value: String): JsonElement = value.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull

    /** Uploads an image to use as an icon and returns the upload, or null when it failed. */
    suspend fun uploadIcon(bytes: ByteArray, mimeType: String): AchievementIconUpload? {
        val data = "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val body = MewdekoJson.encodeToString(AchievementIconUploadRequest.serializer(), AchievementIconUploadRequest(data))
        return runCatching { api.send(Endpoint("$base/icons", HttpMethod.POST, body), AchievementIconUpload.serializer()) }
            .onSuccess { reloadCatalog() }
            .onFailure { postError(it.botMessage("Couldn't upload that image.")) }
            .getOrNull()
    }

    /** Deletes an uploaded icon; anything using it goes back to its default. */
    fun deleteIcon(upload: AchievementIconUpload) = viewModelScope.launch {
        runCatching { api.sendIgnoringBody(Endpoint("$base/icons/${upload.id}", HttpMethod.DELETE)) }
            .onSuccess {
                _state.update { s -> if (s.draft.icon == upload.icon) s.copy(draft = s.draft.copy(icon = null)) else s }
                reloadCatalog()
            }
            .onFailure { postError(it.botMessage("Couldn't delete that image.")) }
    }

    /** Draws the achievement in the editor as it stands, before saving. */
    suspend fun previewImage(): ByteArray? {
        val s = _state.value
        val draft = s.draft
        val item = s.editingItem
        val body = buildJsonObject {
            put("key", item?.key?.let { JsonPrimitive(it) } ?: JsonNull)
            put("categoryKey", JsonPrimitive(draft.categoryKey))
            put("name", JsonPrimitive(draft.name.trim().ifEmpty { item?.defaultName ?: "New achievement" }))
            put("description", JsonPrimitive(draft.description.trim().ifEmpty { s.autoDescription }))
            put("icon", draft.icon?.let { JsonPrimitive(it) } ?: JsonNull)
            put("grade", JsonPrimitive(if (draft.isBuiltIn) item?.grade ?: 0 else draft.grade))
            put("points", JsonPrimitive(s.draftPoints))
        }
        return runCatching {
            api.send(Endpoint("$base/image/preview", HttpMethod.POST, json(body)), AchievementImageResponse.serializer())
        }.getOrNull()?.bytes
    }

    /** Switches the visible section. */
    fun setSection(id: String) {
        _state.update { it.copy(section = id) }
        if (id == "members" && _state.value.members.isEmpty()) loadMembers()
        if (id == "cards" && _state.value.card == null) loadCards()
    }

    /** Loads the card designs and draws missing previews. */
    fun loadCards() = viewModelScope.launch {
        runCatching { api.send(Endpoint("$base/card"), AchievementCardResponse.serializer()) }
            .onSuccess(::applyCards)
            .onFailure { postError(it.botMessage("Couldn't load the card designs.")) }
    }

    /** Opens the designer on a design that is only created when saved; [makeDefault] applies to the first save. */
    fun startNewDesign(name: String, template: AchievementCardTemplate, makeDefault: Boolean = false) {
        val card = _state.value.card ?: return
        if (card.designs.size >= card.limits.maxDesigns) {
            postError("A server keeps at most ${card.limits.maxDesigns} designs. Delete one first.")
            return
        }
        _state.update { it.copy(designer = CardEditTarget(null, name, template, makeDefault)) }
    }

    /** Saves a new design, optionally as the default, and returns it. */
    suspend fun createCardDesign(name: String, template: AchievementCardTemplate, makeDefault: Boolean): AchievementCardDesign? {
        val card = _state.value.card ?: return null
        val before = card.designs.map { it.id }.toSet()
        val body = MewdekoJson.encodeToString(AchievementCardDesignRequest.serializer(),
            AchievementCardDesignRequest(name, template, makeDefault))
        return runCards("Couldn't create the design.") {
            api.send(Endpoint("$base/card/designs", HttpMethod.POST, body), AchievementCardResponse.serializer())
        }?.designs?.firstOrNull { it.id !in before }
    }

    /** Saves changes to a design; returns whether it worked. */
    suspend fun updateCardDesign(id: Int, name: String, template: AchievementCardTemplate): Boolean {
        val body = MewdekoJson.encodeToString(AchievementCardDesignRequest.serializer(), AchievementCardDesignRequest(name, template))
        return runCards("Couldn't save the design.") {
            api.send(Endpoint("$base/card/designs/$id", HttpMethod.PUT, body), AchievementCardResponse.serializer())
        } != null
    }

    /** Deletes a design; anything using it falls back. */
    fun deleteCardDesign(id: Int) = viewModelScope.launch {
        runCards("Couldn't delete the design.") {
            api.send(Endpoint("$base/card/designs/$id", HttpMethod.DELETE), AchievementCardResponse.serializer())
        }
    }

    /** Makes a design the default; null for the built in one. */
    fun setDefaultCard(id: Int?) = viewModelScope.launch {
        val body = MewdekoJson.encodeToString(AchievementCardDefaultRequest.serializer(), AchievementCardDefaultRequest(id))
        runCards("Couldn't change the default design.") {
            api.send(Endpoint("$base/card/default", HttpMethod.PUT, body), AchievementCardResponse.serializer())
        }
    }

    /** Sets the design a category or achievement uses; null follows the default. */
    fun assignCard(category: Boolean, key: String, id: Int?) = viewModelScope.launch {
        val body = MewdekoJson.encodeToString(AchievementCardAssignRequest.serializer(), AchievementCardAssignRequest(category, key, id))
        runCards("Couldn't change which design that uses.") {
            api.send(Endpoint("$base/card/assign", HttpMethod.PUT, body), AchievementCardResponse.serializer())
        }
    }

    /** Draws a design before saving, with where each element landed. */
    suspend fun previewCard(template: AchievementCardTemplate, locked: Boolean, key: String?): AchievementCardPreview? {
        val body = MewdekoJson.encodeToString(AchievementCardPreviewRequest.serializer(), AchievementCardPreviewRequest(template, locked, key))
        return runCatching {
            api.send(Endpoint("$base/card/preview", HttpMethod.POST, body), AchievementCardPreview.serializer())
        }.getOrNull()
    }

    /** Uploads a card image and returns it. */
    suspend fun uploadCardImage(bytes: ByteArray, mimeType: String): AchievementIconUpload? {
        val data = "data:$mimeType;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        val body = MewdekoJson.encodeToString(AchievementIconUploadRequest.serializer(), AchievementIconUploadRequest(data))
        return runCatching { api.send(Endpoint("$base/card/images", HttpMethod.POST, body), AchievementIconUpload.serializer()) }
            .onSuccess { loadCards() }
            .onFailure { postError(it.botMessage("Couldn't upload that image.")) }
            .getOrNull()
    }

    /** Deletes a card image; every design using it loses it. */
    fun deleteCardImage(image: AchievementIconUpload) = viewModelScope.launch {
        runCatching { api.sendIgnoringBody(Endpoint("$base/icons/${image.id}", HttpMethod.DELETE)) }
            .onSuccess { loadCards() }
            .onFailure { postError(it.botMessage("Couldn't delete that image.")) }
    }

    /** Opens a saved design in the designer. */
    fun openDesigner(id: Int) {
        val design = _state.value.card?.designs?.firstOrNull { it.id == id } ?: return
        _state.update { it.copy(designer = CardEditTarget(design.id, design.name, design.template, false)) }
    }

    /** Closes the designer. */
    fun closeDesigner() = _state.update { it.copy(designer = null) }

    private suspend fun runCards(failure: String, action: suspend () -> AchievementCardResponse): AchievementCardResponse? {
        _state.update { it.copy(cardsBusy = true) }
        val result = runCatching { action() }
            .onSuccess(::applyCards)
            .onFailure { postError(it.botMessage(failure)) }
            .getOrNull()
        _state.update { it.copy(cardsBusy = false) }
        return result
    }

    private fun applyCards(next: AchievementCardResponse) {
        _state.update { it.copy(card = next) }
        drawThumbnail("builtin", next.builtIn)
        next.designs.forEach { drawThumbnail(it.versionKey, it.template) }
    }

    private fun drawThumbnail(key: String, template: AchievementCardTemplate) {
        if (_state.value.cardThumbnails.containsKey(key)) return
        viewModelScope.launch {
            previewCard(template, false, null)?.bytes?.let { bytes ->
                _state.update { it.copy(cardThumbnails = it.cardThumbnails + (key to bytes)) }
            }
        }
    }

    /** Turns achievements on or off for the server. */
    fun setServerEnabled(enabled: Boolean) = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        runCatching {
            api.sendIgnoringBody(Endpoint("$base/settings", HttpMethod.PUT, json(buildJsonObject { put("enabled", JsonPrimitive(enabled)) })))
        }.onSuccess {
            reloadOverview()
            if (enabled) postSuccess("Members can now earn achievements. Those who already qualify get theirs quietly.")
        }.onFailure { postError(it.botMessage("Couldn't save that.")) }
        _state.update { it.copy(isSaving = false) }
    }

    /** Turns on message counting. */
    fun enableMessageCounting() = viewModelScope.launch {
        runCatching {
            api.send(Endpoint("$base/data-sources/messages", HttpMethod.POST), MapSerializer(String.serializer(), Boolean.serializer()))
        }.onSuccess { reloadOverview() }
            .onFailure { postError(it.botMessage("Couldn't turn on message counting.")) }
    }

    /** Picks a category to show. */
    fun setCategoryFilter(key: String) = _state.update { it.copy(categoryFilter = key) }

    /** Sets the library search text. */
    fun setSearch(value: String) = _state.update { it.copy(search = value) }

    /** Sets the library filter. */
    fun setFilter(value: AchievementFilter) = _state.update { it.copy(filter = value) }

    /** Sets the library ordering. */
    fun setSort(value: AchievementSort) = _state.update { it.copy(sort = value) }

    /** Turns select mode on or off. */
    fun setSelecting(value: Boolean) = _state.update { it.copy(selecting = value, selectedKeys = emptySet()) }

    /** Picks or unpicks [key]. */
    fun toggleSelected(key: String) = _state.update {
        it.copy(selectedKeys = if (key in it.selectedKeys) it.selectedKeys - key else it.selectedKeys + key)
    }

    /** Picks every shown achievement. */
    fun selectAllShown() = _state.update { s -> s.copy(selectedKeys = s.shown.filterNot { it.isGlobal }.map { it.key }.toSet()) }

    /** Turns [item] on or off. */
    fun toggle(item: AchievementItem) = viewModelScope.launch {
        if (item.isGlobal) return@launch
        _state.update { it.copy(busyKeys = it.busyKeys + item.key) }
        val next = !item.selfEnabled
        val body = buildJsonObject {
            put("keys", JsonArray(listOf(JsonPrimitive(item.key))))
            put("enabled", JsonPrimitive(next))
        }
        runCatching { api.sendIgnoringBody(Endpoint("$base/enabled", HttpMethod.PUT, json(body))) }
            .onSuccess {
                _state.update { s ->
                    val categoryOn = s.category(item.categoryKey)?.enabled ?: true
                    val updated = item.copy(selfEnabled = next, enabled = next && categoryOn,
                        isOverridden = if (item.isCustom) item.isOverridden else true)
                    s.copy(catalog = s.catalog?.let { replace(it, updated) })
                }
                reloadOverview()
            }
            .onFailure { postError(it.botMessage("Couldn't save that.")) }
        _state.update { it.copy(busyKeys = it.busyKeys - item.key) }
    }

    /** Turns every picked achievement on or off. */
    fun bulkSet(enabled: Boolean) = viewModelScope.launch {
        val keys = _state.value.selectedKeys
        if (keys.isEmpty()) return@launch
        _state.update { it.copy(isSaving = true) }
        val body = buildJsonObject {
            put("keys", JsonArray(keys.map { JsonPrimitive(it) }))
            put("enabled", JsonPrimitive(enabled))
        }
        runCatching { api.sendIgnoringBody(Endpoint("$base/enabled", HttpMethod.PUT, json(body))) }
            .onSuccess {
                _state.update { it.copy(selectedKeys = emptySet()) }
                reloadCatalog()
                reloadOverview()
            }
            .onFailure { postError(it.botMessage("Couldn't save that.")) }
        _state.update { it.copy(isSaving = false) }
    }

    /** Moves a server achievement up (negative [offset]) or down among its category's server achievements. */
    fun move(item: AchievementItem, offset: Int) = viewModelScope.launch {
        val id = item.customId ?: return@launch
        val order = _state.value.customOrder
        val siblings = order.filter { it.categoryKey == item.categoryKey }
        val index = siblings.indexOfFirst { it.key == item.key }
        val target = siblings.getOrNull(index + offset)?.customId ?: return@launch
        val ids = order.mapNotNull { it.customId }.toMutableList()
        val from = ids.indexOf(id)
        val to = ids.indexOf(target)
        if (from < 0 || to < 0) return@launch
        ids[from] = target
        ids[to] = id
        val body = buildJsonObject { put("ids", JsonArray(ids.map { JsonPrimitive(it) })) }
        runCatching { api.sendIgnoringBody(Endpoint("$base/custom/order", HttpMethod.PUT, json(body))) }
            .onSuccess { reloadCatalog() }
            .onFailure { postError(it.botMessage("Couldn't reorder.")) }
    }

    private fun replace(catalog: AchievementCatalog, item: AchievementItem): AchievementCatalog {
        val list = if (catalog.achievements.any { it.key == item.key }) {
            catalog.achievements.map { if (it.key == item.key) item else it }
        } else {
            catalog.achievements + item
        }
        return catalog.copy(achievements = list, categories = recount(catalog.categories, list))
    }

    private fun recount(categories: List<AchievementCategoryItem>, list: List<AchievementItem>) = categories.map { c ->
        val items = list.filter { it.categoryKey == c.key }
        c.copy(achievementCount = items.size, enabledCount = items.count { it.enabled })
    }

    /** Opens the editor on a new server achievement. */
    fun startNew() = _state.update { s ->
        val start = if (s.categoryFilter in setOf("all", "global", "prestige")) "custom" else s.categoryFilter
        val metric = s.catalog?.metrics?.firstOrNull { it.allowCustom }?.value ?: 1
        val draft = AchievementDraft(categoryKey = start, metric = metric)
        s.copy(draft = draft, draftBaseline = draft, editorOpen = true)
    }

    /** Opens the editor on [item]. */
    fun startEdit(item: AchievementItem) = _state.update {
        val draft = AchievementDraft.from(item)
        it.copy(draft = draft, draftBaseline = draft, editorOpen = true)
    }

    /** Closes the editor. */
    fun closeEditor() = _state.update { it.copy(editorOpen = false, draft = AchievementDraft(), draftBaseline = AchievementDraft()) }

    /** Changes the draft. */
    fun editDraft(change: (AchievementDraft) -> AchievementDraft) = _state.update { it.copy(draft = change(it.draft)) }

    /** Saves the draft and closes the editor. */
    fun save() = viewModelScope.launch {
        val s = _state.value
        if (s.invalidReason != null || s.isSaving) return@launch
        _state.update { it.copy(isSaving = true) }
        val draft = s.draft
        val endpoint = when {
            draft.isBuiltIn && draft.key != null ->
                Endpoint("$base/builtin/${draft.key}", HttpMethod.PUT, json(builtInBody(s)))
            draft.customId != null -> Endpoint("$base/custom/${draft.customId}", HttpMethod.PUT, json(customBody(draft)))
            else -> Endpoint("$base/custom", HttpMethod.POST, json(customBody(draft)))
        }
        runCatching { api.send(endpoint, AchievementItem.serializer()) }
            .onSuccess { saved ->
                _state.update {
                    it.copy(catalog = it.catalog?.let { c -> replace(c, saved) }, editorOpen = false,
                        draft = AchievementDraft(), draftBaseline = AchievementDraft())
                }
                if (draft.isNew) postSuccess("Made ${saved.name}. Members who already qualify get it quietly.")
                reloadOverview()
            }
            .onFailure { postError(it.botMessage("Couldn't save the achievement.")) }
        _state.update { it.copy(isSaving = false) }
    }

    /** Puts the built in achievement being edited back to its default. */
    fun resetToDefault() = viewModelScope.launch {
        val key = _state.value.draft.key ?: return@launch
        runCatching { api.send(Endpoint("$base/builtin/$key", HttpMethod.DELETE), AchievementItem.serializer()) }
            .onSuccess { saved ->
                _state.update {
                    val draft = AchievementDraft.from(saved)
                    it.copy(catalog = it.catalog?.let { c -> replace(c, saved) }, draft = draft, draftBaseline = draft)
                }
            }
            .onFailure { postError(it.botMessage("Couldn't reset the achievement.")) }
    }

    /** Deletes the server achievement being edited. */
    fun deleteDraft() = viewModelScope.launch {
        val draft = _state.value.draft
        val id = draft.customId ?: return@launch
        runCatching { api.sendIgnoringBody(Endpoint("$base/custom/$id", HttpMethod.DELETE)) }
            .onSuccess {
                _state.update { s ->
                    val catalog = s.catalog?.let { c ->
                        val list = c.achievements.filterNot { it.key == draft.key }
                        c.copy(achievements = list, categories = recount(c.categories, list))
                    }
                    s.copy(catalog = catalog, editorOpen = false, draft = AchievementDraft(), draftBaseline = AchievementDraft())
                }
                reloadOverview()
            }
            .onFailure { postError(it.botMessage("Couldn't delete the achievement.")) }
    }

    private fun builtInBody(s: AchievementsState): JsonObject {
        val draft = s.draft
        val item = s.editingItem
        return buildJsonObject {
            put("enabled", JsonPrimitive(draft.enabled))
            put("name", text(draft.name))
            put("description", text(draft.description))
            put("icon", draft.icon?.let { JsonPrimitive(it) } ?: JsonNull)
            put("points", draft.points?.takeIf { it != item?.defaultPoints }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("hidden", if (draft.hidden != (item?.defaultHidden ?: false)) JsonPrimitive(draft.hidden) else JsonNull)
            put("roleRewardId", draft.roleRewardId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("currencyReward", JsonPrimitive(draft.currencyReward))
            put("xpReward", JsonPrimitive(draft.xpReward))
        }
    }

    private fun customBody(draft: AchievementDraft): JsonObject = buildJsonObject {
        put("categoryKey", JsonPrimitive(draft.categoryKey))
        put("name", JsonPrimitive(draft.name.trim()))
        put("description", text(draft.description))
        put("icon", draft.icon?.let { JsonPrimitive(it) } ?: JsonNull)
        put("grade", JsonPrimitive(draft.grade))
        put("points", draft.points?.let { JsonPrimitive(it) } ?: JsonNull)
        put("hidden", JsonPrimitive(draft.hidden))
        put("enabled", JsonPrimitive(draft.enabled))
        put("trigger", JsonPrimitive(draft.trigger.value))
        put("metric", JsonPrimitive(if (draft.trigger == AchievementTriggerKind.METRIC) draft.metric else 0))
        put("threshold", JsonPrimitive(if (draft.trigger == AchievementTriggerKind.METRIC) draft.threshold else 0))
        put("keyword", if (draft.watchesText) text(draft.keyword) else JsonNull)
        put("channelId", if (draft.watchesText) draft.channelId?.let { JsonPrimitive(it) } ?: JsonNull else JsonNull)
        put("roleRewardId", draft.roleRewardId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("currencyReward", JsonPrimitive(draft.currencyReward))
        put("xpReward", JsonPrimitive(draft.xpReward))
    }

    private fun saveLayout(order: List<String>, disabled: List<String>) = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        val body = buildJsonObject {
            put("order", JsonArray(order.map { JsonPrimitive(it) }))
            put("disabled", JsonArray(disabled.map { JsonPrimitive(it) }))
        }
        runCatching { api.sendIgnoringBody(Endpoint("$base/categories/layout", HttpMethod.PUT, json(body))) }
            .onSuccess {
                reloadCatalog()
                reloadOverview()
            }
            .onFailure { postError(it.botMessage("Couldn't save the categories.")) }
        _state.update { it.copy(isSaving = false) }
    }

    /** Moves the category at [from] to [to], as a drag left it. */
    fun moveCategory(from: Int, to: Int) {
        val categories = _state.value.catalog?.categories ?: return
        if (from !in categories.indices || to !in categories.indices || from == to) return
        val keys = categories.map { it.key }.toMutableList()
        val moved = keys.removeAt(from)
        keys.add(to, moved)
        saveLayout(keys, categories.filterNot { it.enabled }.map { it.key })
    }

    /** Turns [category] on or off. */
    fun setCategory(category: AchievementCategoryItem, enabled: Boolean) {
        val categories = _state.value.catalog?.categories ?: return
        val disabled = categories.filter { if (it.key == category.key) !enabled else !it.enabled }.map { it.key }
        saveLayout(categories.map { it.key }, disabled)
    }

    /** Makes or renames a server category, then calls [onDone] when it worked. */
    fun saveCategory(id: Int?, name: String, icon: String?, description: String, onDone: () -> Unit) = viewModelScope.launch {
        val body = buildJsonObject {
            put("name", JsonPrimitive(name.trim()))
            put("icon", icon?.let { JsonPrimitive(it) } ?: JsonNull)
            put("description", text(description))
        }
        val endpoint = if (id == null) Endpoint("$base/categories", HttpMethod.POST, json(body))
        else Endpoint("$base/categories/$id", HttpMethod.PUT, json(body))
        runCatching { api.sendIgnoringBody(endpoint) }
            .onSuccess {
                reloadCatalog()
                onDone()
            }
            .onFailure { postError(it.botMessage("Couldn't save the category.")) }
    }

    /** Deletes a server category. */
    fun deleteCategory(id: Int) = viewModelScope.launch {
        runCatching { api.sendIgnoringBody(Endpoint("$base/categories/$id", HttpMethod.DELETE)) }
            .onSuccess { reloadCatalog() }
            .onFailure { postError(it.botMessage("Couldn't delete the category.")) }
    }

    /** Changes the settings draft. */
    fun editSettings(change: (AchievementSettingsDraft) -> AchievementSettingsDraft) =
        _state.update { it.copy(settingsDraft = change(it.settingsDraft)) }

    /** Sets the unlock message. */
    fun setMessage(value: EmbedMessage) = editSettings { it.copy(message = value.copy(components = emptyList())) }

    /** Drops unsaved settings edits. */
    fun discardSettings() = _state.update { it.copy(settingsDraft = it.settingsBaseline) }

    /** Saves the announcement and server settings. */
    fun saveSettings() = viewModelScope.launch {
        val draft = _state.value.settingsDraft
        _state.update { it.copy(isSaving = true) }
        val serialized = draft.message.serialize()
        val body = buildJsonObject {
            put("announceMode", JsonPrimitive(draft.announceMode.value))
            put("logChannelId", JsonPrimitive(draft.logChannelId ?: "0"))
            put("dmByDefault", JsonPrimitive(draft.dmByDefault))
            put("mentionUsers", JsonPrimitive(draft.mentionUsers))
            put("unlockMessage", JsonPrimitive(if (serialized == "-") "" else serialized))
            put("xpPerPoint", JsonPrimitive(draft.xpPerPoint.coerceIn(0, 1000)))
            put("revealHidden", JsonPrimitive(draft.revealHidden))
            put("unlockImage", JsonPrimitive(draft.unlockImage))
            put("deleteAfter", JsonPrimitive(draft.deleteAfter))
            put("excludedRoleIds", JsonArray(draft.excludedRoleIds.map { JsonPrimitive(it) }))
            put("excludedChannelIds", JsonArray(draft.excludedChannelIds.map { JsonPrimitive(it) }))
        }
        runCatching { api.send(Endpoint("$base/settings", HttpMethod.PUT, json(body)), AchievementSettingsInfo.serializer()) }
            .onSuccess { saved ->
                val fresh = AchievementSettingsDraft.from(saved)
                _state.update { it.copy(settingsDraft = fresh, settingsBaseline = fresh) }
                reloadOverview()
            }
            .onFailure { postError(it.botMessage("Couldn't save the settings.")) }
        _state.update { it.copy(isSaving = false) }
    }

    /** Checks every member again soon. */
    fun recheck() = viewModelScope.launch {
        runCatching { api.sendIgnoringBody(Endpoint("$base/recheck", HttpMethod.POST)) }
            .onSuccess { postSuccess("Checking every member again. Anything they qualify for unlocks quietly.") }
            .onFailure { postError(it.botMessage("Couldn't start the check.")) }
    }

    /** Clears every achievement in the server. */
    fun resetAll() = viewModelScope.launch {
        runCatching { api.sendIgnoringBody(Endpoint("$base/reset", HttpMethod.POST)) }
            .onSuccess {
                reloadCatalog()
                reloadOverview()
                postSuccess("Cleared every unlock.")
            }
            .onFailure { postError(it.botMessage("Couldn't reset achievements.")) }
    }

    /** Members per page. */
    val memberPageSize = 25

    /** Loads a page of members. */
    fun loadMembers(page: Int = 0) = viewModelScope.launch {
        _state.update { it.copy(loadingMembers = true) }
        val s = _state.value
        val search = URLEncoder.encode(s.memberSearch.trim(), "UTF-8")
        runCatching {
            api.send(
                Endpoint("$base/members?search=$search&page=$page&pageSize=$memberPageSize&sort=${s.memberSort}"),
                AchievementMembersResult.serializer(),
            )
        }.onSuccess { result ->
            _state.update { it.copy(members = result.members, memberTotal = result.total, memberPage = page) }
        }.onFailure { postError(it.botMessage("Couldn't load members.")) }
        _state.update { it.copy(loadingMembers = false) }
    }

    /** Sets the member search text and reloads after a pause. */
    fun setMemberSearch(value: String) {
        _state.update { it.copy(memberSearch = value) }
        memberSearchJob?.cancel()
        memberSearchJob = viewModelScope.launch {
            delay(300)
            loadMembers()
        }
    }

    /** Sets the member ordering. */
    fun setMemberSort(value: Int) {
        _state.update { it.copy(memberSort = value) }
        loadMembers()
    }

    /** Opens a member's progress. */
    fun openMember(userId: Snowflake) = viewModelScope.launch {
        _state.update { it.copy(memberOpen = true, memberDetail = null) }
        runCatching { api.send(Endpoint("$base/members/$userId"), AchievementMemberDetail.serializer()) }
            .onSuccess { detail -> _state.update { it.copy(memberDetail = detail) } }
            .onFailure {
                _state.update { s -> s.copy(memberOpen = false) }
                postError(it.botMessage("Couldn't load that member."))
            }
    }

    /** Closes the member's progress. */
    fun closeMember() = _state.update { it.copy(memberOpen = false, memberDetail = null) }

    /** Hands [key] to the open member. */
    fun grant(key: String) = memberAction("Couldn't give that achievement.") { userId ->
        api.send(
            Endpoint("$base/members/$userId/grant", HttpMethod.POST, json(buildJsonObject { put("key", JsonPrimitive(key)) })),
            AchievementMemberDetail.serializer(),
        )
    }

    /** Takes [key] from the open member. */
    fun revoke(key: String) = memberAction("Couldn't take that achievement away.") { userId ->
        api.send(Endpoint("$base/members/$userId/achievements/$key", HttpMethod.DELETE), AchievementMemberDetail.serializer())
    }

    /** Clears every achievement the open member has. */
    fun resetMember() = memberAction("Couldn't reset that member.") { userId ->
        api.sendIgnoringBody(Endpoint("$base/members/$userId/reset", HttpMethod.POST))
        api.send(Endpoint("$base/members/$userId"), AchievementMemberDetail.serializer())
    }

    private fun memberAction(failure: String, block: suspend (Snowflake) -> AchievementMemberDetail) = viewModelScope.launch {
        val userId = _state.value.memberDetail?.member?.userId ?: return@launch
        _state.update { it.copy(isSaving = true) }
        runCatching { block(userId) }
            .onSuccess { detail ->
                _state.update { it.copy(memberDetail = detail) }
                loadMembers(_state.value.memberPage)
            }
            .onFailure { postError(it.botMessage(failure)) }
        _state.update { it.copy(isSaving = false) }
    }

    /** The bot's plain text error, or [fallback] when there is none. */
    private fun Throwable.botMessage(fallback: String): String {
        val body = (this as? ApiError.Http)?.body?.trim().orEmpty()
        if (body.isEmpty()) return fallback
        if (body.startsWith("{")) {
            val obj = runCatching { MewdekoJson.parseToJsonElement(body).jsonObject }.getOrNull()
            val text = listOf("message", "Message", "title", "error")
                .firstNotNullOfOrNull { key -> (obj?.get(key) as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } }
            return text ?: fallback
        }
        if (body.startsWith("<")) return fallback
        return body.removeSurrounding("\"").take(300)
    }
}
