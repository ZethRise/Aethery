package studio.cluvex.aethery.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import studio.cluvex.aethery.AppAppearance
import kotlin.math.roundToInt

/** A round check mark that fills and pops with a spring when it becomes selected. */
private class CheckMark(context: Context, private val palette: AppAppearance.Palette) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val reduced = Motion.reduced(context)
    private var pop = 1f
    private val spring = SpringValue(1f, stiffness = 600f, damping = 0.45f) { pop = it; invalidate() }

    var checked: Boolean = false
        private set

    fun setChecked(value: Boolean, animate: Boolean) {
        if (value == checked) return
        checked = value
        if (value && animate && !reduced) {
            spring.snapTo(0.7f)
            spring.animateTo(1f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = width / 2f - density * 1.5f
        canvas.save()
        canvas.scale(pop, pop, cx, cy)
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = palette.primary
            canvas.drawCircle(cx, cy, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2f * density
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = palette.canvas
            path.reset()
            path.moveTo(cx - radius * 0.42f, cy + radius * 0.02f)
            path.lineTo(cx - radius * 0.1f, cy + radius * 0.34f)
            path.lineTo(cx + radius * 0.46f, cy - radius * 0.3f)
            canvas.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * density
            paint.color = HaloColors.withAlpha(palette.ink, 0.35f)
            canvas.drawCircle(cx, cy, radius, paint)
        }
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        spring.cancel()
        super.onDetachedFromWindow()
    }
}

/**
 * A page of options with one selected. Choosing one calls [onSelected] at once, lets the check
 * mark settle for 220 ms, then calls [onBack] to close the page.
 */
fun <T> buildChoicePage(
    context: Context,
    palette: AppAppearance.Palette,
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String,
    onBack: () -> Unit,
    onSelected: (T) -> Unit,
): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).roundToInt()
    val scaffold = buildScaffold(context, palette, title, subtitle, onBack)
    val marks = ArrayList<Pair<T, CheckMark>>()
    var chosen = false

    options.forEachIndexed { index, item ->
        val mark = CheckMark(context, palette).apply { setChecked(item == selected, animate = false) }
        marks.add(item to mark)
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(14), dp(16), dp(14))
            background = glassBackground(palette, 18, density)
            isClickable = true
            isFocusable = true
            contentDescription = label(item)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = label(item)
                    textSize = 16f
                    setTextColor(palette.ink)
                    typeface = HaloType.body(context, medium = true)
                })
                addView(TextView(context).apply {
                    text = description(item)
                    textSize = 13f
                    setTextColor(palette.muted)
                    typeface = HaloType.body(context, medium = false)
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(mark, LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(12) })
            setOnClickListener {
                if (chosen) return@setOnClickListener
                chosen = true
                Haptics.tick(this)
                onSelected(item)
                marks.forEach { (value, check) -> check.setChecked(value == item, animate = true) }
                postDelayed(onBack, 220)
            }
        }
        scaffold.content.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { if (index > 0) topMargin = dp(10) })
    }
    return scaffold.page
}
