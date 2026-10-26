package com.tarun.managemyshop.domain.repository

import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.PaymentBreakdownRow
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleSummary
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import com.tarun.managemyshop.data.local.entity.TopProductRow
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.Currency
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
