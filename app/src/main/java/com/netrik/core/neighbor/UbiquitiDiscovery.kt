package com.netrik.core.neighbor

import java.net.InetAddress

/**
 * Ubiquiti discovery protocol: UDP port 10001, enabled by default on many UniFi, EdgeMax and airMAX devices.
 * A 4-byte probe (version, command, 2-byte length 0) gets a unicast reply with TLVs of a 1-byte type and a
 * big-endian 2-byte length. Version 1 replies use command 0; version 2 replies use 6, 9 or 11.
 */
object UbiquitiDiscovery {
    const val PORT = 10001

    val probeV1: ByteArray = byteArrayOf(0x01, 0x00, 0x00, 0x00)
    val probeV2: ByteArray = byteArrayOf(0x02, 0x08, 0x00, 0x00)

    private const val HEADER = 4
    private const val T_MAC = 0x01
    private const val T_MAC_IP = 0x02
    private const val T_FIRMWARE = 0x03
    private const val T_UPTIME = 0x0A
    private const val T_HOSTNAME = 0x0B
    private const val T_PRODUCT = 0x0C
    private const val T_ESSID = 0x0D
    private const val T_MODEL_V1 = 0x14
    private const val T_MODEL_V2 = 0x15
    private const val T_VERSION = 0x16

    private val versionInFirmware = Regex("""\.(v\d+\.\d+\.\d+)""")

    /** Reads a reply. Null for probes, other protocols on the same port and malformed packets. */
    fun parse(data: ByteArray, length: Int, sourceIp: String, nowMillis: Long): Neighbor? {
        if (length <= HEADER || length > data.size) return null
        val version = data[0].toInt()
        val command = data[1].toInt()
        val validHeader = (version == 1 && command == 0) || (version == 2 && command in setOf(6, 9, 11))
        if (!validHeader) return null
        if (data.u16(2) != length - HEADER) return null

        var neighbor = Neighbor(NeighborProtocol.Ubiquiti, sourceIp = sourceIp, lastSeenMillis = nowMillis)
        var product: String? = null
        var pos = HEADER
        while (pos + 3 <= length) {
            val type = data[pos].toInt() and 0xFF
            val len = data.u16(pos + 1)
            val value = pos + 3
            if (value + len > length) return null
            when (type) {
                T_MAC -> if (len >= 6 && neighbor.mac == null && isPlausibleMac(data, value)) neighbor = neighbor.copy(mac = formatMac(data, value))
                T_MAC_IP -> if (len == 10) {
                    // One entry per interface address: keeps the one on the address that answered, else the first.
                    val ip = InetAddress.getByAddress(data.copyOfRange(value + 6, value + 10)).hostAddress
                    if (neighbor.ipv4 == null || ip == sourceIp) {
                        neighbor = neighbor.copy(ipv4 = ip, mac = if (isPlausibleMac(data, value)) formatMac(data, value) else neighbor.mac)
                    }
                }
                T_FIRMWARE -> neighbor = neighbor.copy(firmware = data.text(value, len))
                T_UPTIME -> if (len == 4) neighbor = neighbor.copy(uptimeSeconds = data.u32(value))
                T_HOSTNAME -> neighbor = neighbor.copy(identity = data.text(value, len))
                T_PRODUCT -> product = data.text(value, len)
                T_ESSID -> neighbor = neighbor.copy(essid = data.text(value, len))
                T_MODEL_V1, T_MODEL_V2 -> neighbor = neighbor.copy(model = data.text(value, len))
                T_VERSION -> neighbor = neighbor.copy(version = data.text(value, len))
            }
            pos = value + len
        }
        val firmwareVersion = neighbor.firmware?.let { versionInFirmware.find(it)?.groupValues?.get(1) }
        neighbor = neighbor.copy(model = neighbor.model ?: product, version = neighbor.version ?: firmwareVersion)
        return neighbor.takeIf { it.mac != null || it.identity != null || it.model != null }
    }

    private fun ByteArray.u16(at: Int): Int = ((this[at].toInt() and 0xFF) shl 8) or (this[at + 1].toInt() and 0xFF)

    private fun ByteArray.u32(at: Int): Long =
        ((this[at].toLong() and 0xFF) shl 24) or
            ((this[at + 1].toLong() and 0xFF) shl 16) or
            ((this[at + 2].toLong() and 0xFF) shl 8) or
            (this[at + 3].toLong() and 0xFF)
}
