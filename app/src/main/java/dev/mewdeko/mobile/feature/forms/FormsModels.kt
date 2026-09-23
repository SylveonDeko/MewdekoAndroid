package dev.mewdeko.mobile.feature.forms

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.MewdekoJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.putJsonArray

/** The answer widget a question renders as. */
enum class FormQuestionType(val raw: String, val label: String) {
    SHORT_TEXT("short_text", "Short Text"),
    LONG_TEXT("long_text", "Long Text"),
    MULTIPLE_CHOICE("multiple_choice", "Multiple Choice"),
    CHECKBOXES("checkboxes", "Checkboxes"),
    DROPDOWN("dropdown", "Dropdown"),
    NUMBER("number", "Number"),
    EMAIL("email", "Email"),
    URL("url", "URL"),
    SECTION_BREAK("section_break", "Section Break");

    /** Whether the question carries a fixed list of choices. */
    val supportsOptions: Boolean
        get() = this == MULTIPLE_CHOICE || this == CHECKBOXES || this == DROPDOWN

    /** Whether the question accepts min/max bounds. */
    val supportsValidation: Boolean
        get() = this == SHORT_TEXT || this == LONG_TEXT || this == CHECKBOXES || this == NUMBER

    /** Whether the question asks anything at all, rather than only laying the form out. */
    val isPresentational: Boolean get() = this == SECTION_BREAK

