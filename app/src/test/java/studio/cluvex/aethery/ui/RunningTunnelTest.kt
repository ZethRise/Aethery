package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RunningTunnelTest {
    @Test
    fun aTunnelThatIsAlreadyShownAsUpIsLeftAlone() {
        assertEquals(RunningTunnel.Action.KEEP, RunningTunnel.action(OrbState.CONNECTED, ready = true))
        assertEquals(RunningTunnel.Action.KEEP, RunningTunnel.action(OrbState.DEGRADED, ready = false))
    }

    @Test
    fun aReadyTunnelThatIsNotShownIsShownAsRecovered() {
        assertEquals(RunningTunnel.Action.SHOW_RECOVERED, RunningTunnel.action(OrbState.DISCONNECTED, ready = true))
        assertEquals(RunningTunnel.Action.SHOW_RECOVERED, RunningTunnel.action(OrbState.CONNECTING, ready = true))
    }

    @Test
    fun aConnectionInProgressKeepsItsStageText() {
        assertEquals(RunningTunnel.Action.KEEP, RunningTunnel.action(OrbState.CONNECTING, ready = false))
    }

    @Test
    fun anIdleScreenLearnsThatATunnelIsStillRunning() {
        assertEquals(RunningTunnel.Action.SHOW_STILL_RUNNING, RunningTunnel.action(OrbState.DISCONNECTED, ready = false))
        assertEquals(RunningTunnel.Action.SHOW_STILL_RUNNING, RunningTunnel.action(OrbState.FAILED, ready = false))
    }
}
