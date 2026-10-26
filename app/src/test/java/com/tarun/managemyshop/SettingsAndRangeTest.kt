package com.tarun.managemyshop

import com.tarun.managemyshop.util.DateFilter
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.Percent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SettingsAndRangeTest {
    @Test fun percentParsing() {
        assertEquals(1800, Percent.parseToBasisPoints("18"))
        assertEquals(550, Percent.parseToBasisPoints("5.5"))
        assertEquals(10_000, Percent.parseToBasisPoints("100"))
        assertNull(Percent.parseToBasisPoints("100.01"))
        assertNull(Percent.parseToBasisPoints("-1"))
        assertNull(Percent.parseToBasisPoints("abc"))
        assertEquals("5.5", Percent.toPlainString(550))
        assertEquals("18", Percent.toPlainString(1800))
        assertEquals("0", Percent.toPlainString(0))
    }

    @Test fun weekRangeStartsOnConfiguredFirstDayAndSpansSevenDays() {
        val wednesday = LocalDate.of(2026, 10, 28)
        val range = DateFilter(DatePreset.WEEK).toRange(wednesday, DayOfWeek.MONDAY)
        assertEquals(DateRanges.day(LocalDate.of(2026, 10, 26)).start, range.start)
        assertEquals(DateRanges.day(LocalDate.of(2026, 11, 1)).end, range.end)
    }

    @Test fun customRangeIsInclusiveOfEndDay() {
        val f = DateFilter.custom(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 5))
        val range = f.toRange(LocalDate.of(2026, 10, 28))
        assertEquals(DateRanges.day(LocalDate.of(2026, 3, 5)).start, range.start)
        assertEquals(DateRanges.day(LocalDate.of(2026, 3, 10)).end, range.end)
    }
}
