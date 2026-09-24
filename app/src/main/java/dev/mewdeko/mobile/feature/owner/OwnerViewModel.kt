package dev.mewdeko.mobile.feature.owner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.mewdeko.mobile.core.auth.OwnerAccess
import dev.mewdeko.mobile.core.auth.OwnershipStore
import dev.mewdeko.mobile.core.auth.SessionHolder
import dev.mewdeko.mobile.core.model.Snowflake
import dev.mewdeko.mobile.core.net.ApiClient
import dev.mewdeko.mobile.core.net.userFacingMessage
import dev.mewdeko.mobile.core.theme.GuildColorStore
import dev.mewdeko.mobile.core.theme.GuildPalette
import dev.mewdeko.mobile.core.ui.LoadState
import dev.mewdeko.mobile.core.ui.StatusMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The owner gate's view model: whether the signed-in user owns the selected
 * bot, and the palette owner pages are themed with.
 *
 * Owner pages are fleet level and have no guild icon to sample, so they carry
 * the signed-in user's avatar colors, the same identity the Me tab they are
 * opened from wears. The palette is derived locally rather than applied to the
 * shared store, so entering an owner page never races the Me tab releasing it.
 */
@HiltViewModel
class OwnerAccessViewModel @Inject constructor(
    private val ownership: OwnershipStore,
    session: SessionHolder,
    colorStore: GuildColorStore,
) : ViewModel() {

    /** The ownership answer for the current user and instance. */
    val access: StateFlow<OwnerAccess> = ownership.access

    /** The palette owner pages are themed with, seeded from the current theme. */
    val palette: StateFlow<GuildPalette> = session.user
        .map { it?.avatarUrl }
        .distinctUntilChanged()
        .map { colorStore.paletteFor(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, colorStore.palette.value)

    /** The selected bot's display name, when one is picked. */
    val botName: StateFlow<String?> = session.instance
        .map { it?.botName?.takeIf { name -> name.isNotEmpty() } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            session.instance.value?.botName?.takeIf { it.isNotEmpty() },
        )

    /** Asks the selected bot again whether the user is an owner. */
    fun refresh() = ownership.refresh()
}

/**
 * Shared base for every owner page view model.
 *
 * The owner counterpart of the per guild `FeatureViewModel`: the same load
 * and status bookkeeping a `FeatureScaffold` consumes, without a guild id.
 * Every owner endpoint is fleet level and goes out through [api], which pins
 * the selected bot instance, so subclasses call plain `api/...` paths.
 */
abstract class OwnerFeatureViewModel(
    protected val api: ApiClient,
    session: SessionHolder,
) : ViewModel() {

    /** The acting Discord user, the owner being checked by every endpoint. */
    val userId: Snowflake = session.userId

    /** The selected bot's display name, used as the app bar subtitle. */
    val botName: String? = session.instance.value?.botName?.takeIf { it.isNotEmpty() }

    private val _loadState = MutableStateFlow(LoadState())

    /** Load progress for the enclosing `FeatureScaffold`. */
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val _status = MutableStateFlow<StatusMessage?>(null)

    /** The pending transient message, if any. */
    val status: StateFlow<StatusMessage?> = _status.asStateFlow()

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
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _loadState.update { it.failed(t.userFacingMessage) }
            }
        }
    }

    /**
     * Runs a user initiated mutation. Failures surface as an error status
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
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            _status.value = StatusMessage.error(failureMessage)
        }
    }

    /** Publishes a transient message to the screen's snackbar. */
    protected fun postStatus(message: StatusMessage) {
        _status.value = message
    }

    /**
     * Publishes a success message. Only for effects the screen does not
     * already show, such as a command sent to the host.
     */
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
