package com.netrik.core.network.ping

/** A relevant line of the `/system/bin/ping` (iputils) output. */
sealed interface PingEvent {
    /** `PING google.com (142.250.79.46) 56(84) bytes of data.` */
    data class Header(val host: String, val address: String, val payloadBytes: Int) : PingEvent

    /** `64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.4 ms` */
    data class Reply(val seq: Int, val from: String, val ttl: Int?, val timeMs: Double) : PingEvent

    /** `no answer yet for icmp_seq=3` (-O option): the packet got no reply in time. */
    data class NoAnswer(val seq: Int) : PingEvent

    /** `From 192.168.0.1 icmp_seq=1 Time to live exceeded` */
    data class TtlExceeded(val seq: Int, val from: String) : PingEvent

    /** `From 192.168.0.1 icmp_seq=1 Destination Host Unreachable` e similares. */
    data class Unreachable(val seq: Int, val from: String, val reason: String) : PingEvent

    /** `10 packets transmitted, 9 received, 10% packet loss, time 9012ms` */
    data class Summary(val transmitted: Int, val received: Int) : PingEvent

    /** `ping: unknown host x` */
    data object UnknownHost : PingEvent

    /** `connect: Network is unreachable`, `ping: sendmsg: ...` and other failures of ping itself. */
    data class Failure(val message: String) : PingEvent

    /** The process ended (ping exit code: 0 ok, 1 no reply, 2 error). */
    data class Exited(val code: Int) : PingEvent
}
