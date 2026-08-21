package dev.mewdeko.mobile.core.theme

import android.content.Context
import androidx.collection.LruCache
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
    private var loadJob: Job? = null
    private val imageLoader by lazy { ImageLoader.Builder(context).build() }

    /**
     * Loads and applies the palette derived from the icon at [iconUrl].
     * Passing `null` resets the palette to its default.
     */
    fun update(iconUrl: String?) {
        loadJob?.cancel()
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
            val result = runCatching {
                val request = ImageRequest.Builder(context)
                    .data(iconUrl)
                    .allowHardware(false)
                    .build()
                val drawable = (imageLoader.execute(request) as? SuccessResult)?.drawable
                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: return@runCatching GuildPalette.Default
                withContext(Dispatchers.Default) { PaletteExtractor.extract(bitmap) }
            }.getOrDefault(GuildPalette.Default)

            cache.put(iconUrl, result)
            apply(result, iconUrl)
        }
    }

    private fun apply(palette: GuildPalette, url: String?) {
        _palette.value = palette
        loadedIconUrl = url
    }
}
