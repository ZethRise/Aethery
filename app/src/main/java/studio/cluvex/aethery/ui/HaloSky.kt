package studio.cluvex.aethery.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import studio.cluvex.aethery.AppAppearance
import kotlin.math.PI
import kotlin.math.sin

/**
 * The sky behind the main screen. [warmth] runs from 0 (cold dusk) to 1 (dawn); the horizon
 * glows and the stars fade as it rises. The cold and warm skies are two fixed gradients blended
 * by alpha, so animating warmth allocates nothing per frame.
 */
class HaloSky(context: Context, private val palette: AppAppearance.Palette) : View(context) {
    private val density = resources.displayMetrics.density
    private val stars = SkyMath.stars(11L, 36)
    private val coldPaint = Paint()
    private val warmPaint = Paint()
    private val glowPaint = Paint()
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val reduced = Motion.reduced(context)

    private var tiltX = 0f
    private var tiltY = 0f
    private var clock = 0f
    private var lastFrameNanos = 0L
    private var clockAnimator: ValueAnimator? = null
    private var warmthAnimator: ValueAnimator? = null

    var warmth: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    fun animateWarmth(target: Float, durationMs: Long = 1200) {
        warmthAnimator?.cancel()
        if (reduced || !isAttachedToWindow) {
            warmth = target
            return
        }
        warmthAnimator = ValueAnimator.ofFloat(warmth, target).apply {
            duration = durationMs
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener { warmth = it.animatedValue as Float }
            start()
        }
    }

    /** Tilt in -1..1 on each axis; stars shift up to 6 dp, larger stars further. */
    fun setTilt(x: Float, y: Float) {
        tiltX = x.coerceIn(-1f, 1f)
        tiltY = y.coerceIn(-1f, 1f)
    }

    fun pause() {
        clockAnimator?.cancel()
        clockAnimator = null
    }

    fun resume() {
        if (reduced || clockAnimator != null || !isAttachedToWindow) return
        clockAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 9_000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                clock = it.animatedValue as Float
                val now = System.nanoTime()
                if (now - lastFrameNanos >= FRAME_INTERVAL_NANOS) {
                    lastFrameNanos = now
                    invalidate()
                }
            }
            start()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resume()
    }

    override fun onDetachedFromWindow() {
        pause()
        warmthAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val (coldTop, coldBottom) = SkyMath.colors(0f, palette.skyTop, palette.skyBottom, palette.dawn)
        val (warmTop, warmBottom) = SkyMath.colors(1f, palette.skyTop, palette.skyBottom, palette.dawn)
        coldPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), coldTop, coldBottom, Shader.TileMode.CLAMP)
        warmPaint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), warmTop, warmBottom, Shader.TileMode.CLAMP)
        glowPaint.shader = RadialGradient(
            w / 2f, h * 0.58f, w * 0.95f,
            intArrayOf(HaloColors.withAlpha(palette.dawn, 0.42f), HaloColors.withAlpha(palette.dawn, 0f)),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, coldPaint)
        if (warmth > 0f) {
            warmPaint.alpha = (warmth * 255).toInt()
            canvas.drawRect(0f, 0f, w, h, warmPaint)
            glowPaint.alpha = (warmth * 255).toInt()
            canvas.drawRect(0f, 0f, w, h, glowPaint)
        }
        val visibility = 1f - 0.65f * warmth
        for (star in stars) {
            val twinkle = if (reduced) 0.5f else 0.5f + 0.5f * sin(2.0 * PI * (clock + star.phase)).toFloat()
            starPaint.alpha = ((0.25f + 0.45f * twinkle) * visibility * 255).toInt()
            val shift = star.size * 6f * density
            val x = star.x * w + tiltX * shift
            val y = star.y * h * 0.78f + tiltY * shift
            canvas.drawCircle(x, y, star.size * density, starPaint)
        }
    }

    private companion object {
        const val FRAME_INTERVAL_NANOS = 33_000_000L
    }
}
