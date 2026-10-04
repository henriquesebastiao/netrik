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
 * One SSH terminal session: the remote shell (JSch "shell" channel with an xterm-256color pty)
 * wired to a Termux [TerminalEmulator]. The emulator is only touched on the main thread; reading and
 * writing the socket happen on [io]. Nothing that goes through here is logged.
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

    /** Changes on every output received: the View redraws when it changes. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val outgoing = Channel<ByteArray>(Channel.UNLIMITED)
    @Volatile private var channel: ChannelShell? = null
    @Volatile var closedByUser: Boolean = false
        private set

    private val output = object : TerminalOutput() {
        // Replies from the emulator itself (e.g. terminal attribute queries) go to the server.
        override fun write(data: ByteArray, offset: Int, count: Int) {
            outgoing.trySend(data.copyOfRange(offset, offset + count))
        }
        override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
        // OSC 52 and paste requested by the server: ignored, the user pastes through the menu.
        override fun onCopyTextToClipboard(text: String?) = Unit
        override fun onPasteTextFromClipboard() = Unit
        override fun onBell() = Unit
        override fun onColorsChanged() = bump()
    }

    init {
        // The design colors apply from the moment the emulator is created.
        TerminalTheme.install()
    }

    val emulator: TerminalEmulator = TerminalEmulator(output, 80, 24, 10, 20, TRANSCRIPT_ROWS, SilentClient)

    private var ptySize = PtySize(80, 24, 0, 0)

    private data class PtySize(val columns: Int, val rows: Int, val widthPx: Int, val heightPx: Int)

    /** Opens the shell and keeps reading until the server closes it. Returns when the session ends. */
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
                // Channel closed: the reader notices and ends the session.
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
                // Connection dropped or was closed.
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

    /** Pastes honoring "bracketed paste" mode when the remote program asked for it (main thread). */
    fun paste(text: String) = emulator.paste(text)

    /** New size of the text area (main thread); tells the server (remote SIGWINCH). */
    fun resize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        val size = PtySize(columns, rows, columns * cellWidthPx, rows * cellHeightPx)
        if (size == ptySize) return
        ptySize = size
        emulator.resize(columns, rows, cellWidthPx, cellHeightPx)
        bump()
        val current = channel ?: return
        CoroutineScope(io).launch { runCatching { current.setPtySize(size.columns, size.rows, size.widthPx, size.heightPx) } }
    }

    /** Closes at the user's request (tab, "Disconnect"). */
    fun close() {
        closedByUser = true
        // Drops the socket in the background: the reader leaves read() and calls finish().
        CoroutineScope(io).launch {
            channel?.disconnect()
            session.close()
        }
    }

    /** All the text (scrollback + screen), for "Copy output". */
    fun transcript(): String = emulator.screen.transcriptTextWithFullLinesJoined.trimEnd()

    private fun bump() {
        _revision.value = _revision.value + 1
    }

    /** The Termux emulator requires a session client; nothing is logged here. */
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
