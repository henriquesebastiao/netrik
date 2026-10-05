package com.netrik.feature.hub

import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.Ipv4
import com.netrik.core.network.WifiBand
import com.netrik.core.network.WifiChannels

data class HubUiState(
    val network: NetworkCardState = NetworkCardState.Loading,
    val publicIp: PublicIpUi = PublicIpUi.Hidden,
)

sealed interface NetworkCardState {
    data object Loading : NetworkCardState
    data object Disconnected : NetworkCardState

    data class Connected(
        val transport: CurrentNetwork.Transport,
        /** SSID (Wi-Fi) or carrier (mobile data); null when Android doesn't report it. */
        val name: String?,
        val hasInternet: Boolean,
        val signal: WifiSignal?,
        val localIp: String?,
        /** "255.255.255.0 /24" */
        val maskCidr: String?,
        val gateway: String?,
        /** "1.1.1.1, 8.8.8.8" */
        val dns: String?,
        val ipv6: String?,
        /** Shown only in the details sheet. */
        val bssid: String? = null,
        val frequencyMhz: Int? = null,
        val interfaceName: String? = null,
        /** "192.168.1.10/24", to open the subnet calculator. */
        val localCidr: String? = null,
        /** Identifies the network; a queried public IP is only valid while it doesn't change. */
        val networkKey: String,
    ) : NetworkCardState {
        val ssidHidden: Boolean get() = transport == CurrentNetwork.Transport.Wifi && name == null
    }
}

data class WifiSignal(val rssiDbm: Int?, val band: WifiBand?, val channel: Int?, val widthMhz: Int? = null)

sealed interface PublicIpUi {
    data object Hidden : PublicIpUi
    data object Loading : PublicIpUi
    data class Loaded(val ip: String) : PublicIpUi
    data object Failed : PublicIpUi
}

/**
 * Turns the network snapshot into the state of the "Current network" card. Pure logic.
 * [channelWidths]: width (MHz) per BSSID from the Wi-Fi scan; the connection itself doesn't report the width.
 */
fun CurrentNetwork.toCardState(channelWidths: Map<String, Int> = emptyMap()): NetworkCardState = when (this) {
    CurrentNetwork.Disconnected -> NetworkCardState.Disconnected
    is CurrentNetwork.Connected -> NetworkCardState.Connected(
        transport = transport,
        name = when (transport) {
            CurrentNetwork.Transport.Wifi -> wifi?.ssid
            CurrentNetwork.Transport.Cellular -> carrierName
            else -> null
        },
        hasInternet = validated,
        signal = wifi?.let { w ->
            WifiSignal(
                rssiDbm = w.rssiDbm,
                band = w.frequencyMhz?.let(WifiChannels::bandOf),
                channel = w.frequencyMhz?.let(WifiChannels::frequencyToChannel),
                widthMhz = w.bssid?.let { channelWidths[it] },
            ).takeIf { it.rssiDbm != null || it.band != null }
        },
        localIp = ipv4?.address,
        maskCidr = ipv4?.prefixLength?.takeIf { it in 0..32 }?.let { "${Ipv4.prefixToMask(it)} /$it" },
        localCidr = ipv4?.takeIf { it.prefixLength in 0..32 }?.let { "${it.address}/${it.prefixLength}" },
        gateway = gateway,
        dns = dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ipv6 = ipv6,
        bssid = wifi?.bssid,
        frequencyMhz = wifi?.frequencyMhz,
        interfaceName = interfaceName,
        networkKey = listOf(transport.name, wifi?.ssid, ipv4?.address, gateway, ipv6).joinToString("|"),
    )
}
