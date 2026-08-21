package dev.mewdeko.mobile.core.ui

import androidx.compose.runtime.Immutable

/** Severity of a transient message shown in a snackbar. */
enum class StatusKind { SUCCESS, ERROR }

/** A transient user-facing message emitted by a feature view model. */
@Immutable
data class StatusMessage(
    val kind: StatusKind,
    val text: String,
) {
    companion object {
        /** Builds a success message. */
        fun success(text: String) = StatusMessage(StatusKind.SUCCESS, text)

        /** Builds an error message. */
        fun error(text: String) = StatusMessage(StatusKind.ERROR, text)
    }
}

/**
 * Loading state shared by every feature screen.
 *
 * Features keep their own typed data in the view model's state class; this
 * only tracks whether a first load is in flight and whether it failed, so the
 * [FeatureContent] shell can render the right scaffold state.
 */
@Immutable
data class LoadState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    /** Whether the initial full-screen spinner should be shown. */
    val showsInitialSpinner: Boolean get() = isLoading && !hasLoaded

    /** Whether the full-screen error state should be shown. */
    val showsError: Boolean get() = error != null && !hasLoaded

    /** Marks the start of a load. */
    fun loading(refreshing: Boolean = false) =
        copy(isLoading = !refreshing, isRefreshing = refreshing, error = null)

    /** Marks a successful load. */
    fun loaded() = copy(isLoading = false, isRefreshing = false, hasLoaded = true, error = null)

    /** Marks a failed load. */
    fun failed(message: String) =
        copy(isLoading = false, isRefreshing = false, error = message)
}
