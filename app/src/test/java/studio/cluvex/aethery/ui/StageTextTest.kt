package studio.cluvex.aethery.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StageTextTest {
    @Test
    fun translatesTheRealGatewayHuntLines() {
        assertEquals("Finding a verified gateway", StageText.plain("Finding a verified MASQUE gateway"))
        assertEquals("Scanning over HTTP/3", StageText.plain("HTTP/3 scan running for up to 240s"))
        assertEquals("Checking saved gateways", StageText.plain("Checking 3 cached MASQUE gateway(s)"))
        assertEquals(
            "Saved gateways did not answer, scanning fresh",
            StageText.plain("Cached gateways did not respond; starting a fresh scan"),
        )
    }

    @Test
    fun explainsTheHttp2Fallback() {
        assertEquals(
            "HTTP/3 is blocked here, trying HTTP/2",
            StageText.plain("HTTP/3 found no gateway; retrying HTTP/2 with TLS fragmentation"),
        )
    }

    @Test
    fun announcesTheGatewayWithoutItsAddress() {
        assertEquals("Gateway found", StageText.plain("Selected 162.159.198.221:443 (1.470953645s)"))
    }

    @Test
    fun translatesStartAndProxyLines() {
        assertEquals("Opening local proxies", StageText.plain("Opening local SOCKS5 and HTTP CONNECT proxies"))
        assertEquals("Starting MASQUE", StageText.plain("Native tunnel started (MASQUE)"))
    }

    @Test
    fun explainsTheRegistrationWithCloudflare() {
        assertEquals("Registering with Cloudflare", StageText.plain("Registering with Cloudflare"))
        assertEquals(
            "Cloudflare did not answer, trying again (2 of 4)",
            StageText.plain("Cloudflare did not answer; trying again (2/4)"),
        )
    }

    @Test
    fun staysSilentForLinesItDoesNotKnow() {
        assertNull(StageText.plain("socks5 listening on 127.0.0.1:1819"))
        assertNull(StageText.plain(""))
    }
}
