package com.netrik.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.network.NetworkInfoRepository
import com.netrik.core.network.PublicIpRepository
import com.netrik.core.settings.NetworkPreferences
import com.netrik.core.wifi.WifiScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HubViewModel @Inject constructor(
    networkInfoRepository: NetworkInfoRepository,
    private val publicIpRepository: PublicIpRepository,
    wifiScan: WifiScanRepository,
    networkPreferences: NetworkPreferences,
) : ViewModel() {

    /** Public IP lookup, tied to the network it was made on. */
    private sealed interface PublicIpQuery {
        val networkKey: String
        data class Loading(override val networkKey: String) : PublicIpQuery
        data class Done(override val networkKey: String, val ip: String) : PublicIpQuery
        data class Failed(override val networkKey: String) : PublicIpQuery
    }

    private val publicIpQuery = MutableStateFlow<PublicIpQuery?>(null)
    private var publicIpJob: Job? = null

    /** Channel width per BSSID, from the last Wi-Fi scan (empty without the location permission). */
    private val channelWidths = wifiScan.networks
        .map { list -> list.associate { it.bssid to it.widthMhz } }
        .onStart { emit(emptyMap()) }

    private val card: StateFlow<NetworkCardState> =
        combine(networkInfoRepository.currentNetwork, channelWidths) { network, widths -> network.toCardState(widths) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkCardState.Loading)

    val uiState: StateFlow<HubUiState> = combine(card, publicIpQuery) { card, query ->
        // If the network changed after the lookup, the old public IP is no longer valid.
        val key = (card as? NetworkCardState.Connected)?.networkKey
        val publicIp = when {
            query == null || query.networkKey != key -> PublicIpUi.Hidden
            query is PublicIpQuery.Loading -> PublicIpUi.Loading
            query is PublicIpQuery.Done -> PublicIpUi.Loaded(query.ip)
            else -> PublicIpUi.Failed
        }
        HubUiState(network = card, publicIp = publicIp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HubUiState())

    init {
        // "Always show public IP" (Settings): look it up on its own whenever the network changes.
        viewModelScope.launch {
            combine(card, networkPreferences.alwaysShowPublicIp) { card, always -> (card as? NetworkCardState.Connected)?.networkKey.takeIf { always } }
                .distinctUntilChanged()
                .collect { key -> if (key != null && publicIpQuery.value?.networkKey != key) fetchPublicIp(key) }
        }
    }

    /** On an explicit user action, or automatically when the user turned that on in Settings: the lookup reveals the IP to an external service. */
    fun onShowPublicIp() {
        val connected = card.value as? NetworkCardState.Connected ?: return
        fetchPublicIp(connected.networkKey)
    }

    private fun fetchPublicIp(key: String) {
        publicIpJob?.cancel()
        publicIpQuery.value = PublicIpQuery.Loading(key)
        publicIpJob = viewModelScope.launch {
            publicIpQuery.value = publicIpRepository.fetchPublicIp().fold(
                onSuccess = { PublicIpQuery.Done(key, it) },
                onFailure = { PublicIpQuery.Failed(key) },
            )
        }
    }
}
