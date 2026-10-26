package com.tarun.managemyshop.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tarun.managemyshop.domain.model.StockAdjustmentReason

/** Audit trail of manual stock changes; [delta] is negative for reductions. */
@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("productId")]
)
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val delta: Int,
    val quantityAfter: Int,
    val reason: StockAdjustmentReason,
    val note: String? = null,
    val createdAt: Long
)
