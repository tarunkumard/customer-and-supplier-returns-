package com.tarun.managemyshop.data.repository

import androidx.room.withTransaction
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnItemEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnWithItems
import com.tarun.managemyshop.data.local.entity.RefundSummary
import com.tarun.managemyshop.data.local.entity.ReturnedQuantityRow
import com.tarun.managemyshop.data.local.entity.SaleReturnSummary
import com.tarun.managemyshop.data.local.entity.StockMovementEntity
import com.tarun.managemyshop.data.local.entity.SupplierCreditSummary
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnItemEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnWithItems
import com.tarun.managemyshop.domain.calc.InvoiceNumber
import com.tarun.managemyshop.domain.calc.ReturnCalculator
import com.tarun.managemyshop.domain.model.CustomerReturnDraft
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.StockMovementType
import com.tarun.managemyshop.domain.model.SupplierReturnDraft
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.domain.repository.ReturnRepository
import kotlinx.coroutines.flow.Flow

class ReturnRepositoryImpl(private val db: AppDatabase) : ReturnRepository {

    private val returnDao = db.returnDao()
    private val saleDao = db.saleDao()
    private val productDao = db.productDao()
    private val movementDao = db.stockMovementDao()

    override fun observeCustomerReturns(
        query: String,
        start: Long,
        end: Long,
        reason: CustomerReturnReason?
    ): Flow<List<CustomerReturnEntity>> = returnDao.observeCustomerReturns(query, start, end, reason?.name)

    override fun observeCustomerReturn(id: Long): Flow<CustomerReturnWithItems?> = returnDao.observeCustomerReturn(id)

    override fun observeCustomerReturnsForSale(saleId: Long): Flow<List<CustomerReturnEntity>> =
        returnDao.observeCustomerReturnsForSale(saleId)

    override fun observeRecentCustomerReturns(limit: Int): Flow<List<CustomerReturnEntity>> =
        returnDao.observeRecentCustomerReturns(limit)

    override fun observeReturnedQuantities(saleId: Long): Flow<List<ReturnedQuantityRow>> =
        returnDao.observeReturnedQuantities(saleId)

    override fun observeReturnedSaleSummaries(): Flow<List<SaleReturnSummary>> = returnDao.observeReturnedSaleSummaries()

    override fun observeSaleSummary(saleId: Long): Flow<SaleReturnSummary?> = returnDao.observeSaleSummary(saleId)

    override fun observeRefundSummary(start: Long, end: Long): Flow<RefundSummary> =
        returnDao.observeRefundSummary(start, end)

    override suspend fun nextCustomerReturnNumber(): String =
        InvoiceNumber.format(CUSTOMER_PREFIX, System.currentTimeMillis(), (returnDao.maxCustomerReturnId() ?: 0) + 1)

    override suspend fun createCustomerReturn(draft: CustomerReturnDraft): SaveResult<Long> {
        val requested = draft.lines.filter { it.quantity != 0 }
            .groupBy({ it.originalSaleItemId }, { it.quantity }).mapValues { it.value.sum() }
        if (requested.isEmpty() || requested.values.any { it < 0 }) {
            return SaveResult.Invalid(mapOf("lines" to "Choose at least one item to return"))
        }

        return db.withTransaction<SaveResult<Long>> {
            val sale = saleDao.getWithItems(draft.originalSaleId)
                ?: return@withTransaction SaveResult.Invalid(mapOf("sale" to "The original sale no longer exists"))
            val saleItems = sale.items.associateBy { it.id }
            val alreadyReturned = returnDao.returnedQuantities(sale.sale.id).associate { it.originalSaleItemId to it.quantity }

            val items = mutableListOf<CustomerReturnItemEntity>()
            for ((saleItemId, quantity) in requested) {
                val saleItem = saleItems[saleItemId]
                    ?: return@withTransaction SaveResult.Invalid(mapOf("lines" to "An item does not belong to this sale"))
                val returnable = saleItem.quantity - (alreadyReturned[saleItemId] ?: 0)
                if (quantity > returnable) {
                    return@withTransaction SaveResult.Invalid(
                        mapOf("lines" to "Only $returnable of ${saleItem.productNameSnapshot} can still be returned")
                    )
                }
                items += CustomerReturnItemEntity(
                    customerReturnId = 0,
                    originalSaleItemId = saleItemId,
                    productId = saleItem.productId,
                    productNameSnapshot = saleItem.productNameSnapshot,
                    quantityReturned = quantity,
                    unitPriceMinor = saleItem.unitPriceMinor,
                    unitCostMinor = saleItem.unitCostMinor,
                    lineRefundMinor = saleItem.unitPriceMinor * quantity
                )
            }
            for (item in items) {
                if (productDao.getById(item.productId) == null) {
                    return@withTransaction SaveResult.Invalid(
                        mapOf("lines" to "${item.productNameSnapshot} no longer exists, so its stock cannot be restored")
                    )
                }
            }

            val refund = ReturnCalculator.incrementalRefund(
                subtotalMinor = sale.sale.subtotalMinor,
                discountMinor = sale.sale.discountMinor,
                taxMinor = sale.sale.taxMinor,
                previousGrossMinor = returnDao.grossReturnedMinor(sale.sale.id),
                newGrossMinor = items.sumOf { it.lineRefundMinor }
            )

            val now = System.currentTimeMillis()
            val number = InvoiceNumber.format(CUSTOMER_PREFIX, now, (returnDao.maxCustomerReturnId() ?: 0) + 1)
            val returnId = returnDao.insertCustomerReturn(
                CustomerReturnEntity(
                    returnNumber = number,
                    originalSaleId = sale.sale.id,
                    originalInvoiceNumber = sale.sale.invoiceNumber,
                    returnDate = now,
                    customerName = draft.customerName?.trim()?.ifEmpty { null },
                    refundMethod = draft.refundMethod,
                    reason = draft.reason,
                    notes = draft.notes?.trim()?.ifEmpty { null },
                    subtotalRefundMinor = refund.subtotalMinor,
                    taxRefundMinor = refund.taxMinor,
                    totalRefundMinor = refund.totalMinor
                )
            )
            returnDao.insertCustomerReturnItems(items.map { it.copy(customerReturnId = returnId) })
            items.forEach {
                // Throwing rolls the whole transaction back.
                check(productDao.incrementStock(it.productId, it.quantityReturned, now) == 1) {
                    "Product disappeared while recording return"
                }
            }
            movementDao.insertAll(
                items.map {
                    StockMovementEntity(
                        productId = it.productId,
                        movementType = StockMovementType.CUSTOMER_RETURN,
                        quantityChange = it.quantityReturned,
                        referenceId = returnId,
                        note = "Customer return $number (${draft.reason.label})",
                        createdAt = now
                    )
                }
            )
            SaveResult.Success(returnId)
        }
    }

