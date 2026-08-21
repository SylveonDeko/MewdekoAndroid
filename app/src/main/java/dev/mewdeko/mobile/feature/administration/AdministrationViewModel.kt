package dev.mewdeko.mobile.feature.administration

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.AutoAssignRolesResponse
import dev.mewdeko.mobile.core.model.GuildRole
import dev.mewdeko.mobile.core.model.ScalarString
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.model.TextChannelLite
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
import dev.mewdeko.mobile.core.net.asSnowflakeNumber
import dev.mewdeko.mobile.core.net.jsonBody
import dev.mewdeko.mobile.core.net.snowflakeIds
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

/** Anti-raid's current configuration and hit counter. */
@Serializable
data class AntiRaidSummary(
    val enabled: Boolean = false,
    val userThreshold: Int = 0,
    val seconds: Int = 0,
    val action: Int = 0,
    val usersCount: Int = 0,
)

/** Anti-spam's current configuration and hit counter. */
@Serializable
data class AntiSpamSummary(
    val enabled: Boolean = false,
    val messageThreshold: Int = 0,
    val action: Int = 0,
    val muteTime: Int = 0,
    val userCount: Int = 0,
)

/** Anti-alt's current configuration and hit counter. */
@Serializable
data class AntiAltSummary(
    val enabled: Boolean = false,
    val minAge: String = "",
    val action: Int = 0,
    val counter: Int = 0,
)

/** Anti-mass-mention's current configuration and hit counter. */
@Serializable
data class AntiMassMentionSummary(
    val enabled: Boolean = false,
    val mentionThreshold: Int = 0,
    val maxMentionsInTimeWindow: Int = 0,
    val timeWindowSeconds: Int = 0,
    val action: Int = 0,
    val ignoreBots: Boolean = false,
    val userCount: Int = 0,
)

/** Anti-mass-post's current configuration and hit counter. */
@Serializable
data class AntiMassPostSummary(
    val enabled: Boolean = false,
    val channelThreshold: Int = 0,
    val timeWindowSeconds: Int = 0,
    val action: Int = 0,
    val counter: Int = 0,
)

/** Every protection module's state in one payload. */
@Serializable
data class ProtectionStatusDetail(
    val antiRaid: AntiRaidSummary = AntiRaidSummary(),
    val antiSpam: AntiSpamSummary = AntiSpamSummary(),
    val antiAlt: AntiAltSummary = AntiAltSummary(),
    val antiMassMention: AntiMassMentionSummary = AntiMassMentionSummary(),
    val antiMassPost: AntiMassPostSummary = AntiMassPostSummary(),
)

/** What a protection module does to an offender. */
enum class AntiPunishmentAction(val raw: Int, val label: String) {
    MUTE(0, "Mute"),
    KICK(1, "Kick"),
    BAN(2, "Ban"),
    SOFTBAN(3, "Soft-ban"),
    REMOVE_ROLES(4, "Remove roles"),
    CHAT_MUTE(5, "Chat mute"),
    VOICE_MUTE(6, "Voice mute"),
    ADD_ROLE(7, "Add role"),
    DELETE(8, "Delete"),
    WARN(9, "Warn"),
    TIMEOUT(10, "Timeout"),
    NONE(11, "None");

    companion object {
        /** Maps a wire value onto an action, defaulting to [MUTE]. */
        fun from(raw: Int) = entries.firstOrNull { it.raw == raw } ?: MUTE
    }
}

/** A role granted while a member sits in a particular voice channel. */
@Serializable
data class VoiceChannelRoleEntry(
    @Serializable(with = SnowflakeSerializer::class) val voiceChannelId: Snowflake? = null,
    @Serializable(with = SnowflakeSerializer::class) val roleId: Snowflake? = null,
)

/** One timezone the server can be set to. */
@Serializable
data class GuildTimezoneEntry(
    val id: String = "",
    val displayName: String = "",
    val offset: String = "",
)

