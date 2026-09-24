package dev.mewdeko.mobile.core.auth

import android.util.Log
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MewdekoOwnership"

/** What the app knows about whether the signed-in user owns the selected bot. */
enum class OwnerAccess {
    /** No answer yet: signed out, no instance picked, or the probe is in flight. */
    Unknown,

    /** The selected bot lists the user as an owner. */
    Owner,

    /** The selected bot does not list the user as an owner, or the probe failed. */
    NotOwner,
}

/**
 * Tracks whether the signed-in user owns the bot instance requests are routed
 * to, the gate for the owner panel.
 *
 * Ownership is a property of the bot, not of a guild, so the answer is cached
 * per user and bot id and probed again whenever either changes. `GET
 * api/Ownership/{userId}` returns a bare JSON boolean and goes out through
 * [ApiClient], which pins the selected instance the same way `api/BotStatus`
 * does. A failed first probe reads as [OwnerAccess.NotOwner] but is not
 * cached, so [refresh] or the next instance change asks again; a failed
 * [refresh] keeps the answer already on screen.
 */
@Singleton
class OwnershipStore @Inject constructor(
    private val api: ApiClient,
    private val session: SessionHolder,
    private val scope: CoroutineScope,
) {
    private val _access = MutableStateFlow(OwnerAccess.Unknown)

    /** The ownership answer for the current user and instance. */
    val access: StateFlow<OwnerAccess> = _access.asStateFlow()

    private val cacheLock = Mutex()
    private val cache = mutableMapOf<OwnershipKey, Boolean>()

    init {
        observeSession()
    }

    /**
     * Probes again for the current user and instance, ignoring any cached
     * answer. Keeps the current answer on screen while the probe runs.
     */
    fun refresh() {
        val key = currentKey() ?: return
        scope.launch {
            cacheLock.withLock { cache.remove(key) }
            probe(key, showUnknown = false)
        }
    }

    private fun observeSession() {
        scope.launch {
            combine(session.user, session.instance) { user, instance ->
                val userId = user?.id?.takeIf { it.isNotEmpty() }
                val botId = instance?.botId?.takeIf { it.isNotEmpty() }
                if (userId == null || botId == null) null else OwnershipKey(userId, botId)
            }
                .distinctUntilChanged()
                .collectLatest { key ->
                    if (key == null) {
                        _access.value = OwnerAccess.Unknown
                    } else {
                        probe(key, showUnknown = true)
                    }
                }
        }
    }

    private suspend fun probe(key: OwnershipKey, showUnknown: Boolean) {
        val cached = cacheLock.withLock { cache[key] }
        if (cached != null) {
            publish(key, cached)
            return
        }
        if (showUnknown) _access.value = OwnerAccess.Unknown
        val answer = try {
            val raw = api.sendRaw(Endpoint("api/Ownership/${key.userId}"))
            val primitive = raw as? JsonPrimitive
            primitive?.booleanOrNull ?: primitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.w(TAG, "ownership probe failed: ${t.message}")
            null
        }
        if (currentKey() != key) return
        if (api.currentInstance() != key.botId) return
        if (answer == null && !showUnknown && _access.value != OwnerAccess.Unknown) return
        if (answer != null) cacheLock.withLock { cache[key] = answer }
        publish(key, answer ?: false)
    }

    private fun publish(key: OwnershipKey, isOwner: Boolean) {
        if (currentKey() != key) return
        _access.value = if (isOwner) OwnerAccess.Owner else OwnerAccess.NotOwner
    }

    private fun currentKey(): OwnershipKey? {
        val userId = session.user.value?.id?.takeIf { it.isNotEmpty() } ?: return null
        val botId = session.instance.value?.botId?.takeIf { it.isNotEmpty() } ?: return null
        return OwnershipKey(userId, botId)
    }

    /** One cached answer's identity: the Discord user and the bot they asked. */
    private data class OwnershipKey(val userId: Snowflake, val botId: Snowflake)
}
