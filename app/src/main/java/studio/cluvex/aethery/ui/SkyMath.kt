package studio.cluvex.aethery.ui

import kotlin.random.Random

object SkyMath {
    data class Star(val x: Float, val y: Float, val size: Float, val phase: Float)

    /**
     * Zenith and horizon colours of the sky for a warmth level (0 cold, 1 warm). Warmth pulls the
     * horizon further toward dawn than the zenith, but only part of the way: the rest of the
     * warmth is the radial glow, so text on the lower screen keeps its contrast.
     */
    fun colors(warmth: Float, top: Int, bottom: Int, dawn: Int): Pair<Int, Int> =
        HaloColors.mix(top, dawn, warmth * 0.06f) to HaloColors.mix(bottom, dawn, warmth * 0.3f)

    fun stars(seed: Long, count: Int): List<Star> {
        val random = Random(seed)
        return List(count) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                size = 0.6f + random.nextFloat(),
                phase = random.nextFloat(),
            )
        }
    }
}
