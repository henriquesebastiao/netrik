package com.netrik.feature.portscan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.database.TargetHistoryRepository
import com.netrik.core.lan.ScanRange
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.LocalAddress
import com.netrik.core.network.LocalNetworkAccess
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.ping.HostResolver
import com.netrik.core.network.ping.TargetValidator
import com.netrik.core.portscan.Cidr
import com.netrik.core.portscan.PortCatalog
import com.netrik.core.portscan.PortList
import com.netrik.core.portscan.PortScanEvent
import com.netrik.core.portscan.PortScanner
import com.netrik.core.portscan.PortState
import com.netrik.core.portscan.Protocol
import com.netrik.core.ui.RunFailure
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.TargetError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

enum class ScanMode { Host, Network }
enum class PortPreset(val count: Int) { Top100(100), Top1000(1000), All(65_535), Custom(0) }

data class HostResult(
    val ip: String,
    /** Ports with a useful answer: open, filtered, open|filtered. */
    val ports: Map<Int, PortState> = emptyMap(),
    val closed: Int = 0,
) {
    val open: Int get() = ports.values.count { it == PortState.Open }
}

data class ScanSpec(
    val mode: ScanMode,
    val target: String,
    val protocol: Protocol,
    val preset: PortPreset,
    val portCount: Int,
    val hostCount: Int,
)

data class PortScanUiState(
    val inResults: Boolean = false,
    val mode: ScanMode = ScanMode.Host,
    val hostText: String = "",
    val cidrText: String = "",
    /** CIDR of the current subnet (Network mode suggestion). */
    val currentCidr: String? = null,
    val protocol: Protocol = Protocol.Tcp,
    val preset: PortPreset = PortPreset.Top100,
    val customText: String = "",
    val timeoutText: String = "1000",
    val targetError: TargetError? = null,
    val recents: List<String> = emptyList(),
    val connected: Boolean = true,
    // Run
    val phase: RunPhase = RunPhase.Idle,
    val failure: RunFailure? = null,
    val spec: ScanSpec? = null,
    val discovering: Boolean = false,
    val probed: Int = 0,
    val discoveryTotal: Int = 0,
    val checksDone: Long = 0,
    val checksTotal: Long = 0,
    val hostsDone: Int = 0,
    val hostsTotal: Int = 0,
    val startedAt: Long = 0,
    val elapsedMillis: Long = 0,
    val hosts: List<HostResult> = emptyList(),
    val serviceNames: Map<Int, String> = emptyMap(),
    /** Reverse DNS of the active hosts, filled in apart from the port count. */
    val hostnames: Map<String, String> = emptyMap(),
    val onlyOpen: Boolean = true,
    val expanded: Set<String> = emptySet(),
) {
    val running: Boolean get() = phase == RunPhase.Running || phase == RunPhase.Resolving
    val portList: PortList get() = PortList.parse(customText)
    val cidr: Cidr.Result get() = Cidr.parse(cidrText)
    val timeoutMs: Int? get() = timeoutText.toIntOrNull()?.takeIf { it in 100..10_000 }

    /** Number of ports in the current configuration (0 if invalid). */
    val portCount: Int get() = when (preset) {
        PortPreset.Custom -> (portList as? PortList.Valid)?.ports?.size ?: 0
        else -> preset.count
    }

    val hostCount: Int get() = when (mode) {
        ScanMode.Host -> 1
        ScanMode.Network -> (cidr as? Cidr.Result.Valid)?.let { ScanRange.of(it.address, it.prefixLength)?.hosts?.size } ?: 0
    }
}

