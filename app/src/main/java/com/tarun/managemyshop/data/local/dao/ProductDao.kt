package com.tarun.managemyshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tarun.managemyshop.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query(
        "SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%' " +
            "OR category LIKE '%' || :query || '%' ORDER BY name COLLATE NOCASE"
    )
    fun search(query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE stockQuantity <= lowStockThreshold ORDER BY stockQuantity, name COLLATE NOCASE")
    fun observeLowStock(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: Long): Flow<ProductEntity?>

    @Query("SELECT COUNT(*) FROM sale_items WHERE productId = :id")
    suspend fun countSaleItems(id: Long): Int

    @Query("UPDATE products SET stockQuantity = :quantity, updatedAt = :now WHERE id = :id")
    suspend fun setStock(id: Long, quantity: Int, now: Long)

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT COUNT(*) FROM products")
    suspend fun count(): Int

    @Insert
    suspend fun insert(product: ProductEntity): Long

    @Insert
    suspend fun insertAll(products: List<ProductEntity>)

    @Update
    suspend fun update(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun delete(id: Long)

    /** Atomic guarded decrement; returns 0 rows if there is not enough stock. */
    @Query(
        "UPDATE products SET stockQuantity = stockQuantity - :quantity, updatedAt = :now " +
            "WHERE id = :id AND stockQuantity >= :quantity"
    )
    suspend fun decrementStock(id: Long, quantity: Int, now: Long): Int
}
