package com.netrik.core.network.ping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TraceEvent {
    /** Começou a sondar o salto [hop]. */
    data class Probing(val hop: Int) : TraceEvent

    /** Resultado do salto: [address] null = sem resposta no tempo (`* * *`). */
    data class Hop(val hop: Int, val address: String?, val reachedDestination: Boolean, val rttMs: Double?) : TraceEvent

    /** RTT medido depois, com ping direto ao roteador (o "TTL excedido" não traz o tempo). */
    data class HopRtt(val hop: Int, val rttMs: Double?) : TraceEvent

    data class HopName(val hop: Int, val hostname: String) : TraceEvent

    data class Finished(val reachedDestination: Boolean, val hops: Int) : TraceEvent

    /** O ping falhou de forma que impede continuar (ex.: rede inalcançável). */
    data class Failed(val hop: Int, val message: String) : TraceEvent
}

/**
 * Traceroute sem root: um ping de 1 pacote por TTL, de 1 até [TracerouteOptions.maxHops].
 * O IP que devolve "Time to live exceeded" é o salto; a resposta normal é o destino.
 */
class Traceroute @Inject constructor(
    private val runner: PingRunner,
    private val resolver: HostResolver,
) {

    fun run(host: ResolvedHost, options: TracerouteOptions): Flow<TraceEvent> = channelFlow {
        for (ttl in 1..options.maxHops) {
            send(TraceEvent.Probing(ttl))
            val events = runner.run(PingCommand.probe(host.address, host.ipv6, ttl, options.hopTimeoutSeconds)).toList()

            val failure = events.filterIsInstance<PingEvent.Failure>().firstOrNull()
            val reply = events.filterIsInstance<PingEvent.Reply>().firstOrNull()
            val exceeded = events.filterIsInstance<PingEvent.TtlExceeded>().firstOrNull()
            val unreachable = events.filterIsInstance<PingEvent.Unreachable>().firstOrNull()

            when {
                reply != null -> {
                    send(TraceEvent.Hop(ttl, reply.from, reachedDestination = true, rttMs = reply.timeMs))
                    lookupName(ttl, reply.from)
                    send(TraceEvent.Finished(reachedDestination = true, hops = ttl))
                    return@channelFlow
                }
                exceeded != null -> {
                    send(TraceEvent.Hop(ttl, exceeded.from, reachedDestination = false, rttMs = null))
                    measureRtt(ttl, exceeded.from, host.ipv6, options.hopTimeoutSeconds)
                    lookupName(ttl, exceeded.from)
                }
                unreachable != null -> {
                    // Um roteador avisou que o destino é inalcançável: não adianta seguir.
                    send(TraceEvent.Hop(ttl, unreachable.from, reachedDestination = false, rttMs = null))
                    lookupName(ttl, unreachable.from)
                    send(TraceEvent.Finished(reachedDestination = false, hops = ttl))
                    return@channelFlow
                }
                failure != null -> {
                    send(TraceEvent.Failed(ttl, failure.message))
                    return@channelFlow
                }
                else -> send(TraceEvent.Hop(ttl, null, reachedDestination = false, rttMs = null))
            }
        }
        send(TraceEvent.Finished(reachedDestination = false, hops = options.maxHops))
    }

    private fun kotlinx.coroutines.channels.ProducerScope<TraceEvent>.measureRtt(hop: Int, address: String, ipv6: Boolean, timeout: Int) {
        launch {
            val reply = runner.run(PingCommand.probe(address, ipv6, ttl = null, timeoutSeconds = timeout))
                .toList().filterIsInstance<PingEvent.Reply>().firstOrNull()
            send(TraceEvent.HopRtt(hop, reply?.timeMs))
        }
    }

    private fun kotlinx.coroutines.channels.ProducerScope<TraceEvent>.lookupName(hop: Int, address: String) {
        launch { resolver.reverse(address)?.let { send(TraceEvent.HopName(hop, it)) } }
    }
}
