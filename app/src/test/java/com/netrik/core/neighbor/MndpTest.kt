package com.netrik.core.neighbor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class MndpTest {

    /** MNDP packet: 4-byte header and TLVs (big-endian 2-byte type and length). */
    private fun packet(vararg tlvs: Pair<Int, ByteArray>): ByteArray = ByteArrayOutputStream().apply {
        write(byteArrayOf(0, 0, 0, 7))
        tlvs.forEach { (type, value) ->
            write(byteArrayOf((type shr 8).toByte(), type.toByte(), (value.size shr 8).toByte(), value.size.toByte()))
            write(value)
        }
    }.toByteArray()

    private fun parse(bytes: ByteArray, source: String = "192.168.88.1") = Mndp.parse(bytes, bytes.size, source, nowMillis = 42)

    private val mac = byteArrayOf(0x48, 0xA9.toByte(), 0x8A.toByte(), 0x12, 0x34, 0x56)

    @Test
    fun `full announcement`() {
        val n = parse(
            packet(
                1 to mac,
                5 to "Core-Router".toByteArray(),
                7 to "7.14.3 (stable) Apr/17/2024 08:12:22".toByteArray(),
                8 to "MikroTik".toByteArray(),
                // 90061 s = 1 d 01:01:01, little-endian.
                10 to byteArrayOf(0xCD.toByte(), 0x5F, 0x01, 0x00),
                11 to "AB12-CD34".toByteArray(),
                12 to "hAP ax^2".toByteArray(),
                14 to byteArrayOf(1),
                15 to ByteArray(16).also { it[0] = 0xFE.toByte(); it[1] = 0x80.toByte(); it[15] = 1 },
                16 to "bridge".toByteArray(),
                17 to byteArrayOf(192.toByte(), 168.toByte(), 88, 1),
            ),
        )!!
        assertEquals(NeighborProtocol.Mndp, n.protocol)
        assertEquals("48:A9:8A:12:34:56", n.mac)
        assertEquals("Core-Router", n.identity)
        assertEquals("7.14.3 (stable) Apr/17/2024 08:12:22", n.version)
        assertEquals("MikroTik", n.platform)
        assertEquals(90_061L, n.uptimeSeconds)
        assertEquals("AB12-CD34", n.softwareId)
        assertEquals("hAP ax^2", n.model)
        assertEquals("fe80:0:0:0:0:0:0:1", n.ipv6)
        assertEquals("bridge", n.interfaceName)
        assertEquals("192.168.88.1", n.ipv4)
        assertEquals(42L, n.lastSeenMillis)
    }

    @Test
    fun `fields in any order, unknown types skipped, missing IPv4 falls back to the sender`() {
        val n = parse(packet(99 to byteArrayOf(1, 2, 3), 5 to "R1".toByteArray(), 1 to mac), source = "10.0.0.9")!!
        assertEquals("R1", n.identity)
        assertEquals("48:A9:8A:12:34:56", n.mac)
        assertNull(n.ipv4)
        assertEquals("10.0.0.9", n.address)
    }

    @Test
    fun `requests and malformed packets are ignored`() {
        assertNull(parse(Mndp.request))
        // TLV says 20 bytes, only 3 follow.
        assertNull(parse(byteArrayOf(0, 0, 0, 1, 0, 5, 0, 20, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte())))
        // Nothing that identifies the device.
        assertNull(parse(packet(8 to "MikroTik".toByteArray())))
        // All-zero MAC is not a MAC.
        assertNull(parse(packet(1 to ByteArray(6))))
    }

    @Test
    fun `text is cleaned of control characters`() {
        val n = parse(packet(5 to "Bad\u0007Name\n".toByteArray()))!!
        assertEquals("BadName", n.identity)
    }
}
