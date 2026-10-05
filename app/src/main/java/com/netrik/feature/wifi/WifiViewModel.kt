package com.netrik.feature.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.settings.NetworkPreferences
import com.netrik.core.network.WifiBand
import com.netrik.core.wifi.ScanThrottle
import com.netrik.core.wifi.WifiNetwork
import com.netrik.core.wifi.withoutHidden
import com.netrik.core.wifi.WifiScanRepository
import com.netrik.core.wifi.WifiSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Clock
import javax.inject.Inject

/** Location permission state, evaluated by the screen (it needs the Activity). */
enum class LocationPermission {
    /** Never asked in this session, or denied with the chance to ask again. */
    NotGranted,
    /** Approximate location only: Android doesn't provide the network list. */
    CoarseOnly,
    /** Denied without being able to ask again: only through settings. */
    PermanentlyDenied,
    Granted,
}

enum class WifiView { List, Spectrum }

data class WifiUiState(
    val permission: LocationPermission = LocationPermission.NotGranted,
    val wifiEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val view: WifiView = WifiView.List,
    val listBands: Set<WifiBand> = setOf(WifiBand.GHz2_4, WifiBand.GHz5, WifiBand.GHz6),
    val spectrumBand: WifiBand = WifiBand.GHz2_4,
    val sort: WifiSort = WifiSort.Signal,
    val selectedBssid: String? = null,
    val networks: List<WifiNetwork> = emptyList(),
    val supports6Ghz: Boolean = false,
    /** Seconds until the next automatic refresh. */
    val secondsToRefresh: Int = 0,
    /** Android's scan limit was reached. */
    val throttled: Boolean = false,
) {
    val ready: Boolean get() = permission == LocationPermission.Granted && wifiEnabled && locationEnabled
    val bands: List<WifiBand> get() = if (supports6Ghz) WifiBand.entries else listOf(WifiBand.GHz2_4, WifiBand.GHz5)
    val connectedNetwork: WifiNetwork? get() = networks.firstOrNull { it.connected }
}

sealed interface WifiMessage {
    data class Throttled(val seconds: Int) : WifiMessage
    data object Refreshing : WifiMessage
    data object PermissionLater : WifiMessage
}

@HiltViewModel
class WifiViewModel @Inject constructor(
    private val scanner: WifiScanRepository,
    networkInfo: NetworkInfoRepository,
    private val clock: Clock,
    networkPreferences: NetworkPreferences,
) : ViewModel() {

    private val throttle = ScanThrottle()
    private val local = MutableStateFlow(WifiUiState(supports6Ghz = scanner.supports6Ghz))
    private var nextAutoScanAt = 0L

    private val _messages = Channel<WifiMessage>(Channel.BUFFERED)
    val messages: Flow<WifiMessage> = _messages.receiveAsFlow()

    private val connectedBssid = networkInfo.currentNetwork.map { n ->
        (n as? CurrentNetwork.Connected)?.wifi?.bssid
    }

    /** Only listens for results with the permission granted; without it Android denies access. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val results: Flow<List<WifiNetwork>?> = local.map { it.permission == LocationPermission.Granted }
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) scanner.networks else flowOf(null) }

    /** 1 s clock that only runs while the screen collects the state; triggers the automatic scans. */
    private val ticker = flow {
        while (true) {
            emit(clock.millis())
            delay(1_000)
        }
    }.onEach { now -> autoScan(now) }

    /** Scan results, without hidden networks when the user asked for it in Settings (the connected one always stays). */
    private val visibleResults: Flow<List<WifiNetwork>?> = combine(
        results.onStart { emit(null) },
        connectedBssid,
        networkPreferences.hideHiddenWifi.onStart { emit(false) },
    ) { list, bssid, hideHidden ->
        list?.map { it.copy(connected = bssid != null && it.bssid.equals(bssid, ignoreCase = true)) }
            ?.withoutHidden(hideHidden)
    }

    val uiState: StateFlow<WifiUiState> = combine(
        local,
        visibleResults,
        combine(scanner.wifiEnabled, scanner.locationEnabled) { w, l -> w to l },
        ticker,
    ) { s, list, (wifiOn, locationOn), now ->
        s.copy(
            wifiEnabled = wifiOn,
            locationEnabled = locationOn,
            networks = list.orEmpty(),
            secondsToRefresh = ((nextAutoScanAt - now).coerceAtLeast(0) / 1000).toInt(),
            throttled = !throttle.canScan(now),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onPermissionResult(permission: LocationPermission) = local.update { it.copy(permission = permission) }

    fun onPermissionLater() {
        _messages.trySend(WifiMessage.PermissionLater)
    }

    fun onViewChange(view: WifiView) = local.update { it.copy(view = view, selectedBssid = null) }

    /** List: the bands are independent filters. Spectrum: one band at a time. */
    fun onBandClick(band: WifiBand) = local.update { s ->
        if (s.view == WifiView.Spectrum) {
            s.copy(spectrumBand = band, selectedBssid = null)
        } else {
            val bands = if (band in s.listBands) s.listBands - band else s.listBands + band
            s.copy(listBands = bands)
        }
    }

    fun onCycleSort() = local.update {
        it.copy(sort = WifiSort.entries[(it.sort.ordinal + 1) % WifiSort.entries.size])
    }

    fun onSelectNetwork(bssid: String?) = local.update { it.copy(selectedBssid = bssid) }

    /** Manual refresh: honors the limit and says how long is left. */
    fun onRefresh() {
        val now = clock.millis()
        if (!uiState.value.ready) return
        if (!throttle.canScan(now)) {
            _messages.trySend(WifiMessage.Throttled(((throttle.nextAllowedAt(now) - now) / 1000).toInt().coerceAtLeast(1)))
            return
        }
        scan(now)
        _messages.trySend(WifiMessage.Refreshing)
    }

    private fun autoScan(now: Long) {
        if (!local.value.ready()) return
        if (now < nextAutoScanAt) return
        if (throttle.canScan(now)) scan(now) else nextAutoScanAt = throttle.nextAllowedAt(now)
    }

    private fun scan(now: Long) {
        if (scanner.requestScan()) throttle.record(now) else throttle.markRejected(now)
        nextAutoScanAt = maxOf(now + AUTO_REFRESH_MILLIS, throttle.nextAllowedAt(now))
    }

    /** The local state doesn't know whether Wi-Fi and location are on; uses the last published state. */
    private fun WifiUiState.ready(): Boolean =
        permission == LocationPermission.Granted && uiState.value.wifiEnabled && uiState.value.locationEnabled

    companion object {
        /** 4 scans every 2 min allow one every 30 s without hitting the limit. */
        const val AUTO_REFRESH_MILLIS = 30_000L
    }
}
