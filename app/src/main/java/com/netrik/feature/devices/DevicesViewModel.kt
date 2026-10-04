package com.netrik.feature.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.lan.DeviceSort
import com.netrik.core.lan.Detection
import com.netrik.core.lan.DeviceUpdate
import com.netrik.core.lan.LanDevice
import com.netrik.core.lan.LanScanner
import com.netrik.core.lan.ScanEvent
import com.netrik.core.lan.ScanRange
import com.netrik.core.lan.matches
import com.netrik.core.lan.merge
import com.netrik.core.lan.sortedFor
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.ui.RunPhase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Rede local disponível para varredura (Wi-Fi ou cabo com IPv4). */
data class LocalNetwork(
    val range: ScanRange,
    val selfIp: String,
    val gateway: String?,
    val interfaceName: String?,
    val ssid: String?,
)

/** Permissão de rede local (Android 17+, `ACCESS_LOCAL_NETWORK`); avaliada pela tela. */
enum class LocalNetworkPermission { Unknown, NotGranted, PermanentlyDenied, Granted }

data class DevicesUiState(
    val permission: LocalNetworkPermission = LocalNetworkPermission.Unknown,
    /** Null = sem rede local (dados móveis, VPN ou desconectado). */
    val network: LocalNetwork? = null,
    val networkChecked: Boolean = false,
    val phase: RunPhase = RunPhase.Idle,
    val scanned: Int = 0,
    val total: Int = 0,
    val startedAt: Long = 0,
    val elapsedMillis: Long = 0,
    /** A varredura dos IPs terminou e as fontes de nome estão completando os dados. */
    val sweepDone: Boolean = false,
    val devices: Map<String, LanDevice> = emptyMap(),
    val sort: DeviceSort = DeviceSort.Ip,
    val query: String = "",
    val searchOpen: Boolean = false,
    /** CIDR da última varredura (para avisar se a rede mudou). */
    val scannedCidr: String? = null,
) {
    val running: Boolean get() = phase == RunPhase.Running
    val visible: List<LanDevice> get() = devices.values.filter { it.matches(query) }.sortedFor(sort)
}

/** "Buscar na rede" da Consulta OUI: busca a aplicar quando a aba Dispositivos abrir. */
@Singleton
class PendingDeviceSearch @Inject constructor() {
    val query = MutableStateFlow<String?>(null)
}

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val scanner: LanScanner,
    networkInfo: NetworkInfoRepository,
    private val pendingSearch: PendingDeviceSearch,
    private val clock: Clock,
) : ViewModel() {

    private val state = MutableStateFlow(DevicesUiState())
    private var scanJob: Job? = null
    private var autoStarted = false

    private val localNetwork = networkInfo.currentNetwork

    val uiState: StateFlow<DevicesUiState> = combine(state, localNetwork) { s, network ->
        s.copy(network = network.toLocal(), networkChecked = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        viewModelScope.launch {
            pendingSearch.query.filterNotNull().collect { query ->
                pendingSearch.query.value = null
                state.update { it.copy(searchOpen = true, query = query) }
            }
        }
        // Rede caiu ou trocou no meio: interrompe (os IPs varridos seriam de outra rede).
        viewModelScope.launch {
            localNetwork.collect { n ->
                val cidr = n.toLocal()?.range?.cidr
                if (state.value.running && cidr != state.value.scannedCidr) {
                    scanJob?.cancel()
                    state.update { it.copy(phase = RunPhase.Stopped) }
                }
            }
        }
    }

    fun onScanClick() {
        if (state.value.running) {
            scanJob?.cancel()
            state.update { it.copy(phase = RunPhase.Stopped, elapsedMillis = clock.millis() - it.startedAt) }
        } else if (state.value.permission == LocalNetworkPermission.Granted) {
            uiState.value.network?.let(::startScan)
        }
    }

    /** Primeira vez com a permissão concedida e rede local disponível: já começa a varrer. */
    fun onPermissionResult(permission: LocalNetworkPermission) {
        state.update { it.copy(permission = permission) }
        if (permission == LocalNetworkPermission.Granted && !autoStarted) {
            autoStarted = true
            viewModelScope.launch { localNetwork.first().toLocal()?.let(::startScan) }
        }
    }

    fun onSortChange(sort: DeviceSort) = state.update { it.copy(sort = sort) }
    fun onSearchOpen() = state.update { it.copy(searchOpen = true) }
    fun onSearchClose() = state.update { it.copy(searchOpen = false, query = "") }
    fun onQueryChange(q: String) = state.update { it.copy(query = q) }

    private fun startScan(network: LocalNetwork) {
        scanJob?.cancel()
        val now = clock.millis()
        val self = LanDevice(ip = network.selfIp, detection = Detection.Icmp, isSelf = true, isGateway = network.selfIp == network.gateway)
        state.update {
            it.copy(
                phase = RunPhase.Running, scanned = 0, total = network.range.hosts.size, startedAt = now, elapsedMillis = 0,
                sweepDone = false, devices = mapOf(self.ip to self), scannedCidr = network.range.cidr,
            )
        }
        scanJob = viewModelScope.launch {
            scanner.scan(network.range).collect { event ->
                when (event) {
                    is ScanEvent.Progress -> state.update { it.copy(scanned = event.scanned, total = event.total) }
                    is ScanEvent.Found -> state.update { it.copy(devices = it.devices.apply(event.update, network)) }
                    ScanEvent.SweepDone -> state.update { it.copy(sweepDone = true, elapsedMillis = clock.millis() - it.startedAt) }
                }
            }
            state.update { it.copy(phase = RunPhase.Done) }
        }
    }

    private fun Map<String, LanDevice>.apply(update: DeviceUpdate, network: LocalNetwork): Map<String, LanDevice> {
        val merged = this[update.ip].merge(update).copy(
            isGateway = update.ip == network.gateway,
            isSelf = update.ip == network.selfIp,
        )
        return this + (update.ip to merged)
    }
}

/** Só Wi-Fi ou cabo com IPv4: em dados móveis a "rede local" é a da operadora. */
private fun CurrentNetwork.toLocal(): LocalNetwork? {
    val connected = this as? CurrentNetwork.Connected ?: return null
    if (connected.transport != CurrentNetwork.Transport.Wifi && connected.transport != CurrentNetwork.Transport.Ethernet) return null
    val ipv4 = connected.ipv4 ?: return null
    val range = ScanRange.of(ipv4.address, ipv4.prefixLength) ?: return null
    return LocalNetwork(range, ipv4.address, connected.gateway, connected.interfaceName, connected.wifi?.ssid)
}
