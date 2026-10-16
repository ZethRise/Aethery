package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrbModelTest {
    private fun hop(from: OrbState, to: OrbState) = OrbModel.transition(from, to)

    @Test
    fun warmthFollowsTheState() {
        assertEquals(0f, OrbModel.lookFor(OrbState.DISCONNECTED).warmth, 0f)
        assertEquals(0f, OrbModel.lookFor(OrbState.FAILED).warmth, 0f)
        assertEquals(0.25f, OrbModel.lookFor(OrbState.CONNECTING).warmth, 0f)
        assertEquals(1f, OrbModel.lookFor(OrbState.CONNECTED).warmth, 0f)
        assertEquals(0.45f, OrbModel.lookFor(OrbState.DEGRADED).warmth, 0f)
    }

    @Test
    fun onlyConnectingShowsRingsAndOnlyDegradedFlickers() {
        OrbState.entries.forEach { state ->
            assertEquals(state == OrbState.CONNECTING, OrbModel.lookFor(state).rings)
            assertEquals(state == OrbState.DEGRADED, OrbModel.lookFor(state).flicker)
        }
    }

    @Test
    fun aRepeatedStateEmitsNothing() {
        OrbState.entries.forEach { assertEquals(emptyList<OrbEvent>(), hop(it, it)) }
    }

    @Test
    fun startingToConnectIgnitesTheOrb() {
        assertEquals(listOf(OrbEvent.IGNITE), hop(OrbState.DISCONNECTED, OrbState.CONNECTING))
        assertEquals(listOf(OrbEvent.IGNITE), hop(OrbState.FAILED, OrbState.CONNECTING))
    }

    @Test
    fun connectingToConnectedBloomsWithAConfirmHaptic() {
        assertEquals(listOf(OrbEvent.BLOOM, OrbEvent.HAPTIC_CONFIRM), hop(OrbState.CONNECTING, OrbState.CONNECTED))
    }

    @Test
    fun aRestoredSessionBloomsSilently() {
        assertEquals(listOf(OrbEvent.BLOOM), hop(OrbState.DISCONNECTED, OrbState.CONNECTED))
        assertEquals(listOf(OrbEvent.BLOOM), hop(OrbState.FAILED, OrbState.CONNECTED))
    }

    @Test
    fun recoveringFromDegradedIsQuiet() {
        assertEquals(emptyList<OrbEvent>(), hop(OrbState.DEGRADED, OrbState.CONNECTED))
        assertEquals(emptyList<OrbEvent>(), hop(OrbState.CONNECTED, OrbState.DEGRADED))
    }

    @Test
    fun anyStateToFailedShakesWithARejectHaptic() {
        listOf(OrbState.CONNECTING, OrbState.CONNECTED, OrbState.DEGRADED, OrbState.DISCONNECTED).forEach {
            assertEquals(listOf(OrbEvent.SHAKE, OrbEvent.HAPTIC_REJECT), hop(it, OrbState.FAILED))
        }
    }

    @Test
    fun disconnectingDrainsTheWarmthWithAClickHaptic() {
        assertEquals(listOf(OrbEvent.DRAIN, OrbEvent.HAPTIC_CLICK), hop(OrbState.CONNECTED, OrbState.DISCONNECTED))
        assertEquals(listOf(OrbEvent.DRAIN, OrbEvent.HAPTIC_CLICK), hop(OrbState.DEGRADED, OrbState.DISCONNECTED))
    }

    @Test
    fun cancellingAConnectionOnlyClicks() {
        assertEquals(listOf(OrbEvent.HAPTIC_CLICK), hop(OrbState.CONNECTING, OrbState.DISCONNECTED))
    }

    @Test
    fun clearingAFailureIsSilent() {
        assertEquals(emptyList<OrbEvent>(), hop(OrbState.FAILED, OrbState.DISCONNECTED))
    }

    @Test
    fun rapidFlipsEmitNoEventForARepeatedStateAndOnlyTheLastHopBlooms() {
        val sequence = listOf(
            OrbState.DISCONNECTED, OrbState.CONNECTING, OrbState.FAILED,
            OrbState.FAILED, OrbState.CONNECTING, OrbState.CONNECTED,
        )
        val events = sequence.zipWithNext { from, to -> hop(from, to) }
        assertEquals(emptyList<OrbEvent>(), events[2])
        assertEquals(1, events.count { OrbEvent.BLOOM in it })
        assertEquals(listOf(OrbEvent.BLOOM, OrbEvent.HAPTIC_CONFIRM), events.last())
        assertFalse(events.dropLast(1).any { OrbEvent.HAPTIC_CONFIRM in it })
        assertTrue(events.first() == listOf(OrbEvent.IGNITE))
    }
}
