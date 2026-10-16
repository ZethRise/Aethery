package studio.cluvex.aethery.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

object Motion {
    fun reducedFromScale(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

    /** True when the system has animations turned off ("Remove animations"). */
    fun reduced(context: Context): Boolean = reducedFromScale(
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
    )
}

/**
 * A float that moves to its target with spring physics. It never reads settings: callers that
 * honour reduced motion use [snapTo] instead of [animateTo].
 */
class SpringValue(
    initial: Float,
    private val stiffness: Float,
    private val damping: Float,
    private val onUpdate: (Float) -> Unit,
) {
    private val holder = FloatValueHolder(initial)
    private var animation: SpringAnimation? = null
    private var pendingEnd: (() -> Unit)? = null

    val value: Float get() = holder.value

    fun animateTo(target: Float, velocity: Float = 0f, onEnd: (() -> Unit)? = null) {
        pendingEnd = onEnd
        val running = animation ?: SpringAnimation(holder).also { spring ->
            spring.spring = SpringForce(target)
            spring.setMinimumVisibleChange(0.001f)
            spring.addUpdateListener { _, v, _ -> onUpdate(v) }
            spring.addEndListener { _, canceled, _, _ ->
                if (!canceled) pendingEnd?.also { pendingEnd = null }?.invoke()
            }
            animation = spring
        }
        running.spring.setStiffness(stiffness).setDampingRatio(damping).setFinalPosition(target)
        running.setStartVelocity(velocity)
        running.start()
    }

    fun snapTo(v: Float) {
        cancel()
        holder.value = v
        onUpdate(v)
    }

    fun cancel() {
        pendingEnd = null
        animation?.cancel()
    }
}

object Haptics {
    fun tick(v: View) = perform(v, if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_START else HapticFeedbackConstants.CONTEXT_CLICK)
    fun confirm(v: View) = perform(v, if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
    fun reject(v: View) = perform(v, if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
    fun click(v: View) = perform(v, HapticFeedbackConstants.CONTEXT_CLICK)

    private fun perform(v: View, constant: Int) {
        v.performHapticFeedback(constant)
    }
}
