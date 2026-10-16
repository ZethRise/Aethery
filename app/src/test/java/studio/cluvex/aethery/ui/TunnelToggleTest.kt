package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TunnelToggleTest {
    @Test
    fun aTapWhileConnectingCancelsTheAttempt() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.CONNECTING, coreRunning = false, userDisconnecting = false))
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.CONNECTING, coreRunning = true, userDisconnecting = false))
    }

    @Test
    fun aTapOnALiveTunnelDisconnects() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.CONNECTED, coreRunning = true, userDisconnecting = false))
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.DEGRADED, coreRunning = true, userDisconnecting = false))
    }

    @Test
    fun aTapWhileTheCoreIsStillRunningDisconnects() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.DISCONNECTED, coreRunning = true, userDisconnecting = false))
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.FAILED, coreRunning = true, userDisconnecting = false))
    }

    @Test
    fun aTapWhileADisconnectIsFinishingKeepsDisconnecting() {
        assertEquals(TunnelToggle.Decision.DISCONNECT, TunnelToggle.decide(OrbState.DISCONNECTED, coreRunning = false, userDisconnecting = true))
    }

    @Test
    fun aTapOnAnIdleOrFailedScreenConnects() {
        assertEquals(TunnelToggle.Decision.CONNECT, TunnelToggle.decide(OrbState.DISCONNECTED, coreRunning = false, userDisconnecting = false))
        assertEquals(TunnelToggle.Decision.CONNECT, TunnelToggle.decide(OrbState.FAILED, coreRunning = false, userDisconnecting = false))
    }
}
