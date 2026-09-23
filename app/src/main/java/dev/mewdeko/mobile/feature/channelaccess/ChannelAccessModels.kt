package dev.mewdeko.mobile.feature.channelaccess

import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.InstantSerializer
import kotlinx.serialization.Serializable
import java.time.Instant

/** How an approved applicant is let into the gated channel. */
enum class AccessGrantMode(val value: Int, val label: String) {
    ROLE(0, "Give them a role"),
    CHANNEL_PERMISSION(1, "Add them to the channel directly");

    companion object {
        /** Resolves a stored value, defaulting to the role mode. */
        fun from(value: Int): AccessGrantMode = entries.firstOrNull { it.value == value } ?: ROLE
    }
}

/** What happens to an application when its voting window runs out. */
enum class AccessExpiryBehavior(val value: Int, val label: String) {
    DENY(0, "Deny the application"),
    MAJORITY(1, "Whichever side has more votes"),
    STAY_OPEN(2, "Leave it open for staff");

    companion object {
        /** Resolves a stored value, defaulting to deny. */
        fun from(value: Int): AccessExpiryBehavior = entries.firstOrNull { it.value == value } ?: DENY
    }
}

/** The lifecycle state of an application. */
enum class AccessApplicationStatus(val value: Int, val label: String) {
    PENDING(0, "Pending"),
    APPROVED(1, "Approved"),
    DENIED(2, "Denied"),
    WITHDRAWN(3, "Withdrawn"),
    EXPIRED(4, "Expired");

    companion object {
        /** Resolves a stored value, defaulting to pending. */
        fun from(value: Int): AccessApplicationStatus = entries.firstOrNull { it.value == value } ?: PENDING
    }
}

/** A question on a gate's application form, from `ChannelAccessQuestionResponse`. */
@Serializable
data class ChannelAccessQuestion(
    val id: Int = 0,
    val position: Int = 0,
    val question: String = "",
    val placeholder: String? = null,
    val required: Boolean = true,
    val paragraph: Boolean = true,
)

/** An access gate on a locked channel, from `ChannelAccessGateResponse`. */
@Serializable
data class ChannelAccessGate(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val accessRoleId: Snowflake? = null,
    val grantMode: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val reviewChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val logChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val panelChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val panelMessageId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val voterRoleId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val pingRoleId: Snowflake? = null,
    val enabled: Boolean = false,
    val requiredApprovals: Int = 0,
    val requiredDenials: Int = 0,
    val voteDurationHours: Int = 0,
    val onExpiry: Int = 0,
    val allowAbstain: Boolean = false,
    val anonymousVotes: Boolean = false,
    val anonymousApplicant: Boolean = false,
    val minAccountAgeDays: Int = 0,
    val minServerAgeDays: Int = 0,
    val reapplyCooldownHours: Int = 0,
    val dmOnDecision: Boolean = false,
    val pendingApplications: Int = 0,
    val questions: List<ChannelAccessQuestion> = emptyList(),
) {
    /** The grant mode as an enum. */
    val grant: AccessGrantMode get() = AccessGrantMode.from(grantMode)
}

/** One answer on an application, from `ChannelAccessAnswerResponse`. */
@Serializable
data class ChannelAccessAnswer(
    val question: String = "",
    val answer: String = "",
)

/** One vote on an application, from `ChannelAccessVoteResponse`. */
@Serializable
data class ChannelAccessVote(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val vote: Int = 0,
    @Serializable(with = InstantSerializer::class) val votedAt: Instant? = null,
)

/** An application to join a gated channel, from `ChannelAccessApplicationResponse`. */
@Serializable
data class ChannelAccessApplication(
    val id: Int = 0,
    val configId: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val channelId: Snowflake = "",
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val avatarUrl: String? = null,
    val status: Int = 0,
    @Serializable(with = InstantSerializer::class) val expiresAt: Instant? = null,
    @Serializable(with = InstantSerializer::class) val resolvedAt: Instant? = null,
    @Serializable(with = SnowflakeSerializer::class) val resolvedBy: Snowflake? = null,
    val resolutionReason: String? = null,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant? = null,
    val approvals: Int = 0,
    val denials: Int = 0,
    val abstains: Int = 0,
    val answers: List<ChannelAccessAnswer> = emptyList(),
    val votes: List<ChannelAccessVote> = emptyList(),
) {
    /** The status as an enum. */
    val statusValue: AccessApplicationStatus get() = AccessApplicationStatus.from(status)
}

/** A user barred from applying, from `ChannelAccessBlacklistResponse`. */
@Serializable
data class ChannelAccessBlacklistEntry(
    val id: Int = 0,
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String? = null,
    val configId: Int? = null,
    val reason: String? = null,
    @Serializable(with = SnowflakeSerializer::class) val addedBy: Snowflake = "",
    @Serializable(with = InstantSerializer::class) val addedAt: Instant? = null,
)

/** Editable text drafts for a gate's numeric settings, kept as strings while typing. */
data class GateNumberDraft(
    val requiredApprovals: String = "",
    val requiredDenials: String = "",
    val voteDurationHours: String = "",
    val minAccountAgeDays: String = "",
    val minServerAgeDays: String = "",
    val reapplyCooldownHours: String = "",
) {
    companion object {
        /** Seeds a draft from the gate's current values. */
        fun from(gate: ChannelAccessGate) = GateNumberDraft(
            requiredApprovals = gate.requiredApprovals.toString(),
            requiredDenials = gate.requiredDenials.toString(),
            voteDurationHours = gate.voteDurationHours.toString(),
            minAccountAgeDays = gate.minAccountAgeDays.toString(),
            minServerAgeDays = gate.minServerAgeDays.toString(),
            reapplyCooldownHours = gate.reapplyCooldownHours.toString(),
        )
    }
}

/** The new question form shared by every gate editor. */
data class QuestionDraft(
    val question: String = "",
    val placeholder: String = "",
    val required: Boolean = true,
    val paragraph: Boolean = true,
)

/** Discord caps modal forms at five inputs, and the bot enforces the same limit. */
const val MaxQuestions = 5

/** Discord caps modal input labels at 45 characters. */
const val MaxQuestionLength = 45
