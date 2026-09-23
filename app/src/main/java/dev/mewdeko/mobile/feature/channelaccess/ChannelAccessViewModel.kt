package dev.mewdeko.mobile.feature.channelaccess

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildMember
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** Filter value meaning "no filter" for the gate and status pickers. */
const val FilterAll = "all"

/** Channel Access screen state. */
data class ChannelAccessState(
    val section: String = "gates",
    val gates: List<ChannelAccessGate> = emptyList(),
    val channels: List<TextChannelLite> = emptyList(),
    val roles: List<GuildRole> = emptyList(),
    val members: List<GuildMember> = emptyList(),
    val newGateChannelId: Snowflake? = null,
    val newGateGrantMode: AccessGrantMode = AccessGrantMode.ROLE,
    val newGateRoleId: Snowflake? = null,
    val isCreatingGate: Boolean = false,
    val expandedGateId: Int? = null,
    val numberDrafts: Map<Int, GateNumberDraft> = emptyMap(),
    val roleModePending: Set<Int> = emptySet(),
    val questionDraft: QuestionDraft = QuestionDraft(),
    val panelTargets: Map<Int, Snowflake> = emptyMap(),
    val busyGateIds: Set<Int> = emptySet(),
    val applications: List<ChannelAccessApplication> = emptyList(),
    val applicationsLoading: Boolean = false,
    val applicationsError: String? = null,
    val applicationGateFilter: String = FilterAll,
    val applicationStatusFilter: String = AccessApplicationStatus.PENDING.value.toString(),
    val expandedApplicationId: Int? = null,
    val applicationDetails: Map<Int, ChannelAccessApplication> = emptyMap(),
    val detailLoadingIds: Set<Int> = emptySet(),
    val resolveReasons: Map<Int, String> = emptyMap(),
    val resolvingIds: Set<Int> = emptySet(),
    val blacklist: List<ChannelAccessBlacklistEntry> = emptyList(),
    val blacklistError: String? = null,
    val newBlockUserId: Snowflake? = null,
    val newBlockScope: String = FilterAll,
    val newBlockReason: String = "",
    val isBlocking: Boolean = false,
    val unblockingIds: Set<Int> = emptySet(),
) {
    /** Open applications across every gate. */
    val pendingCount: Int get() = gates.sumOf { it.pendingApplications }

    /** Channels that do not have a gate yet, the only ones a new gate can cover. */
    val ungatedChannels: List<TextChannelLite>
        get() {
            val gated = gates.map { it.channelId }.toSet()
            return channels.filter { it.id !in gated }
        }

    /** The display name of a channel, falling back to its id. */
    fun channelName(id: Snowflake?): String {
        if (id.isNullOrEmpty() || id == "0") return "None"
        return channels.firstOrNull { it.id == id }?.name ?: id
    }

    /** The display name of a role, falling back to its id. */
    fun roleName(id: Snowflake?): String {
        if (id.isNullOrEmpty() || id == "0") return "None"
        return roles.firstOrNull { it.id == id }?.name ?: id
    }

    /** The display name of a gate, or the guild wide scope when [configId] is null. */
    fun gateName(configId: Int?): String {
        if (configId == null) return "Every gate"
        val gate = gates.firstOrNull { it.id == configId } ?: return "Deleted gate"
        return "#${channelName(gate.channelId)}"
    }
}