@HiltViewModel
class PortScanViewModel @Inject constructor(
    private val scanner: PortScanner,
    private val catalog: PortCatalog,
    private val resolver: HostResolver,
    private val history: TargetHistoryRepository,
    private val localNetwork: LocalNetworkAccess,
    private val clock: Clock,
    networkInfo: NetworkInfoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val state = MutableStateFlow(PortScanUiState(hostText = savedStateHandle.get<String>("target").orEmpty()))
    private var job: Job? = null

    private val network = networkInfo.currentNetwork.map { n ->
        val c = n as? CurrentNetwork.Connected
        val cidr = c?.ipv4?.let { ScanRange.of(it.address, it.prefixLength) }?.takeIf { !it.truncated }?.cidr
        (c != null) to cidr
    }

    val uiState: StateFlow<PortScanUiState> = combine(state, history.recent(TOOL), network) { s, recents, (online, cidr) ->
        s.copy(recents = recents, connected = online, currentCidr = cidr)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        // Network mode starts with the current subnet filled in.
        viewModelScope.launch {
            val cidr = network.first().second
            if (cidr != null) state.update { if (it.cidrText.isEmpty()) it.copy(cidrText = cidr) else it }
        }
    }

    fun onModeChange(mode: ScanMode) = state.update { it.copy(mode = mode, targetError = null) }
    fun onHostChange(v: String) = state.update { it.copy(hostText = v, targetError = null) }
    fun onCidrChange(v: String) = state.update { it.copy(cidrText = v.filter { c -> c.isDigit() || c == '.' || c == '/' }) }
    fun onProtocolChange(p: Protocol) = state.update { it.copy(protocol = p, timeoutText = if (p == Protocol.Udp) "2000" else "1000") }
    fun onPresetChange(p: PortPreset) = state.update { it.copy(preset = p) }
    fun onCustomChange(v: String) = state.update { it.copy(customText = v.filter { c -> c.isDigit() || c == ',' || c == '-' || c == ' ' }) }
    fun onTimeoutChange(v: String) = state.update { it.copy(timeoutText = v.filter(Char::isDigit).take(5)) }
    fun onToggleOnlyOpen() = state.update { it.copy(onlyOpen = !it.onlyOpen) }
    fun onToggleHost(ip: String) = state.update { it.copy(expanded = if (ip in it.expanded) it.expanded - ip else it.expanded + ip) }

    fun onClearHistory() {
        viewModelScope.launch { history.clear(TOOL) }
    }

    /** "Back" and "New scan" on the results screen go back to the configuration. */
    fun onBackToConfig() {
        job?.cancel()
        state.update { it.copy(inResults = false, phase = if (it.running) RunPhase.Stopped else it.phase) }
    }

    fun onStop() {
        if (!state.value.running) return
        job?.cancel()
        state.update { it.copy(phase = RunPhase.Stopped, elapsedMillis = clock.millis() - it.startedAt, discovering = false) }
    }

    fun onStart() {
        val s = state.value
        if (s.running || !uiState.value.connected) return
        val timeout = s.timeoutMs ?: return
        if (s.portCount == 0) return
        val hostTarget = s.hostText.trim()
        if (s.mode == ScanMode.Host) {
            val error = when {
                hostTarget.isEmpty() -> TargetError.Required
                !TargetValidator.isValid(hostTarget) -> TargetError.Invalid(hostTarget)
                else -> null
            }
            if (error != null) return state.update { it.copy(targetError = error) }
        } else if (s.cidr !is Cidr.Result.Valid) {
            return
        }

        job = viewModelScope.launch {
            val now = clock.millis()
            state.update {
                it.copy(
                    inResults = true, phase = RunPhase.Resolving, failure = null, targetError = null,
                    hosts = emptyList(), hostnames = emptyMap(), expanded = emptySet(), checksDone = 0, checksTotal = 0, hostsDone = 0, hostsTotal = 0,
                    probed = 0, discoveryTotal = 0, discovering = false, startedAt = now, elapsedMillis = 0,
                )
            }
            val ports = when (s.preset) {
                PortPreset.Custom -> (s.portList as PortList.Valid).ports
                PortPreset.All -> (1..65_535).toList()
                else -> catalog.top(s.protocol, s.preset.count)
            }
            val names = catalog.serviceNames(s.protocol)
            val hosts: List<String>
            val label: String
            if (s.mode == ScanMode.Host) {
                val host = resolver.resolve(hostTarget)
                if (host == null || host.ipv6) {
                    state.update { it.copy(phase = RunPhase.Failed, failure = RunFailure.HostNotFound, targetError = TargetError.Unresolved(hostTarget)) }
                    return@launch
                }
                hosts = listOf(host.address)
                label = if (host.address == hostTarget) hostTarget else "$hostTarget · ${host.address}"
                history.record(TOOL, hostTarget)
            } else {
                val cidr = s.cidr as Cidr.Result.Valid
                val range = ScanRange.of(cidr.address, cidr.prefixLength) ?: return@launch
                hosts = range.hosts
                label = range.cidr
            }
            if (hosts.any(LocalAddress::isLocal) && !localNetwork.isGranted()) {
                state.update { it.copy(phase = RunPhase.Failed, failure = RunFailure.LocalNetworkPermission) }
                return@launch
            }
            state.update {
                it.copy(
                    phase = RunPhase.Running, serviceNames = names,
                    spec = ScanSpec(s.mode, label, s.protocol, s.preset, ports.size, hosts.size),
                )
            }
            run(hosts, ports, s.protocol, timeout, discoverFirst = s.mode == ScanMode.Network)
        }
    }

    /** Gathers the events in memory and publishes at most every 250 ms. */
    private suspend fun run(hosts: List<String>, ports: List<Int>, protocol: Protocol, timeoutMs: Int, discoverFirst: Boolean) {
        val results = LinkedHashMap<String, HostResult>()
        var progress: PortScanEvent.Progress? = null
        var discovery: PortScanEvent.Discovery? = null
        var dirty = false
        val lock = Any()

        fun publish() {
            synchronized(lock) {
                if (!dirty) return
                dirty = false
                val snapshot = results.values.toList()
                state.update { s ->
                    s.copy(
                        hosts = snapshot,
                        discovering = progress == null && discovery != null,
                        probed = discovery?.probed ?: s.probed,
                        discoveryTotal = discovery?.total ?: s.discoveryTotal,
                        checksDone = progress?.checksDone ?: 0,
                        checksTotal = progress?.checksTotal ?: 0,
                        hostsDone = progress?.hostsDone ?: 0,
                        hostsTotal = progress?.hostsTotal ?: 0,
                    )
                }
            }
        }

        val publisher = viewModelScope.launch {
            while (true) {
                delay(250)
                publish()
            }
        }
        try {
            scanner.scan(hosts, ports, protocol, timeoutMs, discoverFirst).collect { event ->
                synchronized(lock) {
                    dirty = true
                    when (event) {
                        is PortScanEvent.Discovery -> discovery = event
                        is PortScanEvent.HostUp -> {
                            results.getOrPut(event.ip) { HostResult(event.ip) }
                            lookupName(event.ip)
                        }
                        is PortScanEvent.Port -> {
                            val host = results.getOrPut(event.ip) { HostResult(event.ip) }
                            results[event.ip] = if (event.state == PortState.Closed) {
                                host.copy(closed = host.closed + 1)
                            } else {
                                host.copy(ports = host.ports + (event.port to event.state))
                            }
                        }
                        is PortScanEvent.Progress -> progress = event
                    }
                }
            }
            publish()
            state.update { it.copy(phase = RunPhase.Done, discovering = false, elapsedMillis = clock.millis() - it.startedAt) }
        } finally {
            publisher.cancel()
        }
    }

    private fun lookupName(ip: String) {
        viewModelScope.launch {
            val name = resolver.reverse(ip) ?: return@launch
            state.update { s -> s.copy(hostnames = s.hostnames + (ip to name)) }
        }
    }

    companion object {
        const val TOOL = "portscan"
    }
}
