package studio.cluvex.aethery.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import studio.cluvex.aethery.AppAppearance
import kotlin.math.roundToInt

sealed class UpdateUi {
    object Checking : UpdateUi()
    data class Available(val version: String) : UpdateUi()
    data class Downloading(val version: String, val percent: Int?) : UpdateUi()
    object NeedsPermission : UpdateUi()
    data class Message(val title: String, val body: String) : UpdateUi()
}

data class UpdateActions(
    val primary: Pair<String, () -> Unit>? = null,
    val secondary: Pair<String, () -> Unit>? = null,
)

interface UpdatePages {
    fun show(state: UpdateUi, actions: UpdateActions = UpdateActions())
    fun dismiss()
}

/** A slim progress line: determinate with a percent, otherwise a sweeping segment. */
private class ProgressLine(context: Context, private val palette: AppAppearance.Palette) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var sweep = 0f
    private var animator: ValueAnimator? = null

    var percent: Int? = null
        set(value) {
            field = value
            updateAnimator()
            invalidate()
        }

    private fun updateAnimator() {
        if (percent == null && isAttachedToWindow && !Motion.reduced(context)) {
            if (animator == null) {
                animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1300
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = LinearInterpolator()
                    addUpdateListener { sweep = it.animatedValue as Float; invalidate() }
                    start()
                }
            }
        } else {
            animator?.cancel()
            animator = null
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimator()
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val w = width.toFloat()
        paint.color = HaloColors.withAlpha(palette.ink, 0.12f)
        rect.set(0f, 0f, w, h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
        paint.color = palette.primary
        val value = percent
        if (value != null) {
            rect.set(0f, 0f, w * value.coerceIn(0, 100) / 100f, h)
        } else {
            val segment = w * 0.3f
            val start = (w + segment) * sweep - segment
            rect.set(start.coerceAtLeast(0f), 0f, (start + segment).coerceAtMost(w), h)
        }
        if (rect.width() > 0f) canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
    }
}

/**
 * The updater's one page. It is pushed once and re-rendered in place as the update moves from
 * checking to available to downloading, so the stack never grows. Back is refused while it works.
 */
class UpdatePageController(
    private val context: Context,
    private val palette: AppAppearance.Palette,
    private val push: (page: View, canClose: () -> Boolean) -> Unit,
    private val pop: () -> Unit,
) : UpdatePages {
    private val density = context.resources.displayMetrics.density
    private var scaffold: Scaffold? = null
    private var body: TextView? = null
    private var progress: ProgressLine? = null
    private var bar: LinearLayout? = null
    private var closable = true

    private fun dp(value: Int) = (value * density).roundToInt()

    override fun show(state: UpdateUi, actions: UpdateActions) {
        val page = scaffold ?: create()
        val (title, text, percent, working) = describe(state)
        page.title.text = title
        body?.text = text
        body?.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        progress?.visibility = if (working) View.VISIBLE else View.GONE
        progress?.percent = percent
        closable = !working
        bar?.removeAllViews()
        actions.secondary?.let { (label, action) ->
            bar?.addView(button(label, filled = false, action), LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(10) })
        }
        actions.primary?.let { (label, action) ->
            bar?.addView(button(label, filled = true, action), LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        bar?.visibility = if (actions.primary == null && actions.secondary == null) View.GONE else View.VISIBLE
    }

    override fun dismiss() {
        if (scaffold == null) return
        scaffold = null
        body = null
        progress = null
        bar = null
        closable = true
        pop()
    }

    private data class Description(val title: String, val body: String, val percent: Int?, val working: Boolean)

    private fun describe(state: UpdateUi): Description = when (state) {
        UpdateUi.Checking -> Description("Checking for updates", "", null, true)
        is UpdateUi.Available -> Description("Update available", "Aethery ${state.version} is ready to download.", null, false)
        is UpdateUi.Downloading -> Description(
            "Downloading ${state.version}",
            state.percent?.let { "$it%" } ?: "Starting the download",
            state.percent,
            true,
        )
        UpdateUi.NeedsPermission -> Description(
            "Allow update installs",
            "Android needs permission to install the downloaded Aethery update.",
            null,
            false,
        )
        is UpdateUi.Message -> Description(state.title, state.body, null, false)
    }

    private fun create(): Scaffold {
        val actionBar = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val built = buildScaffold(context, palette, "", null, onBack = { if (closable) dismiss() }, bottomBar = actionBar)
        val text = TextView(context).apply {
            textSize = 16f
            setTextColor(palette.muted)
            typeface = HaloType.body(context, medium = false)
        }
        val line = ProgressLine(context, palette)
        built.content.addView(text, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(12) })
        built.content.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6)).apply {
            topMargin = dp(24)
        })
        scaffold = built
        body = text
        progress = line
        bar = actionBar
        push(built.page) { closable }
        return built
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(if (filled) palette.canvas else palette.ink)
        typeface = HaloType.body(context, medium = true)
        background = if (filled) {
            android.graphics.drawable.GradientDrawable().apply {
                setColor(palette.primary)
                cornerRadius = dp(16).toFloat()
            }
        } else {
            glassBackground(palette, 16, density)
        }
        isClickable = true
        isFocusable = true
        contentDescription = label
        setOnClickListener { onClick() }
    }
}
