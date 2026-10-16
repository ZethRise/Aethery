package studio.cluvex.aethery.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import studio.cluvex.aethery.AppAppearance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The connect control: a glass orb that is the connection state. Drawn with an AGSL shader on
 * Android 13+ and from layered gradients below, with one state machine behind both.
 */
class HaloOrb(context: Context, private val palette: AppAppearance.Palette) : View(context) {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, FAILED }

    var state: State = State.DISCONNECTED
        set(value) {
            if (field == value) return
            val from = field
            field = value
            onStateChanged(from, value)
        }

    /** Called with the warmth (0..1) of every new state so the sky can follow the orb. */
    var onWarmthTarget: ((Float) -> Unit)? = null

    /** Debug aid: draw the gradient version even where the shader is available. */
    var forceFallback: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val reduced = Motion.reduced(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bounds = RectF()

    private var timeSeconds = 0f
    private var loop: ValueAnimator? = null

    private var pressScale = 1f
    private var shakeX = 0f
    private var pressAmount = 0f
    private var tiltX = 0f
    private var tiltY = 0f
    private var orbWarm = 0f
    private var warmAnimator: ValueAnimator? = null
    private var bloom = -1f
    private var bloomAnimator: ValueAnimator? = null
    private var colorMix = 1f
    private var colorAnimator: ValueAnimator? = null
    private var fromColors = colorsFor(State.DISCONNECTED)
    private var toColors = fromColors
    private var stateAlpha = 1f

    private val pressSpring = SpringValue(1f, stiffness = 800f, damping = 0.6f) { pressScale = it; invalidate() }
    private val releaseSpring = SpringValue(1f, stiffness = 400f, damping = 0.5f) { pressScale = it; invalidate() }
    private val shakeSpring = SpringValue(0f, stiffness = 600f, damping = 0.3f) { shakeX = it; invalidate() }
    private val pressGlow = SpringValue(0f, stiffness = 800f, damping = 0.8f) { pressAmount = it; invalidate() }

    private var shader: RuntimeShader? = null

    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = false
        contentDescription = "Connect"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (380 * density).roundToInt()
        val width = resolveSize(desired, widthMeasureSpec)
        setMeasuredDimension(width, (width * 0.9f).roundToInt())
    }

    // Glow and rings stay within 1.4 radii, which is the view's half-height, so its bounds never clip them.
    private fun radius(): Float = width * 0.33f

    // --- state ---------------------------------------------------------------------------

    private fun onStateChanged(from: State, to: State) {
        val events = OrbModel.transition(OrbState.valueOf(from.name), OrbState.valueOf(to.name))
        val look = OrbModel.lookFor(OrbState.valueOf(to.name))
        contentDescription = when (to) {
            State.DISCONNECTED, State.FAILED -> "Connect"
            State.CONNECTING -> "Connecting"
            State.CONNECTED, State.DEGRADED -> "Disconnect"
        }
        stateAlpha = when (to) {
            State.FAILED -> 0.7f
            State.DEGRADED -> 0.9f
            else -> 1f
        }
        // A new state ends the previous state's one-shots, so rapid flips never stack animators.
        bloomAnimator?.cancel()
        bloom = -1f
        shakeSpring.cancel()
        shakeX = 0f

        animateWarmthTo(look.warmth, if (to == State.DISCONNECTED) 600L else 700L)
        retargetColors(to)
        onWarmthTarget?.invoke(look.warmth)
        events.forEach(::play)
        updateLoop()
        invalidate()
    }

    private fun play(event: OrbEvent) {
        when (event) {
            OrbEvent.IGNITE -> if (!reduced) nudgeScale(from = 0.94f, velocity = 3f)
            OrbEvent.BLOOM -> {
                if (!reduced) {
                    nudgeScale(from = 1.08f, velocity = 0f)
                    bloomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                        duration = 700
                        interpolator = DecelerateInterpolator(1.6f)
                        addUpdateListener { bloom = it.animatedValue as Float; invalidate() }
                        addListener(object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                bloom = -1f
                                invalidate()
                            }
                        })
                        start()
                    }
                }
            }
            OrbEvent.DRAIN -> if (!reduced) nudgeScale(from = 0.9f, velocity = 0f)
            OrbEvent.SHAKE -> if (!reduced) {
                shakeSpring.snapTo(-14f * density)
                shakeSpring.animateTo(0f)
            }
            OrbEvent.HAPTIC_CONFIRM -> Haptics.confirm(this)
            OrbEvent.HAPTIC_REJECT -> Haptics.reject(this)
            OrbEvent.HAPTIC_CLICK -> Haptics.click(this)
        }
    }

    private fun nudgeScale(from: Float, velocity: Float) {
        pressSpring.cancel()
        releaseSpring.snapTo(from)
        releaseSpring.animateTo(1f, velocity)
    }

    private fun animateWarmthTo(target: Float, durationMs: Long) {
        warmAnimator?.cancel()
        if (reduced) {
            warmAnimator = ValueAnimator.ofFloat(orbWarm, target).apply {
                duration = 150
                addUpdateListener { orbWarm = it.animatedValue as Float; invalidate() }
                start()
            }
            return
        }
        warmAnimator = ValueAnimator.ofFloat(orbWarm, target).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { orbWarm = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun retargetColors(to: State) {
        colorAnimator?.cancel()
        fromColors = currentColors()
        toColors = colorsFor(to)
        colorMix = 0f
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (reduced) 150 else 420
            addUpdateListener { colorMix = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun currentColors(): IntArray =
        IntArray(4) { HaloColors.mix(fromColors[it], toColors[it], colorMix) }

    /** Sphere colours as [coldLight, coldDark, warmLight, warmDark] for a state. */
    private fun colorsFor(state: State): IntArray {
        val white = 0xFFFFFFFF.toInt()
        var coldLight = HaloColors.mix(palette.primary, white, 0.55f)
        var coldDark = HaloColors.mix(palette.primaryContainer, palette.canvas, 0.25f)
        var warmLight = HaloColors.mix(palette.dawn, white, 0.6f)
        var warmDark = HaloColors.mix(palette.dawn, palette.canvas, 0.4f)
        when (state) {
            State.DEGRADED -> {
                coldLight = desaturate(coldLight, 0.45f)
                coldDark = desaturate(coldDark, 0.45f)
                warmLight = desaturate(warmLight, 0.5f)
                warmDark = desaturate(warmDark, 0.5f)
            }
            State.FAILED -> {
                coldLight = HaloColors.mix(coldLight, palette.error, 0.55f)
                coldDark = HaloColors.mix(coldDark, palette.error, 0.35f)
            }
            else -> Unit
        }
        return intArrayOf(coldLight, coldDark, warmLight, warmDark)
    }

    private fun desaturate(color: Int, keep: Float): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val grey = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt()
        val greyColor = (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        return HaloColors.mix(greyColor, color, keep)
    }

    // --- lifecycle -----------------------------------------------------------------------

    fun pauseAnimation() {
        loop?.cancel()
        loop = null
    }

    fun resumeAnimation() = updateLoop()

    private fun updateLoop() {
        if (reduced || !isAttachedToWindow || loop != null) return
        loop = ValueAnimator.ofFloat(0f, 600f).apply {
            duration = 600_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                timeSeconds = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateLoop()
    }

    override fun onDetachedFromWindow() {
        pauseAnimation()
        warmAnimator?.cancel()
        bloomAnimator?.cancel()
        colorAnimator?.cancel()
        pressSpring.cancel()
        releaseSpring.cancel()
        shakeSpring.cancel()
        pressGlow.cancel()
        super.onDetachedFromWindow()
    }

    fun setTilt(x: Float, y: Float) {
        if (reduced) return
        tiltX = x
        tiltY = y
    }

    /** The entrance: the orb rises out of the splash logo. */
    fun wake() {
        if (reduced) {
            alpha = 1f
            return
        }
        alpha = 0f
        animate().alpha(1f).setDuration(320).start()
        nudgeScale(from = 0.6f, velocity = 0f)
    }

    // --- input ---------------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!reduced) {
                    releaseSpring.cancel()
                    pressSpring.snapTo(pressScale)
                    pressSpring.animateTo(0.94f)
                    pressGlow.animateTo(1f)
                }
                Haptics.tick(this)
                return true
            }
            MotionEvent.ACTION_UP -> {
                release()
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                release()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun release() {
        pressGlow.animateTo(0f)
        if (reduced) {
            pressScale = 1f
            invalidate()
            return
        }
        pressSpring.cancel()
        releaseSpring.snapTo(pressScale)
        releaseSpring.animateTo(1f, velocity = 4f)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            performClick()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    // --- drawing -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = radius()
        val look = OrbModel.lookFor(OrbState.valueOf(state.name))
        val colors = currentColors()
        val glowColor = HaloColors.mix(palette.primary, palette.dawn, orbWarm)

        // Halo glow behind everything.
        paint.shader = RadialGradient(
            cx, cy, radius * 1.4f,
            intArrayOf(HaloColors.withAlpha(glowColor, 0.34f * stateAlpha), HaloColors.withAlpha(glowColor, 0f)),
            floatArrayOf(0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius * 1.4f, paint)
        paint.shader = null

        drawRings(canvas, cx, cy, radius, look.rings)
        if (state == State.DEGRADED) drawDegradedRing(canvas, cx, cy, radius)
        if (bloom >= 0f) {
            val maxR = min(width, height) / 2f
            val r = radius + (maxR - radius) * bloom
            strokePaint.color = HaloColors.withAlpha(palette.dawn, 0.6f * (1f - bloom))
            strokePaint.strokeWidth = (3f - 2f * bloom) * density
            canvas.drawCircle(cx, cy, r, strokePaint)
        }

        val breathing = if (!reduced && state == State.DISCONNECTED) {
            1f + 0.015f * (1f - cos(2.0 * PI * timeSeconds / 6.0).toFloat())
        } else 1f
        val scale = pressScale * breathing
        canvas.save()
        canvas.translate(cx + shakeX, cy)
        canvas.scale(scale, scale)
        canvas.translate(-cx, -cy)
        drawSphere(canvas, cx, cy, radius, colors)
        drawGlyph(canvas, cx, cy, radius)
        if (isFocused) {
            strokePaint.color = palette.primary
            strokePaint.strokeWidth = 3f * density
            canvas.drawCircle(cx, cy, radius + 8f * density, strokePaint)
        }
        canvas.restore()
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, radius: Float, active: Boolean) {
        if (!active) return
        strokePaint.color = palette.primary
        strokePaint.strokeWidth = 2f * density
        if (reduced) {
            strokePaint.alpha = 90
            canvas.drawCircle(cx, cy, radius * 1.25f, strokePaint)
            return
        }
        for (i in 0 until 3) {
            val phase = ((timeSeconds / 1.6f) + i / 3f) % 1f
            val r = radius * (1.4f - 0.4f * phase)
            val a = sin(PI * phase).toFloat() * 0.55f
            strokePaint.color = HaloColors.withAlpha(palette.primary, a)
            canvas.drawCircle(cx, cy, r, strokePaint)
        }
    }

    private fun drawDegradedRing(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val flicker = if (reduced) 0.7f else {
            val s = sin(timeSeconds * 9f) * sin(timeSeconds * 2.3f)
            0.45f + 0.4f * s * s
        }
        strokePaint.color = HaloColors.withAlpha(palette.warning, flicker)
        strokePaint.strokeWidth = 3f * density
        canvas.drawCircle(cx, cy, radius * 1.1f, strokePaint)
    }

    private fun drawSphere(canvas: Canvas, cx: Float, cy: Float, radius: Float, colors: IntArray) {
        val dim = (stateAlpha * 255).roundToInt()
        if (!forceFallback && OrbShader.supported() && Build.VERSION.SDK_INT >= 33) {
            val s = shader ?: RuntimeShader(OrbShader.SRC).also { shader = it }
            s.setFloatUniform("iCenter", cx, cy)
            s.setFloatUniform("iRadius", radius)
            s.setFloatUniform("iTime", timeSeconds)
            s.setFloatUniform("iWarmth", orbWarm)
            s.setFloatUniform("iPress", pressAmount)
            s.setFloatUniform("iTilt", tiltX, tiltY)
            setColor(s, "iColdA", colors[0])
            setColor(s, "iColdB", colors[1])
            setColor(s, "iWarmA", colors[2])
            setColor(s, "iWarmB", colors[3])
            paint.shader = s
            paint.alpha = dim
            bounds.set(cx - radius * 1.05f, cy - radius * 1.05f, cx + radius * 1.05f, cy + radius * 1.05f)
            canvas.drawRect(bounds, paint)
            paint.shader = null
            paint.alpha = 255
            return
        }
        val lightX = cx - radius * (0.32f - tiltX * 0.2f)
        val lightY = cy - radius * (0.38f - tiltY * 0.2f)
        val cold = intArrayOf(colors[0], HaloColors.mix(colors[0], colors[1], 0.5f), colors[1])
        val warm = intArrayOf(colors[2], HaloColors.mix(colors[2], colors[3], 0.5f), colors[3])
        val stops = floatArrayOf(0f, 0.5f, 1f)
        paint.shader = RadialGradient(lightX, lightY, radius * 1.3f, cold, stops, Shader.TileMode.CLAMP)
        paint.alpha = ((1f - orbWarm) * dim).roundToInt()
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = RadialGradient(lightX, lightY, radius * 1.3f, warm, stops, Shader.TileMode.CLAMP)
        paint.alpha = (orbWarm * dim).roundToInt()
        canvas.drawCircle(cx, cy, radius, paint)
        paint.shader = null
        paint.alpha = 255
        strokePaint.color = HaloColors.withAlpha(HaloColors.mix(palette.primary, palette.dawn, orbWarm), 0.55f * stateAlpha)
        strokePaint.strokeWidth = 1.5f * density
        canvas.drawCircle(cx, cy, radius - density, strokePaint)
    }

    private fun setColor(shader: RuntimeShader, name: String, color: Int) {
        shader.setFloatUniform(
            name,
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
        )
    }

    private fun drawGlyph(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        val alpha = when (state) {
            State.CONNECTING -> 0f
            State.CONNECTED, State.DEGRADED -> 0.5f
            else -> 0.85f
        }
        if (alpha <= 0f) return
        val glyph = radius * 0.28f
        strokePaint.color = HaloColors.withAlpha(0xFFFFFFFF.toInt(), alpha)
        strokePaint.strokeWidth = 2.6f * density
        strokePaint.strokeCap = Paint.Cap.ROUND
        bounds.set(cx - glyph, cy - glyph, cx + glyph, cy + glyph)
        canvas.drawArc(bounds, 310f, 280f, false, strokePaint)
        canvas.drawLine(cx, cy - glyph * 1.15f, cx, cy - glyph * 0.1f, strokePaint)
        strokePaint.strokeCap = Paint.Cap.BUTT
    }
}
