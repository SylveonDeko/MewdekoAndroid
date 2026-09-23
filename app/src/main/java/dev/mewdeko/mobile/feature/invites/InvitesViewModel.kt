package dev.mewdeko.mobile.feature.invites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.net.jsonInt
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import io.ktor.http.HttpMethod as KtorHttpMethod

/** Invite tracking screen state, one field group per tab. */
data class InvitesState(
    val section: String = "overview",

    val overviewRange: InviteStatsRange = InviteStatsRange.MONTHLY,
    val analytics: InviteAnalytics? = null,
    val overviewLoading: Boolean = false,

    val boardRange: InviteStatsRange = InviteStatsRange.ALL_TIME,
    val boardRoleId: Snowflake? = null,
    val boardPage: Int = 1,
    val leaderboard: List<InviteLeaderboardEntry> = emptyList(),
    val boardLoading: Boolean = false,
    val boardExporting: Boolean = false,

    val detailUserId: Snowflake? = null,
    val detailBreakdown: InviteBreakdown? = null,
    val detailInviter: InviterInfo? = null,
    val detailInvited: List<InviteUserLite> = emptyList(),
    val detailLoading: Boolean = false,
    val adjustRegular: Int = 0,
    val adjustBonus: Int = 0,
    val adjustFake: Int = 0,
    val adjusting: Boolean = false,

    val memberFilterInviter: Snowflake? = null,
    val memberFilterCode: String = "",
    val memberFilterLabel: String = "",
    val memberIncludeLeft: Boolean = true,
    val memberPage: Int = 1,
    val invitedPage: InvitedPage? = null,
    val membersLoading: Boolean = false,
    val membersExporting: Boolean = false,

    val codes: List<GuildInviteCode> = emptyList(),
    val labels: List<InviteLabel> = emptyList(),
    val labelDrafts: Map<String, LabelDraft> = emptyMap(),
    val codesLoading: Boolean = false,

    val settings: InviteSettings? = null,
    val minAgeDays: Int = 0,
    val blacklistedUsers: List<Snowflake> = emptyList(),
    val blacklistedRoles: List<Snowflake> = emptyList(),
    val hiddenUsers: List<Snowflake> = emptyList(),
    val settingsLoading: Boolean = false,
    val syncing: Boolean = false,

    val guildMembers: List<InviteMemberLite> = emptyList(),
    val roles: List<GuildRole> = emptyList(),
    val channels: List<TextChannelLite> = emptyList(),

    val pendingExport: PendingExport? = null,
) {
    /** The invited members page count, at least one. */
    val memberPageCount: Int
        get() = invitedPage?.let { page -> maxOf(1, (page.total + page.pageSize - 1) / page.pageSize) } ?: 1

    /** Whether a next leaderboard page is likely available. */
    val hasNextBoardPage: Boolean get() = leaderboard.size >= BoardPageSize
}

private const val BoardPageSize = 25
private const val MemberPageSize = 25

