package com.netrik.core.terminal

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.netrik.core.common.IoDispatcher
import com.netrik.core.ssh.SshSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Fim de uma sessão: [byUser] distingue "Desconectar"/fechar aba de queda ou `exit` no servidor. */
data class SessionEnded(val name: String, val byUser: Boolean, val disconnect: Boolean)

/**
 * Sessões de terminal abertas, vivas enquanto o processo vive (independem da tela). Enquanto houver
 * alguma, o [SshSessionService] fica em primeiro plano para o Android não derrubar as conexões.
 */
@Singleton
class SshSessionManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var nextId = 1L

    private val _sessions = MutableStateFlow<List<SshTerminal>>(emptyList())
    val sessions: StateFlow<List<SshTerminal>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow<Long?>(null)
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    private val _ended = MutableSharedFlow<SessionEnded>(extraBufferCapacity = 8)
    val ended: SharedFlow<SessionEnded> = _ended.asSharedFlow()

    /** Pedidos de fechamento que vieram de "Desconectar" (para o texto do aviso). */
    private val disconnecting = mutableSetOf<Long>()

    /** Abre o shell numa sessão já autenticada e torna a aba ativa. */
    fun open(session: SshSession, hostId: Long?, name: String, address: String): SshTerminal {
        val terminal = SshTerminal(nextId++, hostId, name, address, session, io)
        _sessions.update { it + terminal }
        _activeId.value = terminal.id
        ContextCompat.startForegroundService(context, Intent(context, SshSessionService::class.java))
        scope.launch {
            terminal.run(this)
            remove(terminal)
        }
        return terminal
    }

    fun forHost(hostId: Long): SshTerminal? = _sessions.value.firstOrNull { it.hostId == hostId }

    fun find(id: Long): SshTerminal? = _sessions.value.firstOrNull { it.id == id }

    fun setActive(id: Long) {
        if (find(id) != null) _activeId.value = id
    }

    /** Fecha a aba (ícone ×) ou desconecta ([disconnect] muda só o texto do aviso). */
    fun close(id: Long, disconnect: Boolean = false) {
        val terminal = find(id) ?: return
        if (disconnect) disconnecting += id
        terminal.close()
    }

    fun closeAll() = _sessions.value.forEach { it.close() }

    private fun remove(terminal: SshTerminal) {
        val list = _sessions.value
        val index = list.indexOfFirst { it.id == terminal.id }
        if (index < 0) return
        val remaining = list.filterNot { it.id == terminal.id }
        val activeIndex = list.indexOfFirst { it.id == _activeId.value }.coerceAtLeast(0)
        _sessions.value = remaining
        _activeId.value = remaining.getOrNull(activeAfterClose(activeIndex, index, remaining.size))?.id
        _ended.tryEmit(SessionEnded(terminal.name, terminal.closedByUser, disconnecting.remove(terminal.id)))
    }
}
