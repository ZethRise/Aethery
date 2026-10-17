package studio.cluvex.aethery

/**
 * How the tunnel reaches Cloudflare, or what replaces it: straight out, through Tor or Psiphon
 * (the tunnel is dialled over them), with Tor or Psiphon inside the tunnel as a second proxy, or
 * Tor or Psiphon alone with no tunnel at all. The core knows these as separate Tor and Psiphon
 * modes; this is the one choice the app shows.
 */
enum class Carrier(
    val storedName: String,
    val label: String,
    val description: String,
    val tor: String? = null,
    val psiphon: String? = null,
) {
    DIRECT("direct", "Direct", "Connect straight to Cloudflare"),
    TOR("tor", "Through Tor", "Dial the tunnel over Tor, so your network never sees WARP. MASQUE methods only.", tor = "reverse"),
    TOR_CHAIN("tor-chain", "Tor inside the tunnel", "A second proxy on 127.0.0.1:1822 comes out of Tor, behind WARP.", tor = "chain"),
    TOR_ALONE("tor-alone", "Tor alone", "No WARP. A local proxy that leaves through Tor. Proxy mode.", tor = "only"),
    PSIPHON("psiphon", "Through Psiphon", "Dial the tunnel over Psiphon. MASQUE methods only.", psiphon = "reverse"),
    PSIPHON_CHAIN("psiphon-chain", "Psiphon inside the tunnel", "A second proxy on 127.0.0.1:1821 comes out of Psiphon, behind WARP.", psiphon = "chain"),
    PSIPHON_ALONE("psiphon-alone", "Psiphon alone", "No WARP. A local proxy that leaves through Psiphon. Proxy mode.", psiphon = "only"),
    ;

    /** Tor or Psiphon replaces the tunnel, so there is nothing for the Android VPN to carry. */
    val proxyOnly: Boolean get() = tor == "only" || psiphon == "only"

    /** The tunnel itself is dialled over it, which only carries TCP. */
    val carriesTunnel: Boolean get() = tor == "reverse" || psiphon == "reverse"

    companion object {
        fun fromName(name: String?): Carrier = entries.firstOrNull { it.storedName == name } ?: DIRECT
    }
}
