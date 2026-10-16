package studio.cluvex.aethery.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/** Last-in, first-out record of the pages that are open; no Android types, so it is unit-tested. */
class PageStackModel<T> {
    private val pages = ArrayList<T>()

    val size: Int get() = pages.size

    fun push(page: T) {
        pages.add(page)
    }

    fun pop(): T? = if (pages.isEmpty()) null else pages.removeAt(pages.lastIndex)

    fun top(): T? = pages.lastOrNull()
}

/**
 * The one transition every page uses, new pages and existing ones alike: the page slides in
 * from the right on a spring while the page below shrinks to 0.96 and dims.
 */
object PageMotion {
    private const val DISTANCE_FRACTION = 0.28f

    fun open(page: View, behind: View) {
        if (Motion.reduced(page.context)) {
            page.alpha = 0f
            page.translationX = 0f
            page.animate().alpha(1f).setDuration(150).start()
            behind.animate().alpha(0.6f).setDuration(150).start()
            return
        }
        val distance = page.resources.displayMetrics.widthPixels * DISTANCE_FRACTION
        apply(page, behind, 0f, distance)
        SpringValue(0f, stiffness = 350f, damping = 0.85f) { apply(page, behind, it, distance) }.animateTo(1f)
    }

    fun close(page: View, behind: View, onEnd: () -> Unit) {
        if (Motion.reduced(page.context)) {
            behind.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start()
            page.animate().alpha(0f).setDuration(150).withEndAction(onEnd).start()
            return
        }
        val distance = page.resources.displayMetrics.widthPixels * DISTANCE_FRACTION
        SpringValue(1f, stiffness = 500f, damping = 1f) { apply(page, behind, it, distance) }
            .animateTo(0f, onEnd = {
                apply(page, behind, 0f, distance)
                onEnd()
            })
    }

    private fun apply(page: View, behind: View, progress: Float, distance: Float) {
        page.translationX = (1f - progress) * distance
        page.alpha = (progress * 1.5f).coerceIn(0f, 1f)
        val scale = 1f - 0.04f * progress.coerceIn(0f, 1.2f)
        behind.scaleX = scale
        behind.scaleY = scale
        behind.alpha = 1f - 0.4f * progress.coerceIn(0f, 1f)
    }
}

/** Pushes pages onto [host] above whatever is showing and pops them, one per back press. */
class PageStack(private val host: FrameLayout) {
    private val model = PageStackModel<View>()

    val isEmpty: Boolean get() = model.size == 0

    fun push(page: View) {
        val behind = host.getChildAt(host.childCount - 1)
        model.push(page)
        host.addView(page, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        PageMotion.open(page, behind)
    }

    /** Removes the top page; false when there was none. */
    fun pop(): Boolean {
        val page = model.pop() ?: return false
        val index = host.indexOfChild(page)
        val behind = if (index > 0) host.getChildAt(index - 1) else page
        PageMotion.close(page, behind) { host.removeView(page) }
        return true
    }
}
