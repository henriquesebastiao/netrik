package com.netrik.feature.neighbors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.neighbor.Neighbor
import com.netrik.core.neighbor.NeighborDiscovery
import com.netrik.core.neighbor.NeighborEvent
import com.netrik.core.neighbor.NeighborList
import com.netrik.core.neighbor.NeighborProtocol
import com.netrik.core.network.CurrentNetwork
import com.netrik.core.network.LocalNetworkAccess
import com.netrik.core.network.NetworkInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/** Where the discovery stands; the list survives stopping and starting again. */
data class NeighborsUiState(
    val running: Boolean = false,
    val neighbors: List<Neighbor> = emptyList(),
    val mndpPortBusy: Boolean = false,
    /** Android 17+: broadcasts to the local network need `ACCESS_LOCAL_NETWORK`. */
    val needsPermission: Boolean = false,
    /** Wi-Fi or Ethernet: on mobile data there are no neighbors to find. */
    val onLocalNetwork: Boolean = true,
    val startedAtMillis: Long? = null,
    val nowMillis: Long = 0,
) {
    val mikrotik: List<Neighbor> get() = neighbors.filter { it.protocol == NeighborProtocol.Mndp }
    val ubiquiti: List<Neighbor> get() = neighbors.filter { it.protocol == NeighborProtocol.Ubiquiti }
}

/**
 * Neighbor discovery (MikroTik MNDP and Ubiquiti). Starts when the screen opens, pauses while it isn't
 * visible ([pause]/[resume]) and stops on [stop]; found devices stay listed.
 */
@HiltViewModel
class NeighborsViewModel @Inject constructor(
    private val discovery: NeighborDiscovery,
    private val localNetwork: LocalNetworkAccess,
    private val clock: Clock,
    networkInfo: NetworkInfoRepository,
) : ViewModel() {

    private val state = MutableStateFlow(NeighborsUiState(nowMillis = clock.millis()))
    private var job: Job? = null
    /** The user pressed Stop: going back to the screen doesn't restart. */
    private var stoppedByUser = false

    /** Refreshes "seen X s ago" once a second. */
    private val ticker = flow {
        while (true) {
            emit(clock.millis())
            delay(1_000)
        }
    }

    private val onLocalNetwork = networkInfo.currentNetwork.map { network ->
        network is CurrentNetwork.Connected &&
            network.transport != CurrentNetwork.Transport.Cellular
    }

    val uiState: StateFlow<NeighborsUiState> = combine(state, ticker, onLocalNetwork) { s, now, lan ->
        s.copy(nowMillis = now, onLocalNetwork = lan)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), state.value)

    fun start() {
        stoppedByUser = false
        launchDiscovery()
    }

    fun stop() {
        stoppedByUser = true
        job?.cancel()
        job = null
        state.update { it.copy(running = false) }
    }

    /** Screen visible again: carries on unless the user stopped it. */
    fun resume() {
        if (!stoppedByUser && job == null) launchDiscovery()
    }

    /** Screen hidden (app in background, another screen on top): no broadcasts meanwhile. */
    fun pause() {
        job?.cancel()
        job = null
        state.update { it.copy(running = false) }
    }

    private fun launchDiscovery() {
        if (job != null) return
        if (!localNetwork.isGranted()) {
            state.update { it.copy(needsPermission = true, running = false) }
            return
        }
        state.update { it.copy(needsPermission = false, running = true, mndpPortBusy = false, startedAtMillis = clock.millis()) }
        job = viewModelScope.launch {
            discovery.discover().collect { event ->
                when (event) {
                    is NeighborEvent.Found -> state.update { it.copy(neighbors = NeighborList.sorted(NeighborList.upsert(it.neighbors, event.neighbor))) }
                    NeighborEvent.MndpPortBusy -> state.update { it.copy(mndpPortBusy = true) }
                }
            }
        }
    }

    fun clear() = state.update { it.copy(neighbors = emptyList(), startedAtMillis = if (it.running) clock.millis() else null) }
}
