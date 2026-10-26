package com.tarun.managemyshop.domain.model

/** Tax is held in basis points (1800 = 18.00%) so no floating point is needed. */
data class ShopSettings(
    val shopName: String = "My Shop",
    val shopAddress: String? = null,
    val shopPhone: String? = null,
    val currency: Currency = Currency.Default,
    val taxBasisPoints: Int = 0,
    val invoicePrefix: String = "INV"
)
