package dev.mewdeko.mobile.feature.currency

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import dev.mewdeko.mobile.util.withSeparators
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.net.URLEncoder
import javax.inject.Inject

/** Entries shown per leaderboard page, matching the dashboard. */
const val CurrencyLeaderboardPageSize = 25

/** Currency screen state. */
data class CurrencyState(
    val section: String = CurrencySection.ANALYTICS.id,
    val windowDays: Int = 30,
    val analytics: EconomyAnalytics? = null,
    val analyticsFailed: Boolean = false,
    val analyticsLoading: Boolean = false,
    val config: EconomyConfig? = null,
    val configFailed: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val shopItems: List<ShopItem> = emptyList(),
    val shopFailed: Boolean = false,
    val shopDraft: ShopItemDraft? = null,
    val shopSaving: Boolean = false,
    val leaderboard: List<CurrencyLeaderboardEntry> = emptyList(),
    val leaderboardTotal: Int = 0,
    val leaderboardSupply: Long = 0,
    val leaderboardPage: Int = 0,
    val leaderboardFailed: Boolean = false,
    val leaderboardLoading: Boolean = false,
    val roles: List<GuildRole> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val adjustUserId: Snowflake? = null,
    val adjustAmount: Long = 0,
    val adjustRemoves: Boolean = false,
    val adjustReason: String = "",
    val isAdjusting: Boolean = false,
) {
    /** How many leaderboard pages exist. */
    val leaderboardPageCount: Int
        get() = ((leaderboardTotal + CurrencyLeaderboardPageSize - 1) / CurrencyLeaderboardPageSize)
            .coerceAtLeast(1)
}

