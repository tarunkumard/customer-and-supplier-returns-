package com.tarun.managemyshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.tarun.managemyshop.data.local.entity.PaymentBreakdownRow
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleItemEntity
import com.tarun.managemyshop.data.local.entity.SaleSummary
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.data.local.entity.TopProductRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {
    @Transaction
    @Query("SELECT * FROM sales ORDER BY saleDate DESC, id DESC")
    fun observeAll(): Flow<List<SaleWithItems>>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :id")
    fun observe(id: Long): Flow<SaleWithItems?>

    @Transaction
    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getWithItems(id: Long): SaleWithItems?

    @Query("SELECT COALESCE(SUM(totalMinor), 0) FROM sales WHERE saleDate >= :start AND saleDate < :end")
    fun observeTotalBetween(start: Long, end: Long): Flow<Long>

    @Query(
        "SELECT * FROM sales WHERE (:query = '' OR invoiceNumber LIKE '%' || :query || '%' " +
            "OR customerName LIKE '%' || :query || '%') " +
            "AND saleDate >= :start AND saleDate < :end " +
            "AND (:paymentMethod IS NULL OR paymentMethod = :paymentMethod) " +
            "ORDER BY saleDate DESC, id DESC"
    )
    fun observeFiltered(query: String, start: Long, end: Long, paymentMethod: String?): Flow<List<SaleEntity>>

    @Query("SELECT * FROM sales ORDER BY saleDate DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SaleEntity>>

    @Query(
        "SELECT paymentMethod AS method, COUNT(*) AS saleCount, SUM(totalMinor) AS totalMinor FROM sales " +
            "WHERE saleDate >= :start AND saleDate < :end GROUP BY paymentMethod ORDER BY totalMinor DESC"
    )
    fun observePaymentBreakdown(start: Long, end: Long): Flow<List<PaymentBreakdownRow>>

    @Query(
        "SELECT i.productId AS productId, MAX(i.productNameSnapshot) AS name, SUM(i.quantity) AS quantity, " +
            "SUM(i.lineTotalMinor) AS revenueMinor FROM sale_items i JOIN sales s ON s.id = i.saleId " +
            "WHERE s.saleDate >= :start AND s.saleDate < :end GROUP BY i.productId " +
            "ORDER BY quantity DESC, revenueMinor DESC LIMIT :limit"
    )
    fun observeTopProducts(start: Long, end: Long, limit: Int): Flow<List<TopProductRow>>

    @Query(
        "SELECT COUNT(*) AS saleCount, COALESCE(SUM(totalMinor), 0) AS totalMinor, " +
            "COALESCE(SUM(totalMinor - taxMinor), 0) AS netMinor, " +
            "COALESCE((SELECT SUM(i.unitCostMinor * i.quantity) FROM sale_items i JOIN sales s2 ON s2.id = i.saleId " +
            "WHERE s2.saleDate >= :start AND s2.saleDate < :end), 0) AS costMinor " +
            "FROM sales WHERE saleDate >= :start AND saleDate < :end"
    )
    fun observeSummary(start: Long, end: Long): Flow<SaleSummary>

    @Query("SELECT MAX(id) FROM sales")
    suspend fun maxId(): Long?

    @Insert
    suspend fun insertSale(sale: SaleEntity): Long

    @Insert
    suspend fun insertItems(items: List<SaleItemEntity>)
}
