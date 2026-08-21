package dev.mewdeko.mobile.feature.invites

import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.model.SnowflakeSerializer
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.HttpMethod
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

/** Invite tracking configuration. */
@Serializable
data class InviteSettings(
    val isEnabled: Boolean = false,
    val removeOnLeave: Boolean = false,
    val minAccountAge: String = "00:00:00",
)

/** One entry in the invite leaderboard. */
@Serializable
data class InviteLeaderboardEntry(
    @Serializable(with = SnowflakeSerializer::class) val userId: Snowflake = "",
    val username: String = "Unknown",
    val inviteCount: Int = 0,
)

/** A lightweight user reference returned by the invite endpoints. */
@Serializable
data class InviteUserLite(
    @Serializable(with = SnowflakeSerializer::class) val id: Snowflake = "",
    val username: String = "Unknown",
    val avatarUrl: String? = null,
)

/** Invites screen state. */
data class InvitesState(
    val settings: InviteSettings? = null,
    val pendingMinAge: String = "00:00:00",
    val minAgeDirty: Boolean = false,
    val leaderboard: List<InviteLeaderboardEntry> = emptyList(),
) {
    /** Invites summed across the leaderboard. */
    val totalInvites: Int get() = leaderboard.sumOf { it.inviteCount }

    /** The member with the most invites. */
    val topInviter: InviteLeaderboardEntry? get() = leaderboard.maxByOrNull { it.inviteCount }

    /** Mean invites per tracked member. */
    val averageInvites: Double
        get() = if (leaderboard.isEmpty()) 0.0 else totalInvites.toDouble() / leaderboard.size
}

/** Invite tracking settings and leaderboard. */
@HiltViewModel
class InvitesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(InvitesState())

    /** Observable screen state. */
    val state: StateFlow<InvitesState> = _state.asStateFlow()

    init {
        load()
    }

    /** Reloads settings and the leaderboard. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            val settings = async {
                runCatching {
                    api.send(
                        Endpoint("api/InviteTracking/$guildId/settings"),
                        InviteSettings.serializer(),
                    )
                }.getOrNull()
            }
            val leaderboard = async {
                runCatching {
                    api.send(
                        Endpoint("api/InviteTracking/$guildId/leaderboard?page=1&pageSize=50"),
                        ListSerializer(InviteLeaderboardEntry.serializer()),
                    )
                }.getOrDefault(emptyList())
            }

            val cfg = settings.await()
            _state.update {
                it.copy(
                    settings = cfg,
                    pendingMinAge = cfg?.minAccountAge ?: "00:00:00",
                    minAgeDirty = false,
                    leaderboard = leaderboard.await().sortedByDescending { entry -> entry.inviteCount },
                )
            }
        }
    }

    /** Turns invite tracking on or off. */
    fun setEnabled(enabled: Boolean) = launchAction("Failed to update tracking.") {
        api.sendIgnoringBody(
            Endpoint("api/InviteTracking/$guildId/toggle", HttpMethod.POST, jsonBool(enabled))
        )
        _state.update { it.copy(settings = it.settings?.copy(isEnabled = enabled)) }
        postSuccess(if (enabled) "Tracking enabled." else "Tracking disabled.")
    }

    /** Sets whether an invite credit is revoked when the invitee leaves. */
    fun setRemoveOnLeave(value: Boolean) = launchAction("Failed to update setting.") {
        api.sendIgnoringBody(
            Endpoint(
                "api/InviteTracking/$guildId/remove-on-leave",
                HttpMethod.POST,
                jsonBool(value),
            )
        )
        _state.update { it.copy(settings = it.settings?.copy(removeOnLeave = value)) }
        postSuccess("Saved.")
    }

    /** Stages a new minimum account age without sending it. */
    fun setPendingMinAge(value: String) =
        _state.update { it.copy(pendingMinAge = value, minAgeDirty = true) }

    /** Persists the staged minimum account age. */
    fun saveMinAge() = launchAction("Failed to update minimum age.") {
        val value = _state.value.pendingMinAge
        api.sendIgnoringBody(
            Endpoint("api/InviteTracking/$guildId/min-age", HttpMethod.POST, jsonString(value))
        )
        _state.update {
            it.copy(settings = it.settings?.copy(minAccountAge = value), minAgeDirty = false)
        }
        postSuccess("Minimum account age saved.")
    }

    /** Fetches who invited a given member. */
    suspend fun inviter(userId: Snowflake): InviteUserLite? = runCatching {
        api.send(
            Endpoint("api/InviteTracking/$guildId/inviter/$userId"),
            InviteUserLite.serializer(),
        )
    }.getOrNull()

    /** Fetches the members a given user invited. */
    suspend fun invited(userId: Snowflake): List<InviteUserLite> = runCatching {
        api.send(
            Endpoint("api/InviteTracking/$guildId/invited/$userId"),
            ListSerializer(InviteUserLite.serializer()),
        )
    }.getOrDefault(emptyList())
}
