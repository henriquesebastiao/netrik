package com.netrik.core.network.ping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TraceEvent {
    /** Started probing hop [hop]. */
    data class Probing(val hop: Int) : TraceEvent

    /** Hop result: [address] null = no reply in time (`* * *`). */
    data class Hop(val hop: Int, val address: String?, val reachedDestination: Boolean, val rttMs: Double?) : TraceEvent

    /** RTT measured afterwards, with a direct ping to the router (the "TTL exceeded" doesn't carry the time). */
    data class HopRtt(val hop: Int, val rttMs: Double?) : TraceEvent

    data class HopName(val hop: Int, val hostname: String) : TraceEvent

    data class Finished(val reachedDestination: Boolean, val hops: Int) : TraceEvent

    /** Ping failed in a way that prevents going on (e.g. network unreachable). */
    data class Failed(val hop: Int, val message: String) : TraceEvent
}

/**
 * Root-free traceroute: a 1-packet ping per TTL, from 1 up to [TracerouteOptions.maxHops].
 * The IP that returns "Time to live exceeded" is the hop; the normal reply is the destination.
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
                    // A router said the destination is unreachable: no point in going on.
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
