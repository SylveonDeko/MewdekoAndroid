package dev.mewdeko.mobile.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.AuthManager
import dev.mewdeko.mobile.core.auth.DiscordOAuthFlow
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.InstancesResponse
import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.model.RemoteMobileConfig
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.Endpoint
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.store.InstanceStore
import dev.mewdeko.mobile.core.store.SecureStore
import dev.mewdeko.mobile.core.store.ServerConfig
import dev.mewdeko.mobile.core.store.ServerConfigStore
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.net.URI
import javax.inject.Inject

private const val TAG = "MewdekoApp"

/** Where the user currently sits in the onboarding flow. */
sealed interface AppPhase {
    /** Restoring persisted state. */
    data object Launching : AppPhase

    /** No dashboard has been configured yet. */
    data object NeedsServer : AppPhase

    /**
     * A dashboard is saved but unreachable. Carries the server that was tried
     * and a human-readable reason.
     */
    data class ServerUnavailable(val server: ServerConfig, val reason: String) : AppPhase

    /** The dashboard is reachable but no credentials are stored. */
    data object NeedsSignIn : AppPhase

    /** Signed in, but the dashboard hosts several bots and one must be picked. */
    data class NeedsInstance(val user: MobileUser, val instances: List<MobileInstance>) : AppPhase

    /** Fully signed in and ready to use. */
    data class SignedIn(val user: MobileUser, val instance: MobileInstance?) : AppPhase
}

/** Top-level app state. */
data class AppState(
    val phase: AppPhase = AppPhase.Launching,
    val serverConfig: ServerConfig? = null,
    val savedServers: List<ServerConfig> = emptyList(),
    val lastError: String? = null,
    val isSigningIn: Boolean = false,
)

