package com.netrik.core.subnet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigInteger

class IpAddressTest {

    private fun v6(text: String) = IpAddress.parse(text)!!

    @Test
    fun `IPv4 parse and format`() {
        assertEquals(3_232_235_786L, IpAddress.parseV4("192.168.1.10"))
        assertEquals("192.168.1.10", IpAddress.parse(" 192.168.1.10 ")!!.format())
        listOf("256.1.1.1", "1.2.3", "1.2.3.4.5", "a.b.c.d", "1..2.3", "", "1.2.3.-4").forEach { assertNull(it, IpAddress.parse(it)) }
    }

    @Test
    fun `IPv6 parse accepts every notation`() {
        val expected = BigInteger("20010db8000000000000000000000001", 16)
        listOf(
            "2001:db8::1", "2001:DB8:0:0:0:0:0:1", "2001:0db8:0000:0000:0000:0000:0000:0001",
            "[2001:db8::1]", "2001:db8::1%wlan0",
        ).forEach { assertEquals(it, expected, IpAddress.parseV6(it)) }
        assertEquals(BigInteger.ZERO, IpAddress.parseV6("::"))
        assertEquals(BigInteger.ONE, IpAddress.parseV6("::1"))
        assertEquals(BigInteger("ffffc0000201", 16), IpAddress.parseV6("::ffff:192.0.2.1"))
        assertEquals(BigInteger("10000000000000000000000000000", 16), IpAddress.parseV6("1::"))
    }

    @Test
    fun `IPv6 parse rejects malformed input`() {
        listOf(
            ":::", "1::2::3", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", "12345::", "g::1", ":1::", "1:2:3:4:5:6:7:8::",
            "::1.2.3", "::256.1.1.1", "1:",
        ).forEach { assertNull(it, IpAddress.parseV6(it)) }
    }

    @Test
    fun `IPv6 compressed form follows RFC 5952`() {
        assertEquals("2001:db8::1", v6("2001:0DB8:0000:0000:0000:0000:0000:0001").format())
        // A single zero group is not compressed.
        assertEquals("2001:db8:0:1:1:1:1:1", v6("2001:db8:0:1:1:1:1:1").format())
        // The longest run wins; on a tie, the first.
        assertEquals("2001:0:0:1::1", v6("2001:0:0:1:0:0:0:1").format())
        assertEquals("2001:db8::1:0:0:1", v6("2001:db8:0:0:1:0:0:1").format())
        assertEquals("::", v6("0:0:0:0:0:0:0:0").format())
        assertEquals("::1", v6("0:0:0:0:0:0:0:1").format())
        assertEquals("fe80::", v6("fe80:0:0:0:0:0:0:0").format())
        assertEquals("::ffff:192.0.2.1", v6("0:0:0:0:0:ffff:c000:201").format())
        assertEquals("2001:0db8:0000:0000:0000:0000:0000:0001", v6("2001:db8::1").expanded())
    }
}
