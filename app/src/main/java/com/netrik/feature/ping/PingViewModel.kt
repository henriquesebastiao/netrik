package com.netrik.feature.ping

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.database.TargetHistoryRepository
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.ping.HostResolver
import com.netrik.core.network.ping.OptionField
import com.netrik.core.network.ping.PingCommand
import com.netrik.core.network.ping.PingEvent
import com.netrik.core.network.ping.PingOptionsValidator
import com.netrik.core.network.ping.PingRunner
import com.netrik.core.network.ping.PingSample
import com.netrik.core.network.ping.PingStats
import com.netrik.core.network.ping.TargetValidator
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

enum class PingMode { Count, Continuous }

data class PingUiState(
    val target: String = "",
    val targetError: TargetError? = null,
    val recents: List<String> = emptyList(),
    val advancedOpen: Boolean = true,
    val mode: PingMode = PingMode.Count,
    val countText: String = "10",
    val intervalText: String = "1",
    val sizeText: String = "56",
    val timeoutText: String = "2",
    val optionErrors: Set<OptionField> = emptySet(),
    val phase: RunPhase = RunPhase.Idle,
    val failure: RunFailure? = null,
    /** Alvo e IP da execução atual (o campo pode ser editado depois). */
    val runTarget: String? = null,
    val runAddress: String? = null,
    val runPayload: Int = 56,
    /** Pacotes planejados; null = contínuo. */
    val plannedCount: Int? = null,
    val samples: List<PingSample> = emptyList(),
    val connected: Boolean = true,
) {
    val running: Boolean get() = phase == RunPhase.Running || phase == RunPhase.Resolving
    /** Estatísticas da sessão inteira, calculadas uma vez por estado. */
    val stats: PingStats by lazy { PingStats.of(samples) }
}

