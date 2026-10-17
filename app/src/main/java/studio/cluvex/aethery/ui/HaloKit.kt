package studio.cluvex.aethery.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import studio.cluvex.aethery.AppAppearance
import studio.cluvex.aethery.R
import kotlin.math.roundToInt

/**
 * The pieces every page after the main screen is made of, so they share its look and its motion:
 * sentence-case section headings, glass groups of rows, springy switches, option cards and pill
 * buttons. Nothing here reads preferences; callers hand in values and callbacks.
 */
class HaloKit(val context: Context, val palette: AppAppearance.Palette) {
    val density: Float = context.resources.displayMetrics.density
    val reduced: Boolean = Motion.reduced(context)
    fun dp(value: Int): Int = (value * density).roundToInt()

    fun text(
        value: String,
        sizeSp: Float,
        color: Int,
        medium: Boolean = false,
    ): TextView = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        typeface = HaloType.body(context, medium)
    }

    /** A small heading over a group, in sentence case. */
    fun section(title: String): TextView = text(title, 13f, palette.muted, medium = true).apply {
        letterSpacing = 0.01f
        setPadding(dp(6), 0, 0, 0)
    }

    /** A line of explanation under a group. */
    fun note(value: String): TextView = text(value, 13f, palette.muted).apply {
        setPadding(dp(6), 0, dp(6), 0)
        setLineSpacing(0f, 1.15f)
    }

    fun matchWidth(topDp: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(topDp) }

    /** A glass panel holding [rows], with a hairline between them. */
    fun group(rows: List<View>): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = glassBackground(palette, 20, density)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                addView(View(context).apply { setBackgroundColor(HaloColors.withAlpha(palette.ink, 0.08f)) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        leftMargin = dp(18)
                        rightMargin = dp(18)
                    })
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    class Row(val view: LinearLayout, val title: TextView, val subtitle: TextView?, val value: TextView?, val trailing: View?) {
        fun setValue(text: String) { value?.text = text }
        fun setSubtitle(text: String) { subtitle?.text = text }
    }

    /**
     * A tappable row: title, optional line under it, optional value and chevron on the right.
     * Presses wash the row with the ink colour and tick the haptic.
     */
    fun row(
        title: String,
        subtitle: String? = null,
        value: String? = null,
        chevron: Boolean = true,
        iconRes: Int? = null,
        tintIcon: Boolean = true,
        titleColor: Int = palette.ink,
        onClick: (() -> Unit)? = null,
    ): Row {
        val titleView = text(title, 16f, titleColor, medium = true)
        val subtitleView = subtitle?.let { text(it, 13f, palette.muted).apply { setLineSpacing(0f, 1.1f) } }
        val valueView = value?.let {
            text(it, 15f, palette.muted).apply {
                gravity = Gravity.END
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
        }
        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleView)
            subtitleView?.let { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) }) }
        }
        val chevronView = if (chevron && onClick != null) ImageView(context).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(HaloColors.withAlpha(palette.ink, 0.45f))
        } else null
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(14), dp(15))
            minimumHeight = dp(56)
            iconRes?.let { res ->
                addView(ImageView(context).apply {
                    setImageResource(res)
                    if (tintIcon) setColorFilter(palette.primary)
                }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(14) })
            }
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            valueView?.let { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) }) }
            chevronView?.let { addView(it, LinearLayout.LayoutParams(dp(20), dp(20)).apply { leftMargin = dp(6) }) }
        }
        if (onClick != null) pressable(container) { onClick() }
        container.contentDescription = listOfNotNull(title, value, subtitle).joinToString(", ")
        return Row(container, titleView, subtitleView, valueView, chevronView)
    }

    /** A row whose right side is a [HaloSwitch]. Tapping anywhere on it flips the switch. */
    fun switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): Row {
        val toggle = HaloSwitch(context, palette).apply { setChecked(checked, animate = false) }
        val base = row(title, subtitle)
        base.view.addView(toggle, LinearLayout.LayoutParams(dp(54), dp(32)).apply { leftMargin = dp(12) })
        pressable(base.view) {
            toggle.toggle()
            onChange(toggle.isOn)
        }
        base.view.contentDescription = "$title, ${if (checked) "on" else "off"}"
        toggle.onFlip = { on -> base.view.contentDescription = "$title, ${if (on) "on" else "off"}" }
        return Row(base.view, base.title, base.subtitle, null, toggle)
    }

    /**
     * Washes the view with the ink colour while it is held and calls [onClick] on release. The
     * wash is a foreground so it follows whatever shape clips the view.
     */
    fun pressable(view: View, onClick: () -> Unit) {
        val wash = GradientDrawable().apply { setColor(HaloColors.withAlpha(palette.ink, 0.10f)) }
        view.isClickable = true
        view.isFocusable = true
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.foreground = wash
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.foreground = null
            }
            false
        }
        view.setOnClickListener {
            Haptics.click(it)
            onClick()
        }
    }

    enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

    /** A full-width pill. Springs to 0.97 while pressed. */
    fun button(label: String, kind: ButtonKind = ButtonKind.SECONDARY, iconRes: Int? = null, tintIcon: Boolean = true, onClick: () -> Unit): TextView {
        val fill: Int
        val ink: Int
        val stroke: Int
        when (kind) {
            ButtonKind.PRIMARY -> { fill = palette.primary; ink = palette.canvas; stroke = palette.primary }
            ButtonKind.DANGER -> { fill = HaloColors.withAlpha(palette.error, 0.14f); ink = palette.error; stroke = HaloColors.withAlpha(palette.error, 0.5f) }
            ButtonKind.SECONDARY -> { fill = HaloColors.withAlpha(palette.ink, 0.06f); ink = palette.ink; stroke = HaloColors.withAlpha(palette.ink, 0.10f) }
        }
        return text(label, 15f, ink, medium = true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(54)
            setPadding(dp(20), 0, dp(20), 0)
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dp(27).toFloat()
                setStroke(dp(1), stroke)
            }
            iconRes?.let {
                setCompoundDrawablesRelativeWithIntrinsicBounds(it, 0, 0, 0)
                compoundDrawablePadding = dp(12)
                if (tintIcon) compoundDrawablesRelative[0]?.setTint(ink)
            }
            contentDescription = label
            isClickable = true
            isFocusable = true
            val spring = SpringValue(1f, stiffness = 800f, damping = 0.6f) { scaleX = it; scaleY = it }
            setOnTouchListener { _, event ->
                if (!reduced) when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> spring.animateTo(0.97f)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> spring.animateTo(1f)
                }
                false
            }
            setOnClickListener {
                if (kind == ButtonKind.PRIMARY) Haptics.confirm(it) else Haptics.click(it)
                onClick()
            }
        }
    }

    /**
     * A selectable card: title, description and a check mark. [onPick] runs on tap; the card
     * itself only shows [selected], so a caller that owns several cards updates them together.
     */
    class OptionCard internal constructor(val view: LinearLayout, private val mark: CheckMark, private val panel: GradientDrawable, private val kit: HaloKit) {
        fun setSelected(selected: Boolean, animate: Boolean = true) {
            mark.setChecked(selected, animate)
            panel.setColor(if (selected) HaloColors.withAlpha(kit.palette.primary, 0.14f) else HaloColors.withAlpha(kit.palette.ink, 0.06f))
            panel.setStroke(kit.dp(1), if (selected) HaloColors.withAlpha(kit.palette.primary, 0.6f) else HaloColors.withAlpha(kit.palette.ink, 0.10f))
        }
    }

    fun optionCard(title: String, description: String, selected: Boolean, enabled: Boolean = true, leading: View? = null, onPick: () -> Unit): OptionCard {
        val mark = CheckMark(context, palette)
        val panel = GradientDrawable().apply { cornerRadius = dp(20).toFloat() }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(15), dp(16), dp(15))
            minimumHeight = dp(64)
            background = panel
            alpha = if (enabled) 1f else 0.5f
            leading?.let { addView(it, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(14) }) }
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 16f, palette.ink, medium = true))
                addView(text(description, 13f, palette.muted), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(2) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(mark, LinearLayout.LayoutParams(dp(26), dp(26)).apply { leftMargin = dp(12) })
            contentDescription = "$title, $description"
            isClickable = enabled
            isFocusable = enabled
            val spring = SpringValue(1f, stiffness = 800f, damping = 0.6f) { scaleX = it; scaleY = it }
            setOnTouchListener { _, event ->
                if (enabled && !reduced) when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> spring.animateTo(0.98f)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> spring.animateTo(1f)
                }
                false
            }
            setOnClickListener { Haptics.tick(it); onPick() }
        }
        return OptionCard(card, mark, panel, this).also { it.setSelected(selected, animate = false) }
    }

    /** A text field in the glass style the pages share; [multiline] fields are four lines tall. */
    fun field(value: String, hint: String, secure: Boolean = false, multiline: Boolean = false): android.widget.EditText =
        android.widget.EditText(context).apply {
            setText(value)
            this.hint = hint
            setTextColor(palette.ink)
            setHintTextColor(HaloColors.withAlpha(palette.muted, 0.7f))
            textSize = 15f
            typeface = HaloType.body(context, medium = false)
            inputType = when {
                secure -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                multiline -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> android.text.InputType.TYPE_CLASS_TEXT
            }
            setSingleLine(!multiline)
            if (multiline) maxLines = 5
            gravity = if (multiline) Gravity.TOP else Gravity.CENTER_VERTICAL
            setPadding(dp(18), if (multiline) dp(14) else 0, dp(18), if (multiline) dp(14) else 0)
            background = glassBackground(palette, 16, density)
        }

    /** A short pill that shows one choice among a few, for segmented controls. */
    fun segmented(options: List<String>, selectedIndex: Int, onPick: (Int) -> Unit): LinearLayout {
        val pills = ArrayList<TextView>()
        fun paint(selected: Int) = pills.forEachIndexed { i, pill ->
            val on = i == selected
            pill.setTextColor(if (on) palette.canvas else palette.ink)
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor(if (on) palette.primary else 0)
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = glassBackground(palette, 22, density)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            options.forEachIndexed { index, name ->
                val pill = text(name, 14f, palette.ink, medium = true).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(40)
                    isClickable = true
                    contentDescription = name
                    maxLines = 1
                    setOnClickListener { Haptics.tick(it); paint(index); onPick(index) }
                }
                pills.add(pill)
                addView(pill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            paint(selectedIndex)
        }
    }

    /**
     * Brings the children of [container] in one after another: each fades up on a spring, 35 ms
     * apart. With reduced motion they simply appear.
     */
    fun stagger(container: ViewGroup, startDelayMs: Long = 60) {
        if (reduced) return
        // A long list (every installed app) would take seconds to cascade, so only the first few move.
        for (i in 0 until minOf(container.childCount, 12)) {
            val child = container.getChildAt(i)
            child.alpha = 0f
            child.translationY = dp(18).toFloat()
            val spring = SpringValue(0f, stiffness = 260f, damping = 0.82f) {
                child.alpha = (it * 1.4f).coerceIn(0f, 1f)
                child.translationY = (1f - it) * dp(18)
            }
            child.postDelayed({ spring.animateTo(1f) }, startDelayMs + i * 35L)
        }
    }
}