/** Which protection module the edit sheet is configuring. */
enum class ProtectionEditor(val label: String) {
    RAID("Anti-raid"),
    SPAM("Anti-spam"),
    ALT("Anti-alt"),
    MASS_MENTION("Anti-mass-mention"),
}

/** Which part of the administration screen is showing. */
enum class AdminSection(val id: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    PROTECTION("protection", "Protection"),
    ROLES("roles", "Roles"),
    AUTOMATION("automation", "Automation"),
    ADVANCED("advanced", "Advanced"),
}

/** Administration screen state. */
data class AdministrationState(
    val protection: ProtectionStatusDetail? = null,
    val autoAssign: AutoAssignRolesResponse = AutoAssignRolesResponse(),
    val selfAssignable: List<Snowflake> = emptyList(),
    val autoBanRoles: List<Snowflake> = emptyList(),
    val voiceChannelRoles: List<VoiceChannelRoleEntry> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableTimezones: List<GuildTimezoneEntry> = emptyList(),
    val staffRoleId: Snowflake? = null,
    val memberRoleId: Snowflake? = null,
    val timezoneId: String = "UTC",
    val banMessage: String = "",
    val gameVoiceChannelEnabled: Boolean = false,
    val section: AdminSection = AdminSection.OVERVIEW,
) {
    /** How many of the five protection modules are switched on. */
    val activeProtections: Int
        get() = protection?.let {
            listOf(
                it.antiRaid.enabled,
                it.antiSpam.enabled,
                it.antiAlt.enabled,
                it.antiMassMention.enabled,
                it.antiMassPost.enabled,
            ).count { enabled -> enabled }
        } ?: 0
}

