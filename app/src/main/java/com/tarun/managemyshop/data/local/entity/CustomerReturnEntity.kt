package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.PaymentMethod

/**
 * A customer bringing goods back. The original sale is never edited: refunds are separate rows,
 * so the sale history stays intact. [subtotalRefundMinor] is after the sale's discount share and
 * excludes tax; [totalRefundMinor] = subtotal + tax.
 */
@Entity(
    tableName = "customer_returns",
    indices = [Index(value = ["returnNumber"], unique = true), Index("originalSaleId"), Index("returnDate")]
)
data class CustomerReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnNumber: String,
    val originalSaleId: Long,
    /** Snapshot so the return still reads correctly on its own. */
    val originalInvoiceNumber: String,
    val returnDate: Long,
    val customerName: String? = null,
    val refundMethod: PaymentMethod,
    val reason: CustomerReturnReason,
    val notes: String? = null,
    val subtotalRefundMinor: Long,
    val taxRefundMinor: Long,
    val totalRefundMinor: Long
)
