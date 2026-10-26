package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * No foreign key to products on purpose: deleting a product must not erase sales history,
 * which is why the name and unit price are snapshotted here.
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleEntity::class,
            parentColumns = ["id"],
            childColumns = ["saleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("saleId"), Index("productId")]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val unitPriceMinor: Long,
    /** Purchase price at the time of sale, used for profit; 0 if unknown. */
    val unitCostMinor: Long = 0,
    val quantity: Int,
    val lineTotalMinor: Long
)