/** Server administration: protection, role automation, and bulk moderation. */
@HiltViewModel
class AdministrationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(AdministrationState())

    /** Observable screen state. */
    val state: StateFlow<AdministrationState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads every administration setting. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val protection = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/protection/status"),
                        ProtectionStatusDetail.serializer(),
                    )
                }.getOrNull()
            }
            val autoAssign = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/auto-assign-roles"),
                        AutoAssignRolesResponse.serializer(),
                    )
                }.getOrDefault(AutoAssignRolesResponse())
            }
            val selfAssignable = async { tolerantIds("self-assignable-roles") }
            val autoBan = async { ids("auto-ban-roles") }
            val roles = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/roles/$guildId"),
                        ListSerializer(GuildRole.serializer()),
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
            val voiceRoles = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/voice-channel-roles"),
                        ListSerializer(VoiceChannelRoleEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val timezones = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/timezones"),
                        ListSerializer(GuildTimezoneEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val staff = async { scalar("staff-role") }
            val member = async { scalar("member-role") }
            val timezone = async { scalar("timezone") }
            val banMessage = async { scalar("ban-message") }
            val gameVoice = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/game-voice-channel"),
                        Boolean.serializer(),
                    )
                }.getOrDefault(false)
            }

            _state.update {
                it.copy(
                    protection = protection.await(),
                    autoAssign = autoAssign.await(),
                    selfAssignable = selfAssignable.await(),
                    autoBanRoles = autoBan.await(),
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    voiceChannelRoles = voiceRoles.await(),
                    availableTimezones = timezones.await(),
                    staffRoleId = staff.await(),
                    memberRoleId = member.await(),
                    timezoneId = timezone.await() ?: "UTC",
                    banMessage = banMessage.await().orEmpty(),
                    gameVoiceChannelEnabled = gameVoice.await(),
                )
            }
        }
    }

    /** Switches the visible section. */
    fun setSection(section: AdminSection) = _state.update { it.copy(section = section) }

    /** Names the role that counts as staff, or clears it with null. */
    fun setStaffRole(roleId: Snowflake?) = launchAction("Failed to set staff role.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/staff-role",
                HttpMethod.POST,
                (roleId?.asSnowflakeNumber() ?: 0L).toString(),
            )
        )
        _state.update { it.copy(staffRoleId = roleId) }
        postSuccess("Staff role saved.")
    }

    /** Names the role that counts as a verified member, or clears it with null. */
    fun setMemberRole(roleId: Snowflake?) = launchAction("Failed to set member role.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/member-role",
                HttpMethod.POST,
                (roleId?.asSnowflakeNumber() ?: 0L).toString(),
            )
        )
        _state.update { it.copy(memberRoleId = roleId) }
        postSuccess("Member role saved.")
    }

    /** Adds or removes a role that gets its holder banned on sight. */
    fun toggleAutoBanRole(roleId: Snowflake) = launchAction("Failed to update auto-ban roles.") {
        val present = roleId in _state.value.autoBanRoles
        if (present) {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/auto-ban-roles/$roleId",
                    HttpMethod.DELETE,
                )
            )
        } else {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/auto-ban-roles",
                    HttpMethod.POST,
                    roleId.asSnowflakeNumber().toString(),
                )
            )
        }
        _state.update {
            it.copy(
                autoBanRoles = if (present) {
                    it.autoBanRoles - roleId
                } else {
                    it.autoBanRoles + roleId
                }
            )
        }
    }

    /** Adds or removes a role auto-applied to joining humans. */
    fun toggleAutoAssignNormal(roleId: Snowflake) = launchAction("Failed to update roles.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/auto-assign-roles/normal/$roleId/toggle",
                HttpMethod.POST,
            )
        )
        _state.update {
            it.copy(
                autoAssign = it.autoAssign.copy(
                    normalRoles = it.autoAssign.normalRoles.toggling(roleId),
                )
            )
        }
    }

    /** Adds or removes a role auto-applied to joining bots. */
    fun toggleAutoAssignBot(roleId: Snowflake) = launchAction("Failed to update roles.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/auto-assign-roles/bots/$roleId/toggle",
                HttpMethod.POST,
            )
        )
        _state.update {
            it.copy(
                autoAssign = it.autoAssign.copy(
                    botRoles = it.autoAssign.botRoles.toggling(roleId),
                )
            )
        }
    }

    /** Adds or removes a role members can grant themselves. */
    fun toggleSelfAssignable(roleId: Snowflake) = launchAction("Failed to update roles.") {
        val present = roleId in _state.value.selfAssignable
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/self-assignable-roles/$roleId",
                if (present) HttpMethod.DELETE else HttpMethod.POST,
            )
        )
        _state.update { it.copy(selfAssignable = it.selfAssignable.toggling(roleId)) }
    }

    /** Sets the timezone dates and schedules are rendered in. */
    fun setTimezone(timezoneId: String) = launchAction("Failed to set timezone.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/timezone",
                HttpMethod.POST,
                jsonBody("timezoneId" to timezoneId),
            )
        )
        _state.update { it.copy(timezoneId = timezoneId) }
        postSuccess("Timezone updated.")
    }

    /** Saves the DM sent to a member when they are banned. */
    fun saveBanMessage(message: String) = launchAction("Failed to save ban message.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/ban-message",
                HttpMethod.POST,
                jsonBody("message" to message),
            )
        )
        _state.update { it.copy(banMessage = message) }
        postSuccess("Ban message saved.")
    }

    /** Flips game-based voice channel role assignment. */
    fun toggleGameVoiceChannel() = launchAction("Failed to toggle.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/game-voice-channel/toggle",
                HttpMethod.POST,
            )
        )
        _state.update { it.copy(gameVoiceChannelEnabled = !it.gameVoiceChannelEnabled) }
    }

    /** Bans every listed user in one pass. */
    fun massBan(userIds: List<Snowflake>, reason: String?) = launchAction("Mass ban failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/mass-ban",
                HttpMethod.POST,
                jsonBody(
                    "userIds" to JsonArray(
                        userIds.mapNotNull { it.toLongOrNull() }.map { JsonPrimitive(it) }
                    ),
                    "reason" to reason.orEmpty(),
                ),
            )
        )
        postSuccess("Mass ban submitted for ${userIds.size} users.")
    }

    /** Bulk deletes the most recent messages in a channel. */
    fun prune(channelId: Snowflake, count: Int) = launchAction("Prune failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/prune",
                HttpMethod.POST,
                jsonBody(
                    "channelId" to channelId.asSnowflakeNumber(),
                    "count" to count,
                ),
            )
        )
        postSuccess("Pruned $count messages.")
    }

    /** Writes the anti-raid configuration. */
    fun saveAntiRaid(
        enabled: Boolean,
        userThreshold: Int,
        seconds: Int,
        action: Int,
        punishDuration: Int,
    ) = protectionSave(
        "anti-raid",
        "Anti-raid saved.",
        jsonBody(
            "enabled" to enabled,
            "userThreshold" to userThreshold,
            "seconds" to seconds,
            "action" to action,
            "punishDuration" to punishDuration,
        ),
    )

    /** Writes the anti-spam configuration. */
    fun saveAntiSpam(
        enabled: Boolean,
        messageThreshold: Int,
        action: Int,
        muteTime: Int,
        roleId: Snowflake?,
    ) = protectionSave(
        "anti-spam",
        "Anti-spam saved.",
        jsonBody(
            "enabled" to enabled,
            "messageThreshold" to messageThreshold,
            "action" to action,
            "muteTime" to muteTime,
            "roleId" to roleId?.toLongOrNull(),
        ),
    )

    /** Writes the anti-alt configuration. */
    fun saveAntiAlt(
        enabled: Boolean,
        minAgeMinutes: Int,
        action: Int,
        actionDurationMinutes: Int,
        roleId: Snowflake?,
    ) = protectionSave(
        "anti-alt",
        "Anti-alt saved.",
        jsonBody(
            "enabled" to enabled,
            "minAgeMinutes" to minAgeMinutes,
            "action" to action,
            "actionDurationMinutes" to actionDurationMinutes,
            "roleId" to roleId?.toLongOrNull(),
        ),
    )

    /** Writes the anti-mass-mention configuration. */
    fun saveAntiMassMention(
        enabled: Boolean,
        mentionThreshold: Int,
        timeWindowSeconds: Int,
        maxMentionsInTimeWindow: Int,
        ignoreBots: Boolean,
        action: Int,
        muteTime: Int,
        roleId: Snowflake?,
    ) = protectionSave(
        "anti-mass-mention",
        "Anti-mass-mention saved.",
        jsonBody(
            "enabled" to enabled,
            "mentionThreshold" to mentionThreshold,
            "timeWindowSeconds" to timeWindowSeconds,
            "maxMentionsInTimeWindow" to maxMentionsInTimeWindow,
            "ignoreBots" to ignoreBots,
            "action" to action,
            "muteTime" to muteTime,
            "roleId" to roleId?.toLongOrNull(),
        ),
    )

    private fun protectionSave(tail: String, success: String, body: String) =
        launchAction("Failed to save protection.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/protection/$tail",
                    HttpMethod.PUT,
                    body,
                )
            )
            load()
            postSuccess(success)
        }

    private suspend fun ids(tail: String): List<Snowflake> = runCatching {
        api.send(
            Endpoint("api/Administration/$guildId/$tail"),
            ListSerializer(SnowflakeSerializer),
        )
    }.getOrDefault(emptyList())

    /**
     * Reads a role id collection that may not arrive as a bare array.
     *
     * Self-assignable roles come back as a C# tuple, so the ids sit inside an
     * object alongside the exclusivity flag and the group names.
     */
    private suspend fun tolerantIds(tail: String): List<Snowflake> = runCatching {
        api.sendRaw(Endpoint("api/Administration/$guildId/$tail")).snowflakeIds()
    }.getOrDefault(emptyList())

    private suspend fun scalar(tail: String): String? = runCatching {
        api.send(Endpoint("api/Administration/$guildId/$tail"), ScalarString.serializer()).value
    }.getOrNull()?.takeIf { it.isNotEmpty() && it != "0" }
}

/** Adds the id if absent, removes it if present. */
private fun List<Snowflake>.toggling(id: Snowflake): List<Snowflake> =
    if (id in this) this - id else this + id
