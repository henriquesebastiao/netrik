package com.netrik.core.network.ping

/** Uma linha relevante da saída do `/system/bin/ping` (iputils). */
sealed interface PingEvent {
    /** `PING google.com (142.250.79.46) 56(84) bytes of data.` */
    data class Header(val host: String, val address: String, val payloadBytes: Int) : PingEvent

    /** `64 bytes from 8.8.8.8: icmp_seq=1 ttl=117 time=12.4 ms` */
    data class Reply(val seq: Int, val from: String, val ttl: Int?, val timeMs: Double) : PingEvent

    /** `no answer yet for icmp_seq=3` (opção -O): o pacote não teve resposta no tempo. */
    data class NoAnswer(val seq: Int) : PingEvent

    /** `From 192.168.0.1 icmp_seq=1 Time to live exceeded` */
    data class TtlExceeded(val seq: Int, val from: String) : PingEvent

    /** `From 192.168.0.1 icmp_seq=1 Destination Host Unreachable` e similares. */
    data class Unreachable(val seq: Int, val from: String, val reason: String) : PingEvent

    /** `10 packets transmitted, 9 received, 10% packet loss, time 9012ms` */
    data class Summary(val transmitted: Int, val received: Int) : PingEvent

    /** `ping: unknown host x` */
    data object UnknownHost : PingEvent

    /** `connect: Network is unreachable`, `ping: sendmsg: ...` e outras falhas do próprio ping. */
    data class Failure(val message: String) : PingEvent

    /** O processo terminou (código de saída do ping: 0 ok, 1 sem resposta, 2 erro). */
    data class Exited(val code: Int) : PingEvent
}
