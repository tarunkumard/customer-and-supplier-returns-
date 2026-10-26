package com.tarun.managemyshop.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.tarun.managemyshop.domain.model.SaleReturnStatus

data class CustomerReturnWithItems(
    @Embedded val customerReturn: CustomerReturnEntity,
    @Relation(parentColumn = "id", entityColumn = "customerReturnId")
    val items: List<CustomerReturnItemEntity>
)

data class SupplierReturnWithItems(
    @Embedded val supplierReturn: SupplierReturnEntity,
    @Relation(parentColumn = "id", entityColumn = "supplierReturnId")
    val items: List<SupplierReturnItemEntity>
)

/** Query result: units of one original sale line that have already come back. */
data class ReturnedQuantityRow(val originalSaleItemId: Long, val quantity: Int)

/** Query result: how much of a sale has been returned, for badges and the Create Return action. */
data class SaleReturnSummary(
    val saleId: Long,
    val soldQuantity: Int,
    val returnedQuantity: Int,
    val refundMinor: Long
) {
    val status: SaleReturnStatus
        get() = when {
            returnedQuantity <= 0 -> SaleReturnStatus.NONE
            returnedQuantity >= soldQuantity -> SaleReturnStatus.FULL
            else -> SaleReturnStatus.PARTIAL
        }
}

/** Customer refunds for a period; [costMinor] is the cost of goods sold that the returns reverse. */
data class RefundSummary(
    val returnCount: Int,
    val totalMinor: Long,
    /** Refunded revenue excluding tax (after discount share). */
    val subtotalMinor: Long,
    val costMinor: Long
)

data class SupplierCreditSummary(val returnCount: Int, val totalMinor: Long)
