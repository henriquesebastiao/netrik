package com.netrik.feature.wifi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.WifiBand
import com.netrik.core.wifi.ScanThrottle
import com.netrik.core.wifi.WifiNetwork
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

/** Estado da permissão de localização, avaliado pela tela (precisa da Activity). */
enum class LocationPermission {
    /** Nunca pedida nesta sessão, ou negada com possibilidade de pedir de novo. */
    NotGranted,
    /** Só localização aproximada: o Android não entrega a lista de redes. */
    CoarseOnly,
    /** Negada sem poder pedir de novo: só pelas configurações. */
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
    /** Segundos até a próxima atualização automática. */
    val secondsToRefresh: Int = 0,
    /** O limite de scans do Android foi atingido. */
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
) : ViewModel() {

    private val throttle = ScanThrottle()
    private val local = MutableStateFlow(WifiUiState(supports6Ghz = scanner.supports6Ghz))
    private var nextAutoScanAt = 0L

    private val _messages = Channel<WifiMessage>(Channel.BUFFERED)
    val messages: Flow<WifiMessage> = _messages.receiveAsFlow()

    private val connectedBssid = networkInfo.currentNetwork.map { n ->
        (n as? CurrentNetwork.Connected)?.wifi?.bssid
    }

    /** Só escuta resultados com a permissão concedida; sem ela o Android nega o acesso. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val results: Flow<List<WifiNetwork>?> = local.map { it.permission == LocationPermission.Granted }
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) scanner.networks else flowOf(null) }

    /** Relógio de 1 s que só roda enquanto a tela coleta o estado; dispara os scans automáticos. */
    private val ticker = flow {
        while (true) {
            emit(clock.millis())
            delay(1_000)
        }
    }.onEach { now -> autoScan(now) }

    val uiState: StateFlow<WifiUiState> = combine(
        local,
        results.onStart { emit(null) },
        connectedBssid,
        combine(scanner.wifiEnabled, scanner.locationEnabled) { w, l -> w to l },
        ticker,
    ) { s, list, bssid, (wifiOn, locationOn), now ->
        s.copy(
            wifiEnabled = wifiOn,
            locationEnabled = locationOn,
            networks = list.orEmpty().map { it.copy(connected = bssid != null && it.bssid.equals(bssid, ignoreCase = true)) },
            secondsToRefresh = ((nextAutoScanAt - now).coerceAtLeast(0) / 1000).toInt(),
            throttled = !throttle.canScan(now),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)

    fun onPermissionResult(permission: LocationPermission) = local.update { it.copy(permission = permission) }

    fun onPermissionLater() {
        _messages.trySend(WifiMessage.PermissionLater)
    }

    fun onViewChange(view: WifiView) = local.update { it.copy(view = view, selectedBssid = null) }

    /** Lista: as bandas são filtros independentes. Espectro: uma banda por vez. */
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

    /** Atualização manual: respeita o limite e avisa quanto falta. */
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

    /** O estado local não sabe se Wi-Fi e localização estão ligados; usa o último estado publicado. */
    private fun WifiUiState.ready(): Boolean =
        permission == LocationPermission.Granted && uiState.value.wifiEnabled && uiState.value.locationEnabled

    companion object {
        /** 4 scans a cada 2 min permitem um a cada 30 s sem bater no limite. */
        const val AUTO_REFRESH_MILLIS = 30_000L
    }
}
