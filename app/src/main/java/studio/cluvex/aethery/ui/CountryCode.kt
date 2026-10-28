package studio.cluvex.aethery.ui

/** Country codes for the flag beside the IP address. */
object CountryCode {
    // Cloudflare answers T1 for a Tor exit and XX or ZZ for an address it cannot place.
    private val NOT_A_COUNTRY = setOf("T1", "XX", "ZZ")
    private val IN_JSON = Regex("\"country(?:_code)?\"\\s*:\\s*\"([A-Za-z]{2})\"")

    /** The upper-case code in [raw], or null when it is not a real country. */
    fun of(raw: String?): String? {
        val code = raw?.trim()?.uppercase() ?: return null
        if (code.length != 2 || code.any { it !in 'A'..'Z' && it !in '0'..'9' }) return null
        if (code in NOT_A_COUNTRY || code.any { it.isDigit() }) return null
        return code
    }

    /** The country in the answer of a geolocation service: a JSON field, or the bare code as text. */
    fun fromBody(body: String): String? =
        IN_JSON.find(body)?.let { of(it.groupValues[1]) } ?: of(body)
}
