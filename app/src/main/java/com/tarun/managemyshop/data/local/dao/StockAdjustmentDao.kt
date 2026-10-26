package com.tarun.managemyshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {
    @Query("SELECT * FROM stock_adjustments WHERE productId = :productId ORDER BY createdAt DESC, id DESC LIMIT 50")
    fun observeForProduct(productId: Long): Flow<List<StockAdjustmentEntity>>

    @Insert
    suspend fun insert(adjustment: StockAdjustmentEntity): Long
}
