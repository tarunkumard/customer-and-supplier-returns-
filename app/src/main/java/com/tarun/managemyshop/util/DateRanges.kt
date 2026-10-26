package com.tarun.managemyshop.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Half-open epoch-millis range: start inclusive, end exclusive. */
data class MillisRange(val start: Long, val end: Long)

object DateRanges {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun day(date: LocalDate): MillisRange =
        MillisRange(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )

    fun monthOf(date: LocalDate): MillisRange {
        val first = date.withDayOfMonth(1)
        return MillisRange(
            first.atStartOfDay(zone).toInstant().toEpochMilli(),
            first.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        )
    }

    /** Emits today's date now and again each time the date changes, so "today" never goes stale. */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            val today = LocalDate.now(zone)
            emit(today)
            val untilMidnight = day(today).end - System.currentTimeMillis()
            delay(untilMidnight.coerceAtLeast(1_000) + 500)
        }
    }

    private val dateTimeFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun formatDateTime(millis: Long): String =
        dateTimeFormat.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun formatDate(millis: Long): String =
        dateFormat.format(Instant.ofEpochMilli(millis).atZone(zone))
}
