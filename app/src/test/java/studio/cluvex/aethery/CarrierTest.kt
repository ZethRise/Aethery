package studio.cluvex.aethery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierTest {
    @Test fun directAsksForNothing() {
        assertNull(Carrier.DIRECT.tor)
        assertNull(Carrier.DIRECT.psiphon)
    }

    @Test fun eachNameMapsToItsCoreMode() {
        assertEquals("reverse", Carrier.TOR.tor)
        assertEquals("chain", Carrier.TOR_CHAIN.tor)
        assertEquals("only", Carrier.TOR_ALONE.tor)
        assertEquals("reverse", Carrier.PSIPHON.psiphon)
        assertEquals("chain", Carrier.PSIPHON_CHAIN.psiphon)
        assertEquals("only", Carrier.PSIPHON_ALONE.psiphon)
        assertNull(Carrier.TOR.psiphon)
        assertNull(Carrier.PSIPHON.tor)
    }

    @Test fun onlyTheAloneModesReplaceTheTunnel() {
        assertTrue(Carrier.TOR_ALONE.alone)
        assertTrue(Carrier.PSIPHON_ALONE.alone)
        assertFalse(Carrier.TOR.alone)
        assertFalse(Carrier.PSIPHON_CHAIN.alone)
        assertFalse(Carrier.DIRECT.alone)
    }

    @Test fun savedNamesRoundTripAndUnknownOnesAreDirect() {
        Carrier.entries.forEach { assertEquals(it, Carrier.fromName(it.storedName)) }
        assertEquals(Carrier.DIRECT, Carrier.fromName(null))
        assertEquals(Carrier.DIRECT, Carrier.fromName("nonsense"))
    }

    @Test fun onlyTheReversedCarriersRefuseUdpMethods() {
        assertTrue(Carrier.TOR.carriesTunnel)
        assertTrue(Carrier.PSIPHON.carriesTunnel)
        assertFalse(Carrier.TOR_CHAIN.carriesTunnel)
        assertFalse(Carrier.TOR_ALONE.carriesTunnel)
    }
}
