package studio.cluvex.aethery.ui

/**
 * What a tap on the orb means. Anything in progress or up is ended; only an idle or failed screen
 * connects. A disconnect the core has already finished never blocks a new connect.
 */
object TunnelToggle {
    enum class Decision { CONNECT, DISCONNECT }

    fun decide(shown: OrbState, coreRunning: Boolean): Decision = when {
        coreRunning -> Decision.DISCONNECT
        shown == OrbState.CONNECTING || shown == OrbState.CONNECTED || shown == OrbState.DEGRADED -> Decision.DISCONNECT
        else -> Decision.CONNECT
    }
}
