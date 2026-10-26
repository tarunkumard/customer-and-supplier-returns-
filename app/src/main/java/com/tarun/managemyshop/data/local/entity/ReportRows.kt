package com.tarun.managemyshop.data.local.entity

import com.tarun.managemyshop.domain.model.PaymentMethod

/**
 * Totals for a period computed by a single query, so count, revenue and cost always agree
 * (separate queries could briefly disagree right after a sale is saved).
 */
data class SaleSummary(
    val saleCount: Int,
    val totalMinor: Long,
    /** Revenue excluding tax. */
    val netMinor: Long,
    /** Purchase cost of the items sold. */
    val costMinor: Long
)

/** Query result: sales grouped by payment method. */
data class PaymentBreakdownRow(
    val method: PaymentMethod,
    val saleCount: Int,
    val totalMinor: Long
)

/** Query result: a product's sold quantity and item revenue (before bill-level discount and tax). */
data class TopProductRow(
    val productId: Long,
    val name: String,
    val quantity: Int,
    val revenueMinor: Long
)
