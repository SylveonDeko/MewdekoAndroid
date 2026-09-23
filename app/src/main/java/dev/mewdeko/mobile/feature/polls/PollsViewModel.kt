package dev.mewdeko.mobile.feature.polls

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Polls screen state. */
data class PollsState(
    val section: String = PollsSection.POLLS,
    val polls: List<Poll> = emptyList(),
    val includeInactive: Boolean = false,
    val isLoadingPolls: Boolean = false,
    val pollsError: String? = null,
    val expandedPollId: Int? = null,
    val details: Map<Int, Poll> = emptyMap(),
    val loadingDetailId: Int? = null,
    val busyPollId: Int? = null,
    val scheduled: List<ScheduledPoll> = emptyList(),
    val scheduledError: String? = null,
    val cancellingScheduledId: Int? = null,
    val templates: List<PollTemplate> = emptyList(),
    val templatesError: String? = null,
    val analytics: PollAnalytics? = null,
    val isLoadingAnalytics: Boolean = false,
    val analyticsError: String? = null,
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val draft: PollDraft = PollDraft(),
    val draftError: String? = null,
    val isSubmitting: Boolean = false,
) {
    /** Scheduled polls that have neither run nor been cancelled. */
    val pendingScheduled: List<ScheduledPoll> get() = scheduled.filter { it.isPending }

    /** A channel's display name, falling back to its id. */
    fun channelName(id: Snowflake): String =
        availableChannels.firstOrNull { it.id == id }?.name ?: id
}

/** Section ids for the polls screen. */
object PollsSection {
    const val POLLS = "polls"
    const val CREATE = "create"
    const val SCHEDULED = "scheduled"
    const val TEMPLATES = "templates"
    const val ANALYTICS = "analytics"
}