    override fun observeSupplierReturns(
        query: String,
        start: Long,
        end: Long,
        reason: SupplierReturnReason?
    ): Flow<List<SupplierReturnEntity>> = returnDao.observeSupplierReturns(query, start, end, reason?.name)

    override fun observeSupplierReturn(id: Long): Flow<SupplierReturnWithItems?> = returnDao.observeSupplierReturn(id)

    override fun observeRecentSupplierReturns(limit: Int): Flow<List<SupplierReturnEntity>> =
        returnDao.observeRecentSupplierReturns(limit)

    override fun observeSupplierCreditSummary(start: Long, end: Long): Flow<SupplierCreditSummary> =
        returnDao.observeSupplierCreditSummary(start, end)

    override suspend fun nextSupplierReturnNumber(): String =
        InvoiceNumber.format(SUPPLIER_PREFIX, System.currentTimeMillis(), (returnDao.maxSupplierReturnId() ?: 0) + 1)

    override suspend fun createSupplierReturn(draft: SupplierReturnDraft): SaveResult<Long> {
        val lines = draft.lines.filter { it.quantity != 0 }
        if (lines.isEmpty() || lines.any { it.quantity < 0 }) {
            return SaveResult.Invalid(mapOf("lines" to "Add at least one product with a quantity"))
        }
        if (lines.any { it.unitCostMinor < 0 }) {
            return SaveResult.Invalid(mapOf("lines" to "Unit cost cannot be negative"))
        }

        return db.withTransaction<SaveResult<Long>> {
            val items = mutableListOf<SupplierReturnItemEntity>()
            // Stock is checked against the combined quantity when a product appears on several lines.
            val perProduct = lines.groupBy({ it.productId }, { it.quantity }).mapValues { it.value.sum() }
            val products = HashMap<Long, String>()
            for ((productId, quantity) in perProduct) {
                val product = productDao.getById(productId)
                    ?: return@withTransaction SaveResult.Invalid(mapOf("lines" to "A product no longer exists"))
                if (quantity > product.stockQuantity) {
                    return@withTransaction SaveResult.Invalid(
                        mapOf("lines" to "Only ${product.stockQuantity} of ${product.name} in stock")
                    )
                }
                products[productId] = product.name
            }
            lines.forEach {
                items += SupplierReturnItemEntity(
                    supplierReturnId = 0,
                    productId = it.productId,
                    productNameSnapshot = products.getValue(it.productId),
                    quantityReturned = it.quantity,
                    unitCostMinor = it.unitCostMinor,
                    lineSupplierCreditMinor = it.unitCostMinor * it.quantity
                )
            }

            val now = System.currentTimeMillis()
            val number = InvoiceNumber.format(SUPPLIER_PREFIX, now, (returnDao.maxSupplierReturnId() ?: 0) + 1)
            val returnId = returnDao.insertSupplierReturn(
                SupplierReturnEntity(
                    returnNumber = number,
                    returnDate = now,
                    supplierName = draft.supplierName?.trim()?.ifEmpty { null },
                    reason = draft.reason,
                    notes = draft.notes?.trim()?.ifEmpty { null },
                    totalSupplierCreditMinor = items.sumOf { it.lineSupplierCreditMinor }
                )
            )
            returnDao.insertSupplierReturnItems(items.map { it.copy(supplierReturnId = returnId) })
            perProduct.forEach { (productId, quantity) ->
                check(productDao.decrementStock(productId, quantity, now) == 1) {
                    "Stock changed while recording supplier return"
                }
            }
            movementDao.insertAll(
                items.map {
                    StockMovementEntity(
                        productId = it.productId,
                        movementType = StockMovementType.SUPPLIER_RETURN,
                        quantityChange = -it.quantityReturned,
                        referenceId = returnId,
                        note = "Return to supplier $number (${draft.reason.label})",
                        createdAt = now
                    )
                }
            )
            SaveResult.Success(returnId)
        }
    }

    private companion object {
        const val CUSTOMER_PREFIX = "CR"
        const val SUPPLIER_PREFIX = "SR"
    }
}
