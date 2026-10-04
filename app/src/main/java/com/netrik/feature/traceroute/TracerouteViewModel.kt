package com.netrik.feature.traceroute

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.database.TargetHistoryRepository
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.LocalAddress
import com.netrik.core.network.LocalNetworkAccess
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.ping.HostResolver
import com.netrik.core.network.ping.OptionField
import com.netrik.core.network.ping.PingOptionsValidator
import com.netrik.core.network.ping.TargetValidator
import com.netrik.core.network.ping.TraceEvent
import com.netrik.core.network.ping.Traceroute
import com.netrik.core.ui.RunFailure
import com.netrik.core.ui.RunPhase
import com.netrik.core.ui.TargetError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HopUi(
    val number: Int,
    /** Null = sem resposta no tempo (`* * *`). */
    val address: String?,
    val hostname: String? = null,
    val rttMs: Double? = null,
    val isDestination: Boolean = false,
)

data class TracerouteUiState(
    val target: String = "",
    val targetError: TargetError? = null,
    val recents: List<String> = emptyList(),
    val advancedOpen: Boolean = true,
    val maxHopsText: String = "30",
    val hopTimeoutText: String = "3",
    val optionErrors: Set<OptionField> = emptySet(),
    val phase: RunPhase = RunPhase.Idle,
    val failure: RunFailure? = null,
    val runTarget: String? = null,
    val runAddress: String? = null,
    val runMaxHops: Int = 30,
    val runHopTimeout: Int = 3,
    val hops: List<HopUi> = emptyList(),
    /** Salto sendo sondado agora. */
    val probingHop: Int? = null,
    val reachedDestination: Boolean = false,
    val connected: Boolean = true,
) {
    val running: Boolean get() = phase == RunPhase.Running || phase == RunPhase.Resolving
}

@HiltViewModel
class TracerouteViewModel @Inject constructor(
    private val traceroute: Traceroute,
    private val resolver: HostResolver,
    private val history: TargetHistoryRepository,
    private val localNetwork: LocalNetworkAccess,
    networkInfo: NetworkInfoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val state = MutableStateFlow(TracerouteUiState(target = savedStateHandle.get<String>("target").orEmpty()))
    private var runJob: Job? = null

    private val connected = networkInfo.currentNetwork.map { it is CurrentNetwork.Connected }.distinctUntilChanged()

    val uiState: StateFlow<TracerouteUiState> = combine(state, history.recent(TOOL), connected) { s, recents, online ->
        s.copy(recents = recents, connected = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        viewModelScope.launch {
            connected.collect { online ->
                if (!online && state.value.running) {
                    runJob?.cancel()
                    state.update {
                        it.copy(phase = RunPhase.Failed, failure = RunFailure.ConnectionLost(step = it.probingHop ?: it.hops.size), probingHop = null)
                    }
                }
            }
        }
        if (state.value.target.isNotBlank()) state.update { it.copy(advancedOpen = false) }
    }

    fun onTargetChange(value: String) = state.update { it.copy(target = value, targetError = null) }
    fun onToggleAdvanced() = state.update { it.copy(advancedOpen = !it.advancedOpen) }
    fun onMaxHopsChange(v: String) = state.update { it.copy(maxHopsText = v.filter(Char::isDigit).take(3), optionErrors = it.optionErrors - OptionField.MaxHops) }
    fun onHopTimeoutChange(v: String) = state.update { it.copy(hopTimeoutText = v.filter(Char::isDigit).take(3), optionErrors = it.optionErrors - OptionField.HopTimeout) }

    fun onClearHistory() {
        viewModelScope.launch { history.clear(TOOL) }
    }

    fun onStart() {
        val s = state.value
        if (s.running || !uiState.value.connected) return
        val target = s.target.trim()
        val targetError = when {
            target.isEmpty() -> TargetError.Required
            !TargetValidator.isValid(target) -> TargetError.Invalid(target)
            else -> null
        }
        val options = PingOptionsValidator.traceroute(s.maxHopsText, s.hopTimeoutText)
        if (options is PingOptionsValidator.Result.Invalid) {
            state.update { it.copy(targetError = targetError, optionErrors = options.errors, advancedOpen = true) }
            return
        }
        if (targetError != null) {
            state.update { it.copy(targetError = targetError) }
            return
        }
        val opts = (options as PingOptionsValidator.Result.Ok).value

        runJob = viewModelScope.launch {
            state.update {
                it.copy(
                    phase = RunPhase.Resolving, failure = null, targetError = null, optionErrors = emptySet(),
                    runTarget = target, runAddress = null, runMaxHops = opts.maxHops, runHopTimeout = opts.hopTimeoutSeconds,
                    hops = emptyList(), probingHop = null, reachedDestination = false, advancedOpen = false,
                )
            }
            val host = resolver.resolve(target)
            if (host == null) {
                state.update { it.copy(phase = RunPhase.Failed, failure = RunFailure.HostNotFound, targetError = TargetError.Unresolved(target)) }
                return@launch
            }
            if (LocalAddress.isLocal(host.address) && !localNetwork.isGranted()) {
                state.update { it.copy(phase = RunPhase.Failed, failure = RunFailure.LocalNetworkPermission) }
                return@launch
            }
            history.record(TOOL, target)
            state.update { it.copy(phase = RunPhase.Running, runAddress = host.address, probingHop = 1) }

            traceroute.run(host, opts).collect { event ->
                state.update { s -> s.apply(event) }
            }
        }
    }

    fun onStop() {
        if (!state.value.running) return
        runJob?.cancel()
        state.update { it.copy(phase = RunPhase.Stopped, probingHop = null) }
    }

    private fun TracerouteUiState.apply(event: TraceEvent): TracerouteUiState = when (event) {
        is TraceEvent.Probing -> copy(probingHop = event.hop)
        is TraceEvent.Hop -> copy(
            hops = hops.filter { it.number != event.hop } +
                HopUi(event.hop, event.address, rttMs = event.rttMs, isDestination = event.reachedDestination),
        )
        is TraceEvent.HopRtt -> copy(hops = hops.map { if (it.number == event.hop) it.copy(rttMs = event.rttMs) else it })
        is TraceEvent.HopName -> copy(hops = hops.map { if (it.number == event.hop) it.copy(hostname = event.hostname) else it })
        is TraceEvent.Finished -> if (phase == RunPhase.Running) {
            copy(phase = RunPhase.Done, probingHop = null, reachedDestination = event.reachedDestination)
        } else {
            this
        }
        is TraceEvent.Failed -> copy(
            phase = RunPhase.Failed,
            probingHop = null,
            failure = if (uiState.value.connected) RunFailure.ToolFailed(event.message) else RunFailure.ConnectionLost(step = event.hop),
        )
    }

    companion object {
        const val TOOL = "traceroute"
    }
}
