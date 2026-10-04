package com.netrik.feature.ssh

import com.netrik.core.ssh.HostKey
import com.netrik.core.ssh.PrivateKeyFile
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshField
import com.netrik.core.ssh.SshFailure
import com.netrik.core.ssh.SshGroup
import com.netrik.core.ssh.SshHost

/** Grupo como aparece na lista; [id] nulo é "Sem grupo". */
data class HostGroupUi(
    val id: Long?,
    val name: String?,
    val expanded: Boolean,
    val hosts: List<SshHost>,
    /** Total de hosts do grupo, sem o filtro da busca. */
    val total: Int,
)

data class SshListState(
    val loaded: Boolean = false,
    val groups: List<HostGroupUi> = emptyList(),
    val allGroups: List<SshGroup> = emptyList(),
    val searchOpen: Boolean = false,
    val query: String = "",
    val anyExpanded: Boolean = true,
) {
    val isEmpty: Boolean get() = loaded && allGroups.isEmpty() && groups.isEmpty() && query.isBlank()
    val noMatch: Boolean get() = query.isNotBlank() && groups.isEmpty()
}

object SshHostList {
    /**
     * Monta os grupos da lista. Sem busca: todos os grupos (vazios também) e "Sem grupo" por último,
     * só se tiver hosts. Com busca: só os grupos com resultado, sempre expandidos.
     */
    fun build(groups: List<SshGroup>, hosts: List<SshHost>, query: String, noGroupExpanded: Boolean): List<HostGroupUi> {
        val q = query.trim().lowercase()
        val byGroup = hosts.groupBy { host -> host.groupId?.takeIf { id -> groups.any { it.id == id } } }
        fun matches(host: SshHost) = q.isEmpty() || "${host.name} ${host.username}@${host.host}".lowercase().contains(q)
        val named = groups.map { group ->
            val all = byGroup[group.id].orEmpty()
            HostGroupUi(group.id, group.name, q.isNotEmpty() || group.expanded, all.filter(::matches), all.size)
        }
        val loose = byGroup[null].orEmpty()
        val none = HostGroupUi(null, null, q.isNotEmpty() || noGroupExpanded, loose.filter(::matches), loose.size)
        return (named + none).filter { group ->
            if (q.isNotEmpty()) group.hosts.isNotEmpty() else group.id != null || group.total > 0
        }
    }
}

/** Formulário "Nova conexão" / edição. */
data class SshFormState(
    val editingId: Long? = null,
    /** Aberto como "Nova conexão": continua assim mesmo depois de salvar na primeira tentativa. */
    val isNew: Boolean = true,
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val user: String = "",
    val auth: SshAuth = SshAuth.Password,
    val password: String = "",
    val showPassword: Boolean = false,
    val key: PrivateKeyFile? = null,
    /** Chave já salva (edição), mostrada enquanto o usuário não escolhe outra. */
    val storedKeyName: String? = null,
    val storedKeyInfo: String? = null,
    val keyError: KeyFileError? = null,
    val keyPassphrase: String = "",
    val groupId: Long? = null,
    val save: Boolean = true,
    val errors: Map<SshField, String> = emptyMap(),
    val passphraseError: PassphraseError? = null,
    val passwordRejected: Boolean = false,
    val hasStoredPassword: Boolean = false,
    val hasStoredPassphrase: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null
    val hasKey: Boolean get() = key != null || storedKeyName != null
}

enum class KeyFileError { Invalid, TooLarge, Unreadable }
enum class PassphraseError { Required, Wrong }

/** Diálogos da aba SSH. */
sealed interface SshDialog {
    data class GroupEdit(val groupId: Long?, val value: String) : SshDialog
    data class GroupDelete(val groupId: Long, val name: String, val hostCount: Int) : SshDialog
    data class HostDelete(val hostId: Long, val name: String) : SshDialog
    data class Connecting(val address: String) : SshDialog
    data class Fingerprint(val host: String, val key: HostKey) : SshDialog
    data class ChangedKey(val host: String, val stored: HostKey, val presented: HostKey) : SshDialog
    data class Failure(val failure: SshFailure, val host: String, val port: Int, val user: String, val auth: SshAuth, val hostId: Long?) : SshDialog
}

/** Eventos de uma vez só: avisos e navegação. */
sealed interface SshEvent {
    data class Message(val text: SshMessage) : SshEvent
    data object CloseForm : SshEvent
    /** "Editar dados" depois de uma senha recusada, a partir da lista. */
    data class EditHost(val hostId: Long, val passwordRejected: Boolean) : SshEvent
}

sealed interface SshMessage {
    data class Authenticated(val who: String) : SshMessage
    data class GroupCreated(val name: String) : SshMessage
    data object GroupRenamed : SshMessage
    data object GroupDeleted : SshMessage
    data object HostDeleted : SshMessage
    data class Retrying(val host: String) : SshMessage
}
