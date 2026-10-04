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

/** Executa o ping do sistema e emite cada linha interpretada, terminando com [PingEvent.Exited]. */
interface PingRunner {
    fun run(command: List<String>): Flow<PingEvent>
}

/**
 * Apps sem root não abrem socket ICMP raw; o `/system/bin/ping` usa sockets ICMP de datagrama,
 * permitidos a apps. Cancelar a coleta encerra o processo.
 */
class ProcessPingRunner @Inject constructor(
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : PingRunner {

    override fun run(command: List<String>): Flow<PingEvent> = callbackFlow {
        val process = try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (e: IOException) {
            send(PingEvent.Failure(e.message ?: "Falha ao executar o ping"))
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
                // Fluxo fechado porque o processo foi encerrado (Parar ou sair da tela).
            } finally {
                channel.close()
            }
        }
        awaitClose { process.destroy() }
    }.flowOn(io)
}
