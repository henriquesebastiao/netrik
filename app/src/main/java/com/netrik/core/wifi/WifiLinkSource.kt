package com.netrik.core.wifi

import android.content.Context
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.netrik.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.time.Clock
import javax.inject.Inject

/** Live readings of the connected Wi-Fi link; null while not connected to Wi-Fi. */
fun interface WifiLinkSource {
    fun samples(intervalMillis: Long): Flow<WifiLinkSample?>
}

/**
 * Reads `WifiManager.getConnectionInfo()` on an interval. The signal (RSSI) isn't location data, so it needs no
 * location permission. Android itself refreshes the RSSI every few seconds while the screen is on, so consecutive
 * readings may repeat.
 */
class AndroidWifiLinkSource @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
    private val clock: Clock,
) : WifiLinkSource {

    private val wifi = context.getSystemService(WifiManager::class.java)

    override fun samples(intervalMillis: Long): Flow<WifiLinkSample?> = flow {
        while (true) {
            emit(read())
            delay(intervalMillis)
        }
    }.flowOn(io)

    @Suppress("DEPRECATION") // getConnectionInfo still reports RSSI and link speed; the replacement needs a network callback
    private fun read(): WifiLinkSample? {
        val info: WifiInfo = wifi?.connectionInfo ?: return null
        if (info.networkId == -1 && info.rssi <= INVALID_RSSI) return null
        if (info.rssi <= INVALID_RSSI || info.rssi >= 0) return null
        val tx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.txLinkSpeedMbps else info.linkSpeed
        val rx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) info.rxLinkSpeedMbps else null
        return WifiLinkSample(
            timeMillis = clock.millis(),
            rssiDbm = info.rssi,
            txMbps = tx.takeIf { it > 0 },
            rxMbps = rx?.takeIf { it > 0 },
            frequencyMhz = info.frequency.takeIf { it > 0 },
            standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) WifiStandard.fromAndroid(info.wifiStandard) else null,
        )
    }

    private companion object {
        /** WifiInfo.INVALID_RSSI is hidden; Android uses -127. */
        const val INVALID_RSSI = -127
    }
}
