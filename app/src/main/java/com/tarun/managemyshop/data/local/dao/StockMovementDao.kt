package com.tarun.managemyshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tarun.managemyshop.data.local.entity.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY createdAt DESC, id DESC LIMIT 100")
    fun observeForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Insert
    suspend fun insert(movement: StockMovementEntity): Long

    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>)
}
