package studio.cluvex.aethery.ui

import android.content.Context
import android.graphics.Typeface

/**
 * The app's type: Manrope, a variable font bundled in `assets/fonts` (SIL Open Font License, see
 * OFL.txt beside it). If the file is ever missing or unreadable the system sans takes over, so a
 * font problem never breaks a layout.
 */
object HaloType {
    private const val ASSET = "fonts/manrope.ttf"
    private val cache = HashMap<Int, Typeface?>()

    /** Headlines and page titles. */
    fun display(context: Context): Typeface =
        weight(context, 600) ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Everything else; [medium] is for labels and values that need a little more body. */
    fun body(context: Context, medium: Boolean): Typeface =
        weight(context, if (medium) 600 else 450)
            ?: Typeface.create(if (medium) "sans-serif-medium" else "sans", Typeface.NORMAL)

    @Synchronized
    private fun weight(context: Context, wght: Int): Typeface? {
        if (cache.containsKey(wght)) return cache[wght]
        val built = runCatching {
            Typeface.Builder(context.applicationContext.assets, ASSET)
                .setFontVariationSettings("'wght' $wght")
                .build()
        }.getOrNull()
        cache[wght] = built
        return built
    }
}
