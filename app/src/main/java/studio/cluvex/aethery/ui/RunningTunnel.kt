package studio.cluvex.aethery.ui

/**
 * What the status poll may do when the core reports a tunnel running. The poll must not rewrite a
 * connection that is still being made, or it would wipe the stage text the core just reported.
 */
object RunningTunnel {
    enum class Action { KEEP, SHOW_RECOVERED, SHOW_STILL_RUNNING }

    fun action(shown: OrbState, ready: Boolean): Action = when {
        shown == OrbState.CONNECTED || shown == OrbState.DEGRADED -> Action.KEEP
        ready -> Action.SHOW_RECOVERED
        shown == OrbState.CONNECTING -> Action.KEEP
        else -> Action.SHOW_STILL_RUNNING
    }
}
