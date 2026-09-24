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
import dev.mewdeko.mobile.core.net.jsonInt
import dev.mewdeko.mobile.core.net.jsonString
import dev.mewdeko.mobile.core.net.snowflakeIds
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject

/** Administration screen state. */
data class AdministrationState(
    val protection: ProtectionStatusDetail? = null,
    val imageHash: AntiImageHashSummary = AntiImageHashSummary(),
    val autoAssign: AutoAssignRolesResponse = AutoAssignRolesResponse(),
    val selfAssignable: SelfAssignableRolesPayload = SelfAssignableRolesPayload(),
    val autoBanRoles: List<Snowflake> = emptyList(),
    val voiceChannelRoles: List<VoiceChannelRoleEntry> = emptyList(),
    val reactionRoles: List<ReactionRoleMessageEntry> = emptyList(),
    val availableRoles: List<GuildRole> = emptyList(),
    val availableChannels: List<TextChannelLite> = emptyList(),
    val availableVoiceChannels: List<TextChannelLite> = emptyList(),
    val availableCategories: List<TextChannelLite> = emptyList(),
    val availableTimezones: List<GuildTimezoneEntry> = emptyList(),
    val antiPatternPatterns: List<AntiPatternPatternEntry> = emptyList(),
    val bannedImageHashes: List<BannedImageHashEntry> = emptyList(),
    val commandCooldowns: List<CommandCooldownEntry> = emptyList(),
    val permissionOverrides: List<PermissionOverrideEntry> = emptyList(),
    val permissions: PermissionCachePayload = PermissionCachePayload(),
    val modules: List<ModuleInfo> = emptyList(),
    val serverRecovery: ServerRecoveryStatusPayload = ServerRecoveryStatusPayload(),
    val deleteMessageOnCommand: DeleteMessageOnCommandPayload = DeleteMessageOnCommandPayload(),
    val statsOptOut: Boolean? = null,
    val autoDeleteSelfAssign: Boolean? = null,
    val staffRoleId: Snowflake? = null,
    val memberRoleId: Snowflake? = null,
    val timezoneId: String = "UTC",
    val banMessage: String = "",
    val gameVoiceChannelId: Snowflake? = null,
    val section: AdminSection = AdminSection.OVERVIEW,
) {
    /** How many of the seven protection modules are switched on. */
    val activeProtections: Int
        get() = protection?.let {
            listOf(
                it.antiRaid.enabled,
                it.antiSpam.enabled,
                it.antiAlt.enabled,
                it.antiMassMention.enabled,
                it.antiMassPost.enabled,
                it.antiPattern.enabled,
                it.antiPostChannel.enabled,
                imageHash.enabled,
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

    /**
     * Runs one-id list toggles one after another. A multi-select change can
     * fire several at once (Clear, for one), and the bot stores some of these
     * lists as a single value, so overlapping requests could drop an edit.
     */
    private val listToggleLock = Mutex()

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
            val imageHash = async {
                runCatching {
                    api.send(
                        Endpoint("api/Protection/$guildId/status"),
                        ImageHashStatusWrapper.serializer(),
                    ).antiImageHash
                }.getOrDefault(AntiImageHashSummary())
            }
            val autoAssign = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/auto-assign-roles"),
                        AutoAssignRolesResponse.serializer(),
                    )
                }.getOrDefault(AutoAssignRolesResponse())
            }
            val selfAssignable = async {
                runCatching {
                    api.sendRaw(Endpoint("api/Administration/$guildId/self-assignable-roles"))
                        .toSelfAssignableRolesPayload()
                }.getOrDefault(SelfAssignableRolesPayload())
            }
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
            val voiceChannels = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/channels/$guildId/1"),
                        ListSerializer(TextChannelLite.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val categories = async {
                runCatching {
                    api.send(
                        Endpoint("api/ClientOperations/categories/$guildId"),
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
            val reactionRoles = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/reaction-roles"),
                        ReactionRolesResponse.serializer(),
                    ).reactionRoles
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
            val patterns = async {
                runCatching {
                    api.send(
                        Endpoint("api/Protection/$guildId/anti-pattern/patterns"),
                        ListSerializer(AntiPatternPatternEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val hashes = async {
                runCatching {
                    api.send(
                        Endpoint("api/Protection/$guildId/anti-image-hash/hashes"),
                        ListSerializer(BannedImageHashEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val cooldowns = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/command-cooldowns"),
                        ListSerializer(CommandCooldownEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val overrides = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/permission-overrides"),
                        ListSerializer(PermissionOverrideEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val permissions = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/permissions"),
                        PermissionCachePayload.serializer(),
                    )
                }.getOrDefault(PermissionCachePayload())
            }
            val modules = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/commands"),
                        ListSerializer(ModuleInfo.serializer()),
                    )
                }.getOrDefault(emptyList())
            }
            val recovery = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/server-recovery"),
                        ServerRecoveryStatusPayload.serializer(),
                    )
                }.getOrDefault(ServerRecoveryStatusPayload())
            }
            val deleteMsg = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/delete-message-on-command"),
                        DeleteMessageOnCommandPayload.serializer(),
                    )
                }.getOrDefault(DeleteMessageOnCommandPayload())
            }
            val staff = async { scalar("staff-role") }
            val member = async { scalar("member-role") }
            val timezone = async { scalar("timezone") }
            val banMessage = async { scalar("ban-message") }
            val gameVoice = async {
                runCatching {
                    api.send(
                        Endpoint("api/Administration/$guildId/game-voice-channel"),
                        SnowflakeSerializer.nullable,
                    )
                }.getOrNull()
            }
            val guildConfig = async {
                runCatching {
                    api.sendRaw(Endpoint("api/GuildConfig/$guildId")) as? JsonObject
                }.getOrNull()
            }

            _state.update {
                it.copy(
                    protection = protection.await(),
                    imageHash = imageHash.await(),
                    autoAssign = autoAssign.await(),
                    selfAssignable = selfAssignable.await(),
                    autoBanRoles = autoBan.await(),
                    availableRoles = roles.await()
                        .filter { role -> role.id != guildId }
                        .sortedBy { role -> role.name.lowercase() },
                    availableChannels = channels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableVoiceChannels = voiceChannels.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    availableCategories = categories.await()
                        .sortedBy { channel -> channel.name.lowercase() },
                    voiceChannelRoles = voiceRoles.await(),
                    reactionRoles = reactionRoles.await(),
                    availableTimezones = timezones.await(),
                    antiPatternPatterns = patterns.await(),
                    bannedImageHashes = hashes.await(),
                    commandCooldowns = cooldowns.await(),
                    permissionOverrides = overrides.await(),
                    permissions = permissions.await(),
                    modules = modules.await(),
                    serverRecovery = recovery.await(),
                    deleteMessageOnCommand = deleteMsg.await(),
                    staffRoleId = staff.await(),
                    memberRoleId = member.await(),
                    timezoneId = timezone.await() ?: "UTC",
                    banMessage = banMessage.await().orEmpty(),
                    gameVoiceChannelId = gameVoice.await(),
                    statsOptOut = (guildConfig.await()?.get("statsOptOut") as? JsonPrimitive)?.booleanOrNull,
                    autoDeleteSelfAssign =
                        (guildConfig.await()?.get("autoDeleteSelfAssignedRoleMessages") as? JsonPrimitive)
                            ?.booleanOrNull,
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


    /** Adds or removes a role that gets its holder banned on sight. */
    fun toggleAutoBanRole(roleId: Snowflake) = launchAction("Failed to update auto-ban roles.") {
        listToggleLock.withLock {
            val present = roleId in _state.value.autoBanRoles
            if (present) {
                api.sendIgnoringBody(
                    Endpoint("api/Administration/$guildId/auto-ban-roles/$roleId", HttpMethod.DELETE)
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
                it.copy(autoBanRoles = if (present) it.autoBanRoles - roleId else it.autoBanRoles + roleId)
            }
        }
    }

    /** Adds or removes a role auto-applied to joining humans. */
    fun toggleAutoAssignNormal(roleId: Snowflake) = launchAction("Failed to update roles.") {
        listToggleLock.withLock {
            api.sendIgnoringBody(
                Endpoint("api/Administration/$guildId/auto-assign-roles/normal/$roleId/toggle", HttpMethod.POST)
            )
            _state.update {
                it.copy(autoAssign = it.autoAssign.copy(normalRoles = it.autoAssign.normalRoles.toggling(roleId)))
            }
        }
    }

    /** Adds or removes a role auto-applied to joining bots. */
    fun toggleAutoAssignBot(roleId: Snowflake) = launchAction("Failed to update roles.") {
        listToggleLock.withLock {
            api.sendIgnoringBody(
                Endpoint("api/Administration/$guildId/auto-assign-roles/bots/$roleId/toggle", HttpMethod.POST)
            )
            _state.update {
                it.copy(autoAssign = it.autoAssign.copy(botRoles = it.autoAssign.botRoles.toggling(roleId)))
            }
        }
    }

    /** Adds a self-assignable role with an optional group. */
    fun addSelfAssignableRole(roleId: Snowflake, group: Int, level: Int) =
        launchAction("Failed to add self-assignable role.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/self-assignable-roles/$roleId",
                    HttpMethod.POST,
                    jsonBody("group" to group),
                )
            )
            if (level > 0) {
                api.sendIgnoringBody(
                    Endpoint(
                        "api/Administration/$guildId/self-assignable-roles/$roleId/level",
                        HttpMethod.POST,
                        jsonInt(level),
                    )
                )
            }
            load()
        }

    /** Removes a self-assignable role. */
    fun removeSelfAssignableRole(roleId: Snowflake) = launchAction("Failed to remove role.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/self-assignable-roles/$roleId", HttpMethod.DELETE)
        )
        load()
    }

    /** Renames a self-assignable role group. */
    fun renameSelfAssignableGroup(group: Int, name: String) = launchAction("Failed to rename group.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/self-assignable-roles/groups",
                HttpMethod.POST,
                jsonBody("group" to group, "name" to name.ifBlank { null }),
            )
        )
        load()
    }

    /** Sets a self-assignable role's level requirement. */
    fun setSelfAssignableRoleLevel(roleId: Snowflake, level: Int) = launchAction("Failed to set level.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/self-assignable-roles/$roleId/level",
                HttpMethod.POST,
                jsonInt(level),
            )
        )
        load()
    }

    /** Toggles whether a member can hold only one self-assignable role per group. */
    fun toggleSelfAssignableExclusive() = launchAction("Failed to toggle exclusive mode.") {
        val exclusive = api.send(
            Endpoint("api/Administration/$guildId/self-assignable-roles/exclusive/toggle", HttpMethod.POST),
            Boolean.serializer(),
        )
        _state.update { it.copy(selfAssignable = it.selfAssignable.copy(exclusive = exclusive)) }
    }

    /** Toggles whether the iam/iamnot confirmation message auto-deletes. */
    fun toggleSelfAssignableAutoDelete() = launchAction("Failed to toggle auto-delete.") {
        val newState = api.send(
            Endpoint("api/Administration/$guildId/self-assignable-roles/auto-delete/toggle", HttpMethod.POST),
            Boolean.serializer(),
        )
        _state.update { it.copy(autoDeleteSelfAssign = newState) }
        postSuccess("Auto-delete setting updated.")
    }

    /** Adds a voice channel to role mapping. */
    fun addVoiceChannelRole(channelId: Snowflake, roleId: Snowflake) =
        launchAction("Failed to add voice channel role.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/voice-channel-roles",
                    HttpMethod.POST,
                    jsonBody(
                        "channelId" to channelId.asSnowflakeNumber(),
                        "roleId" to roleId.asSnowflakeNumber(),
                    ),
                )
            )
            load()
        }

    /** Removes a voice channel role mapping. */
    fun removeVoiceChannelRole(channelId: Snowflake) = launchAction("Failed to remove voice channel role.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/voice-channel-roles/$channelId", HttpMethod.DELETE)
        )
        _state.update { it.copy(voiceChannelRoles = it.voiceChannelRoles.filterNot { r -> r.channelId == channelId }) }
    }

    /** Adds a reaction role setup to a message. */
    fun addReactionRoleSetup(
        messageId: Snowflake,
        channelId: Snowflake,
        exclusive: Boolean,
        pairs: List<Pair<String, Snowflake>>,
    ) = launchAction("Failed to add reaction roles. Check the message and channel IDs.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/reaction-roles",
                HttpMethod.POST,
                jsonBody(
                    "messageId" to messageId.asSnowflakeNumber(),
                    "channelId" to channelId.asSnowflakeNumber(),
                    "exclusive" to exclusive,
                    "roles" to JsonArray(
                        pairs.map { (emote, roleId) ->
                            buildJsonObject {
                                put("emoteName", JsonPrimitive(emote))
                                put("roleId", JsonPrimitive(roleId.asSnowflakeNumber()))
                            }
                        }
                    ),
                ),
            )
        )
        load()
        postSuccess("Reaction roles saved.")
    }

    /** Removes a reaction role setup by its list index. */
    fun removeReactionRoleSetup(index: Int) = launchAction("Failed to remove reaction roles.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/reaction-roles/$index", HttpMethod.DELETE)
        )
        load()
    }


    /** Writes the anti-raid configuration. */
    fun saveAntiRaid(enabled: Boolean, userThreshold: Int, seconds: Int, action: Int, punishDuration: Int) =
        protectionSave(
            "api/Administration/$guildId/protection/anti-raid",
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
    fun saveAntiSpam(enabled: Boolean, messageThreshold: Int, action: Int, muteTime: Int, roleId: Snowflake?) =
        protectionSave(
            "api/Administration/$guildId/protection/anti-spam",
            "Anti-spam saved.",
            jsonBody(
                "enabled" to enabled,
                "messageThreshold" to messageThreshold,
                "action" to action,
                "muteTime" to muteTime,
                "roleId" to roleId?.toLongOrNull(),
            ),
        )

    /** Adds or removes a channel anti-spam ignores. */
    fun toggleAntiSpamIgnoredChannel(channelId: Snowflake) = launchAction("Failed to update ignored channels.") {
        listToggleLock.withLock {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/protection/anti-spam/ignored-channels/$channelId",
                    HttpMethod.POST,
                )
            )
            _state.update { current ->
                val protection = current.protection ?: return@update current
                val antiSpam = protection.antiSpam
                current.copy(
                    protection = protection.copy(
                        antiSpam = antiSpam.copy(ignoredChannels = antiSpam.ignoredChannels.toggling(channelId)),
                    ),
                )
            }
        }
    }

    /** Writes the anti-alt configuration. */
    fun saveAntiAlt(enabled: Boolean, minAgeMinutes: Int, action: Int, actionDurationMinutes: Int, roleId: Snowflake?) =
        protectionSave(
            "api/Administration/$guildId/protection/anti-alt",
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
        "api/Administration/$guildId/protection/anti-mass-mention",
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


    /** Writes the anti-pattern configuration. */
    fun saveAntiPattern(
        enabled: Boolean,
        action: Int,
        punishDuration: Int,
        checkAccountAge: Boolean,
        maxAccountAgeMonths: Int,
        checkJoinTiming: Boolean,
        maxJoinHours: Double,
        checkBatchCreation: Boolean,
        checkOfflineStatus: Boolean,
        checkNewAccounts: Boolean,
        newAccountDays: Int,
        minimumScore: Int,
    ) = protectionSave(
        "api/Protection/$guildId/anti-pattern",
        "Anti-pattern saved.",
        jsonBody(
            "enabled" to enabled,
            "action" to action,
            "punishDuration" to punishDuration,
            "roleId" to _state.value.protection?.antiPattern?.roleId?.toLongOrNull(),
            "checkAccountAge" to checkAccountAge,
            "maxAccountAgeMonths" to maxAccountAgeMonths,
            "checkJoinTiming" to checkJoinTiming,
            "maxJoinHours" to maxJoinHours,
            "checkBatchCreation" to checkBatchCreation,
            "checkOfflineStatus" to checkOfflineStatus,
            "checkNewAccounts" to checkNewAccounts,
            "newAccountDays" to newAccountDays,
            "minimumScore" to minimumScore,
        ),
    )

    /** Adds a username/display-name pattern to anti-pattern protection. */
    fun addAntiPatternPattern(pattern: String, name: String, checkUsername: Boolean, checkDisplayName: Boolean) =
        launchAction("Failed to add pattern.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Protection/$guildId/anti-pattern/patterns",
                    HttpMethod.POST,
                    jsonBody(
                        "pattern" to pattern,
                        "name" to name.ifBlank { null },
                        "checkUsername" to checkUsername,
                        "checkDisplayName" to checkDisplayName,
                    ),
                )
            )
            load()
        }

    /** Removes an anti-pattern regex pattern. */
    fun removeAntiPatternPattern(id: Int) = launchAction("Failed to remove pattern.") {
        api.sendIgnoringBody(
            Endpoint("api/Protection/$guildId/anti-pattern/patterns/$id", HttpMethod.DELETE)
        )
        _state.update { it.copy(antiPatternPatterns = it.antiPatternPatterns.filterNot { p -> p.id == id }) }
    }


    /** Writes the anti-mass-post configuration. */
    fun saveAntiMassPost(
        enabled: Boolean,
        channelThreshold: Int,
        timeWindowSeconds: Int,
        checkLinksOnly: Boolean,
        action: Int,
        punishDuration: Int,
    ) = protectionSave(
        "api/Administration/$guildId/protection/anti-mass-post",
        "Anti-mass-post saved.",
        jsonBody(
            "enabled" to enabled,
            "channelThreshold" to channelThreshold,
            "timeWindowSeconds" to timeWindowSeconds,
            "contentSimilarityThreshold" to
                (_state.value.protection?.antiMassPost?.contentSimilarityThreshold ?: 0.8),
            "minContentLength" to (_state.value.protection?.antiMassPost?.minContentLength ?: 20),
            "checkLinksOnly" to checkLinksOnly,
            "checkDuplicateContent" to (_state.value.protection?.antiMassPost?.checkDuplicateContent ?: true),
            "requireIdenticalContent" to
                (_state.value.protection?.antiMassPost?.requireIdenticalContent ?: false),
            "caseSensitive" to (_state.value.protection?.antiMassPost?.caseSensitive ?: false),
            "deleteMessages" to (_state.value.protection?.antiMassPost?.deleteMessages ?: true),
            "notifyUser" to (_state.value.protection?.antiMassPost?.notifyUser ?: true),
            "action" to action,
            "punishDuration" to punishDuration,
            "roleId" to _state.value.protection?.antiMassPost?.roleId?.toLongOrNull(),
            "ignoreBots" to (_state.value.protection?.antiMassPost?.ignoreBots ?: true),
            "maxMessagesTracked" to (_state.value.protection?.antiMassPost?.maxMessagesTracked ?: 50),
        ),
    )


    /** Writes the anti-post-channel configuration. */
    fun saveAntiPostChannel(enabled: Boolean, action: Int, punishDuration: Int, deleteMessages: Boolean, notifyUser: Boolean, ignoreBots: Boolean) =
        protectionSave(
            "api/Administration/$guildId/protection/anti-post-channel",
            "Anti-post-channel saved.",
            jsonBody(
                "enabled" to enabled,
                "action" to action,
                "punishDuration" to punishDuration,
                "roleId" to _state.value.protection?.antiPostChannel?.roleId?.toLongOrNull(),
                "deleteMessages" to deleteMessages,
                "notifyUser" to notifyUser,
                "ignoreBots" to ignoreBots,
            ),
        )

    /** Adds a honeypot channel. */
    fun addHoneypotChannel(channelId: Snowflake) = launchAction("Failed to add honeypot channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/protection/anti-post-channel/channels/$channelId",
                HttpMethod.POST,
            )
        )
        load()
    }

    /** Removes a honeypot channel. */
    fun removeHoneypotChannel(channelId: Snowflake) = launchAction("Failed to remove honeypot channel.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/protection/anti-post-channel/channels/$channelId",
                HttpMethod.DELETE,
            )
        )
        load()
    }

    /** Toggles whether a role is exempt from the honeypot. */
    fun toggleHoneypotIgnoredRole(roleId: Snowflake) = launchAction("Failed to update ignored roles.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/protection/anti-post-channel/ignored-roles/$roleId",
                HttpMethod.POST,
            )
        )
        load()
    }

    /** Toggles whether a user is exempt from the honeypot. */
    fun toggleHoneypotIgnoredUser(userId: Snowflake) = launchAction("Failed to update ignored users.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/protection/anti-post-channel/ignored-users/$userId",
                HttpMethod.POST,
            )
        )
        load()
    }


    /** Writes the anti-image-hash configuration. */
    fun saveAntiImageHash(
        enabled: Boolean,
        action: Int,
        punishDuration: Int,
        hashThreshold: Int,
        deleteMessages: Boolean,
        notifyUser: Boolean,
        checkEmbeds: Boolean,
        ignoreBots: Boolean,
        checkBorders: Boolean,
    ) = launchAction("Failed to save protection.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Protection/$guildId/anti-image-hash",
                HttpMethod.PUT,
                jsonBody(
                    "enabled" to enabled,
                    "action" to action,
                    "punishDuration" to punishDuration,
                    "roleId" to _state.value.imageHash.roleId?.toLongOrNull(),
                    "hashThreshold" to hashThreshold,
                    "deleteMessages" to deleteMessages,
                    "notifyUser" to notifyUser,
                    "checkEmbeds" to checkEmbeds,
                    "ignoreBots" to ignoreBots,
                    "checkBorders" to checkBorders,
                    "usePresetList" to _state.value.imageHash.usePresetList,
                    "maxImageSizeMb" to _state.value.imageHash.maxImageSizeMb,
                ),
            )
        )
        load()
        postSuccess("Anti-image-hash saved.")
    }

    /** Turns the bot's shipped scam image list on or off. */
    fun togglePresetScamImages() = launchAction("Failed to toggle the preset list.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Protection/$guildId/anti-image-hash/preset/${!_state.value.imageHash.usePresetList}",
                HttpMethod.POST,
            )
        )
        load()
    }

    /** Computes the perceptual hash of an image without blocking it. */
    suspend fun computeImageHash(base64: String?, url: String?): ImageHashPreview? = runCatching {
        api.send(
            Endpoint(
                "api/Protection/$guildId/anti-image-hash/compute",
                HttpMethod.POST,
                jsonBody("imageBase64" to base64, "imageUrl" to url),
            ),
            ImageHashPreview.serializer(),
        )
    }.getOrNull()

    /** Blocks an image by its computed hash. */
    fun blockImage(hash: String, imageUrl: String?, name: String?, action: Int?) =
        launchAction("Failed to block that image. It may already be blocked.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Protection/$guildId/anti-image-hash/hashes",
                    HttpMethod.POST,
                    jsonBody(
                        "hash" to hash,
                        "imageUrl" to imageUrl,
                        "name" to name?.ifBlank { null },
                        "action" to action,
                        "addedBy" to userId.asSnowflakeNumber(),
                    ),
                )
            )
            load()
            postSuccess("Image blocked.")
        }

    /** Removes a blocked image. */
    fun removeBannedImageHash(id: Int) = launchAction("Failed to remove blocked image.") {
        api.sendIgnoringBody(
            Endpoint("api/Protection/$guildId/anti-image-hash/hashes/$id", HttpMethod.DELETE)
        )
        _state.update { it.copy(bannedImageHashes = it.bannedImageHashes.filterNot { h -> h.id == id }) }
    }

    /** Loads this guild's custom emojis for the reaction roles emoji picker. */
    suspend fun loadGuildEmojis(): List<EmojiInfo> = runCatching {
        api.send(
            Endpoint("api/ClientOperations/emojis/$userId?adminOnly=true"),
            ListSerializer(GuildEmojiInfo.serializer()),
        ).firstOrNull { it.guild.id == guildId }?.emojis.orEmpty()
    }.getOrDefault(emptyList())


    /** Quick-toggles a protection module on or off without opening its editor. */
    fun quickToggleProtection(module: QuickProtectionModule) = launchAction("Failed to update protection.") {
        val protection = _state.value.protection
        val currentlyEnabled = when (module) {
            QuickProtectionModule.RAID -> protection?.antiRaid?.enabled
            QuickProtectionModule.SPAM -> protection?.antiSpam?.enabled
            QuickProtectionModule.ALT -> protection?.antiAlt?.enabled
            QuickProtectionModule.MASS_MENTION -> protection?.antiMassMention?.enabled
            QuickProtectionModule.MASS_POST -> protection?.antiMassPost?.enabled
            QuickProtectionModule.PATTERN -> protection?.antiPattern?.enabled
            QuickProtectionModule.POST_CHANNEL -> protection?.antiPostChannel?.enabled
            QuickProtectionModule.IMAGE_HASH -> _state.value.imageHash.enabled
        } ?: false

        val disableBody = jsonBody("enabled" to false)
        val (path, enableBody) = when (module) {
            QuickProtectionModule.RAID -> "api/Administration/$guildId/protection/anti-raid" to jsonBody(
                "enabled" to true, "userThreshold" to 5, "seconds" to 10, "action" to 1, "punishDuration" to 60,
            )

            QuickProtectionModule.SPAM -> "api/Administration/$guildId/protection/anti-spam" to jsonBody(
                "enabled" to true, "messageThreshold" to 5, "action" to 1, "muteTime" to 5,
            )

            QuickProtectionModule.ALT -> "api/Administration/$guildId/protection/anti-alt" to jsonBody(
                "enabled" to true, "minAgeMinutes" to 1440, "action" to 2, "actionDurationMinutes" to 0,
            )

            QuickProtectionModule.MASS_MENTION ->
                "api/Administration/$guildId/protection/anti-mass-mention" to jsonBody(
                    "enabled" to true, "mentionThreshold" to 5, "timeWindowSeconds" to 30,
                    "maxMentionsInTimeWindow" to 10, "ignoreBots" to true, "action" to 1, "muteTime" to 5,
                )

            QuickProtectionModule.MASS_POST ->
                "api/Administration/$guildId/protection/anti-mass-post" to jsonBody(
                    "enabled" to true, "channelThreshold" to 3, "timeWindowSeconds" to 60,
                    "contentSimilarityThreshold" to 0.8, "minContentLength" to 20, "checkLinksOnly" to true,
                    "checkDuplicateContent" to true, "requireIdenticalContent" to false, "caseSensitive" to false,
                    "deleteMessages" to true, "notifyUser" to true, "action" to 2, "punishDuration" to 0,
                    "ignoreBots" to true, "maxMessagesTracked" to 50,
                )

            QuickProtectionModule.PATTERN -> "api/Protection/$guildId/anti-pattern" to jsonBody(
                "enabled" to true, "action" to 1, "punishDuration" to 60, "checkAccountAge" to true,
                "maxAccountAgeMonths" to 6, "checkJoinTiming" to true, "maxJoinHours" to 48.0,
                "checkBatchCreation" to true, "checkOfflineStatus" to true, "checkNewAccounts" to true,
                "newAccountDays" to 7, "minimumScore" to 15,
            )

            QuickProtectionModule.POST_CHANNEL ->
                "api/Administration/$guildId/protection/anti-post-channel" to jsonBody(
                    "enabled" to true, "action" to 2, "punishDuration" to 0, "deleteMessages" to true,
                    "notifyUser" to true, "ignoreBots" to true,
                )

            QuickProtectionModule.IMAGE_HASH -> "api/Protection/$guildId/anti-image-hash" to jsonBody(
                "enabled" to true, "action" to 2, "punishDuration" to 0, "hashThreshold" to 31,
                "deleteMessages" to true, "notifyUser" to true, "ignoreBots" to true, "checkEmbeds" to true,
                "checkBorders" to true, "usePresetList" to true, "maxImageSizeMb" to 8,
            )
        }

        api.sendIgnoringBody(
            Endpoint(path, HttpMethod.PUT, if (currentlyEnabled) disableBody else enableBody)
        )
        load()
    }


    /**
     * Sets the game voice channel, or clears it by passing the currently set channel again (the
     * bot endpoint toggles).
     */
    fun toggleGameVoiceChannel(channelId: Snowflake) = launchAction("Failed to update game voice channel.") {
        val result = api.send(
            Endpoint(
                "api/Administration/$guildId/game-voice-channel/toggle",
                HttpMethod.POST,
                jsonBody("channelId" to channelId.asSnowflakeNumber()),
            ),
            SnowflakeSerializer.nullable,
        )
        _state.update { it.copy(gameVoiceChannelId = result) }
    }

    /** Toggles the global "delete message on command" setting. */
    fun toggleDeleteMessageOnCommand() = launchAction("Failed to toggle.") {
        val enabled = api.send(
            Endpoint("api/Administration/$guildId/delete-message-on-command/toggle", HttpMethod.POST),
            Boolean.serializer(),
        )
        _state.update { it.copy(deleteMessageOnCommand = it.deleteMessageOnCommand.copy(enabled = enabled)) }
    }

    /** Sets a per-channel override for "delete message on command". */
    fun setDeleteMessageOnCommandChannel(channelId: Snowflake, state: DeleteMsgState) =
        launchAction("Failed to set channel override.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/delete-message-on-command/channel",
                    HttpMethod.POST,
                    jsonBody(
                        "channelId" to channelId.asSnowflakeNumber(),
                        "state" to state.name.lowercase(),
                    ),
                )
            )
            load()
        }

    /** Toggles whether this guild opts out of anonymous stats collection. */
    fun toggleStatsOptOut() = launchAction("Failed to toggle stats opt-out.") {
        val optedOut = api.send(
            Endpoint("api/Administration/$guildId/stats-opt-out/toggle", HttpMethod.POST),
            Boolean.serializer(),
        )
        _state.update { it.copy(statsOptOut = optedOut) }
    }

    /** Permanently deletes every collected statistic for this guild. */
    fun deleteStatsData() = launchAction("Failed to delete statistics.") {
        api.sendIgnoringBody(Endpoint("api/Administration/$guildId/stats-data", HttpMethod.DELETE))
        postSuccess("Statistics deleted.")
    }


    /** Sets a command's cooldown, in seconds. */
    fun setCommandCooldown(commandName: String, seconds: Int) = launchAction("Failed to set cooldown.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/command-cooldowns/$commandName",
                HttpMethod.PUT,
                jsonInt(seconds),
            )
        )
        load()
    }

    /** Removes a command's cooldown. */
    fun removeCommandCooldown(commandName: String) = launchAction("Failed to remove cooldown.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/command-cooldowns/$commandName", HttpMethod.DELETE)
        )
        _state.update { it.copy(commandCooldowns = it.commandCooldowns.filterNot { c -> c.commandName == commandName }) }
    }

    /** Adds a permission override for a command. */
    fun addPermissionOverride(command: String, permission: String) = launchAction("Failed to add override.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/permission-overrides",
                HttpMethod.POST,
                jsonBody("command" to command, "permission" to permission),
            )
        )
        load()
    }

    /** Removes a single permission override. */
    fun removePermissionOverride(command: String) = launchAction("Failed to remove override.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/permission-overrides/$command", HttpMethod.DELETE)
        )
        _state.update { it.copy(permissionOverrides = it.permissionOverrides.filterNot { o -> o.command == command }) }
    }

    /** Removes every permission override at once. */
    fun clearAllPermissionOverrides() = launchAction("Failed to clear overrides.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/permission-overrides", HttpMethod.DELETE)
        )
        _state.update { it.copy(permissionOverrides = emptyList()) }
    }

    /** Deletes several permission overrides at once. */
    fun deletePermissionOverrides(commands: List<String>) = launchAction("Failed to delete overrides.") {
        commands.forEach { command ->
            api.sendIgnoringBody(
                Endpoint("api/Administration/$guildId/permission-overrides/$command", HttpMethod.DELETE)
            )
        }
        _state.update { it.copy(permissionOverrides = it.permissionOverrides.filterNot { o -> o.command in commands }) }
    }

    /** Adds a new permission rule. */
    fun addPermissionRule(
        primaryTarget: Int,
        primaryTargetId: Snowflake?,
        secondaryTarget: Int,
        secondaryTargetName: String,
        state: Boolean,
    ) = launchAction("Failed to add the rule.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/permissions",
                HttpMethod.POST,
                jsonBody(
                    "primaryTarget" to primaryTarget,
                    "primaryTargetId" to (primaryTargetId?.asSnowflakeNumber() ?: 0L),
                    "secondaryTarget" to secondaryTarget,
                    "secondaryTargetName" to secondaryTargetName,
                    "isCustomCommand" to false,
                    "state" to state,
                    "index" to 0,
                ),
            )
        )
        load()
    }

    /** Removes a permission rule by its position. */
    fun removePermissionRule(index: Int) = launchAction("Failed to remove the rule.") {
        api.sendIgnoringBody(
            Endpoint("api/Administration/$guildId/permissions/$index", HttpMethod.DELETE)
        )
        load()
    }

    /** Moves a permission rule to a new position. */
    fun movePermissionRule(from: Int, to: Int) = launchAction("Failed to move the rule.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/permissions/move",
                HttpMethod.POST,
                jsonBody("from" to from, "to" to to),
            )
        )
        load()
    }

    /** Removes every permission rule and restores the default allow-all rule. */
    fun resetPermissionRules() = launchAction("Failed to reset permissions.") {
        api.sendIgnoringBody(Endpoint("api/Administration/$guildId/permissions/reset", HttpMethod.POST))
        load()
    }

    /** Toggles whether a blocked command explains itself to the user. */
    fun togglePermissionVerbose() = launchAction("Failed to toggle verbose mode.") {
        val newValue = !_state.value.permissions.verbose
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/permissions/verbose",
                HttpMethod.POST,
                jsonBody("verbose" to newValue),
            )
        )
        _state.update { it.copy(permissions = it.permissions.copy(verbose = newValue)) }
    }

    /** Sets the role that can edit permission rules via commands. */
    fun setPermissionRole(roleId: Snowflake?) = launchAction("Failed to set permission role.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/permissions/role",
                HttpMethod.POST,
                jsonString(roleId.orEmpty()),
            )
        )
        _state.update { it.copy(permissions = it.permissions.copy(permRole = roleId)) }
    }


    /** Generates and stores new server recovery keys. */
    fun setupServerRecovery(recoveryKey: String, twoFactorKey: String) =
        launchAction("Failed to set up server recovery.") {
            api.sendIgnoringBody(
                Endpoint(
                    "api/Administration/$guildId/server-recovery",
                    HttpMethod.POST,
                    jsonBody("recoveryKey" to recoveryKey, "twoFactorKey" to twoFactorKey),
                )
            )
            load()
        }

    /** Clears the stored server recovery keys. */
    fun clearServerRecovery() = launchAction("Failed to clear server recovery.") {
        api.sendIgnoringBody(Endpoint("api/Administration/$guildId/server-recovery", HttpMethod.DELETE))
        load()
    }

    /** Bans every listed user in one pass. */
    fun massBan(userIds: List<Snowflake>, reason: String?) = launchAction("Mass ban failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/mass-ban",
                HttpMethod.POST,
                jsonBody(
                    "userIds" to JsonArray(userIds.mapNotNull { it.toLongOrNull() }.map { JsonPrimitive(it) }),
                    "reason" to reason.orEmpty(),
                ),
            )
        )
        postSuccess("Mass ban submitted for ${userIds.size} users.")
    }

    /** Renames every member using a `{username}` pattern. */
    fun massRename(pattern: String) = launchAction("Mass rename failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/mass-rename",
                HttpMethod.POST,
                jsonBody("pattern" to pattern),
            )
        )
        postSuccess("Mass rename submitted.")
    }

    /** Removes members who have been inactive for the given number of days. */
    fun pruneInactiveMembers(days: Int) = launchAction("Prune failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/prune",
                HttpMethod.POST,
                jsonBody("days" to days),
            )
        )
        postSuccess("Pruned inactive members.")
    }

    /** Deletes every message in a channel posted after the given message. */
    fun pruneChannelToMessage(channelId: Snowflake, messageId: Snowflake) = launchAction("Prune failed.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/Administration/$guildId/prune-to",
                HttpMethod.POST,
                jsonBody(
                    "channelId" to channelId.asSnowflakeNumber(),
                    "messageId" to messageId.asSnowflakeNumber(),
                ),
            )
        )
        postSuccess("Channel pruned.")
    }

    private fun protectionSave(path: String, success: String, body: String) =
        launchAction("Failed to save protection.") {
            api.sendIgnoringBody(Endpoint(path, HttpMethod.PUT, body))
            load()
            postSuccess(success)
        }

    private suspend fun ids(tail: String): List<Snowflake> = runCatching {
        api.sendRaw(Endpoint("api/Administration/$guildId/$tail")).snowflakeIds()
    }.getOrDefault(emptyList())

    private suspend fun scalar(tail: String): String? = runCatching {
        api.send(Endpoint("api/Administration/$guildId/$tail"), ScalarString.serializer()).value
    }.getOrNull()?.takeIf { it.isNotEmpty() && it != "0" }
}

/** Adds the id if absent, removes it if present. */
private fun List<Snowflake>.toggling(id: Snowflake): List<Snowflake> =
    if (id in this) this - id else this + id

/** The eight protection modules that support a one-tap quick toggle. */
enum class QuickProtectionModule {
    RAID, SPAM, ALT, MASS_MENTION, MASS_POST, PATTERN, POST_CHANNEL, IMAGE_HASH
}
