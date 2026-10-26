package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tarun.managemyshop.domain.model.StockMovementType

/**
 * Append-only ledger of every stock change. [quantityChange] is positive when stock is added and
 * negative when removed; [referenceId] is the id of the sale / return / (null for manual changes).
 */
@Entity(tableName = "stock_movements", indices = [Index("productId"), Index("createdAt")])
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val movementType: StockMovementType,
    val quantityChange: Int,
    val referenceId: Long? = null,
    val note: String? = null,
    val createdAt: Long
)