/** Loads and edits a guild's economy: analytics, settings, shop, and balances. */
@HiltViewModel
class CurrencyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(CurrencyState())

    /** Observable screen state. */
    val state: StateFlow<CurrencyState> = _state.asStateFlow()

    private val base: String get() = "api/Currency/$guildId"

    init {
        load()
    }

    /**
     * Loads every section at once. Each section fails independently so one
     * broken endpoint does not blank the others; only a total failure
     * replaces the screen with the error state.
     */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        val current = _state.value
        coroutineScope {
            val analytics = async { runCatching { fetchAnalytics(current.windowDays) } }
            val config = async { runCatching { api.send(Endpoint("$base/config"), EconomyConfig.serializer()) } }
            val shop = async { runCatching { fetchShop() } }
            val board = async { runCatching { fetchLeaderboard(current.leaderboardPage) } }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val members = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val analyticsResult = analytics.await()
            val configResult = config.await()
            val shopResult = shop.await()
            val boardResult = board.await()

            val results = listOf(analyticsResult, configResult, shopResult, boardResult)
            if (results.all { it.isFailure }) {
                throw results.first().exceptionOrNull() ?: IllegalStateException("Failed to load economy data")
            }

            _state.update {
                it.copy(
                    analytics = analyticsResult.getOrNull() ?: it.analytics,
                    analyticsFailed = analyticsResult.isFailure,
                    config = configResult.getOrNull() ?: it.config,
                    configFailed = configResult.isFailure && it.config == null,
                    hasUnsavedChanges = if (configResult.isSuccess) false else it.hasUnsavedChanges,
                    shopItems = shopResult.getOrNull() ?: it.shopItems,
                    shopFailed = shopResult.isFailure,
                    leaderboard = boardResult.getOrNull()?.entries ?: it.leaderboard,
                    leaderboardTotal = boardResult.getOrNull()?.total ?: it.leaderboardTotal,
                    leaderboardSupply = boardResult.getOrNull()?.supply ?: it.leaderboardSupply,
                    leaderboardFailed = boardResult.isFailure,
                    roles = roles.await()
                        .filter { role -> role.id != guildId && !role.name.startsWith("@") }
                        .sortedBy { role -> role.name.lowercase() },
                    members = members.await()
                        .filter { member -> !member.isBot }
                        .sortedBy { member -> member.label().lowercase() },
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Changes the analytics window and reloads just that section. */
    fun setWindow(days: Int) {
        if (days == _state.value.windowDays && _state.value.analytics != null) return
        _state.update { it.copy(windowDays = days) }
        reloadAnalytics()
    }

    /** Reloads the analytics section for the current window. */
    fun reloadAnalytics() = viewModelScope.launch {
        val days = _state.value.windowDays
        _state.update { it.copy(analyticsLoading = true) }
        val result = runCatching { fetchAnalytics(days) }
        _state.update {
            it.copy(
                analytics = result.getOrNull() ?: it.analytics,
                analyticsFailed = result.isFailure && it.analytics == null,
                analyticsLoading = false,
            )
        }
        if (result.isFailure) postError("Failed to load analytics.")
    }

    /** Applies an edit to the pending economy settings. */
    fun editConfig(transform: (EconomyConfig) -> EconomyConfig) {
        _state.update { current ->
            val config = current.config ?: return@update current
            current.copy(config = transform(config), hasUnsavedChanges = true)
        }
    }

    /** Writes the full settings object through the partial-update endpoint. */
    fun saveConfig() = viewModelScope.launch {
        val config = _state.value.config ?: return@launch
        _state.update { it.copy(isSaving = true) }
        val result = runCatching {
            api.send(
                Endpoint(
                    "$base/config",
                    HttpMethod.PATCH,
                    MewdekoJson.encodeToString(EconomyConfig.serializer(), config),
                ),
                EconomyConfig.serializer(),
            )
        }
        _state.update {
            it.copy(
                isSaving = false,
                config = result.getOrNull() ?: it.config,
                hasUnsavedChanges = result.isFailure,
            )
        }
        result.exceptionOrNull()?.let { postError("Failed to save settings. ${it.userFacingMessage}") }
    }

    /** Restores every economy setting to the bot's defaults. */
    fun resetConfig() = viewModelScope.launch {
        _state.update { it.copy(isSaving = true) }
        val result = runCatching {
            api.send(Endpoint("$base/config/reset", HttpMethod.POST), EconomyConfig.serializer())
        }
        _state.update {
            it.copy(
                isSaving = false,
                config = result.getOrNull() ?: it.config,
                hasUnsavedChanges = if (result.isSuccess) false else it.hasUnsavedChanges,
                configFailed = if (result.isSuccess) false else it.configFailed,
            )
        }
        if (result.isFailure) postError("Failed to reset settings.")
    }

    /** Reloads the shop list. */
    fun reloadShop() = viewModelScope.launch {
        val result = runCatching { fetchShop() }
        _state.update {
            it.copy(
                shopItems = result.getOrNull() ?: it.shopItems,
                shopFailed = result.isFailure,
            )
        }
    }

    /** Opens the editor for a new item, or for [item] when given. */
    fun openShopEditor(item: ShopItem?) = _state.update {
        it.copy(shopDraft = item?.let { source -> ShopItemDraft.from(source) } ?: ShopItemDraft())
    }

    /** Closes the item editor without saving. */
    fun closeShopEditor() = _state.update { it.copy(shopDraft = null) }

    /** Applies an edit to the open item draft. */
    fun editShopDraft(transform: (ShopItemDraft) -> ShopItemDraft) = _state.update { current ->
        val draft = current.shopDraft ?: return@update current
        current.copy(shopDraft = transform(draft))
    }

    /** Creates or updates the item in the open editor. */
    fun saveShopDraft() = viewModelScope.launch {
        val draft = _state.value.shopDraft ?: return@launch
        if (draft.name.isBlank()) {
            postError("The item needs a name.")
            return@launch
        }
        if (draft.type == ShopItemType.ROLE && draft.roleId.isNullOrEmpty()) {
            postError("Role items need a role to grant.")
            return@launch
        }
        if (draft.price < 0) {
            postError("Price cannot be negative.")
            return@launch
        }

        _state.update { it.copy(shopSaving = true) }
        val original = draft.originalName
        val endpoint = if (original != null) {
            Endpoint("$base/shop/${encodeName(original)}", HttpMethod.PUT, shopBody(draft))
        } else {
            Endpoint("$base/shop", HttpMethod.POST, shopBody(draft))
        }
        val result = runCatching { api.sendIgnoringBody(endpoint) }
        if (result.isFailure) {
            _state.update { it.copy(shopSaving = false) }
            postError(result.exceptionOrNull()?.serverMessage() ?: "Failed to save item.")
            return@launch
        }
        val items = runCatching { fetchShop() }
        _state.update {
            it.copy(
                shopSaving = false,
                shopDraft = null,
                shopItems = items.getOrNull() ?: it.shopItems,
                shopFailed = items.isFailure,
            )
        }
    }

    /** Flips an item between visible and hidden. */
    fun toggleShopItem(item: ShopItem) = viewModelScope.launch {
        val draft = ShopItemDraft.from(item).copy(enabled = !item.enabled)
        val result = runCatching {
            api.sendIgnoringBody(
                Endpoint("$base/shop/${encodeName(item.name)}", HttpMethod.PUT, shopBody(draft))
            )
        }
        if (result.isFailure) {
            postError("Failed to update item.")
            return@launch
        }
        _state.update { current ->
            current.copy(
                shopItems = current.shopItems.map {
                    if (it.id == item.id) it.copy(enabled = !item.enabled) else it
                },
            )
        }
    }

    /** Deletes an item along with every inventory entry holding it. */
    fun deleteShopItem(item: ShopItem) = viewModelScope.launch {
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/shop/${encodeName(item.name)}", HttpMethod.DELETE))
        }
        if (result.isFailure) {
            postError("Failed to delete item.")
            return@launch
        }
        _state.update { current ->
            current.copy(
                shopItems = current.shopItems.filterNot { it.id == item.id },
                shopDraft = current.shopDraft?.takeIf { it.originalName != item.name },
            )
        }
    }

    /** Loads a leaderboard page. */
    fun loadLeaderboardPage(page: Int) = viewModelScope.launch {
        val target = page.coerceAtLeast(0)
        _state.update { it.copy(leaderboardLoading = true) }
        val result = runCatching { fetchLeaderboard(target) }
        _state.update {
            val data = result.getOrNull()
            it.copy(
                leaderboardLoading = false,
                leaderboardPage = if (data != null) target else it.leaderboardPage,
                leaderboard = data?.entries ?: it.leaderboard,
                leaderboardTotal = data?.total ?: it.leaderboardTotal,
                leaderboardSupply = data?.supply ?: it.leaderboardSupply,
                leaderboardFailed = result.isFailure && it.leaderboard.isEmpty(),
            )
        }
        if (result.isFailure) postError("Failed to load leaderboard.")
    }

    /** Picks the member whose balance will be adjusted. */
    fun setAdjustUser(id: Snowflake?) = _state.update { it.copy(adjustUserId = id) }

    /** Sets the unsigned adjustment amount. */
    fun setAdjustAmount(value: Long) = _state.update { it.copy(adjustAmount = value.coerceAtLeast(0)) }

    /** Chooses whether the adjustment adds or removes currency. */
    fun setAdjustRemoves(removes: Boolean) = _state.update { it.copy(adjustRemoves = removes) }

    /** Sets the ledger reason for the adjustment. */
    fun setAdjustReason(value: String) = _state.update { it.copy(adjustReason = value) }

    /** Credits or debits the chosen member's wallet. */
    fun adjustBalance() = viewModelScope.launch {
        val current = _state.value
        val userId = current.adjustUserId
        if (userId.isNullOrEmpty()) {
            postError("Pick a member first.")
            return@launch
        }
        if (current.adjustAmount == 0L) {
            postError("Enter a non-zero amount.")
            return@launch
        }
        val signed = if (current.adjustRemoves) -current.adjustAmount else current.adjustAmount
        _state.update { it.copy(isAdjusting = true) }
        val result = runCatching {
            api.send(
                Endpoint(
                    "$base/balance",
                    HttpMethod.POST,
                    jsonBody(
                        "userId" to snowflakeJson(userId),
                        "amount" to signed,
                        "reason" to current.adjustReason.trim().takeIf { it.isNotEmpty() },
                    ),
                ),
                AdjustBalanceResult.serializer(),
            )
        }
        _state.update { it.copy(isAdjusting = false) }
        val holdings = result.getOrElse {
            postError("Failed to adjust balance.")
            return@launch
        }
        _state.update {
            it.copy(adjustUserId = null, adjustAmount = 0, adjustRemoves = false, adjustReason = "")
        }
        postSuccess("Adjusted. Wallet is now ${holdings.wallet.withSeparators()}.")
        loadLeaderboardPage(current.leaderboardPage)
    }

    private suspend fun fetchAnalytics(days: Int): EconomyAnalytics =
        api.send(Endpoint("$base/analytics?days=$days"), EconomyAnalytics.serializer())

    private suspend fun fetchShop(): List<ShopItem> =
        api.send(Endpoint("$base/shop"), ListSerializer(ShopItem.serializer()))
            .sortedWith(compareBy<ShopItem> { it.sortOrder }.thenBy { it.name.lowercase() })

    private suspend fun fetchLeaderboard(page: Int): CurrencyLeaderboardPage =
        api.send(
            Endpoint("$base/leaderboard?page=$page&pageSize=$CurrencyLeaderboardPageSize"),
            CurrencyLeaderboardPage.serializer(),
        )

    private fun shopBody(draft: ShopItemDraft): String {
        val body = buildJsonObject {
            put("name", JsonPrimitive(draft.name.trim()))
            val description = draft.description.trim()
            put("description", if (description.isEmpty()) JsonNull else JsonPrimitive(description))
            put("price", JsonPrimitive(draft.price))
            put("itemType", JsonPrimitive(draft.type.raw))
            put("roleId", if (draft.type == ShopItemType.ROLE) snowflakeJson(draft.roleId) else JsonNull)
            put(
                "textContent",
                if (draft.type == ShopItemType.TEXT) JsonPrimitive(draft.textContent) else JsonNull,
            )
            put("stock", JsonPrimitive(if (draft.stock < 0) -1 else draft.stock))
            put("maxPerUser", JsonPrimitive(draft.maxPerUser.coerceAtLeast(0)))
            put("requiredRoleId", snowflakeJson(draft.requiredRoleId))
            put("consumable", JsonPrimitive(draft.consumable))
            put("enabled", JsonPrimitive(draft.enabled))
            put("sortOrder", JsonPrimitive(draft.sortOrder))
        }
        return MewdekoJson.encodeToString(JsonObject.serializer(), body)
    }

    private fun snowflakeJson(id: Snowflake?): JsonElement =
        id?.takeIf { it.isNotEmpty() && it != "0" }?.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonNull

    private fun encodeName(name: String): String =
        URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")

    private fun Throwable.serverMessage(): String? =
        (this as? ApiError.Http)?.body?.trim()?.trim('"')?.takeIf { it.isNotEmpty() && it.length < 200 }
}

/** The best available name for a member in pickers and lists. */
internal fun GuildMember.label(): String =
    displayName.takeIf { it.isNotBlank() } ?: username.takeIf { it.isNotBlank() } ?: id