/**
 * Drives onboarding: which dashboard is active, whether credentials exist for
 * it, and which bot instance requests are routed to.
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val configStore: ServerConfigStore,
    private val secureStore: SecureStore,
    private val instanceStore: InstanceStore,
    private val auth: AuthManager,
    private val api: ApiClient,
    private val oauth: DiscordOAuthFlow,
    private val session: SessionHolder,
) : ViewModel() {

    private val _state = MutableStateFlow(AppState())

    /** Observable app state. */
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** The bot instance currently selected, if any. */
    val selectedInstance: MobileInstance?
        get() = (_state.value.phase as? AppPhase.SignedIn)?.instance

    init {
        bootstrap()
    }

    /**
     * Restores any persisted server and credentials, advancing the onboarding
     * phase accordingly.
     */
    fun bootstrap() = viewModelScope.launch {
        refreshServerList()
        val stored = configStore.active()
        if (stored == null) {
            _state.update { it.copy(phase = AppPhase.NeedsServer) }
            return@launch
        }
        if (!select(stored)) {
            val reason = _state.value.lastError ?: "Could not reach ${stored.baseUrl.hostOrSelf()}."
            _state.update { it.copy(phase = AppPhase.ServerUnavailable(stored, reason)) }
            return@launch
        }
        val user = secureStore.loadUser(stored.id)
        if (user == null) {
            _state.update { it.copy(phase = AppPhase.NeedsSignIn) }
            return@launch
        }
        beginInstanceSelection(user)
    }

    /** Retries connecting to the currently-saved server. */
    fun retryStoredServer() = viewModelScope.launch {
        _state.update { it.copy(phase = AppPhase.Launching) }
        bootstrap()
    }

    /**
     * Sets the active dashboard, persists it, and loads its public mobile
     * config. Returns `true` when the dashboard answered with a usable config.
     */
    suspend fun select(serverConfig: ServerConfig): Boolean {
        val saved = configStore.upsert(serverConfig)
        _state.update { it.copy(serverConfig = saved) }
        refreshServerList()
        api.configure(saved.baseUrl)
        auth.configure(saved.baseUrl, saved.id)

        return try {
            val remote = withTimeout(8_000) {
                api.send(
                    Endpoint("api/mobile/config", requiresAuth = false),
                    RemoteMobileConfig.serializer(),
                )
            }
            auth.applyRemote(remote)
            Log.i(TAG, "server selected: ${saved.baseUrl}")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "mobile config fetch failed for ${saved.baseUrl}: ${t.message}")
            val host = saved.baseUrl.hostOrSelf()
            val message = if (t is TimeoutCancellationException) {
                "$host took too long to respond."
            } else {
                "Could not reach $host: ${t.userFacingMessage}"
            }
            _state.update { it.copy(lastError = message) }
            false
        }
    }

    /** Adds a dashboard from the setup screen and advances past it on success. */
    fun addServer(label: String, baseUrl: String) = viewModelScope.launch {
        _state.update { it.copy(phase = AppPhase.Launching, lastError = null) }
        val normalized = baseUrl.trim().let {
            if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
        }.trimEnd('/')
        val config = ServerConfig(label = label.ifBlank { normalized.hostOrSelf() }, baseUrl = normalized)
        if (!select(config)) {
            _state.update { it.copy(phase = AppPhase.NeedsServer) }
            return@launch
        }
        val user = secureStore.loadUser(config.id)
        if (user == null) {
            _state.update { it.copy(phase = AppPhase.NeedsSignIn) }
        } else {
            beginInstanceSelection(user)
        }
    }

    /**
     * Switches to a server already saved in the list. Credentials for that
     * server are picked up automatically when present; otherwise the user is
     * dropped onto sign-in.
     */
    fun switchToSavedServer(id: String) = viewModelScope.launch {
        val target = _state.value.savedServers.firstOrNull { it.id == id } ?: return@launch
        configStore.setActive(id)
        api.setInstance(null)
        _state.update { it.copy(phase = AppPhase.Launching) }
        if (!select(target)) {
            val reason = _state.value.lastError ?: "Could not reach ${target.baseUrl.hostOrSelf()}."
            _state.update { it.copy(phase = AppPhase.ServerUnavailable(target, reason)) }
            return@launch
        }
        val user = secureStore.loadUser(target.id)
        if (user != null) beginInstanceSelection(user)
        else _state.update { it.copy(phase = AppPhase.NeedsSignIn) }
    }

    /**
     * Sends the user to the server-list screen without touching the saved
     * configs, so a stuck active server can be stepped over without being
     * forgotten.
     */
    fun goToServerPicker() = viewModelScope.launch {
        refreshServerList()
        _state.update { it.copy(phase = AppPhase.NeedsServer) }
    }

    /**
     * Removes a server from the saved list and wipes its stored credentials.
     * If it was the active one, another saved server takes over, or onboarding
     * resumes when none remain.
     */
    fun removeSavedServer(id: String) = viewModelScope.launch {
        secureStore.clearAll(id)
        instanceStore.clear(id)
        val wasActive = _state.value.serverConfig?.id == id
        configStore.remove(id)
        refreshServerList()
        if (!wasActive) return@launch
        val next = configStore.active()
        if (next != null) {
            switchToSavedServer(next.id)
        } else {
            _state.update { it.copy(serverConfig = null, phase = AppPhase.NeedsServer) }
        }
    }

    /** Redeems a demo code, which stands in for a Discord sign-in. */
    fun signInWithDemoCode(code: String) = viewModelScope.launch {
        _state.update { it.copy(isSigningIn = true, lastError = null) }
        try {
            val user = auth.signInWithDemoCode(code)
            _state.value.serverConfig?.let { secureStore.saveUser(user, it.id) }
            _state.update { it.copy(isSigningIn = false) }
            beginInstanceSelection(user)
        } catch (t: Throwable) {
            Log.e(TAG, "demo sign-in failed: ${t.message}")
            _state.update {
                it.copy(isSigningIn = false, lastError = "That demo code was not accepted.")
            }
        }
    }

    /** Runs the Discord PKCE flow and records the resulting session. */
    fun signIn(context: Context) = viewModelScope.launch {
        val config = auth.currentRemoteConfig()?.discord
        if (config == null) {
            _state.update { it.copy(lastError = "This dashboard did not publish its Discord settings.") }
            return@launch
        }
        _state.update { it.copy(isSigningIn = true, lastError = null) }
        try {
            val result = oauth.authorize(context, config)
            val user = auth.signIn(result)
            _state.value.serverConfig?.let { secureStore.saveUser(user, it.id) }
            _state.update { it.copy(isSigningIn = false) }
            beginInstanceSelection(user)
        } catch (t: Throwable) {
            Log.e(TAG, "sign-in failed: ${t.message}")
            _state.update { it.copy(isSigningIn = false, lastError = t.userFacingMessage) }
        }
    }

    /** Fails any pending authorization when the user backs out of the browser. */
    fun cancelPendingSignIn() = viewModelScope.launch {
        if (oauth.hasPending()) oauth.cancelPending()
    }

    /** Records the user's chosen bot instance and shows the main shell. */
    fun selectInstance(instance: MobileInstance, user: MobileUser) = viewModelScope.launch {
        api.setInstance(instance.botId)
        _state.value.serverConfig?.let { instanceStore.save(instance, it.id) }
        _state.update { it.copy(phase = AppPhase.SignedIn(user, instance)) }
    }

    /** Returns to the instance picker for the signed-in user. */
    fun switchInstance() = viewModelScope.launch {
        val user = (_state.value.phase as? AppPhase.SignedIn)?.user ?: return@launch
        api.setInstance(null)
        _state.value.serverConfig?.let { instanceStore.clear(it.id) }
        beginInstanceSelection(user)
    }

    /**
     * Loads the dashboard's instances, auto-selecting when only one is active
     * or a previous choice can be restored.
     */
    suspend fun beginInstanceSelection(user: MobileUser) {
        session.set(user)
        try {
            val response = api.send(Endpoint("api/mobile/instances"), InstancesResponse.serializer())
            val active = response.instances.filter { it.isActive }
            Log.i(TAG, "instances loaded: total=${response.instances.size}, active=${active.size}")

            if (active.isEmpty()) {
                _state.update {
                    it.copy(
                        lastError = "No bot instances on this dashboard have a server you administer.",
                        phase = AppPhase.NeedsInstance(user, emptyList()),
                    )
                }
                return
            }
            if (active.size == 1) {
                selectInstance(active.first(), user)
                return
            }
            val serverId = _state.value.serverConfig?.id
            val remembered = serverId?.let { instanceStore.load(it) }
            val match = remembered?.let { saved -> active.firstOrNull { it.botId == saved.botId } }
            if (match != null) {
                Log.i(TAG, "restoring persisted instance ${match.botName}")
                selectInstance(match, user)
                return
            }
            _state.update { it.copy(phase = AppPhase.NeedsInstance(user, active)) }
        } catch (t: Throwable) {
            Log.e(TAG, "instance discovery failed: ${t.message}")
            _state.update {
                it.copy(
                    lastError = "Could not load bot instances: ${t.userFacingMessage}",
                    phase = AppPhase.NeedsInstance(user, emptyList()),
                )
            }
        }
    }

    /**
     * Clears tokens and the cached profile for the current server.
     *
     * @param forgetServer When `true`, also removes the saved server from the
     *   list. Other servers stay intact.
     */
    /**
     * Revokes the session on the dashboard and erases everything this device
     * holds for it: tokens, the cached profile, the pinned instance, and the
     * saved server entry.
     *
     * The Discord account itself is not ours to delete; this removes the link
     * and the local copy, which is what a deletion request here means.
     */
    fun deleteLocalAccountData() = viewModelScope.launch {
        val activeId = _state.value.serverConfig?.id
        session.set(null)
        auth.signOut(revokeOnServer = true)
        if (activeId != null) {
            secureStore.clearAll(activeId)
            instanceStore.clear(activeId)
            configStore.remove(activeId)
        }
        api.setInstance(null)
        refreshServerList()
        _state.update {
            it.copy(serverConfig = null, phase = AppPhase.NeedsServer, lastError = null)
        }
    }

    fun signOut(reason: String? = null, forgetServer: Boolean = false) = viewModelScope.launch {
        val activeId = _state.value.serverConfig?.id
        session.set(null)
        auth.signOut(revokeOnServer = !forgetServer)
        if (activeId != null) {
            secureStore.clearAll(activeId)
            instanceStore.clear(activeId)
        }
        api.setInstance(null)

        if (forgetServer && activeId != null) {
            configStore.remove(activeId)
            refreshServerList()
            val next = configStore.active()
            if (next != null) {
                switchToSavedServer(next.id)
            } else {
                _state.update { it.copy(serverConfig = null, phase = AppPhase.NeedsServer) }
            }
        } else {
            _state.update { it.copy(phase = AppPhase.NeedsSignIn) }
        }
        if (reason != null) _state.update { it.copy(lastError = reason) }
    }

    /** Clears the standing error banner. */
    fun clearError() = _state.update { it.copy(lastError = null) }

    private suspend fun refreshServerList() {
        _state.update { it.copy(savedServers = configStore.snapshot().servers) }
    }
}

/** The host portion of a URL string, falling back to the string itself. */
fun String.hostOrSelf(): String =
    runCatching { URI(this).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: this
