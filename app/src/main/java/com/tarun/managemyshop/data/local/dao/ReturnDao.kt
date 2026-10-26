package com.tarun.managemyshop.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnItemEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnWithItems
import com.tarun.managemyshop.data.local.entity.RefundSummary
import com.tarun.managemyshop.data.local.entity.ReturnedQuantityRow
import com.tarun.managemyshop.data.local.entity.SaleReturnSummary
import com.tarun.managemyshop.data.local.entity.SupplierCreditSummary
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnItemEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnWithItems
import kotlinx.coroutines.flow.Flow

private const val SALE_SUMMARY =
    "SELECT s.id AS saleId, " +
        "COALESCE((SELECT SUM(si.quantity) FROM sale_items si WHERE si.saleId = s.id), 0) AS soldQuantity, " +
        "COALESCE((SELECT SUM(i.quantityReturned) FROM customer_return_items i " +
        "JOIN customer_returns r ON r.id = i.customerReturnId WHERE r.originalSaleId = s.id), 0) AS returnedQuantity, " +
        "COALESCE((SELECT SUM(r2.totalRefundMinor) FROM customer_returns r2 WHERE r2.originalSaleId = s.id), 0) AS refundMinor " +
        "FROM sales s"

@Dao
interface ReturnDao {
    // ---- customer returns ----

    @Query(
        "SELECT * FROM customer_returns WHERE (:query = '' OR returnNumber LIKE '%' || :query || '%' " +
            "OR originalInvoiceNumber LIKE '%' || :query || '%' OR customerName LIKE '%' || :query || '%') " +
            "AND returnDate >= :start AND returnDate < :end AND (:reason IS NULL OR reason = :reason) " +
            "ORDER BY returnDate DESC, id DESC"
    )
    fun observeCustomerReturns(query: String, start: Long, end: Long, reason: String?): Flow<List<CustomerReturnEntity>>

    @Transaction
    @Query("SELECT * FROM customer_returns WHERE id = :id")
    fun observeCustomerReturn(id: Long): Flow<CustomerReturnWithItems?>

    @Query("SELECT * FROM customer_returns WHERE originalSaleId = :saleId ORDER BY returnDate DESC, id DESC")
    fun observeCustomerReturnsForSale(saleId: Long): Flow<List<CustomerReturnEntity>>

    @Query("SELECT * FROM customer_returns ORDER BY returnDate DESC, id DESC LIMIT :limit")
    fun observeRecentCustomerReturns(limit: Int): Flow<List<CustomerReturnEntity>>

    @Query(
        "SELECT i.originalSaleItemId AS originalSaleItemId, SUM(i.quantityReturned) AS quantity " +
            "FROM customer_return_items i JOIN customer_returns r ON r.id = i.customerReturnId " +
            "WHERE r.originalSaleId = :saleId GROUP BY i.originalSaleItemId"
    )
    fun observeReturnedQuantities(saleId: Long): Flow<List<ReturnedQuantityRow>>

    @Query(
        "SELECT i.originalSaleItemId AS originalSaleItemId, SUM(i.quantityReturned) AS quantity " +
            "FROM customer_return_items i JOIN customer_returns r ON r.id = i.customerReturnId " +
            "WHERE r.originalSaleId = :saleId GROUP BY i.originalSaleItemId"
    )
    suspend fun returnedQuantities(saleId: Long): List<ReturnedQuantityRow>

    @Query(
        "SELECT COALESCE(SUM(i.lineRefundMinor), 0) FROM customer_return_items i " +
            "JOIN customer_returns r ON r.id = i.customerReturnId WHERE r.originalSaleId = :saleId"
    )
    suspend fun grossReturnedMinor(saleId: Long): Long

    @Query(SALE_SUMMARY + " WHERE s.id IN (SELECT originalSaleId FROM customer_returns)")
    fun observeReturnedSaleSummaries(): Flow<List<SaleReturnSummary>>

    @Query(SALE_SUMMARY + " WHERE s.id = :saleId")
    fun observeSaleSummary(saleId: Long): Flow<SaleReturnSummary?>

    @Query(
        "SELECT COUNT(*) AS returnCount, COALESCE(SUM(totalRefundMinor), 0) AS totalMinor, " +
            "COALESCE(SUM(subtotalRefundMinor), 0) AS subtotalMinor, " +
            "COALESCE((SELECT SUM(i.unitCostMinor * i.quantityReturned) FROM customer_return_items i " +
            "JOIN customer_returns r2 ON r2.id = i.customerReturnId " +
            "WHERE r2.returnDate >= :start AND r2.returnDate < :end), 0) AS costMinor " +
            "FROM customer_returns WHERE returnDate >= :start AND returnDate < :end"
    )
    fun observeRefundSummary(start: Long, end: Long): Flow<RefundSummary>

    @Query("SELECT MAX(id) FROM customer_returns")
    suspend fun maxCustomerReturnId(): Long?

    @Insert
    suspend fun insertCustomerReturn(entity: CustomerReturnEntity): Long

    @Insert
    suspend fun insertCustomerReturnItems(items: List<CustomerReturnItemEntity>)

    // ---- supplier returns ----

    @Query(
        "SELECT * FROM supplier_returns WHERE (:query = '' OR returnNumber LIKE '%' || :query || '%' " +
            "OR supplierName LIKE '%' || :query || '%') " +
            "AND returnDate >= :start AND returnDate < :end AND (:reason IS NULL OR reason = :reason) " +
            "ORDER BY returnDate DESC, id DESC"
    )
    fun observeSupplierReturns(query: String, start: Long, end: Long, reason: String?): Flow<List<SupplierReturnEntity>>

    @Transaction
    @Query("SELECT * FROM supplier_returns WHERE id = :id")
    fun observeSupplierReturn(id: Long): Flow<SupplierReturnWithItems?>

    @Query("SELECT * FROM supplier_returns ORDER BY returnDate DESC, id DESC LIMIT :limit")
    fun observeRecentSupplierReturns(limit: Int): Flow<List<SupplierReturnEntity>>

    @Query(
        "SELECT COUNT(*) AS returnCount, COALESCE(SUM(totalSupplierCreditMinor), 0) AS totalMinor " +
            "FROM supplier_returns WHERE returnDate >= :start AND returnDate < :end"
    )
    fun observeSupplierCreditSummary(start: Long, end: Long): Flow<SupplierCreditSummary>

    @Query("SELECT MAX(id) FROM supplier_returns")
    suspend fun maxSupplierReturnId(): Long?

    @Insert
    suspend fun insertSupplierReturn(entity: SupplierReturnEntity): Long

    @Insert
    suspend fun insertSupplierReturnItems(items: List<SupplierReturnItemEntity>)
}
