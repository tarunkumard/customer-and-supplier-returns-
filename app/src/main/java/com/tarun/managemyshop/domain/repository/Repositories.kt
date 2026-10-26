package com.tarun.managemyshop.domain.repository

import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnWithItems
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.data.local.entity.RefundSummary
import com.tarun.managemyshop.data.local.entity.ReturnedQuantityRow
import com.tarun.managemyshop.data.local.entity.SaleReturnSummary
import com.tarun.managemyshop.data.local.entity.SupplierCreditSummary
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnWithItems
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.PaymentBreakdownRow
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleSummary
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import com.tarun.managemyshop.data.local.entity.TopProductRow
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.domain.model.CustomerReturnDraft
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.SupplierReturnDraft
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.domain.model.DeleteResult
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.model.StockAdjustmentReason
import kotlinx.coroutines.flow.Flow

interface ProductRepository {
    fun observeAll(): Flow<List<ProductEntity>>
    fun search(query: String): Flow<List<ProductEntity>>
    fun observeLowStock(): Flow<List<ProductEntity>>
    fun observe(id: Long): Flow<ProductEntity?>
    fun observeAdjustments(productId: Long): Flow<List<StockAdjustmentEntity>>
    fun observeInventoryValue(): Flow<Long>
    suspend fun getById(id: Long): ProductEntity?

    /** Inserts when `id == 0`, otherwise updates. Timestamps are managed here. */
    suspend fun save(product: ProductEntity): SaveResult<Long>

    /** Refuses ([DeleteResult.InUse]) when the product is part of any past sale. */
    suspend fun delete(id: Long): DeleteResult

    /** Changes stock by [quantity] and records why; reductions cannot go below zero. */
    suspend fun adjustStock(
        productId: Long,
        type: AdjustmentType,
        quantity: Int,
        reason: StockAdjustmentReason,
        note: String?
    ): SaveResult<Unit>
}

interface SaleRepository {
    fun observe(id: Long): Flow<SaleWithItems?>
    fun observeTotalBetween(start: Long, end: Long): Flow<Long>
    fun observeSummary(start: Long, end: Long): Flow<SaleSummary>
    fun observePaymentBreakdown(start: Long, end: Long): Flow<List<PaymentBreakdownRow>>
    fun observeTopProducts(start: Long, end: Long, limit: Int): Flow<List<TopProductRow>>

    /** Blank [query] matches everything; otherwise matches invoice number or customer name. */
    fun observeFiltered(
        query: String,
        start: Long,
        end: Long,
        paymentMethod: PaymentMethod?
    ): Flow<List<SaleEntity>>

    fun observeRecent(limit: Int): Flow<List<SaleEntity>>

    /**
     * Atomically creates the sale and its items and deducts stock.
     * Fails with [SaveResult.Invalid] (and writes nothing) if stock is insufficient.
     */
    suspend fun recordSale(draft: SaleDraft): SaveResult<Long>
}

interface ReturnRepository {
    // ---- customer returns ----
    fun observeCustomerReturns(
        query: String,
        start: Long,
        end: Long,
        reason: CustomerReturnReason?
    ): Flow<List<CustomerReturnEntity>>

    fun observeCustomerReturn(id: Long): Flow<CustomerReturnWithItems?>
    fun observeCustomerReturnsForSale(saleId: Long): Flow<List<CustomerReturnEntity>>
    fun observeRecentCustomerReturns(limit: Int): Flow<List<CustomerReturnEntity>>

    /** Units of each line of the sale that customers have already returned. */
    fun observeReturnedQuantities(saleId: Long): Flow<List<ReturnedQuantityRow>>

    /** Return status of every sale that has at least one return (sales without returns are absent). */
    fun observeReturnedSaleSummaries(): Flow<List<SaleReturnSummary>>
    fun observeSaleSummary(saleId: Long): Flow<SaleReturnSummary?>

    /** Customer refunds in the period, plus the cost of goods sold they reverse. */
    fun observeRefundSummary(start: Long, end: Long): Flow<RefundSummary>

    /** The number the next customer return will get, e.g. `CR-20261026-0001` (shown as a preview). */
    suspend fun nextCustomerReturnNumber(): String

    /**
     * In ONE transaction: saves the return and its lines, restores stock, writes stock movements.
     * Never touches the original sale. Fails with [SaveResult.Invalid] (writing nothing) if any
     * quantity exceeds what is still returnable.
     */
    suspend fun createCustomerReturn(draft: CustomerReturnDraft): SaveResult<Long>

    // ---- supplier returns ----
    fun observeSupplierReturns(
        query: String,
        start: Long,
        end: Long,
        reason: SupplierReturnReason?
    ): Flow<List<SupplierReturnEntity>>

    fun observeSupplierReturn(id: Long): Flow<SupplierReturnWithItems?>
    fun observeRecentSupplierReturns(limit: Int): Flow<List<SupplierReturnEntity>>
    fun observeSupplierCreditSummary(start: Long, end: Long): Flow<SupplierCreditSummary>
    suspend fun nextSupplierReturnNumber(): String

    /**
     * In ONE transaction: saves the return and its lines, deducts stock, writes negative stock
     * movements and records the supplier credit. Fails with [SaveResult.Invalid] (writing nothing)
     * if a quantity exceeds the stock on hand.
     */
    suspend fun createSupplierReturn(draft: SupplierReturnDraft): SaveResult<Long>
}

interface ExpenseRepository {
    fun observeFiltered(query: String, start: Long, end: Long): Flow<List<ExpenseEntity>>
    fun observeTotalBetween(start: Long, end: Long): Flow<Long>
    suspend fun getById(id: Long): ExpenseEntity?
    suspend fun save(expense: ExpenseEntity): SaveResult<Long>
    suspend fun delete(id: Long)
}

interface ShopSettingsRepository {
    fun observe(): Flow<ShopSettings>
    suspend fun get(): ShopSettings
    suspend fun save(settings: ShopSettings): SaveResult<Unit>
    suspend fun setCurrency(currency: Currency)

    /** First-run sample products are inserted at most once; this flag records that. */
    suspend fun isSampleDataHandled(): Boolean
    suspend fun markSampleDataHandled()

    /** Clears all settings (the sample-data flag stays set). */
    suspend fun resetAll()
}
