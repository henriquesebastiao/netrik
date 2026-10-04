package com.netrik.core.lan

import com.netrik.core.network.Ipv4

/**
 * Hosts a varrer na sub-rede do aparelho.
 * [truncated] = a rede é maior que [MAX_PREFIX_SCANNED] e só o /24 do aparelho será varrido.
 */
data class ScanRange(
    val network: String,
    val prefixLength: Int,
    val hosts: List<String>,
    val truncated: Boolean,
) {
    val cidr: String get() = "$network/$prefixLength"

    companion object {
        /** Redes maiores que /22 (1.022 hosts) varrem só o /24 do aparelho. */
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
                effective == 31 -> listOf(network, network + 1) // RFC 3021: sem rede/broadcast
                else -> (network + 1 until network + size - 1).toList()
            }.map(Ipv4::format)
            return ScanRange(Ipv4.format(network), effective, hosts, truncated = effective != prefixLength)
        }
    }
}
