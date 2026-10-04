package com.netrik.core.network.ping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Linhas reais do /system/bin/ping (iputils) do Android 16/17, capturadas no emulador e de documentação do iputils. */
class PingOutputParserTest {

    @Test
    fun `cabeçalho com nome e IP resolvido`() {
        assertEquals(
            PingEvent.Header("google.com", "172.217.29.206", 56),
            PingOutputParser.parse("PING google.com (172.217.29.206) 56(84) bytes of data."),
        )
        assertEquals(PingEvent.Header("::1", "::1", 56), PingOutputParser.parse("PING ::1(::1) 56 data bytes"))
    }

    @Test
    fun `respostas IPv4 e IPv6`() {
        assertEquals(
            PingEvent.Reply(seq = 2, from = "8.8.8.8", ttl = 117, timeMs = 12.4),
            PingOutputParser.parse("64 bytes from 8.8.8.8: icmp_seq=2 ttl=117 time=12.4 ms"),
        )
        assertEquals(
            PingEvent.Reply(seq = 1, from = "::1", ttl = 64, timeMs = 0.081),
            PingOutputParser.parse("64 bytes from ::1: icmp_seq=1 ttl=64 time=0.081 ms"),
        )
        assertEquals(
            PingEvent.Reply(seq = 7, from = "10.0.2.2", ttl = 255, timeMs = 1.67),
            PingOutputParser.parse("[1791102514.651882] 64 bytes from 10.0.2.2: icmp_seq=7 ttl=255 time=1.67 ms"),
        )
    }

    @Test
    fun `pacote sem resposta (opção -O)`() {
        assertEquals(PingEvent.NoAnswer(3), PingOutputParser.parse("no answer yet for icmp_seq=3"))
    }

    @Test
    fun `TTL excedido e destino inalcançável`() {
        assertEquals(
            PingEvent.TtlExceeded(seq = 1, from = "192.168.0.1"),
            PingOutputParser.parse("From 192.168.0.1 icmp_seq=1 Time to live exceeded"),
        )
        assertEquals(
            PingEvent.TtlExceeded(seq = 1, from = "2804:14d::1"),
            PingOutputParser.parse("From 2804:14d::1 icmp_seq=1 Time exceeded: Hop limit"),
        )
        assertEquals(
            PingEvent.Unreachable(seq = 4, from = "192.168.0.42", reason = "Destination Host Unreachable"),
            PingOutputParser.parse("From 192.168.0.42 icmp_seq=4 Destination Host Unreachable"),
        )
    }

    @Test
    fun `resumo final`() {
        assertEquals(
            PingEvent.Summary(transmitted = 10, received = 9),
            PingOutputParser.parse("10 packets transmitted, 9 received, 10% packet loss, time 9012ms"),
        )
        assertEquals(
            PingEvent.Summary(transmitted = 3, received = 0),
            PingOutputParser.parse("3 packets transmitted, 0 received, +3 errors, 100% packet loss, time 2031ms"),
        )
    }

    @Test
    fun `erros do ping`() {
        assertEquals(PingEvent.UnknownHost, PingOutputParser.parse("ping: unknown host nao-existe.invalid"))
        assertEquals(PingEvent.Failure("Network is unreachable"), PingOutputParser.parse("connect: Network is unreachable"))
        assertEquals(PingEvent.Failure("sendmsg: Operation not permitted"), PingOutputParser.parse("ping: sendmsg: Operation not permitted"))
    }

    @Test
    fun `linhas irrelevantes são ignoradas`() {
        assertNull(PingOutputParser.parse(""))
        assertNull(PingOutputParser.parse("--- 8.8.8.8 ping statistics ---"))
        assertNull(PingOutputParser.parse("rtt min/avg/max/mdev = 130.598/328.493/509.384/155.105 ms, pipe 2"))
    }
}
