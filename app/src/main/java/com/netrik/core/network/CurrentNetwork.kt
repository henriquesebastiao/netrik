package com.netrik.core.network

/** Retrato da rede padrão do aparelho, como o Android a expõe. Campos nulos = indisponível. */
sealed interface CurrentNetwork {

    data object Disconnected : CurrentNetwork

    data class Connected(
        val transport: Transport,
        /** A rede tem internet validada pelo Android (NET_CAPABILITY_VALIDATED). */
        val validated: Boolean,
        val ipv4: Ipv4Address?,
        val gateway: String?,
        val dnsServers: List<String>,
        val ipv6: String?,
        val wifi: WifiDetails?,
        /** Nome da operadora, só para dados móveis. */
        val carrierName: String?,
    ) : CurrentNetwork

    enum class Transport { Wifi, Cellular, Ethernet, Vpn, Other }
}

data class Ipv4Address(val address: String, val prefixLength: Int)

data class WifiDetails(
    /** Null quando o Android oculta o SSID (sem permissão de localização). */
    val ssid: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    /** Null quando oculto pelo Android (sem permissão de localização). */
    val bssid: String? = null,
)
