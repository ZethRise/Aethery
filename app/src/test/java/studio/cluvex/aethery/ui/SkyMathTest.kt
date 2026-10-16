package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyMathTest {
    private val top = 0xFF0B0D24.toInt()
    private val bottom = 0xFF1B1A45.toInt()
    private val dawn = 0xFFFFC58A.toInt()

    @Test
    fun aColdSkyIsTheTwoBaseColours() {
        assertEquals(top to bottom, SkyMath.colors(0f, top, bottom, dawn))
    }

    @Test
    fun aWarmSkyPullsTheHorizonTowardDawnMoreThanTheZenith() {
        val expected = HaloColors.mix(top, dawn, 0.06f) to HaloColors.mix(bottom, dawn, 0.3f)
        assertEquals(expected, SkyMath.colors(1f, top, bottom, dawn))
    }

    @Test
    fun starsStayInsideTheUnitSquare() {
        val stars = SkyMath.stars(7L, 36)
        assertEquals(36, stars.size)
        stars.forEach { star ->
            assertTrue(star.x in 0f..1f && star.y in 0f..1f && star.phase in 0f..1f)
            assertTrue(star.size in 0.6f..1.6f)
        }
    }

    @Test
    fun starsAreDeterministicPerSeed() {
        assertEquals(SkyMath.stars(7L, 36), SkyMath.stars(7L, 36))
        assertNotEquals(SkyMath.stars(7L, 36), SkyMath.stars(8L, 36))
    }
}
