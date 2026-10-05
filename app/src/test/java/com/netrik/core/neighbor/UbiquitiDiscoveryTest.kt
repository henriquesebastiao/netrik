package com.netrik.core.neighbor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class UbiquitiDiscoveryTest {

    /** Reply: version, command, big-endian 2-byte length, TLVs with a 1-byte type and 2-byte length. */
    private fun reply(version: Int, command: Int, vararg tlvs: Pair<Int, ByteArray>, lengthDelta: Int = 0): ByteArray {
        val body = ByteArrayOutputStream().apply {
            tlvs.forEach { (type, value) ->
                write(byteArrayOf(type.toByte(), (value.size shr 8).toByte(), value.size.toByte()))
                write(value)
            }
        }.toByteArray()
        val length = body.size + lengthDelta
        return byteArrayOf(version.toByte(), command.toByte(), (length shr 8).toByte(), length.toByte()) + body
    }

    private fun parse(bytes: ByteArray, source: String = "192.168.1.20") = UbiquitiDiscovery.parse(bytes, bytes.size, source, nowMillis = 7)

    private val mac1 = byteArrayOf(0x80.toByte(), 0x2A, 0xA8.toByte(), 0xAE.toByte(), 0xF1.toByte(), 0x63)
    private val mac2 = byteArrayOf(0x80.toByte(), 0x2A, 0xA8.toByte(), 0xAE.toByte(), 0xF1.toByte(), 0x5E)

    @Test
    fun `version 1 reply`() {
        val n = parse(
            reply(
                1, 0,
                0x02 to mac2 + byteArrayOf(55, 55, 55, 10),
                0x02 to mac1 + byteArrayOf(192.toByte(), 168.toByte(), 1, 20),
                0x01 to mac1,
                0x03 to "EdgeRouter.ER-e50.v1.10.7.5127989.181001.1227".toByteArray(),
                0x0A to byteArrayOf(0, 1, 0xB9.toByte(), 0x18),
                0x0B to "ubnt-router".toByteArray(),
                0x0C to "ER-X".toByteArray(),
                0x0D to "Office".toByteArray(),
            ),
        )!!
        assertEquals(NeighborProtocol.Ubiquiti, n.protocol)
        // The address that answered wins over the other interfaces.
        assertEquals("192.168.1.20", n.ipv4)
        assertEquals("80:2A:A8:AE:F1:63", n.mac)
        assertEquals("ubnt-router", n.identity)
        // No model TLV: the short product name is used.
        assertEquals("ER-X", n.model)
        assertEquals("v1.10.7", n.version)
        assertEquals(0x0001B918L, n.uptimeSeconds)
        assertEquals("Office", n.essid)
    }

    @Test
    fun `version 2 reply with explicit model and version`() {
        val n = parse(
            reply(
                2, 6,
                0x02 to mac1 + byteArrayOf(192.toByte(), 168.toByte(), 0, 30),
                0x03 to "UCK.mtk7623.v0.12.0.29a26c9.181001.1444".toByteArray(),
                0x15 to "UCK-v2".toByteArray(),
                0x16 to "5.9.29".toByteArray(),
            ),
            source = "192.168.0.30",
        )!!
        assertEquals("UCK-v2", n.model)
        assertEquals("5.9.29", n.version)
        assertEquals("192.168.0.30", n.address)
    }

    @Test
    fun `probes, other protocols and bad lengths are ignored`() {
        assertNull(parse(UbiquitiDiscovery.probeV1))
        assertNull(parse(UbiquitiDiscovery.probeV2))
        assertNull(parse(reply(1, 5, 0x0B to "x".toByteArray())))
        assertNull(parse(reply(3, 0, 0x0B to "x".toByteArray())))
        assertNull(parse(reply(1, 0, 0x0B to "x".toByteArray(), lengthDelta = 1)))
        // TLV longer than the packet.
        assertNull(parse(byteArrayOf(1, 0, 0, 4, 0x0B, 0, 9, 'a'.code.toByte())))
    }
}
