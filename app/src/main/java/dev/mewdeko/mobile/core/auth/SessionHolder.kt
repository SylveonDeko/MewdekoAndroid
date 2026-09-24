package dev.mewdeko.mobile.core.auth

import dev.mewdeko.mobile.core.model.MobileInstance
import dev.mewdeko.mobile.core.model.MobileUser
import dev.mewdeko.mobile.core.model.Snowflake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in user for the current session.
 *
 * Feature view models are constructed by Hilt from a navigation entry that
 * carries only the guild id, so the acting user comes from here rather than
 * being threaded through every route.
 */
@Singleton
class SessionHolder @Inject constructor() {

    private val _user = MutableStateFlow<MobileUser?>(null)

    /** The signed-in user, or `null` before sign-in completes. */
    val user: StateFlow<MobileUser?> = _user.asStateFlow()

    /** The signed-in user's Discord id, or an empty string when signed out. */
    val userId: Snowflake get() = _user.value?.id.orEmpty()

    /** Records the signed-in user. */
    fun set(user: MobileUser?) {
        _user.value = user
    }

    private val _instance = MutableStateFlow<MobileInstance?>(null)

    /**
     * The bot instance requests are currently routed to, or `null` before one
     * is picked. Fleet level answers such as bot ownership depend on it.
     */
    val instance: StateFlow<MobileInstance?> = _instance.asStateFlow()

    /** Records the bot instance requests are routed to. */
    fun setInstance(instance: MobileInstance?) {
        _instance.value = instance
    }
}
