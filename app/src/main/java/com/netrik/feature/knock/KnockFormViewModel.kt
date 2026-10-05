package com.netrik.feature.knock

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.knock.KnockField
import com.netrik.core.knock.KnockForm
import com.netrik.core.knock.KnockGroup
import com.netrik.core.knock.KnockProfile
import com.netrik.core.knock.KnockProtocol
import com.netrik.core.knock.KnockRepository
import com.netrik.core.ssh.SshForm
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** New/edit knock form; the route's "profileId" edits a saved knock. */
@HiltViewModel
class KnockFormViewModel @Inject constructor(
    private val repository: KnockRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>("profileId")?.takeIf { it > 0 }
    private var nextKey = 0L

    private val _form = MutableStateFlow(KnockFormState())
    val form: StateFlow<KnockFormState> = _form.asStateFlow()

    val groups: StateFlow<List<KnockGroup>> = repository.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _closed = Channel<Unit>(Channel.CONFLATED)
    /** Emits once the knock is saved: the screen closes. */
    val closed = _closed.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = editingId?.let { repository.profile(it) }
            _form.value = if (profile == null) {
                KnockFormState(loaded = true, delayMs = KnockForm.DEFAULT_DELAY_MS.toString(), steps = listOf(newStep(KnockProtocol.Tcp)))
            } else {
                KnockFormState(
                    loaded = true,
                    editingId = profile.id,
                    name = profile.name,
                    host = profile.host,
                    groupId = profile.groupId,
                    delayMs = profile.delayMs.toString(),
                    verifyPort = profile.verifyPort?.toString().orEmpty(),
                    steps = profile.steps.map { step ->
                        StepDraft(nextKey++, step.protocol, (step.port ?: step.payloadSize)?.toString().orEmpty())
                    },
                )
            }
        }
    }

    fun update(transform: (KnockFormState) -> KnockFormState) = _form.update(transform)

    fun addStep() = _form.update { form ->
        // Repeats the last protocol: sequences are often all TCP or all UDP.
        val protocol = form.steps.lastOrNull()?.protocol ?: KnockProtocol.Tcp
        form.copy(steps = form.steps + newStep(protocol), errors = form.errors - KnockField.Steps)
    }

    fun removeStep(index: Int) = _form.update { form ->
        form.copy(steps = form.steps.filterIndexed { i, _ -> i != index }, stepErrors = emptyMap())
    }

    fun moveStep(index: Int, offset: Int) = _form.update { form ->
        val target = index + offset
        if (target !in form.steps.indices) return@update form
        val steps = form.steps.toMutableList().apply { add(target, removeAt(index)) }
        form.copy(steps = steps, stepErrors = emptyMap())
    }

    fun setStepProtocol(index: Int, protocol: KnockProtocol) = updateStep(index) { step ->
        // ICMP has no port: the field becomes the (optional) payload size.
        val value = if ((step.protocol == KnockProtocol.Icmp) != (protocol == KnockProtocol.Icmp)) "" else step.value
        step.copy(protocol = protocol, value = value)
    }

    fun setStepValue(index: Int, value: String) = updateStep(index) { it.copy(value = value.filter(Char::isDigit).take(5)) }

    private fun updateStep(index: Int, transform: (StepDraft) -> StepDraft) = _form.update { form ->
        form.copy(steps = form.steps.mapIndexed { i, step -> if (i == index) transform(step) else step }, stepErrors = form.stepErrors - index)
    }

    fun save() {
        val form = _form.value
        val inputs = form.steps.map { KnockForm.StepInput(it.protocol, it.value) }
        val errors = KnockForm.validate(KnockForm.Input(form.host, form.delayMs, form.verifyPort, inputs))
        val stepErrors = KnockForm.stepErrors(inputs)
        if (errors.isNotEmpty() || stepErrors.isNotEmpty()) {
            _form.update { it.copy(errors = errors, stepErrors = stepErrors) }
            return
        }
        val host = SshForm.normalizeHost(form.host)
        val profile = KnockProfile(
            id = form.editingId ?: 0,
            name = KnockForm.displayName(form.name, host),
            host = host,
            groupId = form.groupId,
            delayMs = requireNotNull(KnockForm.parseDelay(form.delayMs)),
            verifyPort = KnockForm.parsePort(form.verifyPort),
            steps = inputs.map { requireNotNull(KnockForm.toStep(it)) },
        )
        viewModelScope.launch {
            repository.save(profile)
            _closed.send(Unit)
        }
    }

    private fun newStep(protocol: KnockProtocol) = StepDraft(nextKey++, protocol, "")
}
