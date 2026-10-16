package studio.cluvex.aethery.ui

enum class OrbState { DISCONNECTED, CONNECTING, CONNECTED, DEGRADED, FAILED }

enum class OrbEvent { IGNITE, BLOOM, DRAIN, SHAKE, HAPTIC_CONFIRM, HAPTIC_REJECT, HAPTIC_CLICK }

/** How the orb and the sky should look in a state. */
data class OrbLook(val warmth: Float, val rings: Boolean, val flicker: Boolean)

object OrbModel {
    fun lookFor(state: OrbState): OrbLook = when (state) {
        OrbState.DISCONNECTED -> OrbLook(warmth = 0f, rings = false, flicker = false)
        OrbState.CONNECTING -> OrbLook(warmth = 0.25f, rings = true, flicker = false)
        OrbState.CONNECTED -> OrbLook(warmth = 1f, rings = false, flicker = false)
        OrbState.DEGRADED -> OrbLook(warmth = 0.45f, rings = false, flicker = true)
        OrbState.FAILED -> OrbLook(warmth = 0f, rings = false, flicker = false)
    }

    /** The one-shot moments to play when the state changes from [from] to [to]. */
    fun transition(from: OrbState, to: OrbState): List<OrbEvent> {
        if (from == to) return emptyList()
        return when (to) {
            OrbState.CONNECTING -> listOf(OrbEvent.IGNITE)
            OrbState.CONNECTED -> when (from) {
                OrbState.CONNECTING -> listOf(OrbEvent.BLOOM, OrbEvent.HAPTIC_CONFIRM)
                OrbState.DEGRADED -> emptyList()
                else -> listOf(OrbEvent.BLOOM)
            }
            OrbState.DEGRADED -> emptyList()
            OrbState.FAILED -> listOf(OrbEvent.SHAKE, OrbEvent.HAPTIC_REJECT)
            OrbState.DISCONNECTED -> when (from) {
                OrbState.CONNECTED, OrbState.DEGRADED -> listOf(OrbEvent.DRAIN, OrbEvent.HAPTIC_CLICK)
                OrbState.CONNECTING -> listOf(OrbEvent.HAPTIC_CLICK)
                else -> emptyList()
            }
        }
    }
}
