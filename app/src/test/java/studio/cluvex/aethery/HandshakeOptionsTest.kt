package studio.cluvex.aethery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandshakeOptionsTest {
    private fun options(texts: Map<String, String> = emptyMap(), flags: Set<String> = emptySet()) =
        HandshakeOptions.options({ texts[it].orEmpty() }, { it in flags })

    @Test fun nothingSetSendsNothing() {
        assertEquals(0, options().size)
    }

    @Test fun textOptionsAreSentTrimmedAndBlankOnesAreLeftOut() {
        val json = options(mapOf("masque_sni" to " www.cloudflare.com ", "tls_groups" to "", "api_host" to "  "))
        assertEquals("www.cloudflare.com", json["masque_sni"])
        assertFalse(json.containsKey("tls_groups"))
        assertFalse(json.containsKey("api_host"))
    }

    @Test fun nonePickedForACompressionOrAlpsChoiceSendsNothing() {
        val json = options(mapOf("tls_cert_compression" to "none", "tls_alps" to "none"))
        assertFalse(json.containsKey("tls_cert_compression"))
        assertFalse(json.containsKey("tls_alps"))
        assertEquals("brotli", options(mapOf("tls_cert_compression" to "brotli"))["tls_cert_compression"])
    }

    @Test fun switchesAreSentOnlyWhenOn() {
        val json = options(flags = setOf("enable_ech_grease", "disable_sct", "tls_verify"))
        assertEquals(true, json["enable_ech_grease"])
        assertEquals(true, json["disable_sct"])
        assertEquals(true, json["tls_verify"])
        assertFalse(json.containsKey("enable_grease_sigalgs"))
    }

    @Test fun everyKeyIsOneTheCoreKnows() {
        val known = setOf(
            "masque_sni", "tls_ciphers", "tls13_ciphers", "tls_groups", "tls_key_shares", "tls_sigalgs",
            "tls_cert_compression", "tls_alps", "tls_trust_anchors", "tls_server_padding",
            "api_address", "api_sni", "api_host", "enable_grease_sigalgs", "enable_ech_grease",
            "disable_ocsp_stapling", "disable_sct", "disable_session_ticket", "disable_extension_permutation",
            "tls_verify",
        )
        assertEquals(known, (HandshakeOptions.TEXT_KEYS + HandshakeOptions.FLAG_KEYS).toSet())
    }
}
