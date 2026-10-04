package com.netrik.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class RelativeTimeTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = LocalDateTime.of(2026, 10, 3, 14, 32).atZone(zone).toInstant().toEpochMilli()
    private val english = Locale.US
    private val portuguese = Locale.forLanguageTag("pt-BR")

    private fun ago(minutes: Long) = now - minutes * 60_000

    @Test
    fun `short intervals`() {
        assertEquals(RelativeLabel.Now, RelativeTime.format(ago(0), now, zone))
        assertEquals(RelativeLabel.MinutesAgo(5), RelativeTime.format(ago(5), now, zone))
        assertEquals(RelativeLabel.HoursAgo(2), RelativeTime.format(ago(130), now, zone))
    }

    @Test
    fun `earlier days follow the language`() {
        assertEquals(RelativeLabel.Yesterday, RelativeTime.format(ago(60 * 20), now, zone))
        val sept28 = LocalDateTime.of(2026, 9, 28, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(RelativeLabel.Date("Sep 28"), RelativeTime.format(sept28, now, zone, english))
        assertEquals(RelativeLabel.Date("28 set"), RelativeTime.format(sept28, now, zone, portuguese))
        val lastYear = LocalDateTime.of(2025, 12, 1, 10, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(RelativeLabel.Date("Dec 1, 2025"), RelativeTime.format(lastYear, now, zone, english))
    }

    @Test
    fun `clock ahead does not produce negative time`() {
        assertEquals(RelativeLabel.Now, RelativeTime.format(now + 60_000, now, zone))
    }

    @Test
    fun `full date in the language format`() {
        assertEquals("03/10/2026", RelativeTime.date(LocalDate.of(2026, 10, 3), portuguese))
        assertEquals("10/3/26", RelativeTime.date(LocalDate.of(2026, 10, 3), english))
    }
}
