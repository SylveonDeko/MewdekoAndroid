package dev.mewdeko.mobile.feature.guilddetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.BotStatus
import dev.mewdeko.mobile.core.model.GraphStats
import dev.mewdeko.mobile.core.model.GuildInfo
import dev.mewdeko.mobile.core.model.RoleGreet
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.theme.GuildColorStore
import dev.mewdeko.mobile.core.ui.FeatureViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import javax.inject.Inject

/** Aggregate member metrics rendered on the guild overview. */
data class GuildMemberStats(
    val total: Int = 0,
    val humans: Int = 0,
    val bots: Int = 0,
)

/** Aggregate role metrics rendered on the guild overview. */
data class GuildRoleStats(
    val totalRoles: Int = 0,
    val roleStates: Int = 0,
    val savedRoles: Int = 0,
    val roleGreets: Int = 0,
)

/** Guild overview screen state. Each panel publishes independently. */
data class GuildOverviewState(
    val info: GuildInfo? = null,
    val bot: BotStatus? = null,
    val memberStats: GuildMemberStats? = null,
    val roleStats: GuildRoleStats? = null,
    val joinStats: GraphStats? = null,
    val leaveStats: GraphStats? = null,
)

/**
 * Loads every overview panel concurrently and publishes each as it resolves,
 * so a slow member fetch does not hold up the header.
 */
@HiltViewModel
class GuildOverviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    api: ApiClient,
    session: SessionHolder,
    private val colorStore: GuildColorStore,
) : FeatureViewModel(savedStateHandle, api, session) {

    private val _state = MutableStateFlow(GuildOverviewState())

    /** Observable screen state. */
    val state: StateFlow<GuildOverviewState> = _state.asStateFlow()

    /** The guild palette driving the theme while this screen is open. */
    val palette = colorStore.palette

    init {
        savedStateHandle.get<String>("guildIcon")
            ?.takeIf { it != "-" && it.isNotEmpty() }
            ?.let { colorStore.update(it) }
        load()
    }

    /** Reloads every panel. */
    fun load(refreshing: Boolean = false) = launchLoad(refreshing) {
        coroutineScope {
            listOf(
                async { loadInfo() },
                async { loadBot() },
                async { loadMembers() },
                async { loadRoles() },
                async { loadJoinLeave() },
            ).awaitAll()
        }
    }

    private suspend fun loadInfo() = runCatching {
        api.send(Endpoint("api/Guild/$guildId/info"), GuildInfo.serializer())
    }.getOrNull()?.let { info -> _state.update { it.copy(info = info) } }

    private suspend fun loadBot() = runCatching {
        api.send(Endpoint("api/BotStatus"), BotStatus.serializer())
    }.getOrNull()?.let { bot -> _state.update { it.copy(bot = bot) } }

    /**
     * Counts humans and bots without decoding the member list, which can run
     * to several megabytes on a large guild.
     */
    private suspend fun loadMembers() {
        val stats = runCatching {
            val array = api.sendRaw(Endpoint("api/ClientOperations/members/$guildId")) as? JsonArray
                ?: return@runCatching GuildMemberStats()
            val bots = array.count { element ->
                val obj = element as? JsonObject ?: return@count false
                val flag = obj["isBot"] ?: obj["IsBot"]
                (flag as? JsonPrimitive)?.booleanOrNull == true
            }
            GuildMemberStats(total = array.size, humans = array.size - bots, bots = bots)
        }.getOrNull() ?: return
        _state.update { it.copy(memberStats = stats) }
    }

    private suspend fun loadRoles() = coroutineScope {
        val rolesDeferred = async {
            runCatching { api.sendArrayCount("api/ClientOperations/roles/$guildId") }.getOrNull()
        }
        val statesDeferred = async {
            runCatching {
                val array = api.sendRaw(Endpoint("api/RoleStates/$guildId/all")) as? JsonArray
                    ?: return@runCatching 0 to 0
                var saved = 0
                array.forEach { element ->
                    val obj = element as? JsonObject ?: return@forEach
                    val raw = (obj["savedRoles"] as? JsonPrimitive)?.content ?: return@forEach
                    saved += raw.split(',').count { it.isNotBlank() }
                }
                array.size to saved
            }.getOrNull()
        }
        val greetsDeferred = async {
            runCatching {
                api.send(Endpoint("api/RoleGreet/$guildId"), ListSerializer(RoleGreet.serializer()))
            }.getOrNull()
        }

        val states = statesDeferred.await()
        val stats = GuildRoleStats(
            totalRoles = rolesDeferred.await() ?: 0,
            roleStates = states?.first ?: 0,
            savedRoles = states?.second ?: 0,
            roleGreets = greetsDeferred.await().orEmpty().count { it.disabled != true },
        )
        _state.update { it.copy(roleStats = stats) }
    }

    private suspend fun loadJoinLeave() = coroutineScope {
        val join = async {
            runCatching {
                api.send(Endpoint("api/JoinLeave/$guildId/join-stats"), GraphStats.serializer())
            }.getOrNull()
        }
        val leave = async {
            runCatching {
                api.send(Endpoint("api/JoinLeave/$guildId/leave-stats"), GraphStats.serializer())
            }.getOrNull()
        }
        _state.update { it.copy(joinStats = join.await(), leaveStats = leave.await()) }
    }

    override fun onCleared() {
        super.onCleared()
        colorStore.update(null)
    }
}
