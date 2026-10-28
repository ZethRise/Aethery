package studio.cluvex.aethery

/**
 * The options that shape the handshakes of the tunnel and of the calls that register a key: the
 * MASQUE server name, the TLS fingerprint and the address, name and host of the registration API.
 * Each is saved under the name the core knows it by, so this is only a copy from preferences into
 * the core's configuration, skipping what was left empty.
 */
object HandshakeOptions {
    val TEXT_KEYS = listOf(
        "masque_sni", "tls_ciphers", "tls13_ciphers", "tls_groups", "tls_key_shares", "tls_sigalgs",
        "tls_cert_compression", "tls_alps", "tls_trust_anchors", "tls_server_padding",
        "api_address", "api_sni", "api_host",
    )
    val FLAG_KEYS = listOf(
        "enable_grease_sigalgs", "enable_ech_grease", "disable_ocsp_stapling", "disable_sct",
        "disable_session_ticket", "disable_extension_permutation", "tls_verify",
    )

    fun options(text: (String) -> String, flag: (String) -> Boolean): Map<String, Any> = buildMap {
        for (key in TEXT_KEYS) {
            val value = text(key).trim()
            if (value.isNotEmpty() && value != "none") put(key, value)
        }
        for (key in FLAG_KEYS) if (flag(key)) put(key, true)
    }
}
