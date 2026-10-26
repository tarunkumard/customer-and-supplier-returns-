package com.tarun.managemyshop.data

import androidx.room.withTransaction
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository

/**
 * First-run setup. A DataStore flag records that sample products were considered once, so they
 * are inserted only into a brand-new empty database and never reappear after the owner deletes them.
 */
object DatabaseSeeder {

    suspend fun seedIfNeeded(db: AppDatabase, settings: ShopSettingsRepository) {
        if (settings.isSampleDataHandled()) return
        db.withTransaction {
            if (db.productDao().count() == 0) {
                val now = System.currentTimeMillis()
                db.productDao().insertAll(samples.map { it.copy(createdAt = now, updatedAt = now) })
            }
        }
        settings.markSampleDataHandled()
    }

    private val samples = listOf(
        sample("Basmati Rice 1kg", "8901001", "Grocery", 14500, 11800, 40, 10),
        sample("Sunflower Oil 1L", "8901002", "Grocery", 18900, 16200, 25, 8),
        sample("Tea Powder 250g", "8901003", "Beverages", 12000, 9400, 6, 10),
        sample("Notebook A4", "8901004", "Stationery", 6000, 4000, 60, 15),
        sample("Ball Pen (Blue)", "8901005", "Stationery", 1000, 600, 3, 20),
        sample("Bath Soap", "8901006", "Personal Care", 4500, 3200, 35, 10)
    )

    private fun sample(
        name: String, sku: String, category: String,
        sellingMinor: Long, purchaseMinor: Long, stock: Int, lowAt: Int
    ) = ProductEntity(
        name = name, sku = sku, category = category,
        sellingPriceMinor = sellingMinor, purchasePriceMinor = purchaseMinor,
        stockQuantity = stock, lowStockThreshold = lowAt
    )
}
