package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tarun.managemyshop.domain.model.SupplierReturnReason

/** Goods sent back to a wholesaler. Not a customer refund: it is credit owed to the shop. */
@Entity(tableName = "supplier_returns", indices = [Index(value = ["returnNumber"], unique = true), Index("returnDate")])
data class SupplierReturnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val returnNumber: String,
    val returnDate: Long,
    val supplierName: String? = null,
    val reason: SupplierReturnReason,
    val notes: String? = null,
    val totalSupplierCreditMinor: Long
)
