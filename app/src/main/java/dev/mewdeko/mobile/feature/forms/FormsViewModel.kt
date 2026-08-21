package dev.mewdeko.mobile.feature.forms

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.jsonBool
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

/** The answer widget a question renders as. */
enum class FormQuestionType(val raw: String, val label: String) {
    SHORT_TEXT("short_text", "Short Text"),
    LONG_TEXT("long_text", "Long Text"),
    MULTIPLE_CHOICE("multiple_choice", "Multiple Choice"),
    CHECKBOXES("checkboxes", "Checkboxes"),
    DROPDOWN("dropdown", "Dropdown"),
    NUMBER("number", "Number"),
    EMAIL("email", "Email"),
    URL("url", "URL");

    /** Whether the question carries a fixed list of choices. */
    val supportsOptions: Boolean
        get() = this == MULTIPLE_CHOICE || this == CHECKBOXES || this == DROPDOWN

    /** Whether the question accepts min/max bounds. */
    val supportsValidation: Boolean
        get() = this == SHORT_TEXT || this == LONG_TEXT || this == CHECKBOXES || this == NUMBER

    companion object {
        /** Maps a wire value onto a type, defaulting to [SHORT_TEXT]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: SHORT_TEXT
    }
}

/** What a form is used for, which decides whether it has a review workflow. */
enum class FormType(val raw: Int, val label: String) {
    REGULAR(0, "Regular"),
    BAN_APPEAL(1, "Ban Appeal"),
    JOIN_APPLICATION(2, "Join Application");

    companion object {
        /** Maps a wire value onto a type, defaulting to [REGULAR]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: REGULAR
    }
}

/** Where a submitted response sits in the review workflow. */
enum class ResponseStatus(val raw: Int, val label: String) {
    PENDING(0, "Pending"),
    UNDER_REVIEW(1, "Under Review"),
    APPROVED(2, "Approved"),
    REJECTED(3, "Rejected");

    companion object {
        /** Maps a wire value onto a status, defaulting to [PENDING]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: PENDING
    }
}

/** How a question-based condition compares the parent answer. */
enum class FormConditionalOperator(val raw: String, val label: String) {
    EQUALS("equals", "Equals"),
    NOT_EQUALS("not_equals", "Not equals"),
    CONTAINS("contains", "Contains"),
    GREATER_THAN("greater_than", "Greater than"),
    LESS_THAN("less_than", "Less than");

    companion object {
        /** Maps a wire value onto an operator, defaulting to [EQUALS]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: EQUALS
    }
}

/** What a question's visibility is gated on. */
enum class FormConditionType(val raw: Int, val label: String) {
    QUESTION_BASED(0, "Based on question"),
    DISCORD_ROLE(1, "Has Discord role"),
    SERVER_TENURE(2, "Server tenure"),
    BOOST_STATUS(3, "Boost / Nitro"),
    PERMISSION(4, "Permission"),
    MULTIPLE_CONDITIONS(5, "Multiple conditions");

    companion object {
        /** Maps a wire value onto a condition type, defaulting to [QUESTION_BASED]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: QUESTION_BASED
    }
}

/** How the conditions in a group combine. */
enum class FormConditionLogicType(val raw: String) {
    AND("AND"),
    OR("OR");

    companion object {
        /** Maps a wire value onto a logic type, defaulting to [AND]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: AND
    }
}

/** The role change applied when a response is approved or rejected. */
enum class FormApprovalActionType(val raw: Int, val label: String) {
    NONE(0, "No role changes"),
    ADD_ROLES(1, "Add roles"),
    REMOVE_ROLES(2, "Remove roles");

    companion object {
        /** Maps a wire value onto an action, defaulting to [NONE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** One choice on a multiple-choice, checkbox, or dropdown question. */
@Serializable
data class FormQuestionOption(
    val id: Int = 0,
    val questionId: Int = 0,
    val optionText: String = "",
    val optionValue: String = "",
    val displayOrder: Int = 0,
)

/** One clause of a question's multi-condition visibility rule. */
@Serializable
data class FormQuestionCondition(
    val id: Int = 0,
    val questionId: Int = 0,
    val conditionGroup: Int = 0,
    val conditionType: Int = 0,
    val targetQuestionId: Int? = null,
    val targetRoleIds: String? = null,
    val operator: String? = null,
    val expectedValue: String? = null,
    val daysThreshold: Int? = null,
    val requiresBoost: Boolean? = null,
    val requiresNitro: Boolean? = null,
    val permissionFlags: Int? = null,
    val logicType: String = "AND",
)

/** One question on a form. */
@Serializable
data class FormQuestion(
    val id: Int = 0,
    val formId: Int = 0,
    val questionText: String = "",
    val questionType: String = "short_text",
    val isRequired: Boolean = false,
    val displayOrder: Int = 0,
    val placeholder: String? = null,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val conditionalParentQuestionId: Int? = null,
    val conditionalOperator: String? = null,
    val conditionalExpectedValue: String? = null,
    val conditionalType: Int = 0,
    val conditionalRoleIds: String? = null,
    val conditionalDaysInServer: Int? = null,
    val conditionalAccountAgeDays: Int? = null,
    val conditionalRequiresBoost: Boolean? = null,
    val conditionalRequiresNitro: Boolean? = null,
    val enableAnswerPiping: Boolean = false,
    val options: List<FormQuestionOption>? = null,
    val conditions: List<FormQuestionCondition>? = null,
) {
    /** The typed form of [questionType]. */
    val type: FormQuestionType get() = FormQuestionType.from(questionType)

    /** Whether this question is gated behind anything at all. */
    val isConditional: Boolean get() = conditionalType != 0 || conditions?.isNotEmpty() == true

    /**
     * The wire body for create and update.
     *
     * Options round-trip through their own endpoint, so they are deliberately
     * left out here.
     */
    fun payload(): String = jsonBody(
        "id" to id,
        "formId" to formId,
        "questionText" to questionText,
        "questionType" to questionType,
        "isRequired" to isRequired,
        "displayOrder" to displayOrder,
        "placeholder" to placeholder,
        "minValue" to minValue,
        "maxValue" to maxValue,
        "minLength" to minLength,
        "maxLength" to maxLength,
        "conditionalType" to conditionalType,
        "conditionalParentQuestionId" to conditionalParentQuestionId,
        "conditionalOperator" to conditionalOperator,
        "conditionalExpectedValue" to conditionalExpectedValue,
        "conditionalRoleIds" to conditionalRoleIds,
        "conditionalDaysInServer" to conditionalDaysInServer,
        "conditionalAccountAgeDays" to conditionalAccountAgeDays,
        "conditionalRequiresBoost" to conditionalRequiresBoost,
        "conditionalRequiresNitro" to conditionalRequiresNitro,
        "enableAnswerPiping" to enableAnswerPiping,
    )

    companion object {
        /** A fresh unsaved question belonging to [formId]. */
        fun blank(formId: Int, displayOrder: Int = 0) =
            FormQuestion(formId = formId, displayOrder = displayOrder)
    }
}

/** A form definition with its settings and counters. */
@Serializable
data class FormDefinition(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val guildId: Snowflake = "",
    val name: String = "",
    val description: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val submitChannelId: Snowflake? = null,
    val allowMultipleSubmissions: Boolean = false,
    val maxResponses: Int? = null,
    val requireCaptcha: Boolean = false,
    val isActive: Boolean = false,
    val isDraft: Boolean = true,
    val allowAnonymous: Boolean = false,
    val expiresAt: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val requiredRoleId: Snowflake? = null,
    val successMessage: String? = null,
    val formType: Int = 0,
    val allowExternalUsers: Boolean = false,
    val inviteMaxUses: Int? = null,
    val inviteMaxAge: Int? = null,
    val requireApproval: Boolean = false,
    val approvalActionType: Int = 0,
    val approvalRoleIds: String? = null,
    val rejectionActionType: Int = 0,
    val rejectionRoleIds: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val responseCount: Int? = null,
    val pendingCount: Int? = null,
) {
    /** The typed form of [formType]. */
    val type: FormType get() = FormType.from(formType)

    /** Whether this form runs submissions through the approval workflow. */
    val hasWorkflow: Boolean get() = formType != FormType.REGULAR.raw

    /** How many responses have come in. */
    val responses: Int get() = responseCount ?: 0

    /** How many responses are waiting on a reviewer. */
    val pending: Int get() = pendingCount ?: 0

    /**
     * The expiry timestamp, with Postgres' unbounded sentinels treated as
     * "no expiry" the way the dashboard does.
     */
    val expiry: String? get() = expiresAt?.takeIf { it != "infinity" && it != "-infinity" }

    /** Roles applied on approval, parsed out of the packed id string. */
    val approvalRoles: List<Snowflake> get() = approvalRoleIds.splitIds()

    /** Roles applied on rejection, parsed out of the packed id string. */
    val rejectionRoles: List<Snowflake> get() = rejectionRoleIds.splitIds()

    /** The wire body for create and update, omitting server-owned counters. */
    fun payload(): String = jsonBody(
        "id" to id,
        "guildId" to guildId,
        "name" to name,
        "description" to description,
        "submitChannelId" to submitChannelId,
        "allowMultipleSubmissions" to allowMultipleSubmissions,
        "maxResponses" to maxResponses,
        "requireCaptcha" to requireCaptcha,
        "isActive" to isActive,
        "isDraft" to isDraft,
        "allowAnonymous" to allowAnonymous,
        "expiresAt" to expiry,
        "requiredRoleId" to requiredRoleId,
        "successMessage" to successMessage,
        "formType" to formType,
        "allowExternalUsers" to allowExternalUsers,
        "inviteMaxUses" to inviteMaxUses,
        "inviteMaxAge" to inviteMaxAge,
        "requireApproval" to requireApproval,
        "approvalActionType" to approvalActionType,
        "approvalRoleIds" to approvalRoleIds,
        "rejectionActionType" to rejectionActionType,
        "rejectionRoleIds" to rejectionRoleIds,
        "createdBy" to createdBy,
    )
}

/** Splits a packed role id string, which the bot writes space or comma separated. */
private fun String?.splitIds(): List<Snowflake> =
    this?.split(',', ' ')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Packs role ids back into the string shape the bot stores. */
internal fun List<Snowflake>.packIds(): String? =
    takeIf { it.isNotEmpty() }?.joinToString(",")

/** A single submission's summary row. */
@Serializable
data class FormResponseRecord(
    val id: Int = 0,
    val formId: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val submittedAt: String? = null,
)

/** One page of a form's responses. */
@Serializable
data class PaginatedFormResponses(
    val responses: List<FormResponseRecord> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 25,
    val totalPages: Int = 1,
)

/** One answer within a submitted response. */
@Serializable
data class FormAnswerEntry(
    val id: Int = 0,
    val questionId: Int = 0,
    val answerText: String? = null,
    val answerValues: List<String>? = null,
    val question: FormQuestion? = null,
)

/** A submitted response with every answer it carried. */
@Serializable
data class FormResponseDetail(
    val response: FormResponseRecord = FormResponseRecord(),
    val answers: List<FormAnswerEntry> = emptyList(),
)

/** The review record attached to a response on a workflow form. */
@Serializable
data class FormResponseWorkflow(
    val id: Int = 0,
    val responseId: Int = 0,
    val status: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val reviewedBy: Snowflake? = null,
    val reviewNotes: String? = null,
    val actionTaken: Int = 0,
    val inviteCode: String? = null,
) {
    /** The typed form of [status]. */
    val state: ResponseStatus get() = ResponseStatus.from(status)
}

/** A response paired with its review record. */
@Serializable
data class FormResponseWithWorkflow(
    val response: FormResponseRecord = FormResponseRecord(),
    val workflow: FormResponseWorkflow = FormResponseWorkflow(),
) {
    /** The response id, which identifies the pair. */
    val id: Int get() = response.id
}

/** What the bot returns after approving a response. */
@Serializable
data class FormApprovalResult(
    val message: String = "",
    val inviteCode: String? = null,
)

/** Which part of a form's detail view is showing. */
enum class FormSection(val id: String, val label: String) {
    SETTINGS("settings", "Settings"),
    QUESTIONS("questions", "Questions"),
    RESPONSES("responses", "Responses"),
    REVIEW("review", "Review"),
}

/** Forms screen state. */
data class FormsState(
    val forms: List<FormDefinition> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val selected: FormDefinition? = null,
    val loadedSelected: FormDefinition? = null,
    val section: FormSection = FormSection.SETTINGS,
    val questions: List<FormQuestion> = emptyList(),
    val questionsLoading: Boolean = false,
    val responses: PaginatedFormResponses? = null,
    val responsePage: Int = 1,
    val responseDetail: FormResponseDetail? = null,
    val review: List<FormResponseWithWorkflow> = emptyList(),
    val reviewFilter: ResponseStatus? = null,
    val busy: Boolean = false,
) {
    /** Whether the open form has edits that have not been saved. */
    val hasUnsavedForm: Boolean get() = selected != null && selected != loadedSelected

    /** The sections available for the open form. */
    val sections: List<FormSection>
        get() = if (selected?.hasWorkflow == true) {
            FormSection.entries
        } else {
            listOf(FormSection.SETTINGS, FormSection.QUESTIONS, FormSection.RESPONSES)
        }
}

/** Member-facing forms: definitions, questions, responses, and review. */
@HiltViewModel
class FormsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(FormsState())

    /** Observable screen state. */
    val state: StateFlow<FormsState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads the form list along with the channel and role pickers. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val forms = async {
                runCatching {
                    api.send(
                        Endpoint("api/forms/guild/$guildId?activeOnly=false"),
                        ListSerializer(FormDefinition.serializer()),
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

            _state.update {
                it.copy(
                    forms = forms.await().sortedBy { form -> form.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableRoles = roles.await().sortedBy { role -> role.name.lowercase() },
                )
            }
        }
    }

    /** Opens a form's detail view and loads the section it lands on. */
    fun open(form: FormDefinition) {
        _state.update {
            it.copy(
                selected = form,
                loadedSelected = form,
                section = FormSection.SETTINGS,
                questions = emptyList(),
                responses = null,
                responsePage = 1,
                responseDetail = null,
                review = emptyList(),
                reviewFilter = null,
            )
        }
    }

    /** Returns to the form list, discarding any unsaved edits. */
    fun closeDetail() = _state.update { it.copy(selected = null, loadedSelected = null) }

    /** Switches the visible detail section, loading it on first view. */
    fun setSection(section: FormSection) {
        _state.update { it.copy(section = section) }
        when (section) {
            FormSection.QUESTIONS -> loadQuestions()
            FormSection.RESPONSES -> loadResponses()
            FormSection.REVIEW -> loadReview()
            FormSection.SETTINGS -> Unit
        }
    }

    /** Applies an edit to the open form without saving it. */
    fun editForm(transform: (FormDefinition) -> FormDefinition) =
        _state.update { it.copy(selected = it.selected?.let(transform)) }

    /** Creates a new draft form and opens it. */
    fun createForm(name: String) = launchAction("Failed to create form.") {
        val blank = FormDefinition(
            guildId = guildId,
            name = name.trim().ifEmpty { "New form" },
            createdBy = userId,
        )
        val created = api.send(
            Endpoint("api/forms/guild/$guildId", HttpMethod.POST, blank.payload()),
            FormDefinition.serializer(),
        )
        _state.update { it.copy(forms = listOf(created) + it.forms) }
        open(created)
        postSuccess("Form created.")
    }

    /** Writes the open form's settings back to the bot. */
    fun saveForm() {
        val form = _state.value.selected ?: return
        launchAction("Failed to save form.") {
            api.sendIgnoringBody(
                Endpoint("api/forms/${form.id}", HttpMethod.PUT, form.payload())
            )
            _state.update { current ->
                current.copy(
                    loadedSelected = form,
                    forms = current.forms.map { if (it.id == form.id) form else it },
                )
            }
            postSuccess("Saved.")
        }
    }

    /** Flips a form between visible and hidden to members. */
    fun toggleActive(form: FormDefinition) = launchAction("Failed to update form.") {
        val target = !form.isActive
        api.sendIgnoringBody(
            Endpoint("api/forms/${form.id}/active", HttpMethod.PATCH, jsonBool(target))
        )
        updateForm(form.id) { it.copy(isActive = target) }
        postSuccess(if (target) "Form activated." else "Form deactivated.")
    }

    /** Takes a draft form live. */
    fun publish(form: FormDefinition) = launchAction("Publish failed.") {
        api.sendIgnoringBody(Endpoint("api/forms/${form.id}/publish", HttpMethod.POST))
        updateForm(form.id) { it.copy(isDraft = false) }
        postSuccess("Form published.")
    }

    /** Copies a form, questions included, as a new draft. */
    fun duplicate(form: FormDefinition) = launchAction("Duplicate failed.") {
        api.send(
            Endpoint("api/forms/${form.id}/duplicate", HttpMethod.POST, jsonString(userId)),
            FormDefinition.serializer(),
        )
        load()
        postSuccess("Form duplicated.")
    }

    /** Deletes a form and everything submitted to it. */
    fun deleteForm(form: FormDefinition) = launchAction("Delete failed.") {
        api.sendIgnoringBody(Endpoint("api/forms/${form.id}", HttpMethod.DELETE))
        _state.update { current ->
            current.copy(
                forms = current.forms.filterNot { it.id == form.id },
                selected = current.selected?.takeIf { it.id != form.id },
                loadedSelected = current.loadedSelected?.takeIf { it.id != form.id },
            )
        }
        postSuccess("Form deleted.")
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
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    questions = questions.sortedBy { question -> question.displayOrder },
                    questionsLoading = false,
                )
            }
        }
    }

    /**
     * Creates or updates a question.
     *
     * The bot's question endpoints ignore any nested options, so newly added
     * options are posted individually once the question itself has an id.
     */
    fun saveQuestion(question: FormQuestion, isNew: Boolean) {
        val form = _state.value.selected ?: return
        launchAction("Failed to save question.") {
            val saved = if (isNew) {
                api.send(
                    Endpoint(
                        "api/forms/${form.id}/questions",
                        HttpMethod.POST,
                        question.copy(formId = form.id).payload(),
                    ),
                    FormQuestion.serializer(),
                )
            } else {
                api.sendIgnoringBody(
                    Endpoint(
                        "api/forms/questions/${question.id}",
                        HttpMethod.PUT,
                        question.payload(),
                    )
                )
                question
            }

            val existingOptions = if (isNew) {
                emptyList()
            } else {
                _state.value.questions.firstOrNull { it.id == question.id }?.options.orEmpty()
            }
            val wanted = question.options.orEmpty().filter { it.optionText.isNotBlank() }
            wanted.forEachIndexed { index, option ->
                val alreadyStored = existingOptions.any {
                    it.id != 0 && it.id == option.id && it.optionText == option.optionText
                }
                if (alreadyStored) return@forEachIndexed
                runCatching {
                    api.sendIgnoringBody(
                        Endpoint(
                            "api/forms/questions/${saved.id}/options",
                            HttpMethod.POST,
                            jsonBody(
                                "id" to 0,
                                "questionId" to saved.id,
                                "optionText" to option.optionText,
                                "optionValue" to option.optionValue.ifEmpty { option.optionText },
                                "displayOrder" to index,
                            ),
                        )
                    )
                }
            }

            loadQuestions()
            postSuccess(if (isNew) "Question added." else "Question saved.")
        }
    }

    /** Removes a question and every answer given to it. */
    fun deleteQuestion(question: FormQuestion) = launchAction("Failed to delete question.") {
        api.sendIgnoringBody(
            Endpoint("api/forms/questions/${question.id}", HttpMethod.DELETE)
        )
        _state.update { it.copy(questions = it.questions.filterNot { q -> q.id == question.id }) }
        postSuccess("Question deleted.")
    }

    /** Loads one page of the open form's responses. */
    fun loadResponses(page: Int = _state.value.responsePage) {
        val form = _state.value.selected ?: return
        launchAction("Failed to load responses.") {
            val data = api.send(
                Endpoint("api/forms/${form.id}/responses?page=$page&pageSize=25"),
                PaginatedFormResponses.serializer(),
            )
            _state.update { it.copy(responses = data, responsePage = page) }
        }
    }

    /** Opens one response's full answer list. */
    fun openResponse(response: FormResponseRecord) = launchAction("Failed to load response.") {
        val detail = api.send(
            Endpoint("api/forms/responses/${response.id}"),
            FormResponseDetail.serializer(),
        )
        _state.update { it.copy(responseDetail = detail) }
    }

    /** Closes the open response detail. */
    fun closeResponse() = _state.update { it.copy(responseDetail = null) }

    /** Deletes one submitted response. */
    fun deleteResponse(response: FormResponseRecord) =
        launchAction("Failed to delete response.") {
            api.sendIgnoringBody(
                Endpoint("api/forms/responses/${response.id}", HttpMethod.DELETE)
            )
            _state.update { current ->
                current.copy(
                    responseDetail = current.responseDetail?.takeIf {
                        it.response.id != response.id
                    },
                    responses = current.responses?.let { page ->
                        page.copy(
                            responses = page.responses.filterNot { it.id == response.id },
                            totalCount = (page.totalCount - 1).coerceAtLeast(0),
                        )
                    },
                )
            }
            postSuccess("Response deleted.")
        }

    /** Narrows the review queue to one status, or clears the filter with null. */
    fun setReviewFilter(status: ResponseStatus?) {
        _state.update { it.copy(reviewFilter = status) }
        loadReview()
    }

    /** Reloads the review queue for the current filter. */
    fun loadReview() {
        val form = _state.value.selected ?: return
        val filter = _state.value.reviewFilter
        launchAction("Failed to load review queue.") {
            val path = buildString {
                append("api/forms/${form.id}/responses/pending")
                filter?.let { append("?status=${it.raw}") }
            }
            val queue = runCatching {
                api.send(Endpoint(path), ListSerializer(FormResponseWithWorkflow.serializer()))
            }.getOrDefault(emptyList())
            _state.update { it.copy(review = queue) }
        }
    }

    /** Approves a response, optionally recording reviewer notes. */
    fun approve(entry: FormResponseWithWorkflow, notes: String) =
        launchAction("Approve failed.") {
            val result = api.send(
                Endpoint(
                    "api/forms/responses/${entry.id}/approve",
                    HttpMethod.POST,
                    jsonBody(
                        "reviewerId" to userId,
                        "notes" to notes.trim().takeIf { it.isNotEmpty() },
                    ),
                ),
                FormApprovalResult.serializer(),
            )
            loadReview()
            postSuccess(
                result.inviteCode?.let { "Approved. Invite: $it" }
                    ?: result.message.ifEmpty { "Response approved." }
            )
        }

    /** Rejects a response. The bot requires a reason here. */
    fun reject(entry: FormResponseWithWorkflow, notes: String) = launchAction("Reject failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/forms/responses/${entry.id}/reject",
                HttpMethod.POST,
                jsonBody("reviewerId" to userId, "notes" to notes.trim()),
            )
        )
        loadReview()
        postSuccess("Response rejected.")
    }

    private fun updateForm(id: Int, transform: (FormDefinition) -> FormDefinition) =
        _state.update { current ->
            current.copy(
                forms = current.forms.map { if (it.id == id) transform(it) else it },
                selected = current.selected?.let { if (it.id == id) transform(it) else it },
                loadedSelected = current.loadedSelected?.let {
                    if (it.id == id) transform(it) else it
                },
            )
        }
}
