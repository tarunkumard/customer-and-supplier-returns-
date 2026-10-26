package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Prices are integer minor units (e.g. paise/cents). */
@Entity(tableName = "products", indices = [Index("name"), Index("sku")])
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sku: String = "",
    val category: String = "",
    val sellingPriceMinor: Long,
    val purchasePriceMinor: Long,
    val stockQuantity: Int,
    val lowStockThreshold: Int = 5,
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val isLowStock: Boolean get() = stockQuantity <= lowStockThreshold
}
