package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "supplier_return_items",
    foreignKeys = [
        ForeignKey(
            entity = SupplierReturnEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierReturnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("supplierReturnId"), Index("productId")]
)
data class SupplierReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val supplierReturnId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantityReturned: Int,
    val unitCostMinor: Long,
    val lineSupplierCreditMinor: Long
)
