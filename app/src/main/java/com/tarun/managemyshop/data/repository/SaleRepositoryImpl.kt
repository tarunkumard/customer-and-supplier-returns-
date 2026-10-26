package com.tarun.managemyshop.data.repository

import androidx.room.withTransaction
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.PaymentBreakdownRow
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleItemEntity
import com.tarun.managemyshop.data.local.entity.SaleSummary
import com.tarun.managemyshop.data.local.entity.StockMovementEntity
import com.tarun.managemyshop.domain.model.StockMovementType
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.data.local.entity.TopProductRow
import com.tarun.managemyshop.domain.calc.InvoiceNumber
import com.tarun.managemyshop.domain.calc.SaleCalculator
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import com.tarun.managemyshop.domain.validation.SaleValidator
import kotlinx.coroutines.flow.Flow

class SaleRepositoryImpl(
    private val db: AppDatabase,
    private val settingsRepository: ShopSettingsRepository
) : SaleRepository {

    private val saleDao = db.saleDao()
    private val productDao = db.productDao()
    private val movementDao = db.stockMovementDao()

    override fun observe(id: Long): Flow<SaleWithItems?> = saleDao.observe(id)

    override fun observeTotalBetween(start: Long, end: Long): Flow<Long> =
        saleDao.observeTotalBetween(start, end)

    override fun observeFiltered(
        query: String,
        start: Long,
        end: Long,
        paymentMethod: PaymentMethod?
    ): Flow<List<SaleEntity>> = saleDao.observeFiltered(query, start, end, paymentMethod?.name)

    override fun observeSummary(start: Long, end: Long): Flow<SaleSummary> = saleDao.observeSummary(start, end)

    override fun observePaymentBreakdown(start: Long, end: Long): Flow<List<PaymentBreakdownRow>> =
        saleDao.observePaymentBreakdown(start, end)

    override fun observeTopProducts(start: Long, end: Long, limit: Int): Flow<List<TopProductRow>> =
        saleDao.observeTopProducts(start, end, limit)

    override fun observeRecent(limit: Int): Flow<List<SaleEntity>> = saleDao.observeRecent(limit)

    override suspend fun recordSale(draft: SaleDraft): SaveResult<Long> {
        val errors = SaleValidator.validate(draft)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val settings = settingsRepository.get()
        return db.withTransaction<SaveResult<Long>> {
            val now = System.currentTimeMillis()

            // Merge duplicate lines so stock is checked against the combined quantity.
            val quantities = draft.lines.groupBy({ it.productId }, { it.quantity }).mapValues { it.value.sum() }
            val items = mutableListOf<SaleItemEntity>()
            for ((productId, quantity) in quantities) {
                val product = productDao.getById(productId)
                    ?: return@withTransaction SaveResult.Invalid(mapOf("lines" to "A product no longer exists"))
                if (product.stockQuantity < quantity) {
                    return@withTransaction SaveResult.Invalid(
                        mapOf("lines" to "Only ${product.stockQuantity} of ${product.name} in stock")
                    )
                }
                items += SaleItemEntity(
                    saleId = 0,
                    productId = productId,
                    productNameSnapshot = product.name,
                    unitPriceMinor = product.sellingPriceMinor,
                    unitCostMinor = product.purchasePriceMinor,
                    quantity = quantity,
                    lineTotalMinor = SaleCalculator.lineTotal(product.sellingPriceMinor, quantity)
                )
            }

            val subtotal = items.sumOf { it.lineTotalMinor }
            if (draft.discountMinor > subtotal) {
                return@withTransaction SaveResult.Invalid(mapOf("discount" to "Discount cannot exceed the subtotal"))
            }
            val totals = SaleCalculator.calculate(subtotal, draft.discountMinor, settings.taxBasisPoints)

            val invoiceNumber = InvoiceNumber.format(settings.invoicePrefix, now, (saleDao.maxId() ?: 0) + 1)
            val saleId = saleDao.insertSale(
                SaleEntity(
                    invoiceNumber = invoiceNumber,
                    customerName = draft.customerName?.trim()?.ifEmpty { null },
                    saleDate = now,
                    subtotalMinor = totals.subtotalMinor,
                    discountMinor = totals.discountMinor,
                    taxMinor = totals.taxMinor,
                    totalMinor = totals.totalMinor,
                    paymentMethod = draft.paymentMethod,
                    notes = draft.notes?.trim()?.ifEmpty { null }
                )
            )
            saleDao.insertItems(items.map { it.copy(saleId = saleId) })
            items.forEach {
                // Throwing rolls the whole transaction back.
                check(productDao.decrementStock(it.productId, it.quantity, now) == 1) {
                    "Stock changed while recording sale"
                }
            }
            movementDao.insertAll(
                items.map {
                    StockMovementEntity(
                        productId = it.productId,
                        movementType = StockMovementType.SALE,
                        quantityChange = -it.quantity,
                        referenceId = saleId,
                        note = "Sale $invoiceNumber",
                        createdAt = now
                    )
                }
            )
            SaveResult.Success(saleId)
        }
    }
}
