package com.netrik.core.terminal

import com.jcraft.jsch.ChannelShell
import com.netrik.core.ssh.SshSession
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Uma sessão de terminal SSH: o shell remoto (canal "shell" do JSch com pty xterm-256color)
 * ligado a um [TerminalEmulator] do Termux. O emulador só é tocado na thread principal; leitura e
 * escrita no socket ficam em [io]. Nada do que passa por aqui vai para log.
 */
class SshTerminal internal constructor(
    val id: Long,
    val hostId: Long?,
    val name: String,
    val address: String,
    private val session: SshSession,
    private val io: CoroutineDispatcher,
) {
    enum class Status { Connecting, Connected, Closed }

    private val _status = MutableStateFlow(Status.Connecting)
    val status: StateFlow<Status> = _status.asStateFlow()

    /** Muda a cada saída recebida: a View redesenha quando ele muda. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
    @Volatile private var channel: ChannelShell? = null
    @Volatile var closedByUser: Boolean = false
        private set

    private val output = object : TerminalOutput() {
        // Respostas do próprio emulador (ex.: consulta de atributos do terminal) vão para o servidor.
        override fun write(data: ByteArray, offset: Int, count: Int) {
            outgoing.trySend(data.copyOfRange(offset, offset + count))
        }
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        // OSC 52 e colar pedido pelo servidor: ignorados, o usuário cola pelo menu.
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = bump()
    }

    init {
        // As cores do design valem desde a criação do emulador.
        TerminalTheme.install()
    }

    val emulator: TerminalEmulator = TerminalEmulator(output, 80, 24, 10, 20, TRANSCRIPT_ROWS, SilentClient)

    private var ptySize = PtySize(80, 24, 0, 0)

    private data class PtySize(val columns: Int, val rows: Int, val widthPx: Int, val heightPx: Int)

    /** Abre o shell e fica lendo até o servidor encerrar. Retorna quando a sessão acaba. */
    suspend fun run(scope: CoroutineScope) {
        val opened = withContext(io) {
            try {
                (session.session.openChannel("shell") as ChannelShell).apply {
                    setPtyType("xterm-256color")
                    ptySize.let { setPtySize(it.columns, it.rows, it.widthPx, it.heightPx) }
                    connect(CONNECT_TIMEOUT_MS)
                }
            } catch (_: Exception) {
                null
            }
        } ?: return finish()
        channel = opened
        _status.value = Status.Connected
        val writer = scope.launch(io) {
            val out = opened.outputStream
            try {
                for (bytes in outgoing) {
                    out.write(bytes)
                    out.flush()
                }
            } catch (_: IOException) {
                // Canal fechado: o leitor percebe e encerra a sessão.
            }
        }
        withContext(io) {
            val input = opened.inputStream
            val buffer = ByteArray(BUFFER_SIZE)
            try {
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    val chunk = buffer.copyOf(n)
                    withContext(Dispatchers.Main.immediate) {
                        emulator.append(chunk, chunk.size)
                        bump()
                    }
                }
            } catch (_: IOException) {
                // Conexão caiu ou foi fechada.
            }
        }
        writer.cancel()
        finish()
    }

    private fun finish() {
        outgoing.close()
        _status.value = Status.Closed
        channel?.disconnect()
        session.close()
        bump()
    }

    /** Teclado, barra extra e "Colar". */
    fun send(bytes: ByteArray) {
        if (bytes.isNotEmpty()) outgoing.trySend(bytes)
    }

    /** Cola respeitando o modo "bracketed paste" quando o programa remoto pediu (thread principal). */
    fun paste(text: String) = emulator.paste(text)

    /** Novo tamanho da área de texto (thread principal); avisa o servidor (SIGWINCH remoto). */
    fun resize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        val size = PtySize(columns, rows, columns * cellWidthPx, rows * cellHeightPx)
        if (size == ptySize) return
        ptySize = size
        emulator.resize(columns, rows, cellWidthPx, cellHeightPx)
        bump()
        val current = channel ?: return
        CoroutineScope(io).launch { runCatching { current.setPtySize(size.columns, size.rows, size.widthPx, size.heightPx) } }
    }

    /** Fecha a pedido do usuário (aba, "Desconectar"). */
    fun close() {
        closedByUser = true
        // Derruba o socket em segundo plano: o leitor sai do read e chama finish().
        CoroutineScope(io).launch {
            channel?.disconnect()
            session.close()
        }
    }

    /** Todo o texto (histórico + tela), para "Copiar saída". */
    fun transcript(): String = emulator.screen.transcriptTextWithFullLinesJoined.trimEnd()

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    /** O emulador do Termux pede um cliente de sessão; aqui nada é registrado em log. */
    private object SilentClient : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) = Unit
        override fun onTitleChanged(changedSession: TerminalSession) = Unit
        override fun onSessionFinished(finishedSession: TerminalSession) = Unit
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit
        override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
        override fun onBell(session: TerminalSession) = Unit
        override fun onColorsChanged(session: TerminalSession) = Unit
        override fun onTerminalCursorStateChange(state: Boolean) = Unit
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) = Unit
        override fun logWarn(tag: String?, message: String?) = Unit
        override fun logInfo(tag: String?, message: String?) = Unit
        override fun logDebug(tag: String?, message: String?) = Unit
        override fun logVerbose(tag: String?, message: String?) = Unit
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
        override fun logStackTrace(tag: String?, e: Exception?) = Unit
    }

    companion object {
        const val TRANSCRIPT_ROWS = 2000
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val BUFFER_SIZE = 8192
    }
}
