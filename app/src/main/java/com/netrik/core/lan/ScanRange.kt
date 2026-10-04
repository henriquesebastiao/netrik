package com.netrik.core.lan

import com.netrik.core.network.Ipv4

/**
 * Hosts to sweep on the device subnet.
 * [truncated] = the network is larger than [MAX_PREFIX_SCANNED] and only the device's /24 will be swept.
 */
data class ScanRange(
    val network: String,
    val prefixLength: Int,
    val hosts: List<String>,
    val truncated: Boolean,
) {
    val cidr: String get() = "$network/$prefixLength"

    companion object {
        /** Networks larger than /22 (1,022 hosts) sweep only the device's /24. */
        const val MAX_PREFIX_SCANNED = 22

        fun of(address: String, prefixLength: Int): ScanRange? {
            if (prefixLength !in 0..32) return null
            val ip = Ipv4.parse(address) ?: return null
            val effective = if (prefixLength < MAX_PREFIX_SCANNED) 24 else prefixLength
            val mask = if (effective == 0) 0L else (0xFFFFFFFFL shl (32 - effective)) and 0xFFFFFFFFL
            val network = ip and mask
            val size = 1L shl (32 - effective)
            val hosts = when {
                effective == 32 -> listOf(network)
                effective == 31 -> listOf(network, network + 1) // RFC 3021: no network/broadcast
                else -> (network + 1 until network + size - 1).toList()
            }.map(Ipv4::format)
            return ScanRange(Ipv4.format(network), effective, hosts, truncated = effective != prefixLength)
        }
    }
}
