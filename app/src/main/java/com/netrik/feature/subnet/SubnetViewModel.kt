package com.netrik.feature.subnet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.database.TargetHistoryRepository
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.subnet.AggregateResult
import com.netrik.core.subnet.ParsedSubnet
import com.netrik.core.subnet.SplitBy
import com.netrik.core.subnet.SplitResult
import com.netrik.core.subnet.SubnetAggregator
import com.netrik.core.subnet.SubnetCalculator
import com.netrik.core.subnet.SubnetInfo
import com.netrik.core.subnet.SubnetInputError
import com.netrik.core.subnet.SubnetParser
import com.netrik.core.subnet.SubnetSplitter
import com.netrik.core.subnet.VlsmResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

enum class SubnetTab { Calculate, Split, Aggregate }

enum class SplitMode { Equal, Vlsm }

enum class SplitKind { Prefix, Count, Hosts }

sealed interface CalcUi {
    data class Result(val info: SubnetInfo, val prefixGiven: Boolean) : CalcUi
    data class Error(val error: SubnetInputError) : CalcUi
}

sealed interface SplitUi {
    data class NetworkError(val error: SubnetInputError) : SplitUi
    data class Equal(val result: SplitResult) : SplitUi
    /** [invalidEntry]: 1-based entry of the plan that isn't "name: hosts". */
    data class Vlsm(val result: VlsmResult?, val invalidEntry: Int?) : SplitUi
}

data class SubnetUiState(
    val tab: SubnetTab = SubnetTab.Calculate,
    val input: String = "",
    /** Null while the field is empty. */
    val calc: CalcUi? = null,
    val recents: List<String> = emptyList(),
    val splitNetwork: String = "",
    val splitMode: SplitMode = SplitMode.Equal,
    val splitKind: SplitKind = SplitKind.Prefix,
    val splitValue: String = "",
    val vlsmText: String = "",
    /** Null until there is a network and something to split it by. */
    val split: SplitUi? = null,
    val aggregateText: String = "",
    val aggregate: AggregateResult? = null,
)

/**
 * Subnet calculator: everything is pure math on the typed text (no network access). Opens with the route's
 * target or, without one, the current network's address and prefix.
 */
@HiltViewModel
class SubnetViewModel @Inject constructor(
    private val history: TargetHistoryRepository,
    networkInfo: NetworkInfoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val state = MutableStateFlow(SubnetUiState())

    val uiState: StateFlow<SubnetUiState> = combine(state, history.recent(TOOL)) { s, recents -> s.copy(recents = recents) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    init {
        val target = savedStateHandle.get<String>("target")
        if (!target.isNullOrBlank()) {
            onInputChange(target)
        } else {
            viewModelScope.launch {
                val network = withTimeoutOrNull(1_000) { networkInfo.currentNetwork.first() } as? CurrentNetwork.Connected
                val ipv4 = network?.ipv4
                if (ipv4 != null && state.value.input.isEmpty()) onInputChange("${ipv4.address}/${ipv4.prefixLength}")
            }
        }
    }

    fun selectTab(tab: SubnetTab) = state.update { s ->
        // The network being calculated is the natural one to split.
        val splitNetwork = if (tab == SubnetTab.Split && s.splitNetwork.isBlank()) {
            (s.calc as? CalcUi.Result)?.info?.cidr?.toString().orEmpty()
        } else {
            s.splitNetwork
        }
        recomputeSplit(s.copy(tab = tab, splitNetwork = splitNetwork))
    }

    // Calculate

    fun onInputChange(text: String) = state.update { it.copy(input = text, calc = calculate(text)) }

    /** Keyboard "Go" or a history chip: keeps a valid input in the history. */
    fun submit() {
        val s = state.value
        if (s.calc is CalcUi.Result) viewModelScope.launch { history.record(TOOL, s.input.trim()) }
    }

    fun useRecent(text: String) {
        onInputChange(text)
        submit()
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear(TOOL) }
    }

    /** −/+ buttons: same address, prefix one shorter/longer. */
    fun changePrefix(delta: Int) {
        val result = state.value.calc as? CalcUi.Result ?: return
        val prefix = (result.info.cidr.prefix + delta).coerceIn(0, result.info.version.bits)
        onInputChange("${result.info.address.format()}/$prefix")
    }

    private fun calculate(text: String): CalcUi? = when (val parsed = SubnetParser.parse(text)) {
        is ParsedSubnet.Ok -> CalcUi.Result(SubnetCalculator.info(parsed.address, parsed.prefix), parsed.prefixGiven)
        is ParsedSubnet.Error -> parsed.error.takeIf { it != SubnetInputError.Empty }?.let(CalcUi::Error)
    }

    // Split

    fun onSplitNetworkChange(text: String) = state.update { recomputeSplit(it.copy(splitNetwork = text)) }
    fun onSplitModeChange(mode: SplitMode) = state.update { recomputeSplit(it.copy(splitMode = mode)) }
    fun onSplitKindChange(kind: SplitKind) = state.update { recomputeSplit(it.copy(splitKind = kind, splitValue = "")) }
    fun onSplitValueChange(text: String) = state.update { recomputeSplit(it.copy(splitValue = text.filter(Char::isDigit).take(12))) }
    fun onVlsmTextChange(text: String) = state.update { recomputeSplit(it.copy(vlsmText = text)) }

    private fun recomputeSplit(s: SubnetUiState): SubnetUiState {
        val parsed = SubnetParser.parse(s.splitNetwork)
        val split = when {
            parsed is ParsedSubnet.Error -> parsed.error.takeIf { it != SubnetInputError.Empty }?.let(SplitUi::NetworkError)
            s.splitMode == SplitMode.Equal -> {
                val parent = (parsed as ParsedSubnet.Ok).cidr
                s.splitValue.toLongOrNull()?.let { value ->
                    val by = when (s.splitKind) {
                        SplitKind.Prefix -> SplitBy.Prefix(value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        SplitKind.Count -> SplitBy.Count(value)
                        SplitKind.Hosts -> SplitBy.Hosts(value)
                    }
                    SplitUi.Equal(SubnetSplitter.split(parent, by))
                }
            }
            else -> {
                val parent = (parsed as ParsedSubnet.Ok).cidr
                val (requests, invalid) = SubnetSplitter.parseRequests(s.vlsmText)
                when {
                    invalid != null -> SplitUi.Vlsm(null, invalid)
                    requests.isEmpty() -> null
                    else -> SplitUi.Vlsm(SubnetSplitter.vlsm(parent, requests), null)
                }
            }
        }
        return s.copy(split = split)
    }

    // Aggregate

    fun onAggregateTextChange(text: String) = state.update {
        it.copy(aggregateText = text, aggregate = SubnetAggregator.aggregate(text).takeIf { r -> !r.isEmpty || r.invalidLines.isNotEmpty() })
    }

    private companion object {
        const val TOOL = "subnet"
    }
}
