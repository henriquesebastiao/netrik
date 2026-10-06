package com.netrik.core.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.net.wifi.rtt.WifiRttManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

/** Whether this phone can measure distance to access points (802.11mc/az round-trip time). */
enum class RttSupport {
    /** No Wi-Fi RTT in the hardware or Android older than 9. */
    Unsupported,
    /** Supported, but off right now (Wi-Fi or location off). */
    Unavailable,
    Available,
}

sealed interface RttReading {
    /** [distanceMm] may come out slightly negative very close to the AP (calibration); the UI shows it as ~0 m. */
    data class Measured(val distanceMm: Int, val stdDevMm: Int, val successful: Int, val attempted: Int) : RttReading
    /** The AP answered but no measurement succeeded this round. */
    data object Failed : RttReading
    /** The AP stopped answering as an 802.11mc responder. */
    data object NotResponder : RttReading
    /** The AP isn't in the latest scan anymore. */
    data object NotInScan : RttReading
    data object MissingPermission : RttReading
}

interface WifiRttRanger {
    fun support(): RttSupport

    /** Measures the distance to [bssid] about once a second until the collection is cancelled. */
    fun range(bssid: String): Flow<RttReading>
}

/**
 * Wi-Fi RTT (IEEE 802.11mc FTM, and 802.11az on Android 15+) with [WifiRttManager]. Works only with APs that
 * answer as responders and on phones with RTT; needs fine location and, on Android 13+, Nearby devices.
 */
class AndroidWifiRttRanger @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : WifiRttRanger {

    private val wifi = context.getSystemService(WifiManager::class.java)

    private val rtt: WifiRttManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            context.getSystemService(WifiRttManager::class.java)
        } else {
            null
        }

    override fun support(): RttSupport = when {
        rtt == null -> RttSupport.Unsupported
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rtt.isAvailable -> RttSupport.Available
        else -> RttSupport.Unavailable
    }

    override fun range(bssid: String): Flow<RttReading> = flow {
        while (true) {
            emit(measure(bssid))
            delay(INTERVAL_MS)
        }
    }

    private fun hasPermissions(): Boolean {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        return granted(Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.NEARBY_WIFI_DEVICES))
    }

    @SuppressLint("MissingPermission") // checked in hasPermissions()
    private suspend fun measure(bssid: String): RttReading {
        val manager = rtt ?: return RttReading.Failed
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return RttReading.Failed
        if (!hasPermissions()) return RttReading.MissingPermission
        val scan = try {
            wifi?.scanResults.orEmpty().firstOrNull { it.BSSID.equals(bssid, ignoreCase = true) }
        } catch (_: SecurityException) {
            return RttReading.MissingPermission
        } ?: return RttReading.NotInScan
        val request = RangingRequest.Builder().addAccessPoint(scan).build()
        val result = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<RangingResult?> { continuation ->
                try {
                    manager.startRanging(
                        request,
                        context.mainExecutor,
                        object : RangingResultCallback() {
                            override fun onRangingFailure(code: Int) {
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onRangingResults(results: List<RangingResult>) {
                                if (continuation.isActive) continuation.resume(results.firstOrNull())
                            }
                        },
                    )
                } catch (_: SecurityException) {
                    if (continuation.isActive) continuation.resume(null)
                } catch (_: IllegalStateException) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
        } ?: return RttReading.Failed
        return when (result.status) {
            RangingResult.STATUS_SUCCESS -> RttReading.Measured(
                distanceMm = result.distanceMm,
                stdDevMm = result.distanceStdDevMm,
                successful = result.numSuccessfulMeasurements,
                attempted = result.numAttemptedMeasurements,
            )
            RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> RttReading.NotResponder
            else -> RttReading.Failed
        }
    }

    companion object {
        /** ScanResult.is80211mcResponder (API 23), plus 802.11az (Android 15) where available. */
        fun isResponder(result: ScanResult): Boolean =
            result.is80211mcResponder || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && result.is80211azNtbResponder)

        private const val INTERVAL_MS = 1_000L
        private const val TIMEOUT_MS = 5_000L
    }
}