/** Invite tracking: growth analytics, leaderboard, invited members, codes/labels, and settings. */
@HiltViewModel
class InvitesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
    private val http: HttpClient,
    private val auth: AuthManager,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(InvitesState())

    /** Observable screen state. */
    val state: StateFlow<InvitesState> = _state.asStateFlow()

    private val base = "api/InviteTracking/$guildId"

    init {
        load()
    }

    /** Switches the visible tab. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Reloads everything: guild lists, overview, leaderboard, invited members, codes and settings. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val members = async { fetchMembers() }
            val roles = async { fetchRoles() }
            val channels = async { fetchChannels() }
            val analytics = async { fetchAnalytics(_state.value.overviewRange) }
            val leaderboard = async { fetchLeaderboard(InviteStatsRange.ALL_TIME, null, 1) }
            val invited = async { fetchInvited() }
            val codes = async { fetchCodes() }
            val labels = async { fetchLabels() }
            val settings = async { fetchSettings() }
            val blacklistedUsers = async { fetchExclusions(InviteExclusionKind.BLACKLISTED_USER) }
            val blacklistedRoles = async { fetchExclusions(InviteExclusionKind.BLACKLISTED_ROLE) }
            val hiddenUsers = async { fetchExclusions(InviteExclusionKind.HIDDEN_USER) }

            val cfg = settings.await()
            val codeList = codes.await()
            _state.update {
                it.copy(
                    guildMembers = members.await(),
                    roles = roles.await(),
                    channels = channels.await(),
                    analytics = analytics.await(),
                    boardRange = InviteStatsRange.ALL_TIME,
                    boardPage = 1,
                    boardRoleId = null,
                    leaderboard = leaderboard.await(),
                    invitedPage = invited.await(),
                    codes = codeList,
                    labels = labels.await(),
                    labelDrafts = draftsFor(codeList),
                    settings = cfg,
                    minAgeDays = timeSpanToDays(cfg?.minAccountAge),
                    blacklistedUsers = blacklistedUsers.await(),
                    blacklistedRoles = blacklistedRoles.await(),
                    hiddenUsers = hiddenUsers.await(),
                )
            }
        }
    }

    // region Overview

    /** Changes the overview time window and reloads growth analytics. */
    fun setOverviewRange(range: InviteStatsRange) {
        _state.update { it.copy(overviewRange = range) }
        viewModelScope.launch {
            _state.update { it.copy(overviewLoading = true) }
            val analytics = fetchAnalytics(range)
            _state.update { it.copy(analytics = analytics, overviewLoading = false) }
        }
    }

    private suspend fun fetchAnalytics(range: InviteStatsRange): InviteAnalytics? = runCatching {
        api.send(Endpoint("$base/analytics?range=${range.value}"), InviteAnalytics.serializer())
    }.getOrNull()

    // endregion

    // region Leaderboard

    /** Changes the leaderboard time window, resets to page one, and reloads. */
    fun setBoardRange(range: InviteStatsRange) {
        _state.update { it.copy(boardRange = range, boardPage = 1) }
        reloadLeaderboard()
    }

    /** Filters the leaderboard to holders of a role, or clears the filter. */
    fun setBoardRole(roleId: Snowflake?) {
        _state.update { it.copy(boardRoleId = roleId, boardPage = 1) }
        reloadLeaderboard()
    }

    /** Moves to the previous leaderboard page. */
    fun boardPreviousPage() {
        val page = _state.value.boardPage
        if (page <= 1) return
        _state.update { it.copy(boardPage = page - 1) }
        reloadLeaderboard()
    }

    /** Moves to the next leaderboard page. */
    fun boardNextPage() {
        if (!_state.value.hasNextBoardPage) return
        _state.update { it.copy(boardPage = it.boardPage + 1) }
        reloadLeaderboard()
    }

    private fun reloadLeaderboard() = viewModelScope.launch {
        _state.update { it.copy(boardLoading = true) }
        val current = _state.value
        val rows = fetchLeaderboard(current.boardRange, current.boardRoleId, current.boardPage)
        _state.update { it.copy(leaderboard = rows, boardLoading = false) }
    }

    private suspend fun fetchLeaderboard(
        range: InviteStatsRange,
        roleId: Snowflake?,
        page: Int,
    ): List<InviteLeaderboardEntry> = runCatching {
        val roleQuery = roleId?.let { "&roleId=$it" }.orEmpty()
        api.send(
            Endpoint("$base/leaderboard?range=${range.value}&page=$page&pageSize=$BoardPageSize$roleQuery"),
            ListSerializer(InviteLeaderboardEntry.serializer()),
        )
    }.getOrDefault(emptyList())

    /** Shares the leaderboard CSV for the current window. */
    fun exportLeaderboard() = viewModelScope.launch {
        _state.update { it.copy(boardExporting = true) }
        val range = _state.value.boardRange
        val csv = fetchCsv("$base/export/leaderboard?range=${range.value}")
        _state.update { it.copy(boardExporting = false) }
        if (csv != null) {
            _state.update { it.copy(pendingExport = PendingExport("invites-${range.name.lowercase()}.csv", csv)) }
        } else {
            postError("Failed to export the leaderboard.")
        }
    }

    // endregion

    // region Member detail

    /** Loads a member's breakdown, who invited them, and who they invited. */
    fun openDetail(userId: Snowflake) = viewModelScope.launch {
        _state.update {
            it.copy(
                detailUserId = userId,
                detailLoading = true,
                detailBreakdown = null,
                detailInviter = null,
                detailInvited = emptyList(),
                adjustRegular = 0,
                adjustBonus = 0,
                adjustFake = 0,
            )
        }
        coroutineScope {
            val breakdown = async { fetchBreakdown(userId) }
            val inviter = async { fetchInviter(userId) }
            val invited = async { fetchInvitedByUser(userId) }
            _state.update {
                it.copy(
                    detailBreakdown = breakdown.await(),
                    detailInviter = inviter.await(),
                    detailInvited = invited.await(),
                    detailLoading = false,
                )
            }
        }
    }

    /** Clears the selected member detail panel. */
    fun clearDetail() = _state.update {
        it.copy(detailUserId = null, detailBreakdown = null, detailInviter = null, detailInvited = emptyList())
    }

    private suspend fun fetchBreakdown(userId: Snowflake): InviteBreakdown? = runCatching {
        api.send(Endpoint("$base/breakdown/$userId"), InviteBreakdown.serializer())
    }.getOrNull()

    private suspend fun fetchInviter(userId: Snowflake): InviterInfo? = runCatching {
        api.send(Endpoint("$base/inviter/$userId"), InviterInfo.serializer())
    }.getOrNull()

    private suspend fun fetchInvitedByUser(userId: Snowflake): List<InviteUserLite> = runCatching {
        api.send(Endpoint("$base/invited/$userId"), ListSerializer(InviteUserLite.serializer()))
    }.getOrDefault(emptyList())

    /** Stages a signed delta for the regular invite count. */
    fun setAdjustRegular(value: Int) = _state.update { it.copy(adjustRegular = value) }

    /** Stages a signed delta for the bonus invite count. */
    fun setAdjustBonus(value: Int) = _state.update { it.copy(adjustBonus = value) }

    /** Stages a signed delta for the fake invite count. */
    fun setAdjustFake(value: Int) = _state.update { it.copy(adjustFake = value) }

    /** Applies the staged adjustment to the selected member's invite tally. */
    fun applyAdjust() = viewModelScope.launch {
        val current = _state.value
        val userId = current.detailUserId ?: return@launch
        if (current.adjustRegular == 0 && current.adjustBonus == 0 && current.adjustFake == 0) return@launch
        _state.update { it.copy(adjusting = true) }
        val body = buildJsonObject {
            put("regular", JsonPrimitive(current.adjustRegular))
            put("bonus", JsonPrimitive(current.adjustBonus))
            put("fake", JsonPrimitive(current.adjustFake))
        }
        val result = runCatching {
            api.send(
                Endpoint("$base/adjust/$userId", HttpMethod.POST, MewdekoJson.encodeToString(JsonObject.serializer(), body)),
                InviteBreakdown.serializer(),
            )
        }
        _state.update { it.copy(adjusting = false) }
        result.onSuccess { updated ->
            _state.update { it.copy(detailBreakdown = updated, adjustRegular = 0, adjustBonus = 0, adjustFake = 0) }
            reloadLeaderboard()
        }.onFailure {
            postError("Failed to adjust invites.")
        }
    }

    /** Resets the selected member's invites to zero. */
    fun resetMember() = viewModelScope.launch {
        val userId = _state.value.detailUserId ?: return@launch
        val ok = runCatching { api.sendIgnoringBody(Endpoint("$base/count/$userId", HttpMethod.DELETE)) }.isSuccess
        if (ok) {
            openDetail(userId)
            reloadLeaderboard()
        } else {
            postError("Failed to reset invites.")
        }
    }

    // endregion

    // region Members (invited list)

    /** Sets the inviter filter for the invited members list. */
    fun setMemberFilterInviter(userId: Snowflake?) {
        _state.update { it.copy(memberFilterInviter = userId, memberPage = 1) }
        reloadInvited()
    }

    /** Sets the invite code filter for the invited members list. */
    fun setMemberFilterCode(value: String) = _state.update { it.copy(memberFilterCode = value) }

    /** Sets the label filter for the invited members list. */
    fun setMemberFilterLabel(value: String) = _state.update { it.copy(memberFilterLabel = value) }

    /** Applies the code and label text filters. */
    fun applyMemberFilters() {
        _state.update { it.copy(memberPage = 1) }
        reloadInvited()
    }

    /** Toggles whether members who have since left are included. */
    fun setMemberIncludeLeft(value: Boolean) {
        _state.update { it.copy(memberIncludeLeft = value, memberPage = 1) }
        reloadInvited()
    }

    /** Moves to the previous invited-members page. */
    fun memberPreviousPage() {
        val page = _state.value.memberPage
        if (page <= 1) return
        _state.update { it.copy(memberPage = page - 1) }
        reloadInvited()
    }

    /** Moves to the next invited-members page. */
    fun memberNextPage() {
        val current = _state.value
        if (current.memberPage >= current.memberPageCount) return
        _state.update { it.copy(memberPage = it.memberPage + 1) }
        reloadInvited()
    }

    private fun reloadInvited() = viewModelScope.launch {
        _state.update { it.copy(membersLoading = true) }
        val page = fetchInvited()
        _state.update { it.copy(invitedPage = page, membersLoading = false) }
    }

    /** Query parameters shared by the invited-members list and its CSV export. */
    private fun memberFilterQuery(state: InvitesState): String = buildList {
        state.memberFilterInviter?.let { add("inviterId=$it") }
        state.memberFilterCode.trim().takeIf { it.isNotEmpty() }?.let { add("code=${encode(it)}") }
        state.memberFilterLabel.trim().takeIf { it.isNotEmpty() }?.let { add("label=${encode(it)}") }
    }.joinToString("&")

    private suspend fun fetchInvited(): InvitedPage? {
        val current = _state.value
        val filters = memberFilterQuery(current)
        val query = "page=${current.memberPage}&pageSize=$MemberPageSize&includeLeft=${current.memberIncludeLeft}" +
            (if (filters.isEmpty()) "" else "&$filters")
        return runCatching {
            api.send(Endpoint("$base/invited?$query"), InvitedPage.serializer())
        }.getOrNull()
    }

    /** Shares the invited-members CSV for the current filters. */
    fun exportInvited() = viewModelScope.launch {
        _state.update { it.copy(membersExporting = true) }
        val filters = memberFilterQuery(_state.value)
        val path = if (filters.isEmpty()) "$base/export/invited" else "$base/export/invited?$filters"
        val csv = fetchCsv(path)
        _state.update { it.copy(membersExporting = false) }
        if (csv != null) {
            _state.update { it.copy(pendingExport = PendingExport("invited-members.csv", csv)) }
        } else {
            postError("Failed to export the invited members.")
        }
    }

    // endregion

    // region Codes and labels

    private suspend fun fetchCodes(): List<GuildInviteCode> = runCatching {
        api.send(Endpoint("$base/codes"), ListSerializer(GuildInviteCode.serializer()))
    }.getOrDefault(emptyList())

    private suspend fun fetchLabels(): List<InviteLabel> = runCatching {
        api.send(Endpoint("$base/labels"), ListSerializer(InviteLabel.serializer()))
    }.getOrDefault(emptyList())

    private fun draftsFor(codes: List<GuildInviteCode>): Map<String, LabelDraft> =
        codes.associate { code -> code.code to LabelDraft(label = code.label.orEmpty(), roleId = code.labelRoleId) }

    private fun reloadCodes() = viewModelScope.launch {
        _state.update { it.copy(codesLoading = true) }
        coroutineScope {
            val codes = async { fetchCodes() }
            val labels = async { fetchLabels() }
            val codeList = codes.await()
            _state.update {
                it.copy(codes = codeList, labels = labels.await(), labelDrafts = draftsFor(codeList), codesLoading = false)
            }
        }
    }

    /** Edits the staged label text for a code without saving it. */
    fun setLabelDraftText(code: String, value: String) = _state.update { state ->
        val existing = state.labelDrafts[code] ?: LabelDraft()
        state.copy(labelDrafts = state.labelDrafts + (code to existing.copy(label = value, dirty = true)))
    }

    /** Edits the staged role-on-join for a code without saving it. */
    fun setLabelDraftRole(code: String, roleId: Snowflake?) = _state.update { state ->
        val existing = state.labelDrafts[code] ?: LabelDraft()
        state.copy(labelDrafts = state.labelDrafts + (code to existing.copy(roleId = roleId, dirty = true)))
    }

    /** Saves a code's label and role-on-join, or removes the label when cleared. */
    fun saveLabel(code: String) = viewModelScope.launch {
        val draft = _state.value.labelDrafts[code] ?: return@launch
        val ok = if (draft.label.isBlank()) {
            runCatching { api.sendIgnoringBody(Endpoint("$base/labels/${encode(code)}", HttpMethod.DELETE)) }.isSuccess
        } else {
            val body = buildJsonObject {
                put("code", JsonPrimitive(code))
                put("label", JsonPrimitive(draft.label.trim().take(64)))
                put("roleId", draft.roleId?.let { JsonPrimitive(it) } ?: JsonNull)
            }
            runCatching {
                api.sendIgnoringBody(Endpoint("$base/labels", HttpMethod.PUT, MewdekoJson.encodeToString(JsonObject.serializer(), body)))
            }.isSuccess
        }
        if (ok) reloadCodes() else postError("Failed to save the label.")
    }

    /** Removes an orphan label for a code that no longer exists. */
    fun removeOrphanLabel(code: String) = viewModelScope.launch {
        val ok = runCatching { api.sendIgnoringBody(Endpoint("$base/labels/${encode(code)}", HttpMethod.DELETE)) }.isSuccess
        if (ok) reloadCodes() else postError("Failed to remove the label.")
    }

    /** Deletes an invite code entirely. */
    fun deleteCode(code: String) = viewModelScope.launch {
        val ok = runCatching { api.sendIgnoringBody(Endpoint("$base/codes/${encode(code)}", HttpMethod.DELETE)) }.isSuccess
        if (ok) reloadCodes() else postError("Failed to delete the invite.")
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    // endregion

    // region Settings

    private suspend fun fetchSettings(): InviteSettings? = runCatching {
        api.send(Endpoint("$base/settings"), InviteSettings.serializer())
    }.getOrNull()

    private fun timeSpanToDays(span: String?): Int {
        val match = span?.let { Regex("""^(?:(\d+)\.)?(\d+):(\d+):(\d+)""").find(it) } ?: return 0
        val days = match.groupValues[1].toIntOrNull() ?: 0
        val hours = match.groupValues[2].toIntOrNull() ?: 0
        return Math.round((days * 24 + hours) / 24.0).toInt().coerceIn(0, 300)
    }

    /** Turns invite tracking on or off. */
    fun setEnabled(value: Boolean) = launchAction("Failed to update tracking.") {
        api.sendIgnoringBody(Endpoint("$base/toggle", HttpMethod.POST, jsonBool(value)))
        _state.update { it.copy(settings = it.settings?.copy(isEnabled = value)) }
    }

    /** Sets whether an invite credit is revoked when the invitee leaves. */
    fun setRemoveOnLeave(value: Boolean) = launchAction("Failed to update the setting.") {
        api.sendIgnoringBody(Endpoint("$base/remove-on-leave", HttpMethod.POST, jsonBool(value)))
        _state.update { it.copy(settings = it.settings?.copy(removeInviteOnLeave = value)) }
    }

    /** Sets whether rejoining members earn a regular invite again. */
    fun setCountRejoins(value: Boolean) = launchAction("Failed to update the setting.") {
        api.sendIgnoringBody(Endpoint("$base/count-rejoins", HttpMethod.POST, jsonBool(value)))
        _state.update { it.copy(settings = it.settings?.copy(countRejoins = value)) }
    }

    /** Sets whether members without an avatar are flagged as fake. */
    fun setFakeOnNoAvatar(value: Boolean) = launchAction("Failed to update the setting.") {
        api.sendIgnoringBody(Endpoint("$base/fake-no-avatar", HttpMethod.POST, jsonBool(value)))
        _state.update { it.copy(settings = it.settings?.copy(fakeOnNoAvatar = value)) }
    }

    /** Stages a new minimum account age in days, from 0 to 300. */
    fun setMinAgeDays(value: Int) = _state.update { it.copy(minAgeDays = value.coerceIn(0, 300)) }

    /** Saves the staged minimum account age. */
    fun saveMinAge() = launchAction("Failed to update the minimum account age.") {
        val days = _state.value.minAgeDays.coerceIn(0, 300)
        api.sendIgnoringBody(Endpoint("$base/min-age", HttpMethod.POST, jsonString("$days.00:00:00")))
        _state.update { it.copy(settings = it.settings?.copy(minAccountAge = "$days.00:00:00")) }
    }

    /** Sets the channel personal invite links point at, or null for the system channel. */
    fun setLinkChannel(channelId: Snowflake?) = launchAction("Failed to update the channel.") {
        api.sendIgnoringBody(Endpoint("$base/link-channel", HttpMethod.POST, encodeNullableSnowflake(channelId)))
        _state.update { it.copy(settings = it.settings?.copy(linkChannelId = channelId)) }
    }

    /** Sets the join and leave log channel, or null to disable. */
    fun setLogChannel(channelId: Snowflake?) = launchAction("Failed to update the channel.") {
        api.sendIgnoringBody(Endpoint("$base/log-channel", HttpMethod.POST, encodeNullableSnowflake(channelId)))
        _state.update { it.copy(settings = it.settings?.copy(logChannelId = channelId)) }
    }

    private fun encodeNullableSnowflake(value: Snowflake?): String =
        if (value.isNullOrEmpty()) "null" else value.asSnowflakeNumber().toString()

    /** Adds an exclusion of the given kind. */
    fun addExclusion(kind: InviteExclusionKind, targetId: Snowflake?) = viewModelScope.launch {
        if (targetId.isNullOrEmpty()) return@launch
        val ok = runCatching {
            api.sendIgnoringBody(Endpoint("$base/exclusions/${kind.value}/$targetId", HttpMethod.POST))
        }.isSuccess
        if (ok) reloadExclusions(kind) else postError("Failed to add the exclusion.")
    }

    /** Removes an exclusion of the given kind. */
    fun removeExclusion(kind: InviteExclusionKind, targetId: Snowflake) = viewModelScope.launch {
        val ok = runCatching {
            api.sendIgnoringBody(Endpoint("$base/exclusions/${kind.value}/$targetId", HttpMethod.DELETE))
        }.isSuccess
        if (ok) reloadExclusions(kind) else postError("Failed to remove the exclusion.")
    }

    private suspend fun fetchExclusions(kind: InviteExclusionKind): List<Snowflake> = runCatching {
        api.send(Endpoint("$base/exclusions/${kind.value}"), ListSerializer(SnowflakeSerializer))
    }.getOrDefault(emptyList())

    private fun reloadExclusions(kind: InviteExclusionKind) = viewModelScope.launch {
        val ids = fetchExclusions(kind)
        _state.update {
            when (kind) {
                InviteExclusionKind.BLACKLISTED_USER -> it.copy(blacklistedUsers = ids)
                InviteExclusionKind.BLACKLISTED_ROLE -> it.copy(blacklistedRoles = ids)
                InviteExclusionKind.HIDDEN_USER -> it.copy(hiddenUsers = ids)
            }
        }
    }

    /** Imports invite use counts from Discord. */
    fun syncInvites() = viewModelScope.launch {
        _state.update { it.copy(syncing = true) }
        val result = runCatching {
            api.send(Endpoint("$base/sync", HttpMethod.POST), Int.serializer())
        }
        _state.update { it.copy(syncing = false) }
        result.onSuccess { raised ->
            postSuccess("Imported invite uses from Discord and raised totals for $raised inviters.")
            reloadLeaderboard()
        }.onFailure {
            postError("Failed to sync invites.")
        }
    }

    /** Resets invites for the whole guild, or only for inviters who left. */
    fun resetAll(scope: InviteResetScope) = viewModelScope.launch {
        val result = runCatching {
            api.send(Endpoint("$base/reset", HttpMethod.POST, jsonInt(scope.value)), Int.serializer())
        }
        result.onSuccess { count ->
            postSuccess("Reset invites for $count inviters.")
            reloadLeaderboard()
        }.onFailure {
            postError("Failed to reset invites.")
        }
    }

    // endregion

    /** Consumes the pending CSV export once the share sheet has been launched. */
    fun clearPendingExport() = _state.update { it.copy(pendingExport = null) }

    private suspend fun fetchMembers(): List<InviteMemberLite> = runCatching {
        api.send(
            Endpoint("api/ClientOperations/members/$guildId"),
            ListSerializer(InviteMemberLite.serializer()),
        )
    }.getOrDefault(emptyList())

    private suspend fun fetchRoles(): List<GuildRole> = runCatching {
        api.send(Endpoint("api/ClientOperations/roles/$guildId"), ListSerializer(GuildRole.serializer()))
    }.getOrDefault(emptyList())

    private suspend fun fetchChannels(): List<TextChannelLite> = runCatching {
        api.send(Endpoint("api/ClientOperations/textchannels/$guildId"), ListSerializer(TextChannelLite.serializer()))
    }.getOrDefault(emptyList())

    /**
     * Fetches a CSV export as raw text.
     *
     * [ApiClient] only decodes JSON responses, so this issues the GET directly
     * against the same base URL and bearer token it uses internally.
     */
    private suspend fun fetchCsv(path: String): String? = runCatching {
        val baseUrl = auth.currentBaseUrl() ?: return@runCatching null
        val response = http.request("$baseUrl/${path.trimStart('/')}") {
            method = KtorHttpMethod.Get
            header("Authorization", "Bearer ${auth.currentAccessToken()}")
        }
        if (!response.status.isSuccess()) return@runCatching null
        response.bodyAsText()
    }.getOrNull()
}
