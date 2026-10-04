package com.netrik.core.network

/** Snapshot of the device's default network, as Android exposes it. Null fields = unavailable. */
sealed interface CurrentNetwork {

    data object Disconnected : CurrentNetwork

    data class Connected(
        val transport: Transport,
        /** The network has internet validated by Android (NET_CAPABILITY_VALIDATED). */
        val validated: Boolean,
        val ipv4: Ipv4Address?,
        val gateway: String?,
        val dnsServers: List<String>,
        val ipv6: String?,
        val wifi: WifiDetails?,
        /** Carrier name, mobile data only. */
        val carrierName: String?,
        /** System interface (e.g. wlan0). */
        val interfaceName: String? = null,
    ) : CurrentNetwork

    enum class Transport { Wifi, Cellular, Ethernet, Vpn, Other }
}

data class Ipv4Address(val address: String, val prefixLength: Int)

data class WifiDetails(
    /** Null when Android hides the SSID (no location permission). */
    val ssid: String?,
    val rssiDbm: Int?,
    val frequencyMhz: Int?,
    /** Null when hidden by Android (no location permission). */
    val bssid: String? = null,
)