/** Lists, creates, schedules, templates, and analyses guild polls. */
@HiltViewModel
class PollsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(PollsState())

    /** Observable screen state. */
    val state: StateFlow<PollsState> = _state.asStateFlow()

    private val base = "api/Poll/$guildId"

    init {
        load()
    }

    /** Loads polls, scheduled polls, templates, channels, and roles, plus analytics when that tab is open. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val polls = async { runCatching { fetchPolls(_state.value.includeInactive) } }
            val scheduled = async {
                runCatching {
                    api.send(Endpoint("$base/scheduled"), ScheduledPollsResponse.serializer()).scheduledPolls
                }
            }
            val templates = async {
                runCatching { api.send(Endpoint("$base/templates"), ListSerializer(PollTemplate.serializer())) }
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

            val pollResult = polls.await()
            val scheduledResult = scheduled.await()
            val templateResult = templates.await()
            _state.update {
                it.copy(
                    polls = pollResult.getOrDefault(it.polls),
                    pollsError = pollResult.exceptionOrNull()?.let { e -> e.userFacingMessage },
                    details = if (pollResult.isSuccess) emptyMap() else it.details,
                    scheduled = scheduledResult.getOrDefault(it.scheduled),
                    scheduledError = scheduledResult.exceptionOrNull()?.let { e -> e.userFacingMessage },
                    templates = templateResult.getOrDefault(it.templates),
                    templatesError = templateResult.exceptionOrNull()?.let { e -> e.userFacingMessage },
                    availableChannels = channels.await().sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                )
            }
        }
        if (_state.value.section == PollsSection.ANALYTICS) loadAnalytics()
    }

    /** Switches the visible section, loading analytics when that tab opens. */
    fun setSection(id: String) {
        _state.update { it.copy(section = id) }
        if (id == PollsSection.ANALYTICS) loadAnalytics()
    }

    /** Opens the create form. */
    fun startNewPoll() = setSection(PollsSection.CREATE)

    /** Toggles whether closed polls are listed, then reloads the list. */
    fun setIncludeInactive(value: Boolean) {
        _state.update { it.copy(includeInactive = value) }
        reloadPolls()
    }

    /** Reloads only the poll list. */
    fun reloadPolls() = viewModelScope.launch {
        _state.update { it.copy(isLoadingPolls = true, pollsError = null) }
        val result = runCatching { fetchPolls(_state.value.includeInactive) }
        _state.update {
            it.copy(
                isLoadingPolls = false,
                polls = result.getOrDefault(it.polls),
                pollsError = result.exceptionOrNull()?.userFacingMessage,
                details = if (result.isSuccess) emptyMap() else it.details,
            )
        }
    }

    /** Expands or collapses a poll, fetching its detail the first time it opens. */
    fun togglePoll(poll: Poll) {
        val current = _state.value
        if (current.expandedPollId == poll.id) {
            _state.update { it.copy(expandedPollId = null) }
            return
        }
        _state.update { it.copy(expandedPollId = poll.id) }
        if (current.details.containsKey(poll.id)) return
        viewModelScope.launch {
            _state.update { it.copy(loadingDetailId = poll.id) }
            val result = runCatching { api.send(Endpoint("$base/${poll.id}"), Poll.serializer()) }
            _state.update {
                it.copy(
                    loadingDetailId = if (it.loadingDetailId == poll.id) null else it.loadingDetailId,
                    details = result.getOrNull()?.let { detail -> it.details + (poll.id to detail) } ?: it.details,
                )
            }
            if (result.isFailure) postError("Couldn't load the results for this poll.")
        }
    }

    /** Closes an active poll so voting stops. */
    fun closePoll(poll: Poll) = viewModelScope.launch {
        val uid = requireUser() ?: return@launch
        _state.update { it.copy(busyPollId = poll.id) }
        val body = buildJsonObject {
            put("userId", JsonPrimitive(uid.asSnowflakeNumber()))
            put("notifyVoters", JsonPrimitive(false))
        }
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/${poll.id}/close", HttpMethod.POST, encode(body)))
        }
        _state.update { it.copy(busyPollId = null) }
        result.onSuccess { reloadAfterMutation() }
            .onFailure { postError("Failed to close poll. ${it.userFacingMessage}") }
    }

    /** Deletes a poll, its votes, and its Discord message. */
    fun deletePoll(poll: Poll) = viewModelScope.launch {
        val uid = requireUser() ?: return@launch
        _state.update { it.copy(busyPollId = poll.id) }
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/${poll.id}/$uid", HttpMethod.DELETE))
        }
        _state.update { it.copy(busyPollId = null) }
        result.onSuccess {
            _state.update {
                it.copy(
                    polls = it.polls.filterNot { p -> p.id == poll.id },
                    expandedPollId = if (it.expandedPollId == poll.id) null else it.expandedPollId,
                    details = it.details - poll.id,
                )
            }
            reloadAfterMutation()
        }.onFailure { postError("Failed to delete poll. ${it.userFacingMessage}") }
    }

    /** Cancels a scheduled poll before it posts. */
    fun cancelScheduled(item: ScheduledPoll) = viewModelScope.launch {
        val uid = requireUser() ?: return@launch
        _state.update { it.copy(cancellingScheduledId = item.id) }
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/scheduled/${item.id}/$uid", HttpMethod.DELETE))
        }
        _state.update { it.copy(cancellingScheduledId = null) }
        result.onSuccess { reloadScheduled() }
            .onFailure { postError("Failed to cancel scheduled poll. ${it.userFacingMessage}") }
    }

    /** Deletes a saved template. */
    fun deleteTemplate(template: PollTemplate) = viewModelScope.launch {
        val uid = requireUser() ?: return@launch
        val result = runCatching {
            api.sendIgnoringBody(Endpoint("$base/templates/${template.id}/$uid", HttpMethod.DELETE))
        }
        result.onSuccess {
            _state.update { it.copy(templates = it.templates.filterNot { t -> t.id == template.id }) }
        }.onFailure { postError("Failed to delete template. ${it.userFacingMessage}") }
    }

    /** Prefills the create form from a template and opens it. */
    fun useTemplate(template: PollTemplate) {
        val options = template.optionTexts()
        val settings = template.parsedSettings()
        _state.update {
            it.copy(
                section = PollsSection.CREATE,
                draftError = null,
                draft = it.draft.copy(
                    question = template.question.take(PollDraft.MAX_QUESTION),
                    options = if (options.size >= PollDraft.MIN_OPTIONS) options.take(PollDraft.MAX_OPTIONS)
                    else listOf("", ""),
                    type = settings.resolvedType,
                    allowVoteChanges = settings.allowVoteChanges,
                    showResults = settings.showResults,
                    showProgressBars = settings.showProgressBars,
                    allowedRoles = settings.allowedRoles.ifEmpty { it.draft.allowedRoles },
                    durationMinutes = settings.durationMinutes?.toString() ?: it.draft.durationMinutes,
                ),
            )
        }
    }

    /** Loads the month's guild-wide analytics. */
    fun loadAnalytics() = viewModelScope.launch {
        _state.update { it.copy(isLoadingAnalytics = true, analyticsError = null) }
        val result = runCatching {
            api.send(Endpoint("$base/analytics?timeframe=month"), PollAnalytics.serializer())
        }
        _state.update {
            it.copy(
                isLoadingAnalytics = false,
                analytics = result.getOrDefault(it.analytics),
                analyticsError = result.exceptionOrNull()?.userFacingMessage,
            )
        }
    }

    /** Reloads the scheduled list. */
    fun reloadScheduled() = viewModelScope.launch {
        val result = runCatching {
            api.send(Endpoint("$base/scheduled"), ScheduledPollsResponse.serializer()).scheduledPolls
        }
        _state.update {
            it.copy(
                scheduled = result.getOrDefault(it.scheduled),
                scheduledError = result.exceptionOrNull()?.userFacingMessage,
            )
        }
    }

    /** Reloads the template list. */
    fun reloadTemplates() = viewModelScope.launch {
        val result = runCatching {
            api.send(Endpoint("$base/templates"), ListSerializer(PollTemplate.serializer()))
        }
        _state.update {
            it.copy(
                templates = result.getOrDefault(it.templates),
                templatesError = result.exceptionOrNull()?.userFacingMessage,
            )
        }
    }

    /** Sets the draft question. */
    fun setQuestion(value: String) = editDraft { it.copy(question = value.take(PollDraft.MAX_QUESTION)) }

    /** Sets the draft poll type. */
    fun setType(value: PollType) = editDraft { it.copy(type = value) }

    /** Sets the channel the poll posts in. */
    fun setChannel(id: Snowflake?) = editDraft { it.copy(channelId = id) }

    /** Edits one option's text. */
    fun setOption(index: Int, value: String) = editDraft { draft ->
        draft.copy(
            options = draft.options.mapIndexed { i, text -> if (i == index) value.take(PollDraft.MAX_OPTION) else text },
        )
    }

    /** Appends an empty option, up to the Discord limit. */
    fun addOption() = editDraft {
        if (it.options.size >= PollDraft.MAX_OPTIONS) it else it.copy(options = it.options + "")
    }

    /** Removes an option, never dropping below two. */
    fun removeOption(index: Int) = editDraft {
        if (it.options.size <= PollDraft.MIN_OPTIONS) it
        else it.copy(options = it.options.filterIndexed { i, _ -> i != index })
    }

    /** Sets the roles allowed to vote on a role restricted poll. */
    fun setAllowedRoles(ids: List<Snowflake>) = editDraft { it.copy(allowedRoles = ids) }

    /** Sets the duration in minutes, keeping digits only. Empty means open until closed. */
    fun setDuration(value: String) = editDraft { it.copy(durationMinutes = value.filter(Char::isDigit).take(5)) }

    /** Sets when the poll should be posted, or `null` to post immediately. */
    fun setScheduleFor(value: Instant?) = editDraft { it.copy(scheduleFor = value) }

    /** Toggles whether voters may change their vote. */
    fun setAllowVoteChanges(value: Boolean) = editDraft { it.copy(allowVoteChanges = value) }

    /** Toggles live results. */
    fun setShowResults(value: Boolean) = editDraft { it.copy(showResults = value) }

    /** Toggles progress bars in the poll embed. */
    fun setShowProgressBars(value: Boolean) = editDraft { it.copy(showProgressBars = value) }

    /** Toggles saving the draft as a template on submit. */
    fun setSaveAsTemplate(value: Boolean) = editDraft { it.copy(saveAsTemplate = value) }

    /** Sets the name for the template saved on submit. */
    fun setTemplateName(value: String) = editDraft { it.copy(templateName = value.take(PollDraft.MAX_TEMPLATE_NAME)) }

    /** Clears the create form. */
    fun resetDraft() = _state.update { it.copy(draft = PollDraft(), draftError = null) }

    /** Validates the draft, then posts or schedules it and optionally saves it as a template. */
    fun submit() = viewModelScope.launch {
        val current = _state.value
        if (current.isSubmitting) return@launch
        val uid = requireUser() ?: return@launch
        val draft = current.draft
        val request = buildRequest(draft, uid) ?: return@launch
        val scheduleFor = draft.scheduleFor
        if (scheduleFor != null) {
            val now = Instant.now()
            if (!scheduleFor.isAfter(now)) {
                _state.update { it.copy(draftError = "Scheduled time must be in the future.") }
                return@launch
            }
            if (scheduleFor.isAfter(now.plus(Duration.ofDays(30)))) {
                _state.update { it.copy(draftError = "Polls can be scheduled at most 30 days ahead.") }
                return@launch
            }
        }

        _state.update { it.copy(isSubmitting = true, draftError = null) }
        val posted = runCatching {
            if (scheduleFor != null) {
                val body = JsonObject(
                    request + ("scheduledFor" to JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(scheduleFor)))
                )
                api.sendIgnoringBody(Endpoint("$base/schedule", HttpMethod.POST, encode(body)))
            } else {
                api.sendIgnoringBody(Endpoint(base, HttpMethod.POST, encode(JsonObject(request))))
            }
        }
        if (posted.isFailure) {
            val message = posted.exceptionOrNull()?.userFacingMessage ?: "Failed to create poll."
            _state.update { it.copy(isSubmitting = false, draftError = message) }
            return@launch
        }

        var templateFailure: String? = null
        val templateName = draft.templateName.trim()
        if (draft.saveAsTemplate && templateName.isNotEmpty()) {
            val templateBody = buildJsonObject {
                put("name", JsonPrimitive(templateName))
                put("question", request.getValue("question"))
                put("options", request.getValue("options"))
                put("defaultType", request.getValue("type"))
                put("allowMultipleVotes", request.getValue("allowMultipleVotes"))
                put("isAnonymous", request.getValue("isAnonymous"))
                put("allowVoteChanges", request.getValue("allowVoteChanges"))
                put("showResults", request.getValue("showResults"))
                put("userId", request.getValue("userId"))
            }
            runCatching {
                api.sendIgnoringBody(Endpoint("$base/templates", HttpMethod.POST, encode(templateBody)))
            }.onFailure { templateFailure = it.userFacingMessage }
        }

        _state.update {
            it.copy(
                isSubmitting = false,
                draft = PollDraft(),
                draftError = null,
                section = if (scheduleFor != null) PollsSection.SCHEDULED else PollsSection.POLLS,
            )
        }
        val verb = if (scheduleFor != null) "scheduled" else "posted"
        val failure = templateFailure
        if (failure != null) {
            postError("Poll $verb, but the template could not be saved. $failure")
        } else {
            postSuccess(if (scheduleFor != null) "Poll scheduled." else "Poll posted.")
        }
        reloadPolls()
        reloadScheduled()
        reloadTemplates()
    }

    private fun buildRequest(draft: PollDraft, uid: Snowflake): Map<String, JsonElement>? {
        fun fail(message: String): Map<String, JsonElement>? {
            _state.update { it.copy(draftError = message) }
            return null
        }

        val question = draft.question.trim()
        if (question.isEmpty()) return fail("Ask a question.")
        val channelId = draft.channelId ?: return fail("Pick the channel the poll is posted in.")
        val isYesNo = draft.type == PollType.YES_NO
        val isRoleRestricted = draft.type == PollType.ROLE_RESTRICTED
        val options = if (isYesNo) listOf("Yes", "No") else draft.options.map { it.trim() }.filter { it.isNotEmpty() }
        if (!isYesNo && options.size < PollDraft.MIN_OPTIONS) return fail("Add at least two options.")
        if (options.size > PollDraft.MAX_OPTIONS) return fail("Discord polls support at most 25 options.")
        if (isRoleRestricted && draft.allowedRoles.isEmpty()) return fail("Choose which roles may vote.")
        val duration = draft.durationMinutes.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (draft.durationMinutes.isNotBlank() &&
            (duration == null || duration < 1 || duration > PollDraft.MAX_DURATION_MINUTES)
        ) {
            return fail("Duration must be between 1 and ${PollDraft.MAX_DURATION_MINUTES} minutes.")
        }

        return linkedMapOf<String, JsonElement>(
            "question" to JsonPrimitive(question),
            "options" to JsonArray(options.map { text -> JsonObject(mapOf("text" to JsonPrimitive(text))) }),
            "type" to JsonPrimitive(draft.type.value),
            "channelId" to JsonPrimitive(channelId.asSnowflakeNumber()),
            "durationMinutes" to (duration?.let { JsonPrimitive(it) } ?: JsonNull),
            "allowMultipleVotes" to JsonPrimitive(draft.type == PollType.MULTI_CHOICE),
            "isAnonymous" to JsonPrimitive(draft.type == PollType.ANONYMOUS),
            "allowedRoles" to if (isRoleRestricted) {
                JsonArray(draft.allowedRoles.map { JsonPrimitive(it.asSnowflakeNumber()) })
            } else {
                JsonNull
            },
            "allowVoteChanges" to JsonPrimitive(draft.allowVoteChanges),
            "showResults" to JsonPrimitive(draft.showResults),
            "showProgressBars" to JsonPrimitive(draft.showProgressBars),
            "userId" to JsonPrimitive(uid.asSnowflakeNumber()),
        )
    }

    private suspend fun fetchPolls(includeInactive: Boolean): List<Poll> =
        api.send(Endpoint("$base?includeInactive=$includeInactive"), ListSerializer(Poll.serializer()))

    private fun reloadAfterMutation() {
        reloadPolls()
        reloadScheduled()
    }

    private fun requireUser(): Snowflake? {
        if (userId.isEmpty()) {
            postError("Your session is missing a user id. Sign in again.")
            return null
        }
        return userId
    }

    private fun editDraft(transform: (PollDraft) -> PollDraft) {
        _state.update { it.copy(draft = transform(it.draft), draftError = null) }
    }

    private fun encode(body: JsonObject): String = MewdekoJson.encodeToString(JsonObject.serializer(), body)
}
