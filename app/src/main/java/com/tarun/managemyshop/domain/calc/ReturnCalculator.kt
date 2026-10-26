package com.tarun.managemyshop.domain.calc

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Refund maths for customer returns. A sale's bill-level discount and tax are shared out pro rata
 * over the goods returned. Amounts are computed cumulatively (everything returned so far) and a
 * return refunds the difference, so rounding never drifts and returning every unit refunds
 * exactly what the customer paid.
 */
object ReturnCalculator {

    data class Refund(val subtotalMinor: Long, val taxMinor: Long) {
        val totalMinor: Long get() = subtotalMinor + taxMinor
    }

    /**
     * Refund owed for [grossReturnedMinor] worth of returned lines (price × quantity, before discount).
     * [subtotalMinor], [discountMinor] and [taxMinor] are the original sale's figures.
     * The returned `subtotalMinor` is after the discount share and excludes tax.
     */
    fun cumulativeRefund(
        subtotalMinor: Long,
        discountMinor: Long,
        taxMinor: Long,
        grossReturnedMinor: Long
    ): Refund {
        if (subtotalMinor <= 0 || grossReturnedMinor <= 0) return Refund(0, 0)
        val gross = grossReturnedMinor.coerceAtMost(subtotalMinor)
        val discountShare = prorate(discountMinor, gross, subtotalMinor)
        return Refund(gross - discountShare, prorate(taxMinor, gross, subtotalMinor))
    }

    /** Refund for returning [newGrossMinor] more, given [previousGrossMinor] was already returned. */
    fun incrementalRefund(
        subtotalMinor: Long,
        discountMinor: Long,
        taxMinor: Long,
        previousGrossMinor: Long,
        newGrossMinor: Long
    ): Refund {
        val before = cumulativeRefund(subtotalMinor, discountMinor, taxMinor, previousGrossMinor)
        val after = cumulativeRefund(subtotalMinor, discountMinor, taxMinor, previousGrossMinor + newGrossMinor)
        return Refund(after.subtotalMinor - before.subtotalMinor, after.taxMinor - before.taxMinor)
    }

    private fun prorate(amount: Long, part: Long, whole: Long): Long =
        BigDecimal(amount).multiply(BigDecimal(part)).divide(BigDecimal(whole), 0, RoundingMode.HALF_UP).toLong()
}
