package studio.cluvex.aethery.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpAddressesTest {
    @Test fun theTunnelAddressIsInternal() = assertTrue(IpAddresses.isInternal("172.16.0.2"))
    @Test fun privateRangesAreInternal() {
        listOf("10.1.2.3", "192.168.1.5", "172.31.255.1", "100.64.0.1", "169.254.1.1", "127.0.0.1", "fd00::1")
            .forEach { assertTrue(it, IpAddresses.isInternal(it)) }
    }
    @Test fun publicAddressesAreNot() {
        listOf("104.28.1.9", "172.15.0.1", "172.32.0.1", "8.8.8.8", "2606:4700::1")
            .forEach { assertFalse(it, IpAddresses.isInternal(it)) }
    }
}
