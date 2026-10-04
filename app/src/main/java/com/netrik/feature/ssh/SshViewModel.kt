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
 * Estado da aba SSH (lista de hosts e formulário), compartilhado pelas telas do grafo da aba.
 * Nesta etapa a conexão termina na autenticação; o terminal entra na Etapa 7.
 */
@HiltViewModel
class SshViewModel @Inject constructor(
    private val repository: SshRepository,
    private val connector: SshConnector,
    private val keyReader: KeyFileReader,
    private val sessions: SshSessionManager,
    private val terminalPrefs: TerminalPreferences,
) : ViewModel() {

    /** Sessões de terminal abertas (faixa "sessões ativas", ponto verde nos hosts, abas do terminal). */
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

    /** Entrada da pilha cujo formulário já foi preparado (evita resetar ao recompor). */
    private var formEntry: String? = null
    private var connectJob: Job? = null
    /** Última tentativa, para repetir depois de confiar na chave ou conceder a permissão. */
    private var pending: Pending? = null

    private data class SearchState(val open: Boolean = false, val query: String = "")

    /** [hostId] salvo (lista ou formulário com "Salvar conexão") ou [draft] avulso (só conectar). */
    private class Pending(val hostId: Long?, val draft: SshHostDraft?, val fromForm: Boolean)

    // Lista

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

    /** Toque num host: com sessão aberta, volta para ela; senão conecta. */
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

    // Formulário

    /** Prepara o formulário uma vez por entrada da pilha: novo (com [target] opcional) ou edição de [hostId]. */
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
                errors = if (passwordRejected) mapOf(SshField.Password to REJECTED) else emptyMap(),
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
        // Mexer num campo limpa o erro dele.
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

    /** "Salvar e conectar" / "Conectar". [connect] falso: só salva (edição). */
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
        // Chave cifrada: confere a senha antes de ir à rede.
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
            // Daqui em diante o formulário edita o host salvo: tentar de novo não duplica.
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

    // Conexão

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

    /** Identificador no known_hosts da última tentativa, para "Confiar e conectar". */
    private var trustTarget: String? = null

    private fun hostIdLabel(host: String, port: Int) = if (port == 22) host else "$host:$port"

    fun cancelConnect() {
        connectJob?.cancel()
        _dialog.value = null
    }

    /** "Confiar e conectar" / "Substituir chave": grava a chave apresentada e tenta de novo. */
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

    /** "Editar dados" depois de uma recusa de autenticação. */
    fun editAfterAuthFailure() {
        val failure = _dialog.value as? SshDialog.Failure ?: return
        _dialog.value = null
        val passwordRejected = failure.auth == SshAuth.Password
        if (pending?.fromForm == true) {
            if (passwordRejected) {
                _form.update { it.copy(passwordRejected = true, errors = it.errors + (SshField.Password to REJECTED)) }
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
        // Fim de sessão (aba fechada, "Desconectar", exit ou queda): aviso, e volta à lista se acabaram.
        viewModelScope.launch {
            sessions.ended.collect { end ->
                // Fecha o terminal antes do aviso: quem mostra o aviso é a lista, que continua na tela.
                if (sessions.sessions.value.isEmpty()) _events.send(SshEvent.CloseTerminal)
                _events.send(SshEvent.Message(if (end.disconnect) SshMessage.Disconnected(end.name) else SshMessage.SessionClosed(end.name)))
            }
        }
    }

    override fun onCleared() {
        connectJob?.cancel()
    }

    companion object {
        const val REJECTED = "Senha recusada pelo servidor"
    }
}