/** Loads and edits the guild's channel access gates, applications and applicant blacklist. */
@HiltViewModel
class ChannelAccessViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(ChannelAccessState())

    /** Observable screen state. */
    val state: StateFlow<ChannelAccessState> = _state.asStateFlow()

    private val base: String get() = "api/ChannelAccess/$guildId"

    init {
        load()
    }

    /** Loads gates, the blacklist, channels, roles and members, then the filtered applications. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val gates = async {
                api.send(Endpoint("$base/gates"), ListSerializer(ChannelAccessGate.serializer()))
            }
            val blacklist = async {
                runCatching {
                    api.send(Endpoint("$base/blacklist"), ListSerializer(ChannelAccessBlacklistEntry.serializer()))
                }
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
            val members = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/members/$guildId"),
                        ListSerializer(GuildMember.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val gateList = gates.await()
            val blacklistResult = blacklist.await()
            _state.update { current ->
                current.copy(
                    gates = gateList,
                    blacklist = blacklistResult.getOrDefault(current.blacklist),
                    blacklistError = blacklistResult.exceptionOrNull()?.userFacingMessage,
                    channels = channels.await().sortedBy { it.name.lowercase() },
                    roles = roles.await()
                        .filter { it.id != guildId && !it.name.startsWith("@") }
                        .sortedBy { it.name.lowercase() },
                    members = members.await()
                        .filter { !it.isBot }
                        .sortedBy { it.displayName.ifBlank { it.username }.lowercase() },
                    numberDrafts = gateList.associate { it.id to GateNumberDraft.from(it) },
                    roleModePending = emptySet(),
                    applicationGateFilter = current.applicationGateFilter.takeIf { filter ->
                        filter == FilterAll || gateList.any { it.id.toString() == filter }
                    } ?: FilterAll,
                    newBlockScope = current.newBlockScope.takeIf { scope ->
                        scope == FilterAll || gateList.any { it.id.toString() == scope }
                    } ?: FilterAll,
                    expandedGateId = current.expandedGateId?.takeIf { id -> gateList.any { it.id == id } },
                )
            }
            fetchApplications()
        }
    }

    /** Switches the visible tab. */
    fun setSection(id: String) = _state.update { it.copy(section = id) }

    /** Sets the channel a new gate will cover. */
    fun setNewGateChannel(id: Snowflake?) = _state.update { it.copy(newGateChannelId = id) }

    /** Sets how a new gate lets approved applicants in, clearing the role in channel permission mode. */
    fun setNewGateGrantMode(mode: AccessGrantMode) = _state.update {
        it.copy(
            newGateGrantMode = mode,
            newGateRoleId = if (mode == AccessGrantMode.CHANNEL_PERMISSION) null else it.newGateRoleId,
        )
    }

    /** Sets the role a new gate grants on approval. */
    fun setNewGateRole(id: Snowflake?) = _state.update { it.copy(newGateRoleId = id) }

    /** Opens applications for the selected locked channel. */
    fun createGate() {
        val current = _state.value
        val channelId = current.newGateChannelId
        if (channelId.isNullOrEmpty()) {
            postError("Pick a channel first.")
            return
        }
        val roleMode = current.newGateGrantMode == AccessGrantMode.ROLE
        if (roleMode && current.newGateRoleId.isNullOrEmpty()) {
            postError("Pick the role approved applicants should get.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isCreatingGate = true) }
            val body = buildJsonObject {
                put("channelId", snowflakeJson(channelId))
                put("accessRoleId", if (roleMode) snowflakeJson(current.newGateRoleId) else JsonNull)
                put("userId", snowflakeJson(userId))
            }
            val result = runCatching {
                api.send(Endpoint("$base/gates", HttpMethod.POST, encode(body)), ChannelAccessGate.serializer())
            }
            _state.update { it.copy(isCreatingGate = false) }
            result.onSuccess {
                _state.update {
                    it.copy(
                        newGateChannelId = null,
                        newGateRoleId = null,
                        newGateGrantMode = AccessGrantMode.ROLE,
                    )
                }
                load(refreshing = true)
            }.onFailure { postError(failureText(it, "Failed to create the gate.")) }
        }
    }

    /** Expands or collapses a gate's settings editor. */
    fun toggleGateExpanded(gate: ChannelAccessGate) = _state.update {
        val opening = it.expandedGateId != gate.id
        it.copy(
            expandedGateId = if (opening) gate.id else null,
            numberDrafts = if (opening) it.numberDrafts + (gate.id to GateNumberDraft.from(gate)) else it.numberDrafts,
        )
    }

    /** Turns a gate's applications on or off. */
    fun setEnabled(gate: ChannelAccessGate, value: Boolean) =
        updateGate(gate, "Failed to save the gate settings.") { put("enabled", JsonPrimitive(value)) }

    /**
     * Changes how a gate lets people in. Channel permission mode is applied immediately by clearing
     * the access role; role mode waits for a role to be picked, as the bot switches mode from the role.
     */
    fun setGrantMode(gate: ChannelAccessGate, mode: AccessGrantMode) {
        if (mode == AccessGrantMode.CHANNEL_PERMISSION) {
            _state.update { it.copy(roleModePending = it.roleModePending - gate.id) }
            if (gate.grant != AccessGrantMode.CHANNEL_PERMISSION) {
                updateGate(gate, "Failed to save the gate settings.") { put("accessRoleId", JsonPrimitive(0L)) }
            }
        } else if (gate.grant != AccessGrantMode.ROLE) {
            _state.update { it.copy(roleModePending = it.roleModePending + gate.id) }
        }
    }

    /** Sets or clears the role granted on approval. Clearing switches the gate to channel permission mode. */
    fun setAccessRole(gate: ChannelAccessGate, id: Snowflake?) {
        _state.update { it.copy(roleModePending = it.roleModePending - gate.id) }
        updateGate(gate, "Failed to save the gate settings.") { put("accessRoleId", snowflakeOrZero(id)) }
    }

    /** Sets or clears the channel applications are posted to for voting. */
    fun setReviewChannel(gate: ChannelAccessGate, id: Snowflake?) =
        updateGate(gate, "Failed to save the gate settings.") { put("reviewChannelId", snowflakeOrZero(id)) }

    /** Sets or clears the channel decisions are logged to. */
    fun setLogChannel(gate: ChannelAccessGate, id: Snowflake?) =
        updateGate(gate, "Failed to save the gate settings.") { put("logChannelId", snowflakeOrZero(id)) }

    /** Sets or clears the role allowed to vote. */
    fun setVoterRole(gate: ChannelAccessGate, id: Snowflake?) =
        updateGate(gate, "Failed to save the gate settings.") { put("voterRoleId", snowflakeOrZero(id)) }

    /** Sets or clears the role pinged on new applications. */
    fun setPingRole(gate: ChannelAccessGate, id: Snowflake?) =
        updateGate(gate, "Failed to save the gate settings.") { put("pingRoleId", snowflakeOrZero(id)) }

    /** Sets what happens when the voting window closes. */
    fun setOnExpiry(gate: ChannelAccessGate, behavior: AccessExpiryBehavior) =
        updateGate(gate, "Failed to save the gate settings.") { put("onExpiry", JsonPrimitive(behavior.value)) }

    /** Sets whether voters get an abstain button. */
    fun setAllowAbstain(gate: ChannelAccessGate, value: Boolean) =
        updateGate(gate, "Failed to save the gate settings.") { put("allowAbstain", JsonPrimitive(value)) }

    /** Sets whether the applicant is hidden until the vote closes. */
    fun setAnonymousApplicant(gate: ChannelAccessGate, value: Boolean) =
        updateGate(gate, "Failed to save the gate settings.") { put("anonymousApplicant", JsonPrimitive(value)) }

    /** Sets whether individual votes are hidden. */
    fun setAnonymousVotes(gate: ChannelAccessGate, value: Boolean) =
        updateGate(gate, "Failed to save the gate settings.") { put("anonymousVotes", JsonPrimitive(value)) }

    /** Sets whether the applicant is DMed on a decision. */
    fun setDmOnDecision(gate: ChannelAccessGate, value: Boolean) =
        updateGate(gate, "Failed to save the gate settings.") { put("dmOnDecision", JsonPrimitive(value)) }

    /** Edits one gate's numeric drafts. */
    fun editNumbers(gateId: Int, transform: (GateNumberDraft) -> GateNumberDraft) = _state.update {
        val current = it.numberDrafts[gateId] ?: GateNumberDraft()
        it.copy(numberDrafts = it.numberDrafts + (gateId to transform(current)))
    }

    /** Whether any numeric draft differs from the gate's saved values. */
    fun numbersChanged(gate: ChannelAccessGate, draft: GateNumberDraft?): Boolean =
        draft != null && numberChanges(gate, draft).isNotEmpty()

    /** Saves the changed numeric settings for a gate, clamped at zero like the bot does. */
    fun saveNumbers(gate: ChannelAccessGate) {
        val draft = _state.value.numberDrafts[gate.id] ?: return
        val changes = numberChanges(gate, draft)
        if (changes.isEmpty()) return
        updateGate(gate, "Failed to save the voting limits.") {
            changes.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }
    }

    /** Edits the shared new question form. */
    fun editQuestion(transform: (QuestionDraft) -> QuestionDraft) = _state.update {
        val next = transform(it.questionDraft)
        it.copy(questionDraft = next.copy(question = next.question.take(MaxQuestionLength)))
    }

    /** Adds the drafted question to a gate's application form. */
    fun addQuestion(gate: ChannelAccessGate) {
        val draft = _state.value.questionDraft
        val text = draft.question.trim()
        if (text.isEmpty()) return
        if (gate.questions.size >= MaxQuestions) {
            postError("Discord caps application forms at five questions.")
            return
        }
        viewModelScope.launch {
            markBusy(gate.id, true)
            val body = buildJsonObject {
                put("question", JsonPrimitive(text))
                put("placeholder", draft.placeholder.trim().takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
                put("required", JsonPrimitive(draft.required))
                put("paragraph", JsonPrimitive(draft.paragraph))
            }
            val result = runCatching {
                api.send(
                    Endpoint("$base/gates/${gate.id}/questions", HttpMethod.POST, encode(body)),
                    ListSerializer(ChannelAccessQuestion.serializer()),
                )
            }
            markBusy(gate.id, false)
            result.onSuccess { questions ->
                _state.update {
                    it.copy(
                        gates = it.gates.map { existing -> if (existing.id == gate.id) existing.copy(questions = questions) else existing },
                        questionDraft = QuestionDraft(),
                    )
                }
            }.onFailure { postError(failureText(it, "Failed to add the question.")) }
        }
    }

    /** Removes the question at a one-based display position. */
    fun removeQuestion(gate: ChannelAccessGate, position: Int) = viewModelScope.launch {
        markBusy(gate.id, true)
        val result = runCatching {
            api.send(
                Endpoint("$base/gates/${gate.id}/questions/$position", HttpMethod.DELETE),
                ListSerializer(ChannelAccessQuestion.serializer()),
            )
        }
        markBusy(gate.id, false)
        result.onSuccess { questions ->
            _state.update {
                it.copy(gates = it.gates.map { existing -> if (existing.id == gate.id) existing.copy(questions = questions) else existing })
            }
        }.onFailure { postError(failureText(it, "Failed to remove the question.")) }
    }

    /** Picks the channel a gate's apply panel will be posted in. */
    fun setPanelTarget(gateId: Int, id: Snowflake?) = _state.update {
        it.copy(panelTargets = if (id.isNullOrEmpty()) it.panelTargets - gateId else it.panelTargets + (gateId to id))
    }

    /** Posts the gate's apply panel in the picked channel. */
    fun postPanel(gate: ChannelAccessGate) {
        val target = _state.value.panelTargets[gate.id]
        if (target.isNullOrEmpty()) {
            postError("Pick a channel to post the panel in.")
            return
        }
        viewModelScope.launch {
            markBusy(gate.id, true)
            val body = buildJsonObject { put("channelId", snowflakeJson(target)) }
            val result = runCatching {
                api.sendIgnoringBody(Endpoint("$base/gates/${gate.id}/panel", HttpMethod.POST, encode(body)))
            }
            markBusy(gate.id, false)
            result.onSuccess {
                postSuccess("Apply panel posted.")
                load(refreshing = true)
            }.onFailure { postError(failureText(it, "Failed to post the panel.")) }
        }
    }

    /** Deletes a gate along with its questions, applications and votes. */
    fun deleteGate(gate: ChannelAccessGate) = viewModelScope.launch {
        markBusy(gate.id, true)
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/gates/${gate.id}", HttpMethod.DELETE))
        }
        markBusy(gate.id, false)
        result.onSuccess { load(refreshing = true) }
            .onFailure { postError(failureText(it, "Failed to delete the gate.")) }
    }

    /** Filters applications to one gate, or every gate for [FilterAll]. */
    fun setApplicationGateFilter(value: String?) {
        _state.update { it.copy(applicationGateFilter = value ?: FilterAll) }
        reloadApplications()
    }

    /** Filters applications to one status, or any status for [FilterAll]. */
    fun setApplicationStatusFilter(value: String?) {
        _state.update { it.copy(applicationStatusFilter = value ?: FilterAll) }
        reloadApplications()
    }

    /** Reloads the application list with the current filters. */
    fun reloadApplications() = viewModelScope.launch { fetchApplications() }

    /** Expands an application and fetches its full record, including individual votes. */
    fun toggleApplicationExpanded(application: ChannelAccessApplication) {
        val opening = _state.value.expandedApplicationId != application.id
        _state.update { it.copy(expandedApplicationId = if (opening) application.id else null) }
        if (!opening) return
        viewModelScope.launch {
            _state.update { it.copy(detailLoadingIds = it.detailLoadingIds + application.id) }
            val result = runCatching {
                api.send(
                    Endpoint("$base/applications/${application.id}"),
                    ChannelAccessApplication.serializer(),
                )
            }
            _state.update { current ->
                current.copy(
                    detailLoadingIds = current.detailLoadingIds - application.id,
                    applicationDetails = result.getOrNull()
                        ?.let { current.applicationDetails + (application.id to it) }
                        ?: current.applicationDetails,
                )
            }
        }
    }

    /** Edits the note recorded when closing an application. */
    fun setResolveReason(applicationId: Int, value: String) = _state.update {
        it.copy(resolveReasons = it.resolveReasons + (applicationId to value))
    }

    /** Approves or denies a pending application, overriding the vote count. */
    fun resolveApplication(application: ChannelAccessApplication, approve: Boolean) = viewModelScope.launch {
        _state.update { it.copy(resolvingIds = it.resolvingIds + application.id) }
        val status = if (approve) AccessApplicationStatus.APPROVED else AccessApplicationStatus.DENIED
        val reason = _state.value.resolveReasons[application.id]?.trim().orEmpty()
        val body = buildJsonObject {
            put("status", JsonPrimitive(status.value))
            put("userId", snowflakeJson(userId))
            put("reason", if (reason.isEmpty()) JsonNull else JsonPrimitive(reason))
        }
        val result = runCatching {
            api.send(
                Endpoint("$base/applications/${application.id}/resolve", HttpMethod.POST, encode(body)),
                ChannelAccessApplication.serializer(),
            )
        }
        _state.update { it.copy(resolvingIds = it.resolvingIds - application.id) }
        result.onSuccess { updated ->
            _state.update {
                it.copy(
                    resolveReasons = it.resolveReasons - application.id,
                    applicationDetails = it.applicationDetails + (application.id to updated),
                    applications = it.applications.map { existing -> if (existing.id == updated.id) updated else existing },
                )
            }
            postSuccess("Application #${application.id} was ${if (approve) "approved" else "denied"}.")
            load(refreshing = true)
        }.onFailure { postError(failureText(it, "Failed to close the application.")) }
    }

    /** Picks the member to block from applying. */
    fun setNewBlockUser(id: Snowflake?) = _state.update { it.copy(newBlockUserId = id) }

    /** Picks whether the block covers every gate or one gate. */
    fun setNewBlockScope(value: String?) = _state.update { it.copy(newBlockScope = value ?: FilterAll) }

    /** Edits the reason recorded with a new block. */
    fun setNewBlockReason(value: String) = _state.update { it.copy(newBlockReason = value) }

    /** Bars the selected member from applying. */
    fun addBlock() {
        val current = _state.value
        val target = current.newBlockUserId
        if (target.isNullOrEmpty()) {
            postError("Pick a user first.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBlocking = true) }
            val scope = current.newBlockScope.takeIf { it != FilterAll }?.toIntOrNull()
            val reason = current.newBlockReason.trim()
            val body = buildJsonObject {
                put("userId", snowflakeJson(target))
                put("configId", scope?.let { JsonPrimitive(it) } ?: JsonNull)
                put("addedBy", snowflakeJson(userId))
                put("reason", if (reason.isEmpty()) JsonNull else JsonPrimitive(reason))
            }
            val result = runCatching {
                api.sendIgnoringBody(Endpoint("$base/blacklist", HttpMethod.POST, encode(body)))
            }
            _state.update { it.copy(isBlocking = false) }
            result.onSuccess {
                _state.update { it.copy(newBlockUserId = null, newBlockScope = FilterAll, newBlockReason = "") }
                refreshBlacklist()
            }.onFailure { postError(failureText(it, "Failed to block that user.")) }
        }
    }

    /** Lifts a block so the user can apply again. */
    fun removeBlock(entry: ChannelAccessBlacklistEntry) = viewModelScope.launch {
        _state.update { it.copy(unblockingIds = it.unblockingIds + entry.id) }
        val query = entry.configId?.let { "?configId=$it" }.orEmpty()
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/blacklist/${entry.userId}$query", HttpMethod.DELETE))
        }
        _state.update { it.copy(unblockingIds = it.unblockingIds - entry.id) }
        result.onSuccess {
            _state.update { it.copy(blacklist = it.blacklist.filterNot { existing -> existing.id == entry.id }) }
        }.onFailure { postError(failureText(it, "Failed to lift the block.")) }
    }

    /** Reloads only the blacklist. */
    fun refreshBlacklist() = viewModelScope.launch {
        val result = runCatching {
            api.send(Endpoint("$base/blacklist"), ListSerializer(ChannelAccessBlacklistEntry.serializer()))
        }
        _state.update {
            it.copy(
                blacklist = result.getOrDefault(it.blacklist),
                blacklistError = result.exceptionOrNull()?.userFacingMessage,
            )
        }
    }

    private suspend fun fetchApplications() {
        val current = _state.value
        val params = buildList {
            current.applicationGateFilter.takeIf { it != FilterAll }?.let { add("configId=$it") }
            current.applicationStatusFilter.takeIf { it != FilterAll }?.let { add("status=$it") }
        }
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        _state.update { it.copy(applicationsLoading = true, applicationsError = null) }
        val result = runCatching {
            api.send(
                Endpoint("$base/applications$query"),
                ListSerializer(ChannelAccessApplication.serializer()),
            )
        }
        _state.update {
            it.copy(
                applicationsLoading = false,
                applications = result.getOrDefault(it.applications),
                applicationsError = result.exceptionOrNull()?.userFacingMessage,
            )
        }
    }

    private fun updateGate(
        gate: ChannelAccessGate,
        failureMessage: String,
        fields: JsonObjectBuilder.() -> Unit,
    ) = viewModelScope.launch {
        markBusy(gate.id, true)
        val body = buildJsonObject(fields)
        val result = runCatching {
            api.send(
                Endpoint("$base/gates/${gate.id}", HttpMethod.PUT, encode(body)),
                ChannelAccessGate.serializer(),
            )
        }
        markBusy(gate.id, false)
        result.onSuccess { updated ->
            _state.update {
                it.copy(
                    gates = it.gates.map { existing -> if (existing.id == updated.id) updated else existing },
                    numberDrafts = it.numberDrafts + (updated.id to GateNumberDraft.from(updated)),
                )
            }
        }.onFailure { postError(failureText(it, failureMessage)) }
    }

    private fun numberChanges(gate: ChannelAccessGate, draft: GateNumberDraft): List<Pair<String, Int>> {
        fun parse(value: String, fallback: Int): Int = value.trim().toIntOrNull()?.coerceAtLeast(0) ?: fallback
        return listOf(
            Triple("requiredApprovals", parse(draft.requiredApprovals, gate.requiredApprovals), gate.requiredApprovals),
            Triple("requiredDenials", parse(draft.requiredDenials, gate.requiredDenials), gate.requiredDenials),
            Triple("voteDurationHours", parse(draft.voteDurationHours, gate.voteDurationHours), gate.voteDurationHours),
            Triple("minAccountAgeDays", parse(draft.minAccountAgeDays, gate.minAccountAgeDays), gate.minAccountAgeDays),
            Triple("minServerAgeDays", parse(draft.minServerAgeDays, gate.minServerAgeDays), gate.minServerAgeDays),
            Triple("reapplyCooldownHours", parse(draft.reapplyCooldownHours, gate.reapplyCooldownHours), gate.reapplyCooldownHours),
        ).filter { (_, next, saved) -> next != saved }.map { (key, next, _) -> key to next }
    }

    private fun markBusy(gateId: Int, busy: Boolean) = _state.update {
        it.copy(busyGateIds = if (busy) it.busyGateIds + gateId else it.busyGateIds - gateId)
    }

    private fun snowflakeJson(id: Snowflake?): JsonPrimitive =
        JsonPrimitive(id?.toLongOrNull() ?: 0L)

    private fun snowflakeOrZero(id: Snowflake?): JsonPrimitive =
        JsonPrimitive(id?.takeIf { it.isNotEmpty() && it != "0" }?.toLongOrNull() ?: 0L)

    private fun encode(body: JsonObject): String = MewdekoJson.encodeToString(JsonObject.serializer(), body)

    private fun failureText(error: Throwable, fallback: String): String =
        if (error is ApiError.Http && error.status in 400..499 && error.body.isNotBlank()) {
            error.userFacingMessage
        } else {
            fallback
        }
}
