package com.tarun.managemyshop.domain.model

data class SaleLine(val productId: Long, val quantity: Int)

/** What the UI submits to record a sale; prices and totals are computed by the repository. */
data class SaleDraft(
    val lines: List<SaleLine>,
    val customerName: String? = null,
    val discountMinor: Long = 0,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val notes: String? = null
)
