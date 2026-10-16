package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageStackModelTest {
    @Test
    fun popsInTheReverseOrderOfPushes() {
        val stack = PageStackModel<String>()
        stack.push("a")
        stack.push("b")
        stack.push("c")
        assertEquals("c", stack.pop())
        assertEquals("b", stack.pop())
        assertEquals("a", stack.pop())
        assertNull(stack.pop())
    }

    @Test
    fun topTracksTheLastPushAndIsNullWhenEmpty() {
        val stack = PageStackModel<String>()
        assertNull(stack.top())
        stack.push("a")
        stack.push("b")
        assertEquals("b", stack.top())
        stack.pop()
        assertEquals("a", stack.top())
        stack.pop()
        assertNull(stack.top())
    }

    @Test
    fun sizeFollowsPushesAndPops() {
        val stack = PageStackModel<Int>()
        assertEquals(0, stack.size)
        stack.push(1)
        stack.push(2)
        assertEquals(2, stack.size)
        stack.pop()
        assertEquals(1, stack.size)
    }

    @Test
    fun oneBackPressRemovesExactlyOnePage() {
        val stack = PageStackModel<String>()
        stack.push("only")
        assertEquals("only", stack.pop())
        assertNull(stack.pop())
        assertEquals(0, stack.size)
    }
}
