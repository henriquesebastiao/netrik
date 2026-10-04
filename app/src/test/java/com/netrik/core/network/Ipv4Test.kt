package com.netrik.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4Test {

    @Test
    fun `prefix becomes mask`() {
        assertEquals("255.255.255.0", Ipv4.prefixToMask(24))
        assertEquals("255.255.240.0", Ipv4.prefixToMask(20))
        assertEquals("255.255.255.252", Ipv4.prefixToMask(30))
        assertEquals("0.0.0.0", Ipv4.prefixToMask(0))
        assertEquals("255.255.255.255", Ipv4.prefixToMask(32))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range prefix is rejected`() {
        Ipv4.prefixToMask(33)
    }

    @Test
    fun `mask becomes prefix`() {
        assertEquals(24, Ipv4.maskToPrefix("255.255.255.0"))
        assertEquals(17, Ipv4.maskToPrefix("255.255.128.0"))
        assertEquals(0, Ipv4.maskToPrefix("0.0.0.0"))
        assertEquals(32, Ipv4.maskToPrefix("255.255.255.255"))
    }

    @Test
    fun `non contiguous or invalid mask returns null`() {
        assertNull(Ipv4.maskToPrefix("255.0.255.0"))
        assertNull(Ipv4.maskToPrefix("255.255.255"))
        assertNull(Ipv4.maskToPrefix("255.255.255.256"))
    }

    @Test
    fun `parse accepts only well-formed IPv4`() {
        assertEquals(0xC0A8002AL, Ipv4.parse("192.168.0.42"))
        assertNull(Ipv4.parse("192.168.0"))
        assertNull(Ipv4.parse("192.168.0.a"))
        assertNull(Ipv4.parse("192.168..1"))
        assertNull(Ipv4.parse("1.2.3.4.5"))
        assertNull(Ipv4.parse("1.2.3.1000"))
    }

    @Test
    fun `network address of the subnet`() {
        assertEquals("192.168.0.0", Ipv4.networkAddress("192.168.0.42", 24))
        assertEquals("10.0.0.0", Ipv4.networkAddress("10.0.13.7", 20))
        assertEquals("172.16.5.4", Ipv4.networkAddress("172.16.5.4", 32))
        assertEquals("0.0.0.0", Ipv4.networkAddress("172.16.5.4", 0))
        assertNull(Ipv4.networkAddress("not an ip", 24))
    }
}
