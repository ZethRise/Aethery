package studio.cluvex.aethery.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun zeroAnimatorScaleMeansReducedMotion() {
        assertTrue(Motion.reducedFromScale(0f))
    }

    @Test
    fun anyOtherAnimatorScaleKeepsMotion() {
        assertFalse(Motion.reducedFromScale(1f))
        assertFalse(Motion.reducedFromScale(0.5f))
    }
}
