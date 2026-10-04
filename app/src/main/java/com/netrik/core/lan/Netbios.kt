package com.netrik.core.lan

/**
 * NetBIOS "node status" (NBSTAT, RFC 1002 §4.2.17–18): asks a Windows/Samba host for its registered
 * names and MAC ("unit ID"). Only building and reading the packets; sending is up to the scanner.
 */
object Netbios {
    const val PORT = 137

    data class NodeStatus(
        /** Machine name (type 0x00, not a group); null if there is none. */
        val computerName: String?,
        val workgroup: String?,
        /** Null when the host answers with zeros (Samba in some setups). */
        val mac: String?,
    )

    /** Query packet for the wildcard name "*". */
    fun nodeStatusRequest(transactionId: Int): ByteArray {
        val header = byteArrayOf(
            (transactionId shr 8).toByte(), transactionId.toByte(),
            0x00, 0x00, // flags: consulta
            0x00, 0x01, // 1 pergunta
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        )
        // "*" followed by 15 0x00 bytes, in "first-level encoding" (each nibble + 'A').
        val raw = ByteArray(16).also { it[0] = '*'.code.toByte() }
        val encoded = ByteArray(32)
        raw.forEachIndexed { i, b ->
            val v = b.toInt() and 0xFF
            encoded[2 * i] = ('A'.code + (v shr 4)).toByte()
            encoded[2 * i + 1] = ('A'.code + (v and 0x0F)).toByte()
        }
        val question = byteArrayOf(0x20) + encoded + byteArrayOf(0x00, 0x00, 0x21, 0x00, 0x01) // NBSTAT, IN
        return header + question
    }

    /** Reads the reply; null if it isn't a valid NBSTAT reply. */
    fun parseNodeStatus(packet: ByteArray): NodeStatus? {
        if (packet.size < 57) return null
        var pos = 12
        // Reply name: 32-byte label + terminator
        val labelLength = packet[pos].toInt() and 0xFF
        pos += 1 + labelLength + 1
        if (pos + 10 > packet.size) return null
        val type = u16(packet, pos)
        if (type != 0x21) return null
        pos += 2 + 2 + 4 // tipo, classe, TTL
        val rdLength = u16(packet, pos)
        pos += 2
        if (pos + rdLength > packet.size || rdLength < 1) return null
        val count = packet[pos].toInt() and 0xFF
        pos += 1
        var computer: String? = null
        var group: String? = null
        repeat(count) {
            if (pos + 18 > packet.size) return null
            val name = String(packet, pos, 15, Charsets.ISO_8859_1).trimEnd(' ', '\u0000')
            val suffix = packet[pos + 15].toInt() and 0xFF
            val flags = u16(packet, pos + 16)
            val isGroup = flags and 0x8000 != 0
            if (suffix == 0x00 && !isGroup && computer == null) computer = name
            if (suffix == 0x00 && isGroup && group == null) group = name
            pos += 18
        }
        val mac = if (pos + 6 <= packet.size) {
            val bytes = packet.copyOfRange(pos, pos + 6)
            if (bytes.all { it.toInt() == 0 }) null else bytes.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
        } else {
            null
        }
        return NodeStatus(computer?.takeIf { it.isNotBlank() }, group?.takeIf { it.isNotBlank() }, mac)
    }

    private fun u16(b: ByteArray, i: Int) = ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)
}
