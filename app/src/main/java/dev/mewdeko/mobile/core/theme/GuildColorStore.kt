package dev.mewdeko.mobile.core.theme

import android.content.Context
import androidx.collection.LruCache
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The active per-guild colour palette.
 *
 * Loads the guild icon through Coil (so it shares the app's image cache),
 * derives a palette off the main thread, and memoises the result per URL.
 */
@Singleton
class GuildColorStore @Inject constructor(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _palette = MutableStateFlow(GuildPalette.Default)

    /** The palette currently applied to guild-scoped UI. */
    val palette: StateFlow<GuildPalette> = _palette.asStateFlow()

    private val cache = LruCache<String, GuildPalette>(32)
    private var loadedIconUrl: String? = null
    private var requestedIconUrl: String? = null
    private var loadJob: Job? = null
    private val imageLoader by lazy { ImageLoader.Builder(context).build() }

    /**
     * Loads and applies the palette derived from the icon at [iconUrl].
     * Passing `null` resets the palette to its default.
     */
    fun update(iconUrl: String?) {
        loadJob?.cancel()
        requestedIconUrl = iconUrl?.takeIf { it.isNotEmpty() }
        if (iconUrl.isNullOrEmpty()) {
            apply(GuildPalette.Default, null)
            return
        }
        if (iconUrl == loadedIconUrl) return
        cache.get(iconUrl)?.let {
            apply(it, iconUrl)
            return
        }
        loadJob = scope.launch {
            val result = extract(iconUrl)
            cache.put(iconUrl, result)
            apply(result, iconUrl)
        }
    }

    /**
     * The palette for the image at [iconUrl], without applying it to the
     * shared store.
     *
     * For screens that theme themselves locally through `MewdekoTheme`, such
     * as the fleet level owner pages, so leaving or entering them never races
     * another screen's [update] and [release] pair. Shares the memoised cache
     * with [update].
     */
    suspend fun paletteFor(iconUrl: String?): GuildPalette {
        val url = iconUrl?.takeIf { it.isNotEmpty() } ?: return GuildPalette.Default
        cache.get(url)?.let { return it }
        val result = extract(url)
        cache.put(url, result)
        return result
    }

    private suspend fun extract(iconUrl: String): GuildPalette = runCatching {
        val request = ImageRequest.Builder(context)
            .data(dashboardSourceUrl(iconUrl))
            .size(Size.ORIGINAL)
            .allowHardware(false)
            .build()
        val drawable = (imageLoader.execute(request) as? SuccessResult)?.drawable
        val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            ?: return@runCatching GuildPalette.Default
        withContext(Dispatchers.Default) { PaletteExtractor.extract(bitmap) }
    }.getOrDefault(GuildPalette.Default)

    /**
     * Resets to the default palette, but only while [iconUrl] is still the
     * image the store was last asked for.
     *
     * A screen that themed the app from its own image calls this when it
     * leaves, so it never clobbers a palette another screen has since
     * requested during the navigation transition.
     */
    fun release(iconUrl: String?) {
        if (requestedIconUrl == iconUrl?.takeIf { it.isNotEmpty() }) update(null)
    }

    /**
     * The icon URL the dashboard would sample.
     *
     * The dashboard loads the bot's `iconUrl`, a Discord CDN URL with no
     * `size` parameter, and quantizes it at its natural size. The app's
     * guild list asks the CDN for `?size=128`, which is a different set of
     * pixels, so the query is dropped here to quantize the same image.
     */
    private fun dashboardSourceUrl(iconUrl: String): String =
        if (iconUrl.startsWith("https://cdn.discordapp.com/")) iconUrl.substringBefore('?') else iconUrl

    private fun apply(palette: GuildPalette, url: String?) {
        _palette.value = palette
        loadedIconUrl = url
    }
}
