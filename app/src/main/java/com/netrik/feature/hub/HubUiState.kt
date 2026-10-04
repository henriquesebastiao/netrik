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
        /** SSID (Wi-Fi) ou operadora (dados móveis); null quando o Android não informa. */
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
        /** Identifica a rede; o IP público consultado só vale enquanto ela não mudar. */
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
 * Converte o retrato da rede no estado do card "Rede atual". Lógica pura.
 * [channelWidths]: largura (MHz) por BSSID vinda do scan Wi-Fi; a conexão em si não informa a largura.
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
        gateway = gateway,
        dns = dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", "),
        ipv6 = ipv6,
        networkKey = listOf(transport.name, wifi?.ssid, ipv4?.address, gateway, ipv6).joinToString("|"),
    )
}
