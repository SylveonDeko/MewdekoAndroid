package dev.mewdeko.mobile.feature.guilddetail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.BotStatus
import dev.mewdeko.mobile.core.model.GraphStats
import dev.mewdeko.mobile.core.model.GuildInfo
import dev.mewdeko.mobile.core.model.GuildRole
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import java.time.Instant
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

/** A member's display identity, kept so ranked lists can show names instead of ids. */
@Immutable
data class MemberSummary(val name: String, val avatarUrl: String?)

/** Guild overview screen state. Each panel publishes independently. */
data class GuildOverviewState(
    val info: GuildInfo? = null,
    val bot: BotStatus? = null,
    val memberStats: GuildMemberStats? = null,
    val memberDirectory: Map<String, MemberSummary> = emptyMap(),
    val roleStats: GuildRoleStats? = null,
    val roleNames: Map<String, String> = emptyMap(),
    val roleGreets: List<RoleGreet>? = null,
    val joinStats: GraphStats? = null,
    val leaveStats: GraphStats? = null,
    val lastUpdated: Instant? = null,
)

/** The largest guild whose member directory is kept in memory. */
private const val MemberDirectoryLimit = 25_000

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
        _state.update { it.copy(lastUpdated = Instant.now()) }
    }

    private suspend fun loadInfo() = runCatching {
        api.send(Endpoint("api/Guild/$guildId/info"), GuildInfo.serializer())
    }.getOrNull()?.let { info -> _state.update { it.copy(info = info) } }

    private suspend fun loadBot() = runCatching {
        api.send(Endpoint("api/BotStatus"), BotStatus.serializer())
    }.getOrNull()?.let { bot -> _state.update { it.copy(bot = bot) } }

    /**
     * Counts humans and bots without decoding the member list into models,
     * which can run to several megabytes on a large guild.
     *
     * The same pass keeps a name and avatar per member so ranked lists can
     * resolve ids, but only up to [MemberDirectoryLimit] members; above that
     * the directory stays empty.
     */
    private suspend fun loadMembers() {
        val result = runCatching {
            val array = api.sendRaw(Endpoint("api/ClientOperations/members/$guildId")) as? JsonArray
                ?: return@runCatching GuildMemberStats() to emptyMap<String, MemberSummary>()
            val keepDirectory = array.size <= MemberDirectoryLimit
            val directory = HashMap<String, MemberSummary>(if (keepDirectory) array.size else 0)
            var bots = 0
            array.forEach { element ->
                val obj = element as? JsonObject ?: return@forEach
                if ((obj.field("isBot") as? JsonPrimitive)?.booleanOrNull == true) bots++
                if (keepDirectory) {
                    val id = obj.text("id") ?: return@forEach
                    val name = obj.text("displayName")?.takeIf { it.isNotBlank() }
                        ?: obj.text("username").orEmpty()
                    directory[id] = MemberSummary(name = name, avatarUrl = obj.text("avatarUrl"))
                }
            }
            GuildMemberStats(total = array.size, humans = array.size - bots, bots = bots) to
                directory.toMap()
        }.getOrNull() ?: return
        _state.update { it.copy(memberStats = result.first, memberDirectory = result.second) }
    }

    /** Reads [name] in either camelCase or PascalCase, since raw payloads are not normalized. */
    private fun JsonObject.field(name: String): JsonElement? =
        this[name] ?: this[name.replaceFirstChar { it.uppercaseChar() }]

    /** Reads [name] as text, treating JSON null as absent. */
    private fun JsonObject.text(name: String): String? {
        val primitive = field(name) as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content
    }

    private suspend fun loadRoles() = coroutineScope {
        val rolesDeferred = async {
            runCatching {
                api.send(
                    Endpoint("api/ClientOperations/roles/$guildId"),
                    ListSerializer(GuildRole.serializer()),
                )
            }.getOrNull()
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
        val roles = rolesDeferred.await()
        val greets = greetsDeferred.await()
        val stats = GuildRoleStats(
            totalRoles = roles?.size ?: 0,
            roleStates = states?.first ?: 0,
            savedRoles = states?.second ?: 0,
            roleGreets = greets.orEmpty().count { it.disabled != true },
        )
        _state.update { current ->
            current.copy(
                roleStats = stats,
                roleNames = roles?.associate { it.id to it.name } ?: current.roleNames,
                roleGreets = greets ?: current.roleGreets,
            )
        }
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
