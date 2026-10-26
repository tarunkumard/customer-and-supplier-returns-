package com.tarun.managemyshop.domain.model

enum class CustomerReturnReason(val label: String) {
    DAMAGED("Damaged"),
    EXPIRED("Expired"),
    WRONG_PRODUCT("Wrong product"),
    CUSTOMER_CHANGED_MIND("Customer changed mind"),
    OTHER("Other")
}

enum class SupplierReturnReason(val label: String) {
    DAMAGED("Damaged"),
    EXPIRED("Expired"),
    WRONG_PRODUCT("Wrong product"),
    OTHER("Other")
}

enum class StockMovementType { SALE, CUSTOMER_RETURN, SUPPLIER_RETURN, STOCK_INCREASE, STOCK_DECREASE }

/** How much of an original sale has been returned so far. */
enum class SaleReturnStatus(val label: String) {
    NONE("No returns"),
    PARTIAL("Partially returned"),
    FULL("Fully returned")
}

/** One line of a customer return: how many units of an original sale line come back. */
data class CustomerReturnLine(val originalSaleItemId: Long, val quantity: Int)

data class CustomerReturnDraft(
    val originalSaleId: Long,
    val customerName: String?,
    val refundMethod: PaymentMethod,
    val reason: CustomerReturnReason,
    val notes: String?,
    val lines: List<CustomerReturnLine>
)

data class SupplierReturnLine(val productId: Long, val quantity: Int, val unitCostMinor: Long)

data class SupplierReturnDraft(
    val supplierName: String?,
    val reason: SupplierReturnReason,
    val notes: String?,
    val lines: List<SupplierReturnLine>
)
