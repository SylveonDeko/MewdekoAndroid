package dev.mewdeko.mobile.core.net

/** Errors surfaced by [ApiClient]. */
sealed class ApiError(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** No dashboard base URL has been selected yet. */
    data object NotConfigured : ApiError("No dashboard configured")

    /** The server rejected the request with a non-2xx status. */
    data class Http(val status: Int, val body: String) : ApiError("HTTP $status: $body")

    /** The response body did not match the expected shape. */
    data class Decoding(val reason: Throwable) : ApiError("Decode failure: ${reason.message}", reason)

    /** The request never reached the server. */
    data class Transport(val reason: Throwable) : ApiError("Transport failure: ${reason.message}", reason)

    /** Authentication failed and could not be recovered by refreshing. */
    data object Unauthorized : ApiError("Unauthorized")
}

/** Human-readable text for surfacing an API failure in the UI. */
val Throwable.userFacingMessage: String
    get() = when (this) {
        is ApiError.NotConfigured -> "No dashboard selected."
        is ApiError.Unauthorized -> "Your session expired. Sign in again."
        is ApiError.Http -> if (body.isBlank()) "Server error ($status)." else body.take(300)
        is ApiError.Transport -> "Could not reach the dashboard."
        is ApiError.Decoding -> "The server sent an unexpected response."
        else -> message ?: "Something went wrong."
    }
