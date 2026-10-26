package com.tarun.managemyshop.domain.calc

object SaleCalculator {
    data class Totals(
        val subtotalMinor: Long,
        val discountMinor: Long,
        val taxMinor: Long,
        val totalMinor: Long
    )

    /** Tax applies to (subtotal - discount), rounded half-up to the nearest minor unit. */
    fun calculate(subtotalMinor: Long, discountMinor: Long, taxBasisPoints: Int): Totals {
        val taxable = (subtotalMinor - discountMinor).coerceAtLeast(0)
        val tax = (taxable * taxBasisPoints + 5_000) / 10_000
        return Totals(subtotalMinor, discountMinor, tax, taxable + tax)
    }

    fun lineTotal(unitPriceMinor: Long, quantity: Int): Long = unitPriceMinor * quantity
}
