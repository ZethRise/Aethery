package studio.cluvex.aethery.ui

import android.graphics.drawable.GradientDrawable
import studio.cluvex.aethery.AppAppearance
import kotlin.math.roundToInt

/** Translucent panel: the palette's ink at 6% over the sky, with a hairline at 10%. */
fun glassBackground(palette: AppAppearance.Palette, radiusDp: Int, density: Float): GradientDrawable =
    GradientDrawable().apply {
        setColor(HaloColors.withAlpha(palette.ink, 0.06f))
        cornerRadius = radiusDp * density
        setStroke(density.roundToInt().coerceAtLeast(1), HaloColors.withAlpha(palette.ink, 0.10f))
    }

fun glassCircle(palette: AppAppearance.Palette, sizeDp: Int, density: Float): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(HaloColors.withAlpha(palette.ink, 0.06f))
        setSize((sizeDp * density).roundToInt(), (sizeDp * density).roundToInt())
        setStroke(density.roundToInt().coerceAtLeast(1), HaloColors.withAlpha(palette.ink, 0.10f))
    }
