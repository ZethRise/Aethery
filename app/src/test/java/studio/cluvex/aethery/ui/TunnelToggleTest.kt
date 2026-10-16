package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelToggleTest {
    @Test
    fun aTapWhileConnectingCancelsTheAttempt() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.CONNECTING, coreRunning = false))
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.CONNECTING, coreRunning = true))
    }

    @Test
    fun aTapOnALiveTunnelDisconnects() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.CONNECTED, coreRunning = true))
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.DEGRADED, coreRunning = true))
    }

    @Test
    fun aTapWhileTheCoreIsStillRunningDisconnects() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.DISCONNECTED, coreRunning = true))
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.FAILED, coreRunning = true))
    }

    @Test
    fun aTapAfterACancelledAttemptConnectsAgain() {
        // The attempt was cancelled before the core ever started, so there is nothing left to
        // wait for and the screen is disconnected: the next tap must connect.
        assertEquals(TunnelToggle.Decision.CONNECT, TunnelToggle.decide(OrbState.DISCONNECTED, coreRunning = false))
    }

    @Test
    fun aTapOnAnIdleOrFailedScreenConnects() {
        assertEquals(TunnelToggle.Decision.CONNECT, TunnelToggle.decide(OrbState.DISCONNECTED, coreRunning = false))
        assertEquals(TunnelToggle.Decision.CONNECT, TunnelToggle.decide(OrbState.FAILED, coreRunning = false))
    }
}
