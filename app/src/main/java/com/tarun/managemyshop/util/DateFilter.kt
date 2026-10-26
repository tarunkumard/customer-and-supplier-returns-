package com.tarun.managemyshop.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class DatePreset(val label: String) {
    ALL("All time"),
    TODAY("Today"),
    WEEK("This week"),
    MONTH("This month"),
    CUSTOM("Custom")
}

data class DateFilter(
    val preset: DatePreset = DatePreset.ALL,
    val from: LocalDate? = null,
    val to: LocalDate? = null
) {
    fun toRange(today: LocalDate = LocalDate.now(), firstDay: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek): MillisRange =
        when (preset) {
            DatePreset.ALL -> MillisRange(Long.MIN_VALUE, Long.MAX_VALUE)
            DatePreset.TODAY -> DateRanges.day(today)
            DatePreset.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(firstDay))
                MillisRange(DateRanges.day(start).start, DateRanges.day(start.plusDays(6)).end)
            }
            DatePreset.MONTH -> DateRanges.monthOf(today)
            DatePreset.CUSTOM -> {
                val start = from ?: today
                MillisRange(DateRanges.day(start).start, DateRanges.day(to ?: start).end)
            }
        }

    companion object {
        fun custom(a: LocalDate, b: LocalDate) = DateFilter(DatePreset.CUSTOM, minOf(a, b), maxOf(a, b))
    }
}
