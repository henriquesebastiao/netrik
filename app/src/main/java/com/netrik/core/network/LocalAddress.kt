package com.netrik.core.network

/**
 * Local network addresses (private and link-local). On Android 17+, talking to them requires the
 * `ACCESS_LOCAL_NETWORK` permission; without it the system drops the traffic and everything times out.
 */
object LocalAddress {

    fun isLocal(address: String): Boolean {
        val v4 = Ipv4.parse(address)
        if (v4 != null) {
            val a = (v4 shr 24) and 0xFF
            val b = (v4 shr 16) and 0xFF
            return a == 10L ||
                (a == 172L && b in 16L..31L) ||
                (a == 192L && b == 168L) ||
                (a == 169L && b == 254L)
        }
        val v6 = address.lowercase().substringBefore('%')
        if (!v6.contains(':')) return false
        val first = v6.substringBefore(':').ifEmpty { "0" }.toIntOrNull(16) ?: return false
        return (first and 0xFE00) == 0xFC00 || // fc00::/7 (ULA)
            (first and 0xFFC0) == 0xFE80 // fe80::/10 (link-local)
    }
}
