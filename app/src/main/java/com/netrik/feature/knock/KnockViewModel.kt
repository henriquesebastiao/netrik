package com.netrik.feature.knock

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.knock.KnockEvent
import com.netrik.core.knock.KnockFileStore
import com.netrik.core.knock.KnockProfile
import com.netrik.core.knock.KnockRepository
import com.netrik.core.knock.KnockRunner
import com.netrik.core.knock.KnockSender
import com.netrik.core.knock.KnockTransfer
import com.netrik.core.network.LocalAddress
import com.netrik.core.network.LocalNetworkAccess
import com.netrik.core.network.ping.HostResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import javax.inject.Inject

/** Port knocking list: groups, knocks, running a sequence, export and import. */
@HiltViewModel
class KnockViewModel @Inject constructor(
    private val repository: KnockRepository,
    private val sender: KnockSender,
    private val resolver: HostResolver,
    private val localNetwork: LocalNetworkAccess,
    private val files: KnockFileStore,
) : ViewModel() {

    private val noGroupExpanded = MutableStateFlow(true)

    val list: StateFlow<KnockListState> = combine(repository.groups, repository.profiles, noGroupExpanded) { groups, profiles, looseOpen ->
        KnockListState(
            loaded = true,
            groups = KnockList.build(groups, profiles, looseOpen),
            allGroups = groups,
            anyExpanded = groups.any { it.expanded } || (looseOpen && profiles.any { it.groupId == null }),
            hasKnocks = profiles.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KnockListState())

    private val _dialog = MutableStateFlow<KnockDialog?>(null)
    val dialog: StateFlow<KnockDialog?> = _dialog.asStateFlow()

    private val _run = MutableStateFlow<KnockRunState?>(null)
    val run: StateFlow<KnockRunState?> = _run.asStateFlow()

    private val _messages = Channel<KnockMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var runJob: Job? = null

    // Groups

    fun toggleGroup(group: KnockGroupUi) {
        if (group.id == null) {
            noGroupExpanded.update { !it }
        } else {
            viewModelScope.launch { repository.setExpanded(group.id, !group.expanded) }
        }
    }

    fun setAllExpanded(expanded: Boolean) {
        noGroupExpanded.value = expanded
        viewModelScope.launch { repository.setAllExpanded(expanded) }
    }

    fun newGroup() { _dialog.value = KnockDialog.GroupEdit(null, "") }
    fun renameGroup(group: KnockGroupUi) { _dialog.value = KnockDialog.GroupEdit(group.id, group.name.orEmpty()) }
    fun onGroupNameChange(value: String) = _dialog.update { (it as? KnockDialog.GroupEdit)?.copy(value = value) ?: it }

    fun confirmGroupEdit() {
        val edit = _dialog.value as? KnockDialog.GroupEdit ?: return
        val name = edit.value.trim()
        if (name.isEmpty()) return
        _dialog.value = null
        viewModelScope.launch {
            if (edit.groupId == null) {
                repository.createGroup(name)
                _messages.send(KnockMessage.GroupCreated(name))
            } else {
                repository.renameGroup(edit.groupId, name)
                _messages.send(KnockMessage.GroupRenamed)
            }
        }
    }

    fun deleteGroup(group: KnockGroupUi) {
        val id = group.id ?: return
        _dialog.value = KnockDialog.GroupDelete(id, group.name.orEmpty(), group.knocks.size)
    }

    fun confirmGroupDelete() {
        val delete = _dialog.value as? KnockDialog.GroupDelete ?: return
        _dialog.value = null
        viewModelScope.launch {
            repository.deleteGroup(delete.groupId)
            _messages.send(KnockMessage.GroupDeleted)
        }
    }

    // Knocks

    fun deleteKnock(profile: KnockProfile) { _dialog.value = KnockDialog.KnockDelete(profile.id, profile.name) }

    fun confirmKnockDelete() {
        val delete = _dialog.value as? KnockDialog.KnockDelete ?: return
        _dialog.value = null
        viewModelScope.launch {
            repository.delete(delete.id)
            _messages.send(KnockMessage.KnockDeleted)
        }
    }

    /** Saves a copy named [copyName] in the same group. */
    fun duplicate(profile: KnockProfile, copyName: String) {
        viewModelScope.launch {
            repository.save(profile.copy(id = 0, name = copyName))
            _messages.send(KnockMessage.KnockDuplicated(copyName))
        }
    }

    fun moveKnock(profile: KnockProfile) { _dialog.value = KnockDialog.Move(profile.id, profile.name, profile.groupId) }

    fun confirmMove(groupId: Long?) {
        val move = _dialog.value as? KnockDialog.Move ?: return
        _dialog.value = null
        if (groupId == move.groupId) return
        viewModelScope.launch {
            repository.move(move.id, groupId)
            _messages.send(KnockMessage.KnockMoved)
        }
    }

    fun dismissDialog() { _dialog.value = null }

    // Run

    fun knock(profile: KnockProfile) {
        runJob?.cancel()
        _run.value = KnockRunState(profile)
        runJob = viewModelScope.launch {
            val resolved = resolver.resolve(profile.host)
            if (resolved == null) {
                fail(KnockRunError.HostNotFound)
                return@launch
            }
            // Android 17+: without the permission, LAN traffic is dropped silently.
            if (LocalAddress.isLocal(resolved.address) && !localNetwork.isGranted()) {
                fail(KnockRunError.LocalNetworkPermission)
                return@launch
            }
            val address = InetAddress.getByName(resolved.address)
            _run.update { it?.copy(phase = KnockPhase.Running, address = resolved.address) }
            KnockRunner.run(profile, address, sender).collect { event ->
                _run.update { state -> state?.apply(event) }
            }
            _run.update { state ->
                if (state == null || state.phase != KnockPhase.Running) state else state.copy(phase = KnockPhase.Done)
            }
        }
    }

    private fun KnockRunState.apply(event: KnockEvent): KnockRunState = when (event) {
        is KnockEvent.StepSent -> copy(steps = steps.withStatus(event.index, StepStatus.Sent))
        is KnockEvent.StepFailed -> copy(
            steps = steps.withStatus(event.index, StepStatus.Failed),
            phase = KnockPhase.Failed,
            error = KnockRunError.Step(event.index, event.error),
        )
        is KnockEvent.Verifying -> copy(verifying = true)
        is KnockEvent.Verified -> copy(verifying = false, verifyResult = event.result)
    }

    private fun List<StepStatus>.withStatus(index: Int, status: StepStatus) = mapIndexed { i, s -> if (i == index) status else s }

    private fun fail(error: KnockRunError) {
        _run.update { it?.copy(phase = KnockPhase.Failed, error = error) }
    }

    fun stopKnock() {
        runJob?.cancel()
        _run.update { state -> if (state != null && state.running) state.copy(phase = KnockPhase.Stopped, verifying = false) else state }
    }

    fun retryKnock() {
        _run.value?.profile?.let(::knock)
    }

    fun closeRun() {
        runJob?.cancel()
        _run.value = null
    }

    // Export / import

    /** Writes every knock (or only [groupId]'s) to [uri]. */
    fun exportTo(uri: Uri, groupId: Long?) {
        viewModelScope.launch {
            val ok = files.write(uri, repository.export(groupId))
            _messages.send(if (ok) KnockMessage.Exported else KnockMessage.ExportFailed)
        }
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val text = files.read(uri)
            if (text == null) {
                _dialog.value = KnockDialog.ImportFailed(ImportFailure.Unreadable)
                return@launch
            }
            when (val parsed = KnockTransfer.parse(text)) {
                KnockTransfer.Parsed.NotKnockFile -> _dialog.value = KnockDialog.ImportFailed(ImportFailure.NotKnockFile)
                is KnockTransfer.Parsed.NewerVersion -> _dialog.value = KnockDialog.ImportFailed(ImportFailure.NewerVersion)
                is KnockTransfer.Parsed.Ok -> {
                    val file = parsed.file
                    if (file.knocks.isEmpty() && file.groups.isEmpty() && file.invalid == 0) {
                        _dialog.value = KnockDialog.ImportFailed(ImportFailure.Empty)
                    } else {
                        _dialog.value = KnockDialog.Imported(repository.import(file))
                    }
                }
            }
        }
    }
}