    companion object {
        /** Maps a wire value onto a type, defaulting to [SHORT_TEXT]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: SHORT_TEXT
    }
}

/** What a form is used for, which decides which extra settings apply. */
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
enum class ResponseStatus(val raw: Int, val label: String, val queryName: String, val key: String) {
    PENDING(0, "Pending", "Pending", "pending"),
    UNDER_REVIEW(1, "Under Review", "UnderReview", "underReview"),
    APPROVED(2, "Approved", "Approved", "approved"),
    REJECTED(3, "Rejected", "Rejected", "rejected");

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
    QUESTION_BASED(0, "Answer-based"),
    DISCORD_ROLE(1, "Role-based"),
    SERVER_TENURE(2, "Server tenure"),
    BOOST_STATUS(3, "Boost / Nitro"),
    PERMISSION(4, "Permission-based"),
    MULTIPLE_CONDITIONS(5, "Multiple conditions");

    companion object {
        /** Maps a wire value onto a condition type, defaulting to [QUESTION_BASED]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: QUESTION_BASED
    }
}

/** How several roles in a role-based condition combine. */
enum class FormRoleLogic(val raw: String, val label: String) {
    ANY("any", "Any of"),
    ALL("all", "All of"),
    NONE("none", "None of");

    companion object {
        /** Maps a wire value onto a logic type, defaulting to [ANY]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: ANY
    }
}

/** How the conditions in a multi-condition group combine with the group's running result. */
enum class FormConditionLogicType(val raw: String) {
    AND("AND"),
    OR("OR");

    companion object {
        /** Maps a wire value onto a logic type, defaulting to [AND]. */
        fun from(raw: String?) = entries.firstOrNull { it.raw == raw } ?: AND
    }
}

/** The role change applied when a response is approved or rejected, legacy single-list form. */
enum class FormApprovalActionType(val raw: Int, val label: String) {
    NONE(0, "No role changes"),
    ADD_ROLES(1, "Add roles"),
    REMOVE_ROLES(2, "Remove roles");

    companion object {
        /** Maps a wire value onto an action, defaulting to [NONE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

/** A permission flag a permission-based condition can require. */
data class FormPermissionFlag(val value: Long, val label: String)

/** The permissions a permission-based condition can test for, matching the dashboard's list. */
val FormPermissionFlags: List<FormPermissionFlag> = listOf(
    FormPermissionFlag(0x0000000008L, "Administrator"),
    FormPermissionFlag(0x0000000010L, "Manage Channels"),
    FormPermissionFlag(0x0000000020L, "Manage Guild"),
    FormPermissionFlag(0x0000002000L, "Manage Messages"),
    FormPermissionFlag(0x0000004000L, "Manage Nicknames"),
    FormPermissionFlag(0x0000010000L, "Manage Roles"),
    FormPermissionFlag(0x0000020000L, "Manage Webhooks"),
    FormPermissionFlag(0x0000000004L, "Ban Members"),
    FormPermissionFlag(0x0000000002L, "Kick Members"),
    FormPermissionFlag(0x0010000000L, "Moderate Members"),
    FormPermissionFlag(0x0000000400L, "View Audit Log"),
)

/** Splits a packed id string, which the bot writes comma or space separated. */
fun String?.splitIds(): List<Snowflake> =
    this?.split(',', ' ')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Packs ids back into the string shape the bot stores. */
fun List<Snowflake>.packIds(): String? = takeIf { it.isNotEmpty() }?.joinToString(",")

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
    val permissionFlags: Long? = null,
    val logicType: String = "AND",
    val createdAt: String? = null,
) {
    /** The typed form of [conditionType]. */
    val type: FormConditionType get() = FormConditionType.from(conditionType)

    /** The typed form of [logicType]. */
    val logic: FormConditionLogicType get() = FormConditionLogicType.from(logicType)

    /** The roles this clause tests for. */
    val roles: List<Snowflake> get() = targetRoleIds.splitIds()
}

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
    val conditionalRoleLogic: String? = null,
    val conditionalDaysInServer: Int? = null,
    val conditionalAccountAgeDays: Int? = null,
    val conditionalRequiresBoost: Boolean? = null,
    val conditionalRequiresNitro: Boolean? = null,
    val conditionalPermissionFlags: Long? = null,
    val requiredWhenParentQuestionId: Int? = null,
    val requiredWhenOperator: String? = null,
    val requiredWhenValue: String? = null,
    val enableAnswerPiping: Boolean = false,
    val imageUrl: String? = null,
    val createdAt: String? = null,
    val options: List<FormQuestionOption> = emptyList(),
    val conditions: List<FormQuestionCondition> = emptyList(),
) {
    /** The typed form of [questionType]. */
    val type: FormQuestionType get() = FormQuestionType.from(questionType)

    /** Whether this question is gated behind anything at all. */
    val isConditional: Boolean get() = conditionalType != 0

    /** Whether this question becomes required only when another answer matches. */
    val isConditionallyRequired: Boolean get() = requiredWhenParentQuestionId != null

    /** Roles this question is visible to, when its trigger is role-based. */
    val visibleToRoles: List<Snowflake> get() = conditionalRoleIds.splitIds()

    companion object {
        /** A fresh unsaved question belonging to [formId]. */
        fun blank(formId: Int, displayOrder: Int = 0) =
            FormQuestion(formId = formId, displayOrder = displayOrder)

        /** A fresh unsaved page break belonging to [formId]. */
        fun blankBreak(formId: Int, displayOrder: Int = 0) = FormQuestion(
            formId = formId,
            displayOrder = displayOrder,
            questionType = FormQuestionType.SECTION_BREAK.raw,
            questionText = "New page",
        )
    }
}

/** A form's full settings, as saved and loaded whole. */
@Serializable
data class Form(
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
    val autoApproveRoleIds: String? = null,
    val requireApproval: Boolean = false,
    val approvalActionType: Int = 0,
    val approvalRoleIds: String? = null,
    val rejectionActionType: Int = 0,
    val rejectionRoleIds: String? = null,
    val inviteMaxUses: Int? = null,
    val inviteMaxAge: Int? = null,
    val notificationWebhookUrl: String? = null,
    val opensAt: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val announceChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val announceRoleId: Snowflake? = null,
    val announceMessage: String? = null,
    val announcedAt: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val notifyRoleId: Snowflake? = null,
    val submitRoleIds: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val pendingRoleId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val reviewerRoleId: Snowflake? = null,
    val approveEmote: String? = null,
    val rejectEmote: String? = null,
    val minAccountAgeDays: Int? = null,
    val allowResubmitAfterRejection: Boolean = false,
    val blockReappealAfterRejection: Boolean = false,
    val maxAppealAttempts: Int? = null,
    val reappealCooldownDays: Int? = null,
    val appealDelayDays: Int? = null,
    val approvalAddRoleIds: String? = null,
    val approvalRemoveRoleIds: String? = null,
    val rejectionAddRoleIds: String? = null,
    val rejectionRemoveRoleIds: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val responseCount: Int? = null,
    val pendingCount: Int? = null,
    val questionCount: Int? = null,
) {
    /** The typed form of [formType]. */
    val type: FormType get() = FormType.from(formType)

    /** Whether this form's responses go through a review decision at all. */
    val reviewsResponses: Boolean get() = requireApproval || type != FormType.REGULAR

    /** How many responses have come in. */
    val responses: Int get() = responseCount ?: 0

    /** How many responses are waiting on a reviewer. */
    val pending: Int get() = pendingCount ?: 0

    /** How many questions the form asks. */
    val questions: Int get() = questionCount ?: 0

    /**
     * The expiry timestamp, with Postgres' unbounded sentinels treated as
     * "no expiry" the way the dashboard does.
     */
    val expiry: String? get() = expiresAt?.takeIf { it != "infinity" && it != "-infinity" }

    val autoApproveRoles: List<Snowflake> get() = autoApproveRoleIds.splitIds()
    val approvalRoles: List<Snowflake> get() = approvalRoleIds.splitIds()
    val rejectionRoles: List<Snowflake> get() = rejectionRoleIds.splitIds()
    val submitRoles: List<Snowflake> get() = submitRoleIds.splitIds()
    val approvalAddRoles: List<Snowflake> get() = approvalAddRoleIds.splitIds()
    val approvalRemoveRoles: List<Snowflake> get() = approvalRemoveRoleIds.splitIds()
    val rejectionAddRoles: List<Snowflake> get() = rejectionAddRoleIds.splitIds()
    val rejectionRemoveRoles: List<Snowflake> get() = rejectionRemoveRoleIds.splitIds()

    companion object {
        /** A fresh unsaved form for [guildId], created by [userId]. */
        fun blank(guildId: Snowflake, userId: Snowflake, name: String) = Form(
            guildId = guildId,
            createdBy = userId,
            name = name.trim().ifEmpty { "New form" },
        )
    }
}

/** A single submitted response. */
@Serializable
data class FormResponse(
    val id: Int = 0,
    val formId: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake? = null,
    val username: String? = null,
    val submittedAt: String? = null,
    val ipAddress: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val messageId: Snowflake? = null,
    val editedAt: String? = null,
    val formVersionId: Int? = null,
)

/** One answer within a submitted response. */
@Serializable
data class FormAnswer(
    val id: Int = 0,
    val responseId: Int = 0,
    val questionId: Int = 0,
    val answerText: String? = null,
    val answerValues: List<String>? = null,
    val questionText: String? = null,
    val questionType: String? = null,
    val answerDisplay: String? = null,
    val formVersionId: Int? = null,
    val createdAt: String? = null,
) {
    /** The answer as it should be shown, favouring the resolved display text. */
    val display: String
        get() = answerDisplay?.takeIf { it.isNotEmpty() }
            ?: answerValues?.takeIf { it.isNotEmpty() }?.joinToString(", ")
            ?: answerText.orEmpty()
}

/** The review record attached to a response on a form that reviews its responses. */
@Serializable
data class FormResponseWorkflow(
    val id: Int = 0,
    val responseId: Int = 0,
    val status: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val reviewedBy: Snowflake? = null,
    val reviewedAt: String? = null,
    val reviewNotes: String? = null,
    val actionTaken: Int = 0,
    val dmFailed: Boolean = false,
    val inviteCode: String? = null,
    val inviteExpiresAt: String? = null,
    val statusCheckToken: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    /** The typed form of [status]. */
    val state: ResponseStatus get() = ResponseStatus.from(status)
}

/** One response as it appears in the review queue, with its state and answers attached. */
@Serializable
data class QueuedResponse(
    val response: FormResponse = FormResponse(),
    val workflow: FormResponseWorkflow? = null,
    val answers: List<FormAnswer> = emptyList(),
    val revisionCount: Int = 0,
) {
    /** The typed review state, defaulting to pending when the form does not review responses. */
    val state: ResponseStatus get() = workflow?.state ?: ResponseStatus.PENDING

    /** Whether this response is still awaiting a decision. */
    val isReviewable: Boolean get() = state == ResponseStatus.PENDING || state == ResponseStatus.UNDER_REVIEW
}

/** A page of a form's responses, with the counts the filter chips need. */
@Serializable
data class ResponseQueuePage(
    val responses: List<QueuedResponse> = emptyList(),
    val totalCount: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 25,
    val totalPages: Int = 1,
    val statusCounts: Map<String, Int> = emptyMap(),
) {
    /** The count for one status, ignoring the current filter. */
    fun count(status: ResponseStatus): Int = statusCounts[status.key] ?: 0
}

/** What the bot returns after approving a response. */
@Serializable
data class FormApprovalResult(
    val message: String = "",
    val inviteCode: String? = null,
)

/** The emotes on the approve and reject buttons. */
@Serializable
data class FormReviewEmotes(
    val approveEmote: String? = null,
    val rejectEmote: String? = null,
)

/** A saved snapshot of a form, taken every time it is saved. */
@Serializable
data class FormVersion(
    val id: Int = 0,
    val versionNumber: Int = 0,
    val questionCount: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val createdBy: Snowflake? = null,
    val createdAt: String? = null,
)

/** A form's saved history, and the cap on how much of it is kept. */
@Serializable
data class FormVersionList(
    val versionsKept: Int = 0,
    val versions: List<FormVersion> = emptyList(),
)

/** One difference between a saved version of a form and the version before it. */
@Serializable
data class FormVersionChange(
    val kind: String = "Changed",
    val section: String = "",
    val label: String = "",
    val before: String? = null,
    val after: String? = null,
)

/** One answer as it stood in an earlier revision of a response. */
@Serializable
data class FormRevisionAnswer(
    val questionId: Int = 0,
    val questionText: String? = null,
    val questionType: String? = null,
    val answerText: String? = null,
    val answerValues: List<String>? = null,
    val answerDisplay: String? = null,
)

/** An earlier version of a response's answers, kept when the submitter edits it. */
@Serializable
data class FormResponseRevision(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val editedBy: Snowflake? = null,
    val createdAt: String? = null,
    val answers: List<FormRevisionAnswer> = emptyList(),
)

/** The share code the bot minted, or already had, for a form and instance. */
@Serializable
data class FormShareLinkResult(val shareCode: String = "")

/** One guild's emoji list, as returned by the mutual-guild emoji picker endpoint. */
@Serializable
data class FormEmojiGuildInfo(
    val guild: FormEmojiGuildSummary = FormEmojiGuildSummary(),
    val emojis: List<FormEmoji> = emptyList(),
)

/** Basic guild identity attached to a [FormEmojiGuildInfo] entry. */
@Serializable
data class FormEmojiGuildSummary(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val iconUrl: String? = null,
)

/** A single custom guild emoji available for the approve/reject review buttons. */
@Serializable
data class FormEmoji(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val name: String = "",
    val animated: Boolean = false,
    val isAvailable: Boolean? = null,
    val requireColons: Boolean = false,
    val url: String = "",
) {
    /** Discord message-format representation, e.g. `<:name:id>` or `<a:name:id>`. */
    val formatted: String get() = "<${if (animated) "a" else ""}:$name:$id>"
}

/** The body of a 400 response from save or publish: a message plus the reasons it failed. */
@Serializable
data class FormErrorResponse(
    val message: String? = null,
    val errors: List<String>? = null,
)

/**
 * Builds the wire body for a whole-form save: the form's settings plus every question with its
 * options and conditions, in the order they should display.
 *
 * Sending the whole tree in one call is what fixes the data loss a partial PUT caused: the bot's
 * save endpoint round-trips every column itself, so nothing the app does not render is ever wiped.
 *
 * A question added this session carries a negative temporary id, which the picker UI needs so it
 * can be told apart from every other unsaved question; here it is normalised back to zero, which
 * is what the server reads as "create a new one". A question with no text, other than a page
 * break, is dropped rather than saved, matching the dashboard's builder.
 */
fun buildFormSavePayload(form: Form, questions: List<FormQuestion>, userId: Snowflake): String {
    val payload = buildJsonObject {
        put("form", MewdekoJson.encodeToJsonElement(Form.serializer(), form))
        putJsonArray("questions") {
            questions
                .filter { it.type == FormQuestionType.SECTION_BREAK || it.questionText.trim().isNotEmpty() }
                .forEachIndexed { index, question ->
                    val normalized = question.copy(
                        id = if (question.id > 0) question.id else 0,
                        displayOrder = index,
                    )
                    addJsonObject {
                        put(
                            "question",
                            MewdekoJson.encodeToJsonElement(FormQuestion.serializer(), normalized),
                        )
                        put(
                            "options",
                            MewdekoJson.encodeToJsonElement(
                                ListSerializer(FormQuestionOption.serializer()),
                                question.options.filter { it.optionText.trim().isNotEmpty() },
                            ),
                        )
                        put(
                            "conditions",
                            MewdekoJson.encodeToJsonElement(
                                ListSerializer(FormQuestionCondition.serializer()),
                                question.conditions,
                            ),
                        )
                    }
                }
        }
        put("userId", JsonPrimitive(userId))
    }
    return MewdekoJson.encodeToString(JsonObject.serializer(), payload)
}

/** Which part of a form's detail view is showing. */
enum class FormSection(val id: String, val label: String) {
    SETTINGS("settings", "Settings"),
    QUESTIONS("questions", "Questions"),
    RESPONSES("responses", "Responses"),
    VERSIONS("versions", "History"),
}

/** Forms screen state. */
data class FormsState(
    val forms: List<Form> = emptyList(),
    val availableChannels: List<dev.mewdeko.mobile.core.model.TextChannelLite> = emptyList(),
    val availableRoles: List<dev.mewdeko.mobile.core.model.GuildRole> = emptyList(),
    val availableEmojiGuilds: List<FormEmojiGuildInfo> = emptyList(),
    val guildReviewEmotes: FormReviewEmotes = FormReviewEmotes(),
    val selected: Form? = null,
    val loadedSelected: Form? = null,
    val section: FormSection = FormSection.SETTINGS,
    val questions: List<FormQuestion> = emptyList(),
    val loadedQuestions: List<FormQuestion> = emptyList(),
    val questionsLoading: Boolean = false,
    val activePage: Int = 0,
    val responses: ResponseQueuePage? = null,
    val responsePage: Int = 1,
    val responseFilter: ResponseStatus? = null,
    val expandedResponseId: Int? = null,
    val responseRevisions: Map<Int, List<FormResponseRevision>> = emptyMap(),
    val versions: FormVersionList? = null,
    val versionDiff: Pair<Int, List<FormVersionChange>>? = null,
    val shareLink: String? = null,
) {
    /** Whether the open form has edits that have not been saved. */
    val hasUnsavedForm: Boolean
        get() = selected != null && (selected != loadedSelected || questions != loadedQuestions)
}
