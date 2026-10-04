package com.netrik.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.netrik.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Short relative moment, as in the design: "now", "5 min ago", "2 h ago", "yesterday", "Sep 28". */
sealed interface RelativeLabel {
    data object Now : RelativeLabel
    data class MinutesAgo(val minutes: Long) : RelativeLabel
    data class HoursAgo(val hours: Long) : RelativeLabel
    data object Yesterday : RelativeLabel
    /** Older dates, already formatted for the current language ("Sep 28", "28 set"). */
    data class Date(val text: String) : RelativeLabel
}

object RelativeTime {

    fun format(thenMillis: Long, nowMillis: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): RelativeLabel {
        val elapsed = (nowMillis - thenMillis).coerceAtLeast(0)
        val minutes = elapsed / 60_000
        val then = Instant.ofEpochMilli(thenMillis).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when {
            minutes < 1 -> RelativeLabel.Now
            minutes < 60 -> RelativeLabel.MinutesAgo(minutes)
            then == today -> RelativeLabel.HoursAgo(minutes / 60)
            ChronoUnit.DAYS.between(then, today) == 1L -> RelativeLabel.Yesterday
            then.year == today.year -> RelativeLabel.Date(DateTimeFormatter.ofPattern(dayMonthPattern(locale), locale).format(then).replace(".", ""))
            else -> RelativeLabel.Date(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(then))
        }
    }

    /** Full date in the current language ("10/3/26", "03/10/2026"). */
    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))

    /** English puts the month first ("Sep 28"); Portuguese and most others put the day first ("28 set"). */
    private fun dayMonthPattern(locale: Locale): String = if (locale.language == "en") "MMM d" else "d MMM"
}

@Composable
fun relativeTimeText(label: RelativeLabel): String = when (label) {
    RelativeLabel.Now -> stringResource(R.string.time_now)
    is RelativeLabel.MinutesAgo -> stringResource(R.string.time_minutes_ago, label.minutes.toInt())
    is RelativeLabel.HoursAgo -> stringResource(R.string.time_hours_ago, label.hours.toInt())
    RelativeLabel.Yesterday -> stringResource(R.string.time_yesterday)
    is RelativeLabel.Date -> label.text
}
