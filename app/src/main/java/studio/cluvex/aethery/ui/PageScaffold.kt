package studio.cluvex.aethery.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import studio.cluvex.aethery.AppAppearance
import kotlin.math.roundToInt

class Scaffold(val page: FrameLayout, val content: LinearLayout, val title: TextView)

/**
 * A full page: glass back button top-left, a large title under it, scrolling content, and an
 * optional bar pinned to the bottom (it rides above the keyboard because the window resizes).
 */
fun buildScaffold(
    context: Context,
    palette: AppAppearance.Palette,
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    bottomBar: View? = null,
): Scaffold {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()

    val page = FrameLayout(context).apply {
        background = haloPageBackground(palette)
        isClickable = true
    }
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(12), dp(24), dp(24))
    }
    content.addView(haloBackButton(context, palette, onBack), LinearLayout.LayoutParams(dp(44), dp(44)))
    val titleView = TextView(context).apply {
        text = title
        textSize = 28f
        setTextColor(palette.ink)
        typeface = HaloType.display(context)
    }
    content.addView(titleView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(20)
    })
    if (subtitle != null) {
        content.addView(TextView(context).apply {
            text = subtitle
            textSize = 15f
            setTextColor(palette.muted)
            typeface = HaloType.body(context, medium = false)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
            bottomMargin = dp(20)
        })
    }
    page.addView(ScrollView(context).apply {
        isFillViewport = true
        isVerticalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    if (bottomBar != null) {
        bottomBar.setBackgroundColor(HaloColors.withAlpha(palette.skyBottom, 0.94f))
        page.addView(bottomBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))
    }
    page.setOnApplyWindowInsetsListener { _, insets ->
        @Suppress("DEPRECATION")
        val top = insets.systemWindowInsetTop
        @Suppress("DEPRECATION")
        val bottom = insets.systemWindowInsetBottom
        content.setPadding(dp(24), top + dp(12), dp(24), if (bottomBar != null) dp(112) else bottom + dp(24))
        bottomBar?.setPadding(dp(24), dp(12), dp(24), bottom + dp(16))
        insets
    }
    return Scaffold(page, content, titleView)
}
