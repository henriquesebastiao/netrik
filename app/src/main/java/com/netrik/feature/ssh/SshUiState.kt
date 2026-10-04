package com.netrik.feature.ssh

import com.netrik.core.ssh.HostKey
import com.netrik.core.ssh.PrivateKeyFile
import com.netrik.core.ssh.SshAuth
import com.netrik.core.ssh.SshField
import com.netrik.core.ssh.SshFieldError
import com.netrik.core.ssh.SshFailure
import com.netrik.core.ssh.SshGroup
import com.netrik.core.ssh.SshHost

/** Group as it shows in the list; a null [id] is "No group". */
data class HostGroupUi(
    val id: Long?,
    val name: String?,
    val expanded: Boolean,
    val hosts: List<SshHost>,
    /** Total hosts in the group, without the search filter. */
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
     * Builds the list groups. Without search: all groups (empty ones too) and "No group" last,
     * only if it has hosts. With search: only groups with results, always expanded.
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

/** "New connection" / edit form. */
data class SshFormState(
    val editingId: Long? = null,
    /** Opened as "New connection": stays that way even after saving on the first attempt. */
    val isNew: Boolean = true,
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val user: String = "",
    val auth: SshAuth = SshAuth.Password,
    val password: String = "",
    val showPassword: Boolean = false,
    val key: PrivateKeyFile? = null,
    /** Key already saved (editing), shown until the user picks another. */
    val storedKeyName: String? = null,
    val storedKeyInfo: String? = null,
    val keyError: KeyFileError? = null,
    val keyPassphrase: String = "",
    val groupId: Long? = null,
    val save: Boolean = true,
    val errors: Map<SshField, SshFieldError> = emptyMap(),
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

/** SSH tab dialogs. */
sealed interface SshDialog {
    data class GroupEdit(val groupId: Long?, val value: String) : SshDialog
    data class GroupDelete(val groupId: Long, val name: String, val hostCount: Int) : SshDialog
    data class HostDelete(val hostId: Long, val name: String) : SshDialog
    data class Connecting(val address: String) : SshDialog
    data class Fingerprint(val host: String, val key: HostKey) : SshDialog
    data class ChangedKey(val host: String, val stored: HostKey, val presented: HostKey) : SshDialog
    data class Failure(val failure: SshFailure, val host: String, val port: Int, val user: String, val auth: SshAuth, val hostId: Long?) : SshDialog
}

/** One-off events: notices and navigation. */
sealed interface SshEvent {
    data class Message(val text: SshMessage) : SshEvent
    data object CloseForm : SshEvent
    /** "Edit details" after a rejected password, from the list. */
    data class EditHost(val hostId: Long, val passwordRejected: Boolean) : SshEvent
    /** Authenticated (or tapped a host with an open session): show the terminal. */
    data object OpenTerminal : SshEvent
    /** The last session ended: leave the terminal. */
    data object CloseTerminal : SshEvent
}

sealed interface SshMessage {
    data class SessionClosed(val name: String) : SshMessage
    data class Disconnected(val name: String) : SshMessage
    data class GroupCreated(val name: String) : SshMessage
    data object GroupRenamed : SshMessage
    data object GroupDeleted : SshMessage
    data object HostDeleted : SshMessage
    data class Retrying(val host: String) : SshMessage
}
