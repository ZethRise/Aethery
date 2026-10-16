package studio.cluvex.aethery.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.widget.ImageView
import studio.cluvex.aethery.AppAppearance
import studio.cluvex.aethery.R
import kotlin.math.roundToInt

/** The 44 dp glass back button every page carries, top-left. Presses spring to 0.9. */
fun haloBackButton(context: Context, palette: AppAppearance.Palette, onClick: () -> Unit): ImageView {
    val density = context.resources.displayMetrics.density
    val rest = glassCircle(palette, 44, density)
    val focused = glassCircle(palette, 44, density).apply {
        setStroke((2 * density).roundToInt(), palette.primary)
    }
    val reduced = Motion.reduced(context)
    return ImageView(context).apply {
        setImageResource(R.drawable.ic_back)
        contentDescription = "Back"
        isClickable = true
        isFocusable = true
        val padding = (11 * density).roundToInt()
        setPadding(padding, padding, padding, padding)
        setColorFilter(palette.ink)
        background = rest
        val spring = SpringValue(1f, stiffness = 800f, damping = 0.6f) {
            scaleX = it
            scaleY = it
        }
        setOnTouchListener { _, event ->
            if (!reduced) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> spring.animateTo(0.9f)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> spring.animateTo(1f)
                }
            }
            false
        }
        setOnFocusChangeListener { view, hasFocus -> view.background = if (hasFocus) focused else rest }
        setOnClickListener { onClick() }
    }
}

/** The cold sky as a static page background. */
fun haloPageBackground(palette: AppAppearance.Palette): Drawable =
    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(palette.skyTop, palette.skyBottom))
