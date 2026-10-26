package com.tarun.managemyshop.data.repository

import androidx.room.withTransaction
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import com.tarun.managemyshop.data.local.entity.StockMovementEntity
import com.tarun.managemyshop.domain.model.StockMovementType
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.DeleteResult
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.StockAdjustmentReason
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.validation.ProductValidator
import kotlinx.coroutines.flow.Flow

class ProductRepositoryImpl(private val db: AppDatabase) : ProductRepository {

    private val dao = db.productDao()
    private val adjustmentDao = db.stockAdjustmentDao()
    private val movementDao = db.stockMovementDao()

    override fun observeAll(): Flow<List<ProductEntity>> = dao.observeAll()

    override fun search(query: String): Flow<List<ProductEntity>> = dao.search(query.trim())

    override fun observeLowStock(): Flow<List<ProductEntity>> = dao.observeLowStock()

    override fun observe(id: Long): Flow<ProductEntity?> = dao.observe(id)

    override fun observeAdjustments(productId: Long): Flow<List<StockAdjustmentEntity>> =
        adjustmentDao.observeForProduct(productId)

    override fun observeInventoryValue(): Flow<Long> = dao.observeInventoryValue()

    override suspend fun getById(id: Long): ProductEntity? = dao.getById(id)

    override suspend fun save(product: ProductEntity): SaveResult<Long> {
        val cleaned = product.copy(
            name = product.name.trim(),
            sku = product.sku.trim(),
            category = product.category.trim()
        )
        val errors = ProductValidator.validate(cleaned)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)

        val now = System.currentTimeMillis()
        return if (cleaned.id == 0L) {
            SaveResult.Success(dao.insert(cleaned.copy(createdAt = now, updatedAt = now)))
        } else {
            dao.update(cleaned.copy(updatedAt = now))
            SaveResult.Success(cleaned.id)
        }
    }

    override suspend fun delete(id: Long): DeleteResult = db.withTransaction {
        val uses = dao.countSaleItems(id)
        if (uses > 0) {
            DeleteResult.InUse(uses)
        } else {
            dao.delete(id)
            DeleteResult.Deleted
        }
    }

    override suspend fun adjustStock(
        productId: Long,
        type: AdjustmentType,
        quantity: Int,
        reason: StockAdjustmentReason,
        note: String?
    ): SaveResult<Unit> {
        if (quantity <= 0) return SaveResult.Invalid(mapOf("quantity" to "Enter a quantity of at least 1"))

        return db.withTransaction<SaveResult<Unit>> {
            val product = dao.getById(productId)
                ?: return@withTransaction SaveResult.Invalid(mapOf("quantity" to "Product no longer exists"))
            val delta = if (type == AdjustmentType.INCREASE) quantity else -quantity
            val newStock = product.stockQuantity.toLong() + delta
            if (newStock < 0) {
                return@withTransaction SaveResult.Invalid(
                    mapOf("quantity" to "Only ${product.stockQuantity} in stock")
                )
            }
            if (newStock > Int.MAX_VALUE) {
                return@withTransaction SaveResult.Invalid(mapOf("quantity" to "Quantity is too large"))
            }
            val now = System.currentTimeMillis()
            dao.setStock(productId, newStock.toInt(), now)
            adjustmentDao.insert(
                StockAdjustmentEntity(
                    productId = productId,
                    delta = delta,
                    quantityAfter = newStock.toInt(),
                    reason = reason,
                    note = note?.trim()?.ifEmpty { null },
                    createdAt = now
                )
            )
            movementDao.insert(
                StockMovementEntity(
                    productId = productId,
                    movementType = if (delta > 0) StockMovementType.STOCK_INCREASE else StockMovementType.STOCK_DECREASE,
                    quantityChange = delta,
                    note = reason.label + (note?.trim()?.ifEmpty { null }?.let { ": $it" } ?: ""),
                    createdAt = now
                )
            )
            SaveResult.Success(Unit)
        }
    }
}
