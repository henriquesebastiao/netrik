package com.netrik.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RelativeTimeTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = LocalDateTime.of(2026, 10, 3, 14, 32).atZone(zone).toInstant().toEpochMilli()

    private fun ago(minutes: Long) = now - minutes * 60_000

    @Test
    fun `intervalos curtos`() {
        assertEquals("agora", RelativeTime.format(ago(0), now, zone))
        assertEquals("há 5 min", RelativeTime.format(ago(5), now, zone))
        assertEquals("há 2 h", RelativeTime.format(ago(130), now, zone))
    }

    @Test
    fun `dias anteriores`() {
        assertEquals("ontem", RelativeTime.format(ago(60 * 20), now, zone))
        val sept28 = LocalDateTime.of(2026, 9, 28, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("28 set", RelativeTime.format(sept28, now, zone))
        val lastYear = LocalDateTime.of(2025, 12, 1, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("1 dez 2025", RelativeTime.format(lastYear, now, zone))
    }

    @Test
    fun `relógio adiantado não gera tempo negativo`() {
        assertEquals("agora", RelativeTime.format(now + 60_000, now, zone))
    }

    @Test
    fun `data no formato brasileiro`() {
        assertEquals("03/10/2026", RelativeTime.date(LocalDate.of(2026, 10, 3)))
    }
}
