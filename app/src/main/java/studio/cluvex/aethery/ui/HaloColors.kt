package studio.cluvex.aethery.ui

import kotlin.math.roundToInt

object HaloColors {
    /** Linear per-channel blend of two ARGB colours; `t` is clamped to 0..1. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = (a ushr shift) and 0xFF
            val to = (b ushr shift) and 0xFF
            return (from + (to - from) * f).roundToInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** `color` with its alpha channel replaced by `alpha` (0..1). */
    fun withAlpha(color: Int, alpha: Float): Int {
        val a = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}
