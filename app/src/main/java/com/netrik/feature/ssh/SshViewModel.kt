package com.netrik.feature.ssh

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netrik.core.ssh.KeyFileReader
import com.netrik.core.ssh.KeyInspection
import com.netrik.core.ssh.PrivateKeyFile
import com.netrik.core.ssh.PrivateKeys
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshConnectResult
import com.netrik.core.ssh.SshConnector
import com.netrik.core.ssh.SshFailure
import com.netrik.core.ssh.SshField
import com.netrik.core.ssh.SshFieldError
import com.netrik.core.ssh.SshForm
import com.netrik.core.ssh.SshHostDraft
import com.netrik.core.ssh.SshRepository
import com.netrik.core.ssh.SshTarget
import com.netrik.core.terminal.SshSessionManager
import com.netrik.core.terminal.SshTerminal
import com.netrik.core.terminal.TerminalFont
import com.netrik.core.terminal.TerminalPreferences
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
import javax.inject.Inject

/**
 * SSH tab state (host list, form and terminal sessions), shared by the screens of the tab graph.
 * After authenticating, the connection becomes a terminal session in [SshSessionManager].
 */
@HiltViewModel
class SshViewModel @Inject constructor(
    private val repository: SshRepository,
    private val connector: SshConnector,
    private val keyReader: KeyFileReader,
    private val sessions: SshSessionManager,
    private val terminalPrefs: TerminalPreferences,
) : ViewModel() {

    /** Open terminal sessions ("active sessions" banner, green dot on hosts, terminal tabs). */
    val terminals: StateFlow<List<SshTerminal>> = sessions.sessions
    val activeTerminalId: StateFlow<Long?> = sessions.activeId
    val fontSize: StateFlow<Int> = terminalPrefs.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, TerminalFont.DEFAULT)


    private val search = MutableStateFlow(SearchState())
    private val noGroupExpanded = MutableStateFlow(true)

    val list: StateFlow<SshListState> = combine(repository.groups, repository.hosts, search, noGroupExpanded) { groups, hosts, s, looseOpen ->
        SshListState(
            loaded = true,
            groups = SshHostList.build(groups, hosts, s.query, looseOpen),
            allGroups = groups,
            searchOpen = s.open,
            query = s.query,
            anyExpanded = groups.any { it.expanded } || (looseOpen && hosts.any { it.groupId == null }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SshListState())

    private val _form = MutableStateFlow(SshFormState())
    val form: StateFlow<SshFormState> = _form.asStateFlow()

    private val _dialog = MutableStateFlow<SshDialog?>(null)
    val dialog: StateFlow<SshDialog?> = _dialog.asStateFlow()

    private val _events = Channel<SshEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Back stack entry whose form was already prepared (avoids resetting on recomposition). */
    private var formEntry: String? = null
    private var connectJob: Job? = null
    /** Last attempt, to retry after trusting the key or granting the permission. */
    private var pending: Pending? = null

    private data class SearchState(val open: Boolean = false, val query: String = "")

    /** Saved [hostId] (list or form with "Save connection") or one-off [draft] (just connect). */
    private class Pending(val hostId: Long?, val draft: SshHostDraft?, val fromForm: Boolean)

    // List

    fun openSearch() = search.update { it.copy(open = true) }
    fun closeSearch() = search.update { SearchState() }
    fun onQueryChange(query: String) = search.update { it.copy(query = query) }

    fun toggleGroup(group: HostGroupUi) {
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

    fun newGroup() { _dialog.value = SshDialog.GroupEdit(null, "") }
    fun renameGroup(group: HostGroupUi) { _dialog.value = SshDialog.GroupEdit(group.id, group.name.orEmpty()) }
    fun onGroupNameChange(value: String) = _dialog.update { (it as? SshDialog.GroupEdit)?.copy(value = value) ?: it }

    fun confirmGroupEdit() {
        val edit = _dialog.value as? SshDialog.GroupEdit ?: return
        val name = edit.value.trim()
        if (name.isEmpty()) return
        _dialog.value = null
        viewModelScope.launch {
            if (edit.groupId == null) {
                repository.createGroup(name)
                _events.send(SshEvent.Message(SshMessage.GroupCreated(name)))
            } else {
                repository.renameGroup(edit.groupId, name)
                _events.send(SshEvent.Message(SshMessage.GroupRenamed))
            }
        }
    }

    fun deleteGroup(group: HostGroupUi) {
        val id = group.id ?: return
        _dialog.value = SshDialog.GroupDelete(id, group.name.orEmpty(), group.total)
    }

    fun confirmGroupDelete() {
        val delete = _dialog.value as? SshDialog.GroupDelete ?: return
        _dialog.value = null
        viewModelScope.launch {
            repository.deleteGroup(delete.groupId)
            _events.send(SshEvent.Message(SshMessage.GroupDeleted))
        }
    }

    fun deleteHost(hostId: Long, name: String) { _dialog.value = SshDialog.HostDelete(hostId, name) }

    fun confirmHostDelete() {
        val delete = _dialog.value as? SshDialog.HostDelete ?: return
        _dialog.value = null
        viewModelScope.launch {
            repository.deleteHost(delete.hostId)
            _events.send(SshEvent.Message(SshMessage.HostDeleted))
        }
    }

    /** Tapping a host: with an open session, go back to it; otherwise connect. */
    fun connectSaved(hostId: Long) {
        val open = sessions.forHost(hostId)
        if (open != null) {
            sessions.setActive(open.id)
            viewModelScope.launch { _events.send(SshEvent.OpenTerminal) }
        } else {
            startConnect(Pending(hostId, null, fromForm = false))
        }
    }

    // Terminal

    fun selectTerminal(id: Long) = sessions.setActive(id)
    fun closeTerminal(id: Long) = sessions.close(id)
    fun disconnectActive() = activeTerminalId.value?.let { sessions.close(it, disconnect = true) }
    fun changeFontSize(delta: Int) {
        viewModelScope.launch { terminalPrefs.setFontSize(fontSize.value + delta) }
    }

    // Form

    /** Prepares the form once per back stack entry: new (with an optional [target]) or editing [hostId]. */
    fun prepareForm(entryId: String, hostId: Long?, target: String?, passwordRejected: Boolean) {
        if (formEntry == entryId) return
        formEntry = entryId
        if (hostId == null) {
            _form.value = SshFormState(host = target.orEmpty())
            return
        }
        _form.value = SshFormState(editingId = hostId, isNew = false)
        viewModelScope.launch {
            val host = repository.host(hostId) ?: return@launch
            val stored = repository.storedSecrets(hostId)
            _form.value = SshFormState(
                editingId = host.id,
                isNew = false,
                name = if (host.name == host.host) "" else host.name,
                host = host.host,
                port = host.port.toString(),
                user = host.username,
                auth = host.auth,
                storedKeyName = host.keyName,
                storedKeyInfo = host.keyInfo,
                groupId = host.groupId,
                hasStoredPassword = stored.password,
                hasStoredPassphrase = stored.keyPassphrase,
                passwordRejected = passwordRejected,
                errors = if (passwordRejected) mapOf(SshField.Password to SshFieldError.PasswordRejected) else emptyMap(),
            )
        }
    }

    fun onFormClosed() {
        connectJob?.cancel()
        formEntry = null
        _form.value = SshFormState()
    }

    fun updateForm(transform: (SshFormState) -> SshFormState) = _form.update { old ->
        val new = transform(old)
        // Editing a field clears its error.
        val cleared = old.errors.filterKeys { field ->
            when (field) {
                SshField.Host -> new.host == old.host
                SshField.Port -> new.port == old.port
                SshField.User -> new.user == old.user
                SshField.Password -> new.password == old.password && new.auth == old.auth
                SshField.Key -> new.key == old.key && new.auth == old.auth
            }
        }
        new.copy(
            errors = cleared,
            passphraseError = if (new.keyPassphrase != old.keyPassphrase || new.key != old.key) null else new.passphraseError,
        )
    }

    fun onKeyPicked(uri: Uri) {
        viewModelScope.launch {
            when (val read = keyReader.read(uri)) {
                KeyFileReader.Result.TooLarge -> _form.update { it.copy(keyError = KeyFileError.TooLarge) }
                KeyFileReader.Result.Unreadable -> _form.update { it.copy(keyError = KeyFileError.Unreadable) }
                is KeyFileReader.Result.Read -> when (val inspection = PrivateKeys.inspect(read.bytes)) {
                    is KeyInspection.Valid -> updateForm {
                        it.copy(key = PrivateKeyFile(read.name, read.bytes, inspection.summary, inspection.encrypted), keyError = null)
                    }
                    KeyInspection.TooLarge -> _form.update { it.copy(keyError = KeyFileError.TooLarge) }
                    else -> _form.update { it.copy(keyError = KeyFileError.Invalid) }
                }
            }
        }
    }

    /** "Save and connect" / "Connect". [connect] false: only saves (editing). */
    fun submitForm(connect: Boolean = true) {
        val f = _form.value
        val errors = SshForm.validate(
            SshForm.Input(
                host = f.host,
                port = f.port,
                user = f.user,
                auth = f.auth,
                passwordFilled = f.password.isNotEmpty(),
                hasKey = f.hasKey,
                hasStoredPassword = f.isEditing && f.hasStoredPassword && !f.passwordRejected,
            ),
        )
        if (errors.isNotEmpty()) {
            _form.update { it.copy(errors = errors) }
            return
        }
        // Encrypted key: checks the passphrase before going to the network.
        val passphrase = f.keyPassphrase.toByteArray()
        if (f.auth == SshAuth.Key && f.key != null) {
            when (val inspection = PrivateKeys.inspect(f.key.bytes, passphrase)) {
                KeyInspection.WrongPassphrase -> return _form.update { it.copy(passphraseError = PassphraseError.Wrong) }
                is KeyInspection.Valid -> if (inspection.encrypted && passphrase.isEmpty()) {
                    return _form.update { it.copy(passphraseError = PassphraseError.Required) }
                }
                else -> return _form.update { it.copy(keyError = KeyFileError.Invalid) }
            }
        }
        val host = SshForm.normalizeHost(f.host)
        val draft = SshHostDraft(
            id = f.editingId,
            name = SshForm.displayName(f.name, host),
            host = host,
            port = SshForm.parsePort(f.port)!!,
            username = f.user.trim(),
            auth = f.auth,
            groupId = f.groupId,
            password = f.password.takeIf { f.auth == SshAuth.Password && it.isNotEmpty() }?.toByteArray(),
            key = f.key,
            keyPassphrase = passphrase.takeIf { f.auth == SshAuth.Key && it.isNotEmpty() },
        )
        if (!f.save && !f.isEditing) {
            if (connect) startConnect(Pending(null, draft, fromForm = true))
            return
        }
        viewModelScope.launch {
            val id = repository.save(draft)
            draft.password?.fill(0)
            draft.keyPassphrase?.fill(0)
            // From here on the form edits the saved host: retrying doesn't duplicate it.
            _form.update {
                it.copy(
                    editingId = id,
                    password = "",
                    hasStoredPassword = it.hasStoredPassword || draft.password != null,
                    passwordRejected = false,
                    key = null,
                    storedKeyName = draft.key?.name ?: it.storedKeyName,
                    storedKeyInfo = draft.key?.info ?: it.storedKeyInfo,
                    keyPassphrase = "",
                    hasStoredPassphrase = if (draft.key != null) draft.keyPassphrase != null else it.hasStoredPassphrase || draft.keyPassphrase != null,
                )
            }
            if (connect) startConnect(Pending(id, null, fromForm = true)) else _events.send(SshEvent.CloseForm)
        }
    }

    // Connection

    private fun startConnect(attempt: Pending) {
        connectJob?.cancel()
        pending = attempt
        connectJob = viewModelScope.launch {
            val target = attempt.hostId?.let { repository.target(it) } ?: attempt.draft?.toTarget() ?: return@launch
            val name = attempt.hostId?.let { repository.host(it)?.name } ?: attempt.draft?.name ?: target.host
            val label = "${target.username}@${target.host}:${target.port}"
            val host = target.host
            val port = target.port
            val user = target.username
            val auth = target.auth
            trustTarget = target.hostId
            _dialog.value = SshDialog.Connecting(label)
            when (val result = connector.connect(target)) {
                is SshConnectResult.Connected -> {
                    sessions.open(result.session, attempt.hostId, name, label)
                    _dialog.value = null
                    pending = null
                    if (attempt.fromForm) _events.send(SshEvent.CloseForm)
                    _events.send(SshEvent.OpenTerminal)
                }
                is SshConnectResult.UnknownHostKey -> _dialog.value = SshDialog.Fingerprint(hostIdLabel(host, port), result.key)
                is SshConnectResult.ChangedHostKey ->
                    _dialog.value = SshDialog.ChangedKey(hostIdLabel(host, port), result.stored, result.presented)
                is SshConnectResult.Failed -> {
                    if (result.failure == SshFailure.KeyWrongPassphrase || result.failure == SshFailure.KeyPassphraseRequired) {
                        if (attempt.fromForm) {
                            _dialog.value = null
                            _form.update {
                                it.copy(passphraseError = if (result.failure == SshFailure.KeyWrongPassphrase) PassphraseError.Wrong else PassphraseError.Required)
                            }
                            return@launch
                        }
                    }
                    _dialog.value = SshDialog.Failure(result.failure, host, port, user, auth, attempt.hostId)
                }
            }
        }
    }

    /** known_hosts id of the last attempt, for "Trust and connect". */
    private var trustTarget: String? = null

    private fun hostIdLabel(host: String, port: Int) = if (port == 22) host else "$host:$port"

    fun cancelConnect() {
        connectJob?.cancel()
        _dialog.value = null
    }

    /** "Trust and connect" / "Replace key": stores the presented key and tries again. */
    fun trustAndConnect() {
        val key = when (val d = _dialog.value) {
            is SshDialog.Fingerprint -> d.key
            is SshDialog.ChangedKey -> d.presented
            else -> return
        }
        val hostId = trustTarget ?: return
        val attempt = pending ?: return
        _dialog.value = null
        viewModelScope.launch {
            repository.trust(hostId, key)
            startConnect(attempt)
        }
    }

    fun retry() {
        val attempt = pending ?: return
        val failure = _dialog.value as? SshDialog.Failure
        _dialog.value = null
        viewModelScope.launch {
            failure?.let { _events.send(SshEvent.Message(SshMessage.Retrying(it.host))) }
            startConnect(attempt)
        }
    }

    /** "Edit details" after an authentication rejection. */
    fun editAfterAuthFailure() {
        val failure = _dialog.value as? SshDialog.Failure ?: return
        _dialog.value = null
        val passwordRejected = failure.auth == SshAuth.Password
        if (pending?.fromForm == true) {
            if (passwordRejected) {
                _form.update { it.copy(passwordRejected = true, errors = it.errors + (SshField.Password to SshFieldError.PasswordRejected)) }
            }
            return
        }
        failure.hostId?.let { id -> viewModelScope.launch { _events.send(SshEvent.EditHost(id, passwordRejected)) } }
    }

    fun dismissDialog() {
        if (_dialog.value is SshDialog.Connecting) connectJob?.cancel()
        _dialog.value = null
    }

    private fun SshHostDraft.toTarget() = SshTarget(
        host = host,
        port = port,
        username = username,
        auth = auth,
        password = password?.copyOf(),
        privateKey = key?.bytes?.copyOf(),
        keyPassphrase = keyPassphrase?.copyOf(),
    )

    init {
        // End of session (closed tab, "Disconnect", exit or drop): notice, and back to the list if none are left.
        viewModelScope.launch {
            sessions.ended.collect { end ->
                // Closes the terminal before the notice: the list shows the notice, since it stays on screen.
                if (sessions.sessions.value.isEmpty()) _events.send(SshEvent.CloseTerminal)
                _events.send(SshEvent.Message(if (end.disconnect) SshMessage.Disconnected(end.name) else SshMessage.SessionClosed(end.name)))
            }
        }
    }

    override fun onCleared() {
        connectJob?.cancel()
    }
}
