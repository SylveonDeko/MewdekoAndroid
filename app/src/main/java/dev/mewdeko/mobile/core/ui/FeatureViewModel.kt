package dev.mewdeko.mobile.core.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.userFacingMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared base for every per-guild feature view model.
 *
 * Supplies the guild id (from the navigation route), the acting user id, an
 * [ApiClient], and the load/status bookkeeping that each feature screen's
 * [FeatureScaffold] consumes. Subclasses hold their own typed data in a state
 * class and call [launchLoad] to populate it.
 */
abstract class FeatureViewModel(
    savedStateHandle: SavedStateHandle,
    protected val api: ApiClient,
    session: SessionHolder,
) : ViewModel() {

    /** The guild this screen is scoped to. */
    val guildId: Snowflake = savedStateHandle.get<String>("guildId").orEmpty()

    /** The guild's display name, as carried through the route. */
    val guildName: String = savedStateHandle.get<String>("guildName")
        ?.takeIf { it != "-" }
        .orEmpty()

    /** The acting Discord user. */
    val userId: Snowflake = session.userId

    private val _loadState = MutableStateFlow(LoadState())

    /** Load progress for the enclosing [FeatureScaffold]. */
    val loadState = _loadState

    private val _status = MutableStateFlow<StatusMessage?>(null)

    /** The pending transient message, if any. */
    val status = _status

    private var activeLoad: Job? = null

    /**
     * Runs [block] as the screen's primary load, driving [loadState] through
     * its loading, loaded, and failed transitions. A second call cancels the
     * first, so a pull to refresh never races the initial load.
     */
    protected fun launchLoad(refreshing: Boolean = false, block: suspend () -> Unit) {
        activeLoad?.cancel()
        activeLoad = viewModelScope.launch {
            _loadState.update { it.loading(refreshing) }
            try {
                block()
                _loadState.update { it.loaded() }
            } catch (t: Throwable) {
                _loadState.update { it.failed(t.userFacingMessage) }
            }
        }
    }

    /**
     * Runs a user-initiated mutation. Failures surface as an error status
     * rather than replacing the screen, since the data already on screen is
     * still valid.
     */
    protected fun launchAction(
        failureMessage: String,
        onSuccess: (suspend () -> Unit)? = null,
        block: suspend () -> Unit,
    ) = viewModelScope.launch {
        try {
            block()
            onSuccess?.invoke()
        } catch (t: Throwable) {
            _status.value = StatusMessage.error(failureMessage)
        }
    }

    /** Publishes a transient message to the screen's snackbar. */
    protected fun postStatus(message: StatusMessage) {
        _status.value = message
    }

    /** Publishes a success message. */
    protected fun postSuccess(text: String) = postStatus(StatusMessage.success(text))

    /** Publishes an error message. */
    protected fun postError(text: String) = postStatus(StatusMessage.error(text))

    /** Clears the pending message once the snackbar has shown it. */
    fun clearStatus() {
        _status.value = null
    }

    /** Marks the screen loaded without running a request. */
    protected fun markLoaded() = _loadState.update { it.loaded() }
}
