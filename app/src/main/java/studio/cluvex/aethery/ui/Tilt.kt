package studio.cluvex.aethery.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Reports how far the phone is tilted from the way it was held when [start] was called, as -1..1
 * on each axis, low-pass filtered. Does nothing without a rotation sensor or with reduced motion.
 */
class Tilt(
    private val context: Context,
    private val onTilt: (x: Float, y: Float) -> Unit,
) : SensorEventListener {
    private val manager = context.getSystemService(SensorManager::class.java)
    private val sensor = manager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotation = FloatArray(9)
    private val orientation = FloatArray(3)
    private var origin: FloatArray? = null
    private var x = 0f
    private var y = 0f
    private var running = false

    fun start() {
        if (running || sensor == null || Motion.reduced(context)) return
        running = true
        origin = null
        manager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        running = false
        manager?.unregisterListener(this)
        x = 0f
        y = 0f
        onTilt(0f, 0f)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, orientation)
        val start = origin ?: orientation.copyOf().also { origin = it }
        val roll = orientation[2] - start[2]
        val pitch = orientation[1] - start[1]
        x += (clamp(roll / MAX_RADIANS) - x) * ALPHA
        y += (clamp(pitch / MAX_RADIANS) - y) * ALPHA
        onTilt(x, y)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun clamp(value: Float): Float = if (abs(value) > 1f) Math.signum(value) else value

    private companion object {
        const val ALPHA = 0.12f
        const val MAX_RADIANS = 0.5f
    }
}
