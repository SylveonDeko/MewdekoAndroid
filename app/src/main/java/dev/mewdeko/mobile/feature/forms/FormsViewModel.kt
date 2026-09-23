package dev.mewdeko.mobile.feature.forms

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.ApiError
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.MewdekoJson
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import javax.inject.Inject

/** Member-facing forms: build them, collect answers, review submissions, and track history. */
@HiltViewModel
class FormsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
    private val http: HttpClient,
    private val auth: AuthManager,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(FormsState())

    /** Observable screen state. */
    val state: StateFlow<FormsState> = _state.asStateFlow()

    /**
     * Counter for temporary ids given to questions and page breaks added this session. Negative
     * numbers cannot collide with anything saved, and the save payload normalises them back to
     * zero, which is what still lets an unsaved question be picked as another question's
     * condition or "required when" parent before either has ever been saved.
     */
    private var nextTempId = -1

    init {
        load()
    }

    private fun takeTempId(): Int {
        val id = nextTempId
        nextTempId -= 1
        return id
    }

    /** Reloads the form list along with the channel, role, and guild default pickers. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val forms = async {
                runCatching {
                    api.send(
                        Endpoint("api/forms/guild/$guildId?activeOnly=false"),
                        ListSerializer(Form.serializer()),
                    )
                }.getOrDefault(emptyList())
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
            val emotes = async {
                runCatching {
                    api.send(
                        Endpoint("api/forms/guild/$guildId/review-emotes"),
                        FormReviewEmotes.serializer(),
                    )
                }.getOrDefault(FormReviewEmotes())
            }
            val emojiGuilds = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/emojis/$userId?adminOnly=false"),
                        ListSerializer(FormEmojiGuildInfo.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            _state.update {
                it.copy(
                    forms = forms.await().sortedBy { form -> form.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await().sortedBy { role -> role.name.lowercase() },
                    guildReviewEmotes = emotes.await(),
                    availableEmojiGuilds = emojiGuilds.await(),
                )
            }
        }
    }

    /** Opens a form's detail view and loads its questions. */
    fun open(form: Form) {
        _state.update {
            it.copy(
                selected = form,
                loadedSelected = form,
                section = FormSection.SETTINGS,
                questions = emptyList(),
                loadedQuestions = emptyList(),
                activePage = 0,
                responses = null,
                responsePage = 1,
                responseFilter = null,
                expandedResponseId = null,
                responseRevisions = emptyMap(),
                versions = null,
                versionDiff = null,
            )
        }
        loadQuestions()
    }

    /** Returns to the form list, discarding any unsaved edits. */
    fun closeDetail() = _state.update { it.copy(selected = null, loadedSelected = null) }

    /** Switches the visible detail section, loading it on first view. */
    fun setSection(section: FormSection) {
        _state.update { it.copy(section = section) }
        when (section) {
            FormSection.RESPONSES -> loadResponses()
            FormSection.VERSIONS -> loadVersions()
            FormSection.SETTINGS, FormSection.QUESTIONS -> Unit
        }
    }

    /** Applies an edit to the open form without saving it. */
    fun editForm(transform: (Form) -> Form) = _state.update { it.copy(selected = it.selected?.let(transform)) }

    /** Applies an edit to the open form's local question list without saving it. */
    fun editQuestions(transform: (List<FormQuestion>) -> List<FormQuestion>) =
        _state.update { it.copy(questions = transform(it.questions)) }

    /**
     * Creates a new draft form with a name and a type and opens its builder.
     *
     * The type decides which extra settings apply and, like the dashboard, is fixed once the form
     * is created: every later edit goes through [saveForm], which round-trips whatever type the
     * form already has.
     */
    fun createForm(name: String, formType: Int = FormType.REGULAR.raw) = launchAction("Failed to create form.") {
        val blank = Form.blank(guildId, userId, name).copy(formType = formType)
        val saved = api.send(
            Endpoint(
                "api/forms/guild/$guildId/save",
                HttpMethod.POST,
                buildFormSavePayload(blank, emptyList(), userId),
            ),
            Form.serializer(),
        )
        _state.update { it.copy(forms = listOf(saved) + it.forms) }
        open(saved)
    }

    /**
     * Writes the open form's settings and questions back to the bot in one call.
     *
     * The whole tree is round-tripped every time, which is what keeps a save from wiping any
     * setting or question field the app itself does not render a control for.
     */
    fun saveForm() {
        val form = _state.value.selected ?: return
        val questions = _state.value.questions
        launchAction("Failed to save form.") {
            val saved = try {
                api.send(
                    Endpoint(
                        "api/forms/guild/$guildId/save",
                        HttpMethod.POST,
                        buildFormSavePayload(form, questions, userId),
                    ),
                    Form.serializer(),
                )
            } catch (error: ApiError.Http) {
                postError(saveErrorMessage(error))
                return@launchAction
            }
            val savedQuestions = runCatching {
                api.send(
                    Endpoint("api/forms/${saved.id}/questions"),
                    ListSerializer(FormQuestion.serializer()),
                )
            }.getOrDefault(questions).sortedBy { it.displayOrder }
            _state.update { current ->
                current.copy(
                    selected = saved,
                    loadedSelected = saved,
                    questions = savedQuestions,
                    loadedQuestions = savedQuestions,
                    forms = current.forms.map { if (it.id == saved.id) saved else it }
                        .let { list -> if (list.any { it.id == saved.id }) list else list + saved },
                )
            }
            postSuccess("Form saved.")
        }
    }

    /** Flips a form between visible and hidden to members. */
    fun toggleActive(form: Form) = launchAction("Failed to update form.") {
        val target = !form.isActive
        api.sendIgnoringBody(
            Endpoint("api/forms/${form.id}/active", HttpMethod.PATCH, target.toString())
        )
        updateForm(form.id) { it.copy(isActive = target) }
    }

    /** Takes a draft form live, or surfaces the reasons the server refused to. */
    fun publish(form: Form) = launchAction("Publish failed.") {
        try {
            api.sendIgnoringBody(Endpoint("api/forms/${form.id}/publish", HttpMethod.POST))
        } catch (error: ApiError.Http) {
            postError(saveErrorMessage(error, fallback = "This form cannot be published yet."))
            return@launchAction
        }
        updateForm(form.id) { it.copy(isDraft = false) }
        postSuccess("Form published.")
    }

    /** Copies a form, questions included, as a new draft. */
    fun duplicate(form: Form) = launchAction("Duplicate failed.") {
        val duplicated = api.send(
            Endpoint("api/forms/${form.id}/duplicate", HttpMethod.POST, jsonString(userId)),
            Form.serializer(),
        )
        _state.update { it.copy(forms = listOf(duplicated) + it.forms) }
        postSuccess("Form duplicated as \"${duplicated.name}\".")
    }

    /** Deletes a form and everything submitted to it. */
    fun deleteForm(form: Form) = launchAction("Delete failed.") {
        api.sendIgnoringBody(Endpoint("api/forms/${form.id}", HttpMethod.DELETE))
        _state.update { current ->
            current.copy(
                forms = current.forms.filterNot { it.id == form.id },
                selected = current.selected?.takeIf { it.id != form.id },
                loadedSelected = current.loadedSelected?.takeIf { it.id != form.id },
            )
        }
    }

    /** Generates, or reuses, this form's permanent share link and stores it for display. */
    fun requestShareLink(form: Form) = launchAction("Failed to generate a share link.") {
        val base = auth.currentBaseUrl()?.trimEnd('/') ?: return@launchAction
        val result = api.send(
            Endpoint(
                "api/forms/${form.id}/share-link",
                HttpMethod.POST,
                jsonBody("instanceIdentifier" to MobileInstanceIdentifier),
            ),
            FormShareLinkResult.serializer(),
        )
        _state.update { it.copy(shareLink = "$base/forms/${result.shareCode}") }
    }

    /** Clears the pending share link once its dialog has been shown. */
    fun clearShareLink() = _state.update { it.copy(shareLink = null) }

    /** Generates a preview link for this form and returns it for the caller to open. */
    suspend fun previewUrl(form: Form): String? {
        val base = auth.currentBaseUrl()?.trimEnd('/') ?: return null
        return runCatching {
            val result = api.send(
                Endpoint(
                    "api/forms/${form.id}/share-link",
                    HttpMethod.POST,
                    jsonBody("instanceIdentifier" to MobileInstanceIdentifier),
                ),
                FormShareLinkResult.serializer(),
            )
            "$base/forms/${result.shareCode}?preview=true"
        }.getOrNull()
    }

    /** Reloads the open form's questions in display order. */
    fun loadQuestions() {
        val form = _state.value.selected ?: return
        _state.update { it.copy(questionsLoading = true) }
        launchAction("Failed to load questions.") {
            val questions = runCatching {
                api.send(
                    Endpoint("api/forms/${form.id}/questions"),
                    ListSerializer(FormQuestion.serializer()),
                )
            }.getOrDefault(emptyList()).sortedBy { it.displayOrder }
            _state.update {
                it.copy(questions = questions, loadedQuestions = questions, questionsLoading = false)
            }
        }
    }

    /**
     * Adds a new question of [type] to the end of the page being edited, and returns where it
     * landed so the caller can open it straight into the editor. Can be called any number of
     * times before saving: each one gets its own negative temporary id, so several can be added,
     * wired into each other's conditions, and saved together in one call.
     */
    fun addQuestion(type: FormQuestionType = FormQuestionType.SHORT_TEXT): Int {
        val form = _state.value.selected ?: return -1
        val fresh = FormQuestion(id = takeTempId(), formId = form.id, questionType = type.raw)
        val (next, index) = formPageInsertQuestion(_state.value.questions, fresh, _state.value.activePage)
        _state.update { it.copy(questions = next) }
        return index
    }

    /** Replaces one question in the local working list, matched by its position. */
    fun replaceQuestionAt(index: Int, question: FormQuestion) = editQuestions { current ->
        current.toMutableList().also { if (index in it.indices) it[index] = question }
    }

    /** Duplicates a question in place, right after the original. */
    fun duplicateQuestionAt(index: Int) {
        val current = _state.value.questions
        val source = current.getOrNull(index) ?: return
        val copy = source.copy(id = takeTempId(), questionText = "${source.questionText} (copy)")
        _state.update { it.copy(questions = formQuestionDuplicateInList(current, index, copy)) }
    }

    /** Removes a question, or a page break, from the local working list. */
    fun removeQuestionAt(index: Int) = editQuestions { current ->
        current.toMutableList().also { if (index in it.indices) it.removeAt(index) }
    }

    /** Moves a question one place earlier or later within its own page. */
    fun moveQuestion(index: Int, forward: Boolean) {
        val moved = formQuestionMoveInPage(_state.value.questions, index, forward) ?: return
        _state.update { it.copy(questions = moved) }
    }

    /** Switches which page of the form the question builder is showing. */
    fun setActivePage(page: Int) = _state.update { it.copy(activePage = page) }

    /** Starts a new page right after the one being edited, and moves onto it. */
    fun addPage() {
        val form = _state.value.selected ?: return
        val brk = FormQuestion.blankBreak(form.id).copy(id = takeTempId())
        val (next, activePage) = formPageAdd(_state.value.questions, brk, _state.value.activePage)
        _state.update { it.copy(questions = next, activePage = activePage) }
    }

    /** Gives the first page a heading, which it does not have until a break is put above it. */
    fun addHeadingToFirstPage() {
        val form = _state.value.selected ?: return
        val brk = FormQuestion.blankBreak(form.id).copy(id = takeTempId())
        _state.update {
            it.copy(questions = formPageAddHeading(it.questions, brk), activePage = 0)
        }
    }

    /**
     * Swaps the page being edited with its neighbour, heading and every question on it moving
     * together. Refuses, with an error, when the first page has no heading of its own to move.
     */
    fun movePage(forward: Boolean) {
        val result = formPageMove(_state.value.questions, _state.value.activePage, forward)
        if (result == null) {
            postError("Give the first page a heading before moving pages around.")
            return
        }
        _state.update { it.copy(questions = result.first, activePage = result.second) }
    }

    /** Removes the page being edited; its questions join the page before it. */
    fun removePage() {
        val result = formPageRemove(_state.value.questions, _state.value.activePage) ?: return
        _state.update { it.copy(questions = result.first, activePage = result.second) }
    }

    /** Writes a field of the section break heading the given page. */
    fun updatePageHeading(headingIndex: Int, transform: (FormQuestion) -> FormQuestion) = editQuestions { current ->
        current.toMutableList().also { list ->
            if (headingIndex in list.indices) list[headingIndex] = transform(list[headingIndex])
        }
    }

    /** Loads one page of the open form's unified response queue. */
    fun loadResponses(page: Int = _state.value.responsePage, status: ResponseStatus? = _state.value.responseFilter) {
        val form = _state.value.selected ?: return
        launchAction("Failed to load responses.") {
            val path = buildString {
                append("api/forms/${form.id}/responses?page=$page&pageSize=25")
                status?.let { append("&status=${it.queryName}") }
            }
            val data = api.send(Endpoint(path), ResponseQueuePage.serializer())
            _state.update { it.copy(responses = data, responsePage = page, responseFilter = status) }
        }
    }

    /** Narrows the response queue to one status, or clears the filter with null. */
    fun setResponseFilter(status: ResponseStatus?) = loadResponses(page = 1, status = status)

    /** Expands, or collapses, one response's inline answers. */
    fun toggleResponseExpanded(response: QueuedResponse) = _state.update {
        val id = response.response.id
        it.copy(expandedResponseId = if (it.expandedResponseId == id) null else id)
    }

    /** Loads the edit history of one response's answers. */
    fun loadResponseRevisions(response: QueuedResponse) = launchAction("Failed to load revision history.") {
        val revisions = api.send(
            Endpoint("api/forms/responses/${response.response.id}/revisions"),
            ListSerializer(FormResponseRevision.serializer()),
        )
        _state.update { it.copy(responseRevisions = it.responseRevisions + (response.response.id to revisions)) }
    }

    /** Approves a response, optionally recording reviewer notes. */
    fun approve(response: QueuedResponse, notes: String) = launchAction("Approve failed.") {
        val result = api.send(
            Endpoint(
                "api/forms/responses/${response.response.id}/approve",
                HttpMethod.POST,
                jsonBody("reviewerId" to userId, "notes" to notes.trim().takeIf { it.isNotEmpty() }),
            ),
            FormApprovalResult.serializer(),
        )
        loadResponses()
        postSuccess(result.inviteCode?.let { "Approved. Invite: $it" } ?: "Response approved.")
    }

    /** Rejects a response. The bot requires a reason here. */
    fun reject(response: QueuedResponse, notes: String) = launchAction("Reject failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/forms/responses/${response.response.id}/reject",
                HttpMethod.POST,
                jsonBody("reviewerId" to userId, "notes" to notes.trim()),
            )
        )
        loadResponses()
        postSuccess("Response rejected.")
    }

    /** Deletes one submitted response. */
    fun deleteResponse(response: QueuedResponse) = launchAction("Failed to delete response.") {
        api.sendIgnoringBody(Endpoint("api/forms/responses/${response.response.id}", HttpMethod.DELETE))
        loadResponses()
        postSuccess("Response deleted.")
    }

    /**
     * Fetches the form's responses as CSV text.
     *
     * [ApiClient] only decodes JSON, so this issues the request directly with the same
     * credentials, matching the bot instance header the rest of the screen uses.
     */
    suspend fun exportResponsesCsv(form: Form): String? {
        val base = auth.currentBaseUrl() ?: return null
        return runCatching {
            val response = http.get("${base.trimEnd('/')}/api/forms/${form.id}/responses/export") {
                header("Authorization", "Bearer ${auth.currentAccessToken()}")
                api.currentInstance()?.let { header("X-Mobile-Instance", it) }
            }
            response.bodyAsText()
        }.getOrNull()
    }

    /** Reloads the open form's saved version history. */
    fun loadVersions() {
        val form = _state.value.selected ?: return
        launchAction("Failed to load version history.") {
            val versions = api.send(Endpoint("api/forms/${form.id}/versions"), FormVersionList.serializer())
            _state.update { it.copy(versions = versions) }
        }
    }

    /** Loads what one saved version changed compared to the version before it. */
    fun loadVersionDiff(version: FormVersion) = launchAction("Failed to compare versions.") {
        val form = _state.value.selected ?: return@launchAction
        val changes = api.send(
            Endpoint("api/forms/${form.id}/versions/${version.versionNumber}/diff"),
            ListSerializer(FormVersionChange.serializer()),
        )
        _state.update { it.copy(versionDiff = version.versionNumber to changes) }
    }

    /** Clears the open version diff. */
    fun clearVersionDiff() = _state.update { it.copy(versionDiff = null) }

    /** Restores the form to a saved version. */
    fun restoreVersion(version: FormVersion) = launchAction("Failed to restore that version.") {
        val form = _state.value.selected ?: return@launchAction
        api.sendIgnoringBody(
            Endpoint(
                "api/forms/${form.id}/versions/${version.versionNumber}/restore",
                HttpMethod.POST,
                jsonString(userId),
            )
        )
        val restored = api.send(Endpoint("api/forms/${form.id}"), Form.serializer())
        val questions = runCatching {
            api.send(Endpoint("api/forms/${form.id}/questions"), ListSerializer(FormQuestion.serializer()))
        }.getOrDefault(emptyList()).sortedBy { it.displayOrder }
        _state.update {
            it.copy(
                selected = restored,
                loadedSelected = restored,
                questions = questions,
                loadedQuestions = questions,
                versionDiff = null,
            )
        }
        loadVersions()
        postSuccess("Form restored to version ${version.versionNumber}.")
    }

    /** Saves the guild's default review button emotes. */
    fun saveGuildReviewEmotes(approve: String?, reject: String?) = launchAction("Failed to save defaults.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/forms/guild/$guildId/review-emotes",
                HttpMethod.POST,
                jsonBody("approveEmote" to approve, "rejectEmote" to reject),
            )
        )
        _state.update { it.copy(guildReviewEmotes = FormReviewEmotes(approve, reject)) }
        postSuccess("Review emotes saved.")
    }

    private fun saveErrorMessage(error: ApiError.Http, fallback: String = "This form could not be saved."): String {
        val parsed = runCatching {
            MewdekoJson.decodeFromString(FormErrorResponse.serializer(), error.body)
        }.getOrNull()
        val reasons = parsed?.errors?.filter { it.isNotBlank() }
        return when {
            !reasons.isNullOrEmpty() -> reasons.joinToString(". ")
            !parsed?.message.isNullOrBlank() -> parsed?.message!!
            else -> fallback
        }
    }

    private fun updateForm(id: Int, transform: (Form) -> Form) = _state.update { current ->
        current.copy(
            forms = current.forms.map { if (it.id == id) transform(it) else it },
            selected = current.selected?.let { if (it.id == id) transform(it) else it },
            loadedSelected = current.loadedSelected?.let { if (it.id == id) transform(it) else it },
        )
    }

    companion object {
        /** Stands in for the dashboard's per-instance port, which mobile has no equivalent of. */
        private const val MobileInstanceIdentifier = "mobile"
    }
}
