package com.netrik.core.subnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class SubnetSplitAggregateTest {

    private fun cidr(text: String) = (SubnetParser.parse(text) as ParsedSubnet.Ok).cidr

    private fun ok(result: SplitResult) = result as SplitResult.Ok

    @Test
    fun `equal split by prefix, count and hosts`() {
        ok(SubnetSplitter.split(cidr("192.168.0.0/24"), SplitBy.Prefix(26))).let {
            assertEquals(BigInteger.valueOf(4), it.count)
            assertEquals(listOf("192.168.0.0/26", "192.168.0.64/26", "192.168.0.128/26", "192.168.0.192/26"), it.subnets.map(Cidr::toString))
            assertEquals(BigInteger.valueOf(62), it.usablePerSubnet)
        }
        // 5 subnets need 3 bits: 8 × /27.
        assertEquals(27, ok(SubnetSplitter.split(cidr("10.0.0.0/24"), SplitBy.Count(5))).prefix)
        // 50 hosts + network + broadcast → 64 addresses: /26.
        assertEquals(26, ok(SubnetSplitter.split(cidr("10.0.0.0/24"), SplitBy.Hosts(50))).prefix)
        assertEquals(30, SubnetSplitter.prefixForHosts(IpVersion.V4, 2))
        assertEquals(32, SubnetSplitter.prefixForHosts(IpVersion.V4, 1))
        assertEquals(66, SubnetSplitter.prefixForHosts(IpVersion.V6, 1L shl 62))
        assertEquals(65, SubnetSplitter.prefixForHosts(IpVersion.V6, (1L shl 62) + 1))
    }

    @Test
    fun `huge splits list only the first blocks`() {
        val it = ok(SubnetSplitter.split(cidr("2001:db8::/32"), SplitBy.Prefix(64)))
        assertEquals(BigInteger.ONE.shiftLeft(32), it.count)
        assertEquals(SubnetSplitter.MAX_LISTED, it.subnets.size)
        assertEquals("2001:db8:0:ff::/64", it.subnets.last().toString())
    }

    @Test
    fun `split errors`() {
        assertEquals(SplitError.PrefixTooShort, (SubnetSplitter.split(cidr("10.0.0.0/24"), SplitBy.Prefix(16)) as SplitResult.Error).error)
        assertEquals(SplitError.PrefixTooLong, (SubnetSplitter.split(cidr("10.0.0.0/24"), SplitBy.Prefix(33)) as SplitResult.Error).error)
        assertEquals(SplitError.DoesNotFit, (SubnetSplitter.split(cidr("10.0.0.0/30"), SplitBy.Count(8)) as SplitResult.Error).error)
        assertEquals(SplitError.DoesNotFit, (SubnetSplitter.split(cidr("10.0.0.0/24"), SplitBy.Hosts(300)) as SplitResult.Error).error)
        assertEquals(SplitError.NotPositive, (SubnetSplitter.split(cidr("10.0.0.0/24"), SplitBy.Count(0)) as SplitResult.Error).error)
    }

    @Test
    fun `VLSM packs the largest first`() {
        val (requests, invalid) = SubnetSplitter.parseRequests("LAN A: 100\nLAN B = 50\n20, Link 2")
        assertNull(invalid)
        assertEquals(listOf("LAN A", "LAN B", null, "Link"), requests.map { it.label })
        val result = SubnetSplitter.vlsm(cidr("192.168.10.0/24"), requests)
        assertTrue(result.allFit)
        assertEquals(
            listOf("192.168.10.0/25", "192.168.10.128/26", "192.168.10.192/27", "192.168.10.224/30"),
            result.allocations.map { it.cidr.toString() },
        )
        assertEquals(BigInteger.valueOf(128 + 64 + 32 + 4), result.used)
        assertEquals(BigInteger.valueOf(256 - 228), result.free)
    }

    @Test
    fun `VLSM reports what doesn't fit and keeps going`() {
        val (requests, _) = SubnetSplitter.parseRequests("200\n100\n20")
        val result = SubnetSplitter.vlsm(cidr("10.0.0.0/24"), requests)
        assertFalse(result.allFit)
        // 200 → /24 takes it all; 100 no longer fits; nor does 20.
        assertEquals(listOf("10.0.0.0/24", "null", "null"), result.allocations.map { it.cidr.toString() })
        assertEquals(2, SubnetSplitter.parseRequests("10\nabc\n5").second)
        assertEquals(1, SubnetSplitter.parseRequests("0").second)
    }

    @Test
    fun `aggregation merges neighbors and overlaps`() {
        val result = SubnetAggregator.aggregate(
            """
            # branch offices
            10.0.0.0/25
            10.0.0.128/25
            10.0.1.0/24
            10.0.1.10
            10.0.3.0/24
            """.trimIndent(),
        )
        val v4 = result.v4!!
        assertEquals(listOf("10.0.0.0/23", "10.0.3.0/24"), v4.cidrs.map(Cidr::toString))
        assertEquals("10.0.0.0/22", v4.supernet.toString())
        assertEquals(BigInteger.valueOf(768), v4.covered)
        assertEquals(BigInteger.valueOf(256), v4.extra)
        assertNull(result.v6)
        assertTrue(result.invalidLines.isEmpty())
    }

    @Test
    fun `ranges, normalization, IPv6 and invalid lines`() {
        val result = SubnetAggregator.aggregate("10.0.1.5-10.0.1.20\n192.168.1.77/24\n2001:db8::/49\n2001:db8:0:8000::/49\nnope\n10.0.0.9-10.0.0.1")
        assertEquals(
            listOf("10.0.1.5/32", "10.0.1.6/31", "10.0.1.8/29", "10.0.1.16/30", "10.0.1.20/32", "192.168.1.0/24"),
            result.v4!!.cidrs.map(Cidr::toString),
        )
        assertEquals(listOf(2), result.normalized.map { it.line })
        assertEquals("192.168.1.0/24", result.normalized.single().cidr.toString())
        assertEquals(listOf("2001:db8::/48"), result.v6!!.cidrs.map(Cidr::toString))
        assertEquals(BigInteger.ZERO, result.v6!!.extra)
        assertEquals(listOf(5, 6), result.invalidLines)
    }

    @Test
    fun `range to CIDR covers the whole space`() {
        val all = SubnetAggregator.rangeToCidrs(IpVersion.V4, BigInteger.ZERO, Cidr.allOnes(IpVersion.V4))
        assertEquals(listOf("0.0.0.0/0"), all.map(Cidr::toString))
        assertEquals("::/0", SubnetAggregator.supernet(IpVersion.V6, BigInteger.ZERO, Cidr.allOnes(IpVersion.V6)).toString())
    }
}