@HiltViewModel
class PingViewModel @Inject constructor(
    private val runner: PingRunner,
    private val resolver: HostResolver,
    private val history: TargetHistoryRepository,
    networkInfo: NetworkInfoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val state = MutableStateFlow(PingUiState(target = savedStateHandle.get<String>("target").orEmpty()))
    private var runJob: Job? = null

    private val connected = networkInfo.currentNetwork.map { it is CurrentNetwork.Connected }.distinctUntilChanged()

    val uiState: StateFlow<PingUiState> = combine(state, history.recent(TOOL), connected) { s, recents, online ->
        s.copy(recents = recents, connected = online)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        // Rede caiu no meio da execução: interrompe e mostra o erro em vez de acumular timeouts.
        viewModelScope.launch {
            connected.collect { online ->
                if (!online && state.value.running) {
                    runJob?.cancel()
                    state.update { it.copy(phase = RunPhase.Failed, failure = RunFailure.ConnectionLost()) }
                }
            }
        }
        if (state.value.target.isNotBlank()) state.update { it.copy(advancedOpen = false) }
    }

    fun onTargetChange(value: String) = state.update { it.copy(target = value, targetError = null) }

    fun onToggleAdvanced() = state.update { it.copy(advancedOpen = !it.advancedOpen) }
    fun onModeChange(mode: PingMode) = state.update { it.copy(mode = mode, optionErrors = it.optionErrors - OptionField.Count) }
    fun onCountChange(v: String) = state.update { it.copy(countText = v.digits(), optionErrors = it.optionErrors - OptionField.Count) }
    fun onIntervalChange(v: String) = state.update { it.copy(intervalText = v.decimal(), optionErrors = it.optionErrors - OptionField.Interval) }
    fun onSizeChange(v: String) = state.update { it.copy(sizeText = v.digits(), optionErrors = it.optionErrors - OptionField.Size) }
    fun onTimeoutChange(v: String) = state.update { it.copy(timeoutText = v.digits(), optionErrors = it.optionErrors - OptionField.Timeout) }

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
        val options = PingOptionsValidator.ping(
            count = s.countText.takeIf { s.mode == PingMode.Count },
            interval = s.intervalText,
            size = s.sizeText,
            timeout = s.timeoutText,
        )
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
                    runTarget = target, runAddress = null, runPayload = opts.payloadBytes,
                    plannedCount = opts.count, samples = emptyList(), advancedOpen = false,
                )
            }
            val host = resolver.resolve(target)
            if (host == null) {
                state.update { it.copy(phase = RunPhase.Failed, failure = RunFailure.HostNotFound, targetError = TargetError.Unresolved(target)) }
                return@launch
            }
            history.record(TOOL, target)
            state.update { it.copy(phase = RunPhase.Running, runAddress = host.address) }

            var lastFailure: String? = null
            runner.run(PingCommand.build(host.address, host.ipv6, opts)).collect { event ->
                when (event) {
                    is PingEvent.Reply -> addSample(PingSample(event.seq, event.ttl, event.timeMs))
                    is PingEvent.NoAnswer -> addTimeout(event.seq)
                    is PingEvent.TtlExceeded -> addTimeout(event.seq)
                    is PingEvent.Unreachable -> addTimeout(event.seq)
                    is PingEvent.Summary -> fillMissing(event.transmitted)
                    is PingEvent.Failure -> lastFailure = event.message
                    PingEvent.UnknownHost -> state.update {
                        it.copy(phase = RunPhase.Failed, failure = RunFailure.HostNotFound, targetError = TargetError.Unresolved(target))
                    }
                    is PingEvent.Exited -> state.update {
                        when {
                            it.phase != RunPhase.Running -> it
                            event.code == 2 && it.samples.isEmpty() -> it.copy(phase = RunPhase.Failed, failure = RunFailure.ToolFailed(lastFailure))
                            else -> it.copy(phase = RunPhase.Done)
                        }
                    }
                    is PingEvent.Header -> Unit
                }
            }
        }
    }

    fun onStop() {
        if (!state.value.running) return
        runJob?.cancel()
        state.update { it.copy(phase = RunPhase.Stopped) }
    }

    /** Resposta substitui um timeout anterior do mesmo pacote (resposta atrasada). */
    private fun addSample(sample: PingSample) = state.update { s -> s.copy(samples = s.samples.upsert(sample, replace = true)) }

    private fun addTimeout(seq: Int) = state.update { s -> s.copy(samples = s.samples.upsert(PingSample(seq, null, null), replace = false)) }

    /** O último pacote sem resposta só aparece no resumo final: completa os que faltam como timeout. */
    private fun fillMissing(transmitted: Int) = state.update { s ->
        val seen = s.samples.mapTo(HashSet()) { it.seq }
        val missing = (1..transmitted).filter { it !in seen }.map { PingSample(it, null, null) }
        if (missing.isEmpty()) s else s.copy(samples = (s.samples + missing).sortedBy { it.seq }.takeLast(MAX_SAMPLES))
    }

    /** Os pacotes chegam quase sempre em ordem: o caso comum só acrescenta no fim. */
    private fun List<PingSample>.upsert(sample: PingSample, replace: Boolean): List<PingSample> {
        if (isEmpty() || last().seq < sample.seq) return (this + sample).let { if (it.size > MAX_SAMPLES) it.drop(it.size - MAX_SAMPLES) else it }
        val index = indexOfLast { it.seq == sample.seq }
        return when {
            index >= 0 && replace -> toMutableList().also { it[index] = sample }
            index >= 0 -> this
            else -> (this + sample).sortedBy { it.seq }
        }
    }

    private fun String.digits() = filter { it.isDigit() }.take(6)
    private fun String.decimal() = filter { it.isDigit() || it == '.' || it == ',' }.take(6)

    companion object {
        const val TOOL = "ping"
        /** Limite de memória do modo contínuo (~28 h a 1 pacote/s); acima disso descarta os mais antigos. */
        const val MAX_SAMPLES = 100_000
    }
}
