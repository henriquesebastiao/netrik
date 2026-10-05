package com.netrik.core.neighbor

import java.net.InetAddress

/**
 * MNDP, the MikroTik Neighbor Discovery Protocol: UDP port 5678. RouterOS broadcasts an announcement on each
 * discovery interface about once a minute and answers right away to an empty 4-byte request.
 *
 * Packet: 4-byte header (2 unknown bytes and a sequence number), then TLVs with a big-endian 2-byte type and
 * 2-byte length. Types as decoded by Wireshark's MNDP dissector; the uptime is a little-endian 32-bit count
 * of seconds.
 */
object Mndp {
    const val PORT = 5678

    /** Discovery request: an empty packet (header only). */
    val request: ByteArray = ByteArray(4)

    private const val HEADER = 4
    private const val T_MAC = 1
    private const val T_IDENTITY = 5
    private const val T_VERSION = 7
    private const val T_PLATFORM = 8
    private const val T_UPTIME = 10
    private const val T_SOFTWARE_ID = 11
    private const val T_BOARD = 12
    private const val T_IPV6 = 15
    private const val T_INTERFACE = 16
    private const val T_IPV4 = 17

    /**
     * Reads an announcement. Null for requests (ours or another app's), malformed packets and announcements
     * without a MAC or identity. Unknown TLVs are skipped.
     */
    fun parse(data: ByteArray, length: Int, sourceIp: String, nowMillis: Long): Neighbor? {
        if (length <= HEADER || length > data.size) return null
        var neighbor = Neighbor(NeighborProtocol.Mndp, sourceIp = sourceIp, lastSeenMillis = nowMillis)
        var pos = HEADER
        while (pos + 4 <= length) {
            val type = data.u16(pos)
            val len = data.u16(pos + 2)
            val value = pos + 4
            if (value + len > length) return null
            neighbor = when (type) {
                T_MAC -> if (len == 6 && isPlausibleMac(data, value)) neighbor.copy(mac = formatMac(data, value)) else neighbor
                T_IDENTITY -> neighbor.copy(identity = data.text(value, len))
                T_VERSION -> neighbor.copy(version = data.text(value, len))
                T_PLATFORM -> neighbor.copy(platform = data.text(value, len))
                T_UPTIME -> if (len == 4) neighbor.copy(uptimeSeconds = data.u32le(value)) else neighbor
                T_SOFTWARE_ID -> neighbor.copy(softwareId = data.text(value, len))
                T_BOARD -> neighbor.copy(model = data.text(value, len))
                T_IPV6 -> if (len == 16) neighbor.copy(ipv6 = address(data, value, 16)) else neighbor
                T_INTERFACE -> neighbor.copy(interfaceName = data.text(value, len))
                T_IPV4 -> if (len == 4) neighbor.copy(ipv4 = address(data, value, 4)) else neighbor
                else -> neighbor
            }
            pos = value + len
        }
        return neighbor.takeIf { it.mac != null || it.identity != null }
    }

    private fun address(data: ByteArray, offset: Int, size: Int): String? =
        InetAddress.getByAddress(data.copyOfRange(offset, offset + size)).hostAddress?.takeIf { it != "0.0.0.0" }

    private fun ByteArray.u16(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

    private fun ByteArray.u32le(at: Int): Long =
        (this[at].toLong() and 0xFF) or
            ((this[at + 1].toLong() and 0xFF) shl 8) or
            ((this[at + 2].toLong() and 0xFF) shl 16) or
            ((this[at + 3].toLong() and 0xFF) shl 24)
}
