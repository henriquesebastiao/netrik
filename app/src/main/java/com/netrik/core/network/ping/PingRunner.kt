package com.netrik.core.network.ping

import com.netrik.core.common.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** Runs the system ping and emits each parsed line, ending with [PingEvent.Exited]. */
interface PingRunner {
    fun run(command: List<String>): Flow<PingEvent>
}

/**
 * Non-root apps can't open raw ICMP sockets; `/system/bin/ping` uses ICMP datagram sockets,
 * permitidos a apps. Cancelar a coleta encerra o processo.
 */
class ProcessPingRunner @Inject constructor(
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : PingRunner {

    override fun run(command: List<String>): Flow<PingEvent> = callbackFlow {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            send(PingEvent.Failure(e.message.orEmpty()))
            send(PingEvent.Exited(-1))
            close()
            return@callbackFlow
        }
        launch(io) {
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> PingOutputParser.parse(line)?.let { trySendBlocking(it) } }
                }
                trySendBlocking(PingEvent.Exited(process.waitFor()))
            } catch (_: IOException) {
                // Stream closed because the process was killed (Stop or leaving the screen).
            } finally {
                channel.close()
            }
        }
        awaitClose { process.destroy() }
    }.flowOn(io)
}
