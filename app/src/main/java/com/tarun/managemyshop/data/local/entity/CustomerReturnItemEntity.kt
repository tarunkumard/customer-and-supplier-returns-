package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** No product foreign key on purpose: return history must survive product changes. */
@Entity(
    tableName = "customer_return_items",
    foreignKeys = [
        ForeignKey(
            entity = CustomerReturnEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerReturnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("customerReturnId"), Index("originalSaleItemId"), Index("productId")]
)
data class CustomerReturnItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerReturnId: Long,
    val originalSaleItemId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val quantityReturned: Int,
    val unitPriceMinor: Long,
    /** Purchase cost snapshotted from the original sale line; reverses cost of goods sold. */
    val unitCostMinor: Long,
    /** Gross value of the returned units (unit price × quantity), before the discount/tax share. */
    val lineRefundMinor: Long
)
