package com.netrik.feature.oui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.oui.MacAddresses
import com.netrik.core.oui.MacInput
import com.netrik.core.oui.OuiDbStatus
import com.netrik.core.oui.OuiRepository
import com.netrik.core.oui.OuiUpdateEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class OuiViewModel @Inject constructor(
    private val repository: OuiRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private data class Query(
        val input: String = "",
        val showShortError: Boolean = false,
        val result: OuiResult? = null,
    )

    private val query = MutableStateFlow(Query())
    private var lookupJob: Job? = null

    private val _messages = Channel<OuiMessage>(Channel.BUFFERED)
    val messages: Flow<OuiMessage> = _messages.receiveAsFlow()

    private val history = repository.history.map { entries ->
        entries.map {
            HistoryItem(
                hex = it.hex,
                organization = it.match?.record?.organization,
                locallyAdministered = MacAddresses.isLocallyAdministered(it.hex),
                queriedAt = it.queriedAt,
            )
        }
    }

    val uiState: StateFlow<OuiUiState> = combine(
        query,
        history.onStart { emit(emptyList()) },
        repository.status.map<OuiDbStatus, OuiDbStatus?> { it }.onStart { emit(null) },
    ) { q, items, db ->
        OuiUiState(
            input = q.input,
            parsed = MacAddresses.parse(q.input),
            showShortError = q.showShortError,
            result = q.result,
            history = items,
            db = db,
            now = clock.millis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OuiUiState())

    init {
        viewModelScope.launch {
            repository.updateEvents.collect { event ->
                _messages.send(
                    when (event) {
                        is OuiUpdateEvent.Success -> OuiMessage.Updated(event.total, event.added)
                        is OuiUpdateEvent.Failure -> when (event.reason) {
                            OuiUpdateEvent.Failure.Reason.Network -> OuiMessage.UpdateNetworkError
                            OuiUpdateEvent.Failure.Reason.Rejected -> OuiMessage.UpdateRejected
                            OuiUpdateEvent.Failure.Reason.InvalidData -> OuiMessage.UpdateInvalid
                        }
                    },
                )
                // Base trocada: refaz a consulta exibida com os dados novos.
                if (event is OuiUpdateEvent.Success) query.value.result?.let { lookup(it.hex, record = false) }
            }
        }
        // Argumento "mac" de OuiRoute: pré-preenche e consulta quando a tela é aberta com um MAC.
        savedStateHandle.get<String>("mac")?.let(::onPaste)
    }

    fun onInputChange(text: String) {
        val input = text.uppercase()
        lookupJob?.cancel()
        query.value = Query(input = input)
        // MAC completo é consultado sozinho, após uma pausa curta na digitação.
        if (MacAddresses.parse(input) is MacInput.Full) {
            lookupJob = viewModelScope.launch {
                delay(AUTO_LOOKUP_DELAY_MS)
                lookup(MacAddresses.parse(input).hex)
            }
        }
    }

    fun onSubmit() {
        val parsed = MacAddresses.parse(query.value.input)
        if (!parsed.isQueryable) {
            query.value = query.value.copy(showShortError = true)
            return
        }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch { lookup(parsed.hex) }
    }

    /** Colar consulta na hora, inclusive prefixos (que normalmente esperam o botão). */
    fun onPaste(text: String) {
        onInputChange(text.trim())
        if (MacAddresses.parse(query.value.input).isQueryable) onSubmit()
    }

    fun onHistorySelected(hex: String) = onInputChange(MacAddresses.format(hex)).also { onSubmit() }

    fun onHistoryRemove(hex: String) {
        viewModelScope.launch { repository.removeFromHistory(hex) }
    }

    fun onClearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _messages.send(OuiMessage.HistoryCleared)
        }
    }

    fun onUpdateDatabase() = repository.updateFromIeee()

    private suspend fun lookup(hex: String, record: Boolean = true) {
        query.value = query.value.copy(showShortError = false, result = OuiResult.Loading(hex))
        val match = repository.lookup(hex)
        query.value = query.value.copy(
            result = OuiResult.Done(
                hex = hex,
                match = match,
                locallyAdministered = MacAddresses.isLocallyAdministered(hex),
                multicast = MacAddresses.isMulticast(hex),
            ),
        )
        if (record) repository.recordQuery(hex)
    }

    private companion object {
        const val AUTO_LOOKUP_DELAY_MS = 350L
    }
}
