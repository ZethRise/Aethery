package studio.cluvex.aethery.ui

import android.content.Context
import android.graphics.Typeface

/**
 * The app's type. One bundled variable family when `assets/fonts/bricolage_grotesque.ttf` is
 * present; the system sans otherwise, so a missing or broken font never breaks a layout.
 */
object HaloType {
    private const val ASSET = "fonts/bricolage_grotesque.ttf"
    private val cache = HashMap<String, Typeface?>()

    fun display(context: Context): Typeface =
        variable(context, weight = 500, width = 92) ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    fun body(context: Context, medium: Boolean): Typeface =
        variable(context, weight = if (medium) 500 else 400, width = 100)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans", Typeface.NORMAL)

    @Synchronized
    private fun variable(context: Context, weight: Int, width: Int): Typeface? {
        val key = "$weight/$width"
        if (cache.containsKey(key)) return cache[key]
        val built = runCatching {
            Typeface.Builder(context.applicationContext.assets, ASSET)
                .setFontVariationSettings("'wght' $weight, 'wdth' $width")
                .build()
        }.getOrNull()
        cache[key] = built
        return built
    }
}