/** A switch whose thumb leaps on a spring and whose track warms from glass to the primary colour. */
class HaloSwitch(context: Context, private val palette: AppAppearance.Palette) : View(context) {
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val reduced = Motion.reduced(context)
    private var progress = 0f
    private var squash = 0f
    private val thumb = SpringValue(0f, stiffness = 500f, damping = 0.55f) { progress = it; invalidate() }

    var isOn = false
        private set
    var onFlip: ((Boolean) -> Unit)? = null

    init {
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setChecked(on: Boolean, animate: Boolean) {
        isOn = on
        val target = if (on) 1f else 0f
        if (animate && !reduced) thumb.animateTo(target) else thumb.snapTo(target)
    }

    fun toggle() {
        setChecked(!isOn, animate = true)
        onFlip?.invoke(isOn)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val trackH = h * 0.86f
        val top = (h - trackH) / 2f
        val mix = progress.coerceIn(0f, 1f)
        paint.style = Paint.Style.FILL
        paint.color = HaloColors.mix(HaloColors.withAlpha(palette.ink, 0.14f), palette.primary, mix)
        rect.set(0f, top, w, top + trackH)
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, paint)
        val pad = density * 3f
        val radius = trackH / 2f - pad
        val travel = w - 2f * (radius + pad)
        val cx = pad + radius + travel * progress
        paint.color = HaloColors.mix(palette.ink, palette.canvas, mix * 0.2f)
        canvas.drawCircle(cx, h / 2f, radius, paint)
    }

    override fun onDetachedFromWindow() {
        thumb.cancel()
        super.onDetachedFromWindow()
    }
}
