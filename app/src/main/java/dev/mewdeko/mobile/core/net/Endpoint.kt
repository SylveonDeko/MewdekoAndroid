package dev.mewdeko.mobile.core.net

/** HTTP verbs the dashboard API uses. */
enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

/**
 * A strongly-typed HTTP endpoint description. [T] is the decoded response
 * type; use [Unit] for calls whose body is ignored.
 */
data class Endpoint(
    val path: String,
    val method: HttpMethod = HttpMethod.GET,
    val body: String? = null,
    val requiresAuth: Boolean = true,
)
