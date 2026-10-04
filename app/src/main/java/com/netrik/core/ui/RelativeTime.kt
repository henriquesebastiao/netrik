package com.netrik.core.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Momento relativo curto, como no design: "agora", "há 5 min", "há 2 h", "ontem", "28 set". */
object RelativeTime {

    private val ptBr = Locale.forLanguageTag("pt-BR")
    private val sameYear = DateTimeFormatter.ofPattern("d MMM", ptBr)
    private val otherYear = DateTimeFormatter.ofPattern("d MMM yyyy", ptBr)

    fun format(thenMillis: Long, nowMillis: Long, zone: ZoneId): String {
        val elapsed = (nowMillis - thenMillis).coerceAtLeast(0)
        val minutes = elapsed / 60_000
        val then = Instant.ofEpochMilli(thenMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when {
            minutes < 1 -> "agora"
            minutes < 60 -> "há $minutes min"
            then == today -> "há ${minutes / 60} h"
            ChronoUnit.DAYS.between(then, today) == 1L -> "ontem"
            then.year == today.year -> sameYear.format(then).removeSuffix(".")
            else -> otherYear.format(then).replace(".", "")
        }
    }

    /** "03/10/2026" */
    fun date(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
}
