package com.netrik.core.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.netrik.core.oui.MacAddresses
import com.netrik.core.oui.OuiRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Scan de redes Wi-Fi próximas. Exige localização precisa concedida e ativada no sistema. */
interface WifiScanRepository {
    /** Resultados mais recentes, reemitidos a cada scan concluído (deste ou de outro app). */
    val networks: Flow<List<WifiNetwork>>

    val wifiEnabled: Flow<Boolean>
    val locationEnabled: Flow<Boolean>
    val supports6Ghz: Boolean

    /** Pede um scan ao sistema; false se recusado (limite de scans ou Wi-Fi indisponível). */
    fun requestScan(): Boolean
}

@Singleton
class AndroidWifiScanRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val oui: OuiRepository,
) : WifiScanRepository {

    private val wifi = context.getSystemService(WifiManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission") // a tela só coleta com ACCESS_FINE_LOCATION concedida
    override val networks: Flow<List<WifiNetwork>> =
        broadcasts(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION).map { readResults() }

    override val wifiEnabled: Flow<Boolean> =
        broadcasts(WifiManager.WIFI_STATE_CHANGED_ACTION).map { wifi?.isWifiEnabled == true }.distinctUntilChanged()

    override val locationEnabled: Flow<Boolean> =
        broadcasts(LocationManager.PROVIDERS_CHANGED_ACTION, LocationManager.MODE_CHANGED_ACTION)
            .map { isLocationEnabled() }
            .distinctUntilChanged()

    override val supports6Ghz: Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi?.is6GHzBandSupported == true

    @Suppress("DEPRECATION") // startScan segue funcionando, com o limite de 4 scans a cada 2 min
    override fun requestScan(): Boolean = try {
        wifi?.startScan() == true
    } catch (_: SecurityException) {
        false
    }

    @SuppressLint("MissingPermission")
    private suspend fun readResults(): List<WifiNetwork> {
        val results = try {
            wifi?.scanResults.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        val parsed = results.mapNotNull { it.toNetwork() }
        val vendors = oui.lookupMany(parsed.filterNot { it.bssidLocal }.map { it.bssid.replace(":", "") })
        return parsed.map { n -> n.copy(vendor = vendors[n.bssid.replace(":", "")]?.record?.organization) }
    }

    private fun ScanResult.toNetwork(): WifiNetwork? {
        val bssid = BSSID?.uppercase() ?: return null
        val width = WifiChannelWidth.toMhz(channelWidth)
        val (band, channel) = bandAndChannel(frequency)
        val hex = bssid.replace(":", "")
        return WifiNetwork(
            ssid = ssidText(),
            bssid = bssid,
            rssiDbm = level,
            frequencyMhz = frequency,
            band = band,
            channel = channel,
            widthMhz = width,
            centerMhz = WifiChannelWidth.center(frequency, width, centerFreq0),
            security = WifiSecurity.fromCapabilities(capabilities.orEmpty()),
            bssidLocal = MacAddresses.isLocallyAdministered(hex),
        )
    }

    /** SSID legível; null para rede oculta (vazio ou só zeros). */
    @Suppress("DEPRECATION")
    private fun ScanResult.ssidText(): String? {
        val text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wifiSsid?.toString()?.removeSurrounding("\"")
        } else {
            SSID
        }
        return text?.takeIf { it.isNotBlank() && it.any { c -> c != '\u0000' } && it != "<unknown ssid>" }
    }

    private fun isLocationEnabled(): Boolean = location?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLocationEnabled
        else it.isProviderEnabled(LocationManager.GPS_PROVIDER) || it.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    } == true

    /** Emite logo ao assinar e a cada broadcast das [actions]. */
    private fun broadcasts(vararg actions: String): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply { actions.forEach(::addAction) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
