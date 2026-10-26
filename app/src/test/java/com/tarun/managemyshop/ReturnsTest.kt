package com.tarun.managemyshop

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.Migrations
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.preferences.ShopSettingsRepositoryImpl
import com.tarun.managemyshop.data.repository.ProductRepositoryImpl
import com.tarun.managemyshop.data.repository.ReturnRepositoryImpl
import com.tarun.managemyshop.data.repository.SaleRepositoryImpl
import com.tarun.managemyshop.domain.calc.ReturnCalculator
import com.tarun.managemyshop.domain.model.CustomerReturnDraft
import com.tarun.managemyshop.domain.model.CustomerReturnLine
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaleLine
import com.tarun.managemyshop.domain.model.SaleReturnStatus
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.StockMovementType
import com.tarun.managemyshop.domain.model.SupplierReturnDraft
import com.tarun.managemyshop.domain.model.SupplierReturnLine
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReturnsTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepositoryImpl
    private lateinit var sales: SaleRepositoryImpl
    private lateinit var returns: ReturnRepositoryImpl
    private val all = Long.MIN_VALUE to Long.MAX_VALUE

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val settings = ShopSettingsRepositoryImpl(context)
        runBlocking { settings.resetAll() }
        products = ProductRepositoryImpl(db)
        sales = SaleRepositoryImpl(db, settings)
        returns = ReturnRepositoryImpl(db)
    }

    @After fun tearDown() = db.close()

    private suspend fun addProduct(name: String = "Rice", price: Long = 1_000, cost: Long = 600, stock: Int = 10): Long =
        (products.save(ProductEntity(name = name, sellingPriceMinor = price, purchasePriceMinor = cost, stockQuantity = stock))
            as SaveResult.Success).value

    private suspend fun sell(productId: Long, qty: Int): Long =
        (sales.recordSale(SaleDraft(listOf(SaleLine(productId, qty)))) as SaveResult.Success).value

    private fun draft(saleId: Long, itemId: Long, qty: Int, reason: CustomerReturnReason = CustomerReturnReason.DAMAGED) =
        CustomerReturnDraft(saleId, null, PaymentMethod.CASH, reason, null, listOf(CustomerReturnLine(itemId, qty)))

    // ------------------------------------------------------------ customer returns

    @Test fun partialReturnRestoresStockRefundsAndKeepsTheSaleIntact() = runBlocking {
        val product = addProduct(price = 1_000, cost = 600, stock = 10)
        val saleId = sell(product, 4)                       // stock 6, sale total 4000
        val item = sales.observe(saleId).first()!!.items.single()

        val result = returns.createCustomerReturn(draft(saleId, item.id, 1))
        assertTrue(result is SaveResult.Success)

        assertEquals(7, products.getById(product)!!.stockQuantity)
        val ret = returns.observeCustomerReturn((result as SaveResult.Success).value).first()!!
        assertTrue(ret.customerReturn.returnNumber.matches(Regex("CR-\\d{8}-0001")))
        assertEquals(1_000, ret.customerReturn.totalRefundMinor)
        assertEquals(1, ret.items.single().quantityReturned)
        assertEquals(600, ret.items.single().unitCostMinor)

        // original sale untouched
        val sale = sales.observe(saleId).first()!!
        assertEquals(4_000, sale.sale.totalMinor)
        assertEquals(4, sale.items.single().quantity)
        assertEquals(SaleReturnStatus.PARTIAL, returns.observeSaleSummary(saleId).first()!!.status)

        // stock ledger
        val movements = db.stockMovementDao().observeForProduct(product).first()
        assertEquals(
            setOf(StockMovementType.SALE to -4, StockMovementType.CUSTOMER_RETURN to 1),
            movements.map { it.movementType to it.quantityChange }.toSet()
        )
    }

    @Test fun cannotReturnMoreThanNetSoldAfterEarlierReturnsAndNothingIsWritten() = runBlocking {
        val product = addProduct(stock = 10)
        val saleId = sell(product, 3)
        val item = sales.observe(saleId).first()!!.items.single()

        assertTrue(returns.createCustomerReturn(draft(saleId, item.id, 2)) is SaveResult.Success)
        assertTrue(returns.createCustomerReturn(draft(saleId, item.id, 2)) is SaveResult.Invalid)

        assertEquals(9, products.getById(product)!!.stockQuantity)   // 10 - 3 + 2
        assertEquals(1, returns.observeCustomerReturns("", all.first, all.second, null).first().size)

        assertTrue(returns.createCustomerReturn(draft(saleId, item.id, 1)) is SaveResult.Success)
        assertEquals(SaleReturnStatus.FULL, returns.observeSaleSummary(saleId).first()!!.status)
        assertTrue(returns.createCustomerReturn(draft(saleId, item.id, 1)) is SaveResult.Invalid)
        assertEquals(10, products.getById(product)!!.stockQuantity)
    }

    @Test fun returnOfAnItemFromAnotherSaleIsRejected() = runBlocking {
        val product = addProduct(stock = 10)
        val saleA = sell(product, 1)
        val saleB = sell(product, 1)
        val itemB = sales.observe(saleB).first()!!.items.single()
        assertTrue(returns.createCustomerReturn(draft(saleA, itemB.id, 1)) is SaveResult.Invalid)
        assertEquals(8, products.getById(product)!!.stockQuantity)
    }

    @Test fun refundSharesDiscountAndTaxAndFullReturnRefundsExactlyWhatWasPaid() {
        // subtotal 1000, discount 100, tax 90 -> paid 990
        val third = ReturnCalculator.cumulativeRefund(1_000, 100, 90, 333)
        assertEquals(333 - 33, third.subtotalMinor)
        assertEquals(30, third.taxMinor)

        val total = (1..3).fold(0L to ReturnCalculator.Refund(0, 0)) { (gross, sum), _ ->
            val step = ReturnCalculator.incrementalRefund(1_000, 100, 90, gross, 333)
            (gross + 333) to ReturnCalculator.Refund(sum.subtotalMinor + step.subtotalMinor, sum.taxMinor + step.taxMinor)
        }.second
        // 999 gross of 1000, so one more unit of value 1 finishes the sale exactly
        val last = ReturnCalculator.incrementalRefund(1_000, 100, 90, 999, 1)
        assertEquals(990, total.totalMinor + last.totalMinor)
    }

    @Test fun summaryNetsRefundsAndReversesCostOfGoodsSold() = runBlocking {
        val product = addProduct(price = 1_000, cost = 600, stock = 10)
        val saleId = sell(product, 4)
        val item = sales.observe(saleId).first()!!.items.single()
        returns.createCustomerReturn(draft(saleId, item.id, 1))

        val refunds = returns.observeRefundSummary(all.first, all.second).first()
        assertEquals(1, refunds.returnCount)
        assertEquals(1_000, refunds.totalMinor)
        assertEquals(600, refunds.costMinor)
        val gross = sales.observeSummary(all.first, all.second).first()
        // net revenue 3000, cost of goods kept 4*600-600 = 1800
        assertEquals(3_000 - 1_800, (gross.netMinor - refunds.subtotalMinor) - (gross.costMinor - refunds.costMinor))
    }

    @Test fun filtersByReason() = runBlocking {
        val product = addProduct(stock = 10)
        val saleId = sell(product, 3)
        val item = sales.observe(saleId).first()!!.items.single()
        returns.createCustomerReturn(draft(saleId, item.id, 1, CustomerReturnReason.DAMAGED))
        returns.createCustomerReturn(draft(saleId, item.id, 1, CustomerReturnReason.EXPIRED))

        val expired = returns.observeCustomerReturns("", all.first, all.second, CustomerReturnReason.EXPIRED).first()
        assertEquals(listOf(CustomerReturnReason.EXPIRED), expired.map { it.reason })
        assertEquals(2, returns.observeCustomerReturns("", all.first, all.second, null).first().size)
        assertEquals(1, returns.observeCustomerReturns(saleInvoice(saleId), all.first, all.second, CustomerReturnReason.DAMAGED).first().size)
    }

    private suspend fun saleInvoice(id: Long) = sales.observe(id).first()!!.sale.invoiceNumber

    // ------------------------------------------------------------ supplier returns

    @Test fun supplierReturnDeductsStockRecordsCreditAndNegativeMovements() = runBlocking {
        val a = addProduct("Rice", cost = 600, stock = 10)
        val b = addProduct("Dal", cost = 250, stock = 4)

        val result = returns.createSupplierReturn(
            SupplierReturnDraft(
                "Metro Wholesale", SupplierReturnReason.EXPIRED, null,
                listOf(SupplierReturnLine(a, 3, 600), SupplierReturnLine(b, 4, 250))
            )
        )
        assertTrue(result is SaveResult.Success)
        assertEquals(7, products.getById(a)!!.stockQuantity)
        assertEquals(0, products.getById(b)!!.stockQuantity)

        val ret = returns.observeSupplierReturn((result as SaveResult.Success).value).first()!!
        assertTrue(ret.supplierReturn.returnNumber.matches(Regex("SR-\\d{8}-0001")))
        assertEquals(3 * 600 + 4 * 250, ret.supplierReturn.totalSupplierCreditMinor)
        assertEquals(2_800, returns.observeSupplierCreditSummary(all.first, all.second).first().totalMinor)
        assertEquals((10 - 3) * 600L, products.observeInventoryValue().first())

        val movement = db.stockMovementDao().observeForProduct(a).first().single()
        assertEquals(StockMovementType.SUPPLIER_RETURN, movement.movementType)
        assertEquals(-3, movement.quantityChange)
        // supplier credit is not a customer refund
        assertEquals(0, returns.observeRefundSummary(all.first, all.second).first().totalMinor)
    }

    @Test fun supplierReturnCannotExceedStockAndWritesNothing() = runBlocking {
        val a = addProduct(stock = 5)
        val b = addProduct("Dal", stock = 5)

        val tooMuch = returns.createSupplierReturn(
            SupplierReturnDraft(null, SupplierReturnReason.DAMAGED, null, listOf(SupplierReturnLine(b, 1, 100), SupplierReturnLine(a, 6, 100)))
        )
        assertTrue(tooMuch is SaveResult.Invalid)
        // duplicate lines are checked against their combined quantity
        val combined = returns.createSupplierReturn(
            SupplierReturnDraft(null, SupplierReturnReason.DAMAGED, null, listOf(SupplierReturnLine(a, 3, 100), SupplierReturnLine(a, 3, 100)))
        )
        assertTrue(combined is SaveResult.Invalid)

        assertEquals(5, products.getById(a)!!.stockQuantity)
        assertEquals(5, products.getById(b)!!.stockQuantity)
        assertTrue(returns.observeSupplierReturns("", all.first, all.second, null).first().isEmpty())
        assertTrue(db.stockMovementDao().observeForProduct(a).first().isEmpty())
    }

    @Test fun manualAdjustmentsAreInTheLedger() = runBlocking {
        val id = addProduct(stock = 5)
        products.adjustStock(id, com.tarun.managemyshop.domain.model.AdjustmentType.REDUCE, 2,
            com.tarun.managemyshop.domain.model.StockAdjustmentReason.LOST, null)
        val m = db.stockMovementDao().observeForProduct(id).first().single()
        assertEquals(StockMovementType.STOCK_DECREASE, m.movementType)
        assertEquals(-2, m.quantityChange)
    }

    // ------------------------------------------------------------ migration

    @Test fun migration3to4MatchesTheRoomSchemaAndBackfillsTheLedger() {
        val name = "migration-test.db"
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }
        val schemaDir = File("schemas/com.tarun.managemyshop.data.local.AppDatabase")
        val v3 = JSONObject(File(schemaDir, "3.json").readText()).getJSONObject("database")

        SQLiteDatabase.openOrCreateDatabase(file, null).use { raw ->
            val entities = v3.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                raw.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    raw.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            raw.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            raw.execSQL("INSERT INTO room_master_table VALUES (42, '${v3.getString("identityHash")}')")
            raw.execSQL("INSERT INTO products (id, name, sku, category, sellingPriceMinor, purchasePriceMinor, stockQuantity, lowStockThreshold, createdAt, updatedAt) VALUES (1, 'Rice', '', '', 1000, 600, 7, 5, 0, 0)")
            raw.execSQL("INSERT INTO sales (id, invoiceNumber, saleDate, subtotalMinor, discountMinor, taxMinor, totalMinor, paymentMethod) VALUES (1, 'INV-1', 1000, 3000, 0, 0, 3000, 'CASH')")
            raw.execSQL("INSERT INTO sale_items (saleId, productId, productNameSnapshot, unitPriceMinor, unitCostMinor, quantity, lineTotalMinor) VALUES (1, 1, 'Rice', 1000, 600, 3, 3000)")
            raw.version = 3
        }

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(*Migrations.ALL).allowMainThreadQueries().build()
        try {
            runBlocking {
                // Opening validates the migrated schema against the entities Room generated.
                val movement = migrated.stockMovementDao().observeForProduct(1).first().single()
                assertEquals(StockMovementType.SALE, movement.movementType)
                assertEquals(-3, movement.quantityChange)
                assertNotNull(migrated.productDao().getById(1))
                assertTrue(migrated.returnDao().observeCustomerReturns("", Long.MIN_VALUE, Long.MAX_VALUE, null).first().isEmpty())
            }
        } finally {
            migrated.close()
            file.delete()
        }
    }
}
