package com.netrik.core.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkInfoRepository {
    /** Rede padrão atual; emite de novo a cada mudança de rede, endereço ou sinal. */
    val currentNetwork: Flow<CurrentNetwork>
}

@Singleton
class AndroidNetworkInfoRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NetworkInfoRepository {

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    override val currentNetwork: Flow<CurrentNetwork> = callbackFlow {
        var network: Network? = null
        var capabilities: NetworkCapabilities? = null
        var linkProperties: LinkProperties? = null

        fun publish() {
            val caps = capabilities
            trySend(
                if (network == null || caps == null) CurrentNetwork.Disconnected
                else snapshot(caps, linkProperties),
            )
        }

        val callback = object : ConnectivityManager.NetworkCallback(callbackFlags()) {
            override fun onAvailable(net: Network) {
                network = net
                capabilities = connectivity.getNetworkCapabilities(net)
                linkProperties = connectivity.getLinkProperties(net)
                publish()
            }

            override fun onCapabilitiesChanged(net: Network, caps: NetworkCapabilities) {
                network = net
                capabilities = caps
                publish()
            }

            override fun onLinkPropertiesChanged(net: Network, props: LinkProperties) {
                network = net
                linkProperties = props
                publish()
            }

            override fun onLost(net: Network) {
                if (net == network) {
                    network = null
                    capabilities = null
                    linkProperties = null
                    publish()
                }
            }
        }

        // Sem rede padrão o callback nunca é chamado; parte de "desconectado".
        if (connectivity.activeNetwork == null) trySend(CurrentNetwork.Disconnected)
        connectivity.registerDefaultNetworkCallback(callback)
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun snapshot(caps: NetworkCapabilities, props: LinkProperties?): CurrentNetwork.Connected {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> CurrentNetwork.Transport.Vpn
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> CurrentNetwork.Transport.Wifi
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CurrentNetwork.Transport.Cellular
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> CurrentNetwork.Transport.Ethernet
            else -> CurrentNetwork.Transport.Other
        }
        val addresses = props?.linkAddresses.orEmpty()
        val ipv4 = addresses.firstOrNull { it.address is Inet4Address }
            ?.let { Ipv4Address(it.address.hostAddress.orEmpty(), it.prefixLength) }
        val ipv6 = addresses.map { it.address }
            .filterIsInstance<Inet6Address>()
            .firstOrNull { !it.isLinkLocalAddress && !it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.plainAddress()
        val gateway = props?.routes.orEmpty()
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.plainAddress()
        val dns = props?.dnsServers.orEmpty()
            .sortedBy { if (it is Inet4Address) 0 else 1 }
            .map { it.plainAddress() }

        return CurrentNetwork.Connected(
            transport = transport,
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            ipv4 = ipv4,
            gateway = gateway,
            dnsServers = dns,
            ipv6 = ipv6,
            wifi = if (transport == CurrentNetwork.Transport.Wifi) wifiDetails(caps) else null,
            carrierName = if (transport == CurrentNetwork.Transport.Cellular) carrierName() else null,
            interfaceName = props?.interfaceName,
        )
    }

    @SuppressLint("MissingPermission") // ACCESS_WIFI_STATE está declarada no manifesto
    private fun wifiDetails(caps: NetworkCapabilities): WifiDetails? {
        val info: WifiInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(WifiManager::class.java)?.connectionInfo
        }
        info ?: return null
        return WifiDetails(
            ssid = info.ssid?.let(::cleanSsid),
            // RSSI inválido é reportado como -127 (WifiInfo.INVALID_RSSI)
            rssiDbm = info.rssi.takeIf { it in -126..0 },
            frequencyMhz = info.frequency.takeIf { it > 0 },
            bssid = info.bssid?.uppercase()?.takeUnless { it == HIDDEN_BSSID },
        )
    }

    private fun carrierName(): String? =
        context.getSystemService(TelephonyManager::class.java)
            ?.networkOperatorName
            ?.takeIf { it.isNotBlank() }

    private fun callbackFlags(): Int =
        // Pede SSID/BSSID; o Android só os entrega se o app tiver permissão de localização.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO else 0
}

/** O Android devolve o SSID entre aspas, ou "<unknown ssid>" quando o oculta. */
internal fun cleanSsid(raw: String): String? {
    val unquoted = raw.removeSurrounding("\"")
    return unquoted.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID || it == "<unknown ssid>" }
}

/** BSSID que o Android devolve quando o app não pode vê-lo. */
private const val HIDDEN_BSSID = "02:00:00:00:00:00"

private fun InetAddress.plainAddress(): String = hostAddress.orEmpty().substringBefore('%')
