package com.netrik.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ipv4Test {

    @Test
    fun `prefixo vira máscara`() {
        assertEquals("255.255.255.0", Ipv4.prefixToMask(24))
        assertEquals("255.255.240.0", Ipv4.prefixToMask(20))
        assertEquals("255.255.255.252", Ipv4.prefixToMask(30))
        assertEquals("0.0.0.0", Ipv4.prefixToMask(0))
        assertEquals("255.255.255.255", Ipv4.prefixToMask(32))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `prefixo fora do intervalo é rejeitado`() {
        Ipv4.prefixToMask(33)
    }

    @Test
    fun `máscara vira prefixo`() {
        assertEquals(24, Ipv4.maskToPrefix("255.255.255.0"))
        assertEquals(17, Ipv4.maskToPrefix("255.255.128.0"))
        assertEquals(0, Ipv4.maskToPrefix("0.0.0.0"))
        assertEquals(32, Ipv4.maskToPrefix("255.255.255.255"))
    }

    @Test
    fun `máscara não contígua ou inválida devolve null`() {
        assertNull(Ipv4.maskToPrefix("255.0.255.0"))
        assertNull(Ipv4.maskToPrefix("255.255.255"))
        assertNull(Ipv4.maskToPrefix("255.255.255.256"))
    }

    @Test
    fun `parse aceita só IPv4 bem formado`() {
        assertEquals(0xC0A8002AL, Ipv4.parse("192.168.0.42"))
        assertNull(Ipv4.parse("192.168.0"))
        assertNull(Ipv4.parse("192.168.0.a"))
        assertNull(Ipv4.parse("192.168..1"))
        assertNull(Ipv4.parse("1.2.3.4.5"))
        assertNull(Ipv4.parse("1.2.3.1000"))
    }

    @Test
    fun `endereço de rede da sub-rede`() {
        assertEquals("192.168.0.0", Ipv4.networkAddress("192.168.0.42", 24))
        assertEquals("10.0.0.0", Ipv4.networkAddress("10.0.13.7", 20))
        assertEquals("172.16.5.4", Ipv4.networkAddress("172.16.5.4", 32))
        assertEquals("0.0.0.0", Ipv4.networkAddress("172.16.5.4", 0))
        assertNull(Ipv4.networkAddress("não é ip", 24))
    }
}
