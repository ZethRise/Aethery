package studio.cluvex.aethery.ui

/** Turns the core's log lines into the short, plain stage text shown while connecting. */
object StageText {
    private val started = Regex("^Native tunnel started \\((.+)\\)$")

    fun plain(coreLine: String): String? {
        val line = coreLine.trim()
        started.matchEntire(line)?.let { return "Starting ${it.groupValues[1]}" }
        return when {
            line.startsWith("Finding a verified") -> "Finding a verified gateway"
            line.startsWith("HTTP/3 scan running") -> "Scanning over HTTP/3"
            line.startsWith("HTTP/3 found no gateway") -> "HTTP/3 is blocked here, trying HTTP/2"
            line.startsWith("Checking") && line.contains("cached") -> "Checking saved gateways"
            line.startsWith("Cached gateways did not respond") -> "Saved gateways did not answer, scanning fresh"
            line.startsWith("Selected ") -> "Gateway found"
            line.startsWith("Opening local SOCKS5") -> "Opening local proxies"
            else -> null
        }
    }
}
