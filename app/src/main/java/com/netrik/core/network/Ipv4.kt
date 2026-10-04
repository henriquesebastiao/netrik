package com.netrik.core.network

/** Pure IPv4 utilities: mask ↔ CIDR prefix and network address. */
object Ipv4 {

    /** "192.168.0.42" → unsigned integer in a [Long], or null if invalid. */
    fun parse(address: String): Long? {
        val parts = address.trim().split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            val octet = part.toInt()
            if (octet > 255) return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }

    fun format(value: Long): String =
        (3 downTo 0).joinToString(".") { ((value shr (it * 8)) and 0xFF).toString() }

    /** /24 → "255.255.255.0". */
    fun prefixToMask(prefixLength: Int): String {
        require(prefixLength in 0..32) { "Prefix outside 0..32: $prefixLength" }
        return format(maskBits(prefixLength))
    }

    /** "255.255.255.0" → 24. Null if the mask isn't contiguous or is invalid. */
    fun maskToPrefix(mask: String): Int? {
        val value = parse(mask) ?: return null
        val prefix = java.lang.Long.bitCount(value)
        return if (maskBits(prefix) == value) prefix else null
    }

    /** Network address of the subnet: ("192.168.0.42", 24) → "192.168.0.0". */
    fun networkAddress(address: String, prefixLength: Int): String? {
        require(prefixLength in 0..32) { "Prefix outside 0..32: $prefixLength" }
        val value = parse(address) ?: return null
        return format(value and maskBits(prefixLength))
    }

    private fun maskBits(prefixLength: Int): Long =
        if (prefixLength == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
}
