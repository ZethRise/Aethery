package studio.cluvex.aethery.ui

/** The tunnel's own address (172.16.0.2 and its kind) is not where the traffic appears to come from. */
object IpAddresses {
    fun isInternal(ip: String): Boolean {
        val parts = ip.trim().split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return ip.trim().startsWith("fd", ignoreCase = true) || ip.trim().startsWith("fe80", ignoreCase = true)
        val (a, b) = parts
        return a == 10 ||
            a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }
}
