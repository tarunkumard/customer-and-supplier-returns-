package com.tarun.managemyshop.domain.calc

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object InvoiceNumber {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)

    /** e.g. `INV-20261026-0042`: prefix, local sale date, then the running sale number. */
    fun format(
        prefix: String,
        saleDateMillis: Long,
        sequence: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val date = dateFormat.format(Instant.ofEpochMilli(saleDateMillis).atZone(zone))
        return String.format(Locale.ROOT, "%s-%s-%04d", prefix, date, sequence)
    }
}
