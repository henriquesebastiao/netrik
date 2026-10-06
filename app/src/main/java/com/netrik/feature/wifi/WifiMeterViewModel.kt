package com.netrik.feature.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.wifi.RttSupport
import com.netrik.core.wifi.SignalHistory
import com.netrik.core.wifi.WifiLinkSource
import com.netrik.core.wifi.WifiRttRanger
import com.netrik.core.wifi.WifiScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WifiMeterUiState(
    /** False until the first reading arrives. */
    val started: Boolean = false,
    /** The last reading found no Wi-Fi connection. */
    val connected: Boolean = true,
    val history: SignalHistory = SignalHistory(),
    /** Null without the location permission (Android hides the name and BSSID). */
    val ssid: String? = null,
    val bssid: String? = null,
    /** The connected AP answers RTT ranging (known from the latest scan). */
    val rttResponder: Boolean = false,
    val rttSupport: RttSupport = RttSupport.Unsupported,
    val rtt: RttUi? = null,
    val sound: Boolean = false,
)

/** Live signal of the connected network, read every second while the screen is visible. */
@HiltViewModel
class WifiMeterViewModel @Inject constructor(
    linkSource: WifiLinkSource,
    networkInfo: NetworkInfoRepository,
    scanner: WifiScanRepository,
    private val ranger: WifiRttRanger,
) : ViewModel() {

    private val local = MutableStateFlow(WifiMeterUiState(rttSupport = ranger.support()))
    private var rangingJob: Job? = null

    private data class Readings(val started: Boolean = false, val connected: Boolean = true, val history: SignalHistory = SignalHistory())

    private val readings = linkSource.samples(SAMPLE_INTERVAL_MS).runningFold(Readings()) { acc, sample ->
        if (sample == null) acc.copy(started = true, connected = false) else Readings(true, true, acc.history.add(sample))
    }

    private val identity = networkInfo.currentNetwork.map { n -> (n as? CurrentNetwork.Connected)?.wifi }

    private val responders = scanner.networks.map { list -> list.filter { it.rttResponder }.map { it.bssid.uppercase() }.toSet() }
        .onStart { emit(emptySet()) }

    val uiState: StateFlow<WifiMeterUiState> = combine(local, readings, identity, responders) { s, r, wifi, rtt ->
        val bssid = wifi?.bssid?.uppercase()
        s.copy(
            started = r.started,
            connected = r.connected,
            history = r.history,
            ssid = wifi?.ssid,
            bssid = bssid,
            rttResponder = bssid != null && bssid in rtt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onToggleSound() = local.update { it.copy(sound = !it.sound) }

    fun onStartRanging() {
        val bssid = uiState.value.bssid ?: return
        rangingJob?.cancel()
        local.update { it.copy(rtt = RttUi(bssid), rttSupport = ranger.support()) }
        rangingJob = viewModelScope.launch {
            ranger.range(bssid).collect { reading -> local.update { s -> s.copy(rtt = s.rtt?.copy(reading = reading)) } }
        }
    }

    fun onStopRanging() {
        rangingJob?.cancel()
        rangingJob = null
        local.update { it.copy(rtt = null) }
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
    }
}
