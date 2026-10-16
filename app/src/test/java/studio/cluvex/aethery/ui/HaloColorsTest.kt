package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HaloColorsTest {
    @Test
    fun mixesBlackAndWhiteToMidGrey() {
        assertEquals(0xFF808080.toInt(), HaloColors.mix(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
    }

    @Test
    fun mixAtTheEndsReturnsTheInputs() {
        val a = 0xFF102030.toInt()
        val b = 0x80A0B0C0.toInt()
        assertEquals(a, HaloColors.mix(a, b, 0f))
        assertEquals(b, HaloColors.mix(a, b, 1f))
    }

    @Test
    fun mixBlendsTheAlphaChannelToo() {
        assertEquals(0x80800080.toInt(), HaloColors.mix(0x00FF0000, 0xFF0000FF.toInt(), 0.5f))
    }

    @Test
    fun mixClampsTheFraction() {
        val a = 0xFF102030.toInt()
        val b = 0xFFA0B0C0.toInt()
        assertEquals(a, HaloColors.mix(a, b, -3f))
        assertEquals(b, HaloColors.mix(a, b, 7f))
    }

    @Test
    fun withAlphaSetsTheAlphaChannel() {
        assertEquals(0x80FF0000.toInt(), HaloColors.withAlpha(0xFFFF0000.toInt(), 0.5f))
    }

    @Test
    fun withAlphaClampsBelowZeroAndAboveOne() {
        assertEquals(0x00FF0000, HaloColors.withAlpha(0xFFFF0000.toInt(), -1f))
        assertEquals(0xFFFF0000.toInt(), HaloColors.withAlpha(0x10FF0000, 2f))
    }
}
