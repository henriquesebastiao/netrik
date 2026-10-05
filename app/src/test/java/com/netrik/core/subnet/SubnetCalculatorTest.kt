package com.netrik.core.subnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class SubnetCalculatorTest {

    private fun ok(text: String) = SubnetParser.parse(text) as ParsedSubnet.Ok
    private fun info(text: String) = ok(text).let { SubnetCalculator.info(it.address, it.prefix) }

    @Test
    fun `input forms`() {
        assertEquals(24, ok("192.168.1.10/24").prefix)
        assertEquals(24, ok("192.168.1.10/255.255.255.0").prefix)
        assertEquals(26, ok("192.168.1.10 255.255.255.192").prefix)
        assertEquals(24, ok("192.168.1.10 24").prefix)
        assertEquals(64, ok("2001:db8::1/64").prefix)
        ok("10.0.0.1").let {
            assertEquals(32, it.prefix)
            assertFalse(it.prefixGiven)
        }
        assertEquals(128, ok("fe80::1").prefix)
        assertEquals(SubnetInputError.Empty, (SubnetParser.parse("  ") as ParsedSubnet.Error).error)
        assertEquals(SubnetInputError.InvalidAddress, (SubnetParser.parse("300.1.1.1/24") as ParsedSubnet.Error).error)
        assertEquals(SubnetInputError.InvalidPrefix, (SubnetParser.parse("10.0.0.0/33") as ParsedSubnet.Error).error)
        assertEquals(SubnetInputError.InvalidPrefix, (SubnetParser.parse("2001:db8::/129") as ParsedSubnet.Error).error)
        assertEquals(SubnetInputError.InvalidMask, (SubnetParser.parse("10.0.0.0/255.0.255.0") as ParsedSubnet.Error).error)
        assertEquals(SubnetInputError.InvalidPrefix, (SubnetParser.parse("2001:db8::/255.0.0.0") as ParsedSubnet.Error).error)
    }

    @Test
    fun `IPv4 subnet`() {
        val i = info("192.168.1.10/26")
        assertEquals("192.168.1.0/26", i.cidr.toString())
        assertEquals("255.255.255.192", i.netmask!!.format())
        assertEquals("0.0.0.63", i.wildcard!!.format())
        assertEquals("192.168.1.63", i.broadcast!!.format())
        assertEquals("192.168.1.1", i.firstHost.format())
        assertEquals("192.168.1.62", i.lastHost.format())
        assertEquals(BigInteger.valueOf(62), i.usableHosts)
        assertEquals(BigInteger.valueOf(64), i.totalAddresses)
        assertEquals('C', i.ipv4Class)
        assertEquals(AddressType.Private, i.type)
        assertEquals("1.168.192.in-addr.arpa", i.reverseZone)
        assertEquals("11000000.10101000.00000001.00001010", SubnetCalculator.binary(i.address))
        assertEquals("0xC0A8010A", SubnetCalculator.hex(i.address))
    }

    @Test
    fun `IPv4 edge prefixes follow RFC 3021`() {
        info("10.0.0.4/31").let {
            assertNull(it.broadcast)
            assertEquals("10.0.0.4", it.firstHost.format())
            assertEquals("10.0.0.5", it.lastHost.format())
            assertEquals(BigInteger.valueOf(2), it.usableHosts)
        }
        info("10.0.0.7").let {
            assertEquals("10.0.0.7/32", it.cidr.toString())
            assertNull(it.broadcast)
            assertEquals(BigInteger.ONE, it.usableHosts)
            assertEquals("7.0.0.10.in-addr.arpa", it.reverseZone)
        }
        info("8.8.8.8/0").let {
            assertEquals("0.0.0.0/0", it.cidr.toString())
            assertEquals(BigInteger.ONE.shiftLeft(32), it.totalAddresses)
            assertEquals("in-addr.arpa", it.reverseZone)
            assertEquals(AddressType.Public, it.type)
        }
    }

    @Test
    fun `address types`() {
        mapOf(
            "10.1.2.3" to AddressType.Private, "172.31.0.1" to AddressType.Private, "172.32.0.1" to AddressType.Public,
            "100.64.0.1" to AddressType.SharedCgnat, "127.0.0.1" to AddressType.Loopback, "169.254.1.1" to AddressType.LinkLocal,
            "192.0.2.1" to AddressType.Documentation, "198.19.0.1" to AddressType.Benchmarking, "224.0.0.251" to AddressType.Multicast,
            "255.255.255.255" to AddressType.LimitedBroadcast, "240.0.0.1" to AddressType.Reserved, "0.1.2.3" to AddressType.ThisNetwork,
            "::" to AddressType.Unspecified, "::1" to AddressType.Loopback, "::ffff:10.0.0.1" to AddressType.Ipv4Mapped,
            "64:ff9b::808:808" to AddressType.Nat64, "2001:db8::1" to AddressType.Documentation, "2001::1" to AddressType.Teredo,
            "2002:c000:201::1" to AddressType.SixToFour, "2606:4700::1111" to AddressType.GlobalUnicast,
            "fd12:3456::1" to AddressType.UniqueLocal, "fe80::1" to AddressType.LinkLocal, "ff02::1" to AddressType.Multicast,
            "4000::1" to AddressType.Reserved,
        ).forEach { (address, type) -> assertEquals(address, type, SubnetCalculator.typeOf(IpAddress.parse(address)!!)) }
    }

    @Test
    fun `IPv6 subnet`() {
        val i = info("2001:db8:abcd:12::42/48")
        assertEquals("2001:db8:abcd::/48", i.cidr.toString())
        assertEquals("2001:db8:abcd:ffff:ffff:ffff:ffff:ffff", i.lastHost.format())
        assertEquals(BigInteger.ONE.shiftLeft(80), i.totalAddresses)
        assertEquals(BigInteger.valueOf(65_536), i.subnets64)
        assertNull(i.broadcast)
        assertNull(i.netmask)
        assertEquals("d.c.b.a.8.b.d.0.1.0.0.2.ip6.arpa", i.reverseZone)
        assertNull(info("2001:db8::1/64").subnets64)
        assertTrue(i.address in i.cidr)
    }
}
