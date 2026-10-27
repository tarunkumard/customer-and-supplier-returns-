package com.tarun.managemyshop

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.data.preferences.ShopSettingsRepositoryImpl
import com.tarun.managemyshop.data.repository.ExpenseRepositoryImpl
import com.tarun.managemyshop.data.repository.ProductRepositoryImpl
import com.tarun.managemyshop.data.repository.ReturnRepositoryImpl
import com.tarun.managemyshop.data.repository.SaleRepositoryImpl
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.domain.model.CustomerReturnDraft
import com.tarun.managemyshop.domain.model.CustomerReturnLine
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.DeleteResult
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaleLine
import com.tarun.managemyshop.domain.model.SaleReturnStatus
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.model.StockAdjustmentReason
import com.tarun.managemyshop.domain.model.StockMovementType
import com.tarun.managemyshop.domain.model.SupplierReturnDraft
import com.tarun.managemyshop.domain.model.SupplierReturnLine
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.DateFilter
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.ReceiptFormatter
import com.tarun.managemyshop.viewmodel.DashboardViewModel
import com.tarun.managemyshop.viewmodel.NewCustomerReturnViewModel
import com.tarun.managemyshop.viewmodel.NewSupplierReturnViewModel
import com.tarun.managemyshop.viewmodel.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * QA scenarios from the returns test plan, run against a real in-memory Room database.
 * Prices: ₹100.00 = 10_000 minor units, 18% tax.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QaScenarioTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: ShopSettingsRepositoryImpl
    private lateinit var products: ProductRepositoryImpl
    private lateinit var sales: SaleRepositoryImpl
    private lateinit var returns: ReturnRepositoryImpl
    private lateinit var expenses: ExpenseRepositoryImpl

    private val any = Long.MIN_VALUE to Long.MAX_VALUE

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = ShopSettingsRepositoryImpl(context)
        runBlocking { settings.resetAll(); settings.save(ShopSettings(taxBasisPoints = 1800)) }
        products = ProductRepositoryImpl(db)
        sales = SaleRepositoryImpl(db, settings)
        returns = ReturnRepositoryImpl(db)
        expenses = ExpenseRepositoryImpl(db.expenseDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun addProduct(name: String = "Rice", price: Long = 10_000, cost: Long = 6_000, stock: Int = 20): Long =
        (products.save(ProductEntity(name = name, sellingPriceMinor = price, purchasePriceMinor = cost, stockQuantity = stock))
            as SaveResult.Success).value

    private suspend fun stock(id: Long) = products.getById(id)!!.stockQuantity

    private fun returnDraft(saleId: Long, itemId: Long, qty: Int, reason: CustomerReturnReason = CustomerReturnReason.DAMAGED) =
        CustomerReturnDraft(saleId, null, PaymentMethod.CASH, reason, null, listOf(CustomerReturnLine(itemId, qty)))

    private suspend fun movements(productId: Long) =
        db.stockMovementDao().observeForProduct(productId).first().sortedBy { it.id }

    // ------------------------------------------------------------ scenarios 1 + 2

    @Test fun customerPartialThenFullReturnEndToEnd() = runBlocking {
        val product = addProduct()
        val reports = ReportsViewModel(sales, expenses, products, returns)
        val dashboard = DashboardViewModel(settings, sales, expenses, products, returns)

        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(product, 5)))) as SaveResult.Success).value
        assertEquals(15, stock(product))
        val originalSale = sales.observe(saleId).first()!!
        assertEquals(50_000, originalSale.sale.subtotalMinor)
        assertEquals(9_000, originalSale.sale.taxMinor)
        assertEquals(59_000, originalSale.sale.totalMinor)
        val itemId = originalSale.items.single().id

        // ---- partial: 2 of 5 ----
        val partial = returns.createCustomerReturn(returnDraft(saleId, itemId, 2)) as SaveResult.Success
        assertEquals(17, stock(product))
        val r1 = returns.observeCustomerReturn(partial.value).first()!!.customerReturn
        assertEquals(20_000, r1.subtotalRefundMinor)          // 2 x 100.00
        assertEquals(3_600, r1.taxRefundMinor)                // 18% of 200.00
        assertEquals(23_600, r1.totalRefundMinor)

        // original sale is byte-for-byte what it was, and now reads "partially returned"
        assertEquals(originalSale, sales.observe(saleId).first())
        assertEquals(SaleReturnStatus.PARTIAL, returns.observeSaleSummary(saleId).first()!!.status)

        withTimeout(10_000) {
            val d = dashboard.state.first { it.loaded && it.monthRefundsMinor == 23_600L }
            assertEquals(59_000 - 23_600, d.todaySalesMinor)          // net sales
            assertEquals(59_000 - 23_600, d.monthSalesMinor)
            // revenue ex tax (500 - 200) - cost kept (3 x 60) = 300 - 180 = 120.00
            assertEquals(12_000, d.estimatedProfitMinor)
            val r = reports.state.first { it.customerRefundsMinor == 23_600L }
            assertEquals(59_000, r.grossSalesMinor)
            assertEquals(35_400, r.totalSalesMinor)                   // reports show NET sales
            assertEquals(12_000, r.estimatedProfitMinor)
            assertEquals(12_000, r.returnedCostMinor)                 // 2 x 60.00 reversed
        }

        // cannot return more than the remaining 3, and nothing is written when refused
        assertTrue(returns.createCustomerReturn(returnDraft(saleId, itemId, 4)) is SaveResult.Invalid)
        assertEquals(17, stock(product))
        assertEquals(1, returns.observeCustomerReturns("", any.first, any.second, null).first().size)

        // ---- full: the remaining 3 ----
        assertTrue(returns.createCustomerReturn(returnDraft(saleId, itemId, 3)) is SaveResult.Success)
        assertEquals(20, stock(product))
        assertEquals(SaleReturnStatus.FULL, returns.observeSaleSummary(saleId).first()!!.status)
        assertTrue(returns.createCustomerReturn(returnDraft(saleId, itemId, 1)) is SaveResult.Invalid)
        assertEquals(originalSale, sales.observe(saleId).first())

        val allReturns = returns.observeCustomerReturns("", any.first, any.second, null).first()
        // refunds across both returns add up to exactly what the customer paid
        assertEquals(59_000, allReturns.sumOf { it.totalRefundMinor })
        assertEquals(9_000, allReturns.sumOf { it.taxRefundMinor })

        withTimeout(10_000) {
            val d = dashboard.state.first { it.monthRefundsMinor == 59_000L }
            assertEquals(0, d.todaySalesMinor)
            assertEquals(0, d.monthSalesMinor)
            assertEquals(0, d.estimatedProfitMinor)
            val r = reports.state.first { it.customerRefundsMinor == 59_000L }
            assertEquals(0, r.totalSalesMinor)
            assertEquals(0, r.estimatedProfitMinor)
            assertEquals(2, r.customerReturnCount)
        }
        // expenses still flow through profit
        expenses.save(com.tarun.managemyshop.data.local.entity.ExpenseEntity(title = "Rent", amountMinor = 1_000, expenseDate = System.currentTimeMillis()))
        withTimeout(10_000) { assertEquals(-1_000, reports.state.first { it.totalExpensesMinor == 1_000L }.estimatedProfitMinor) }

        // ledger: sale -5, return +2, return +3 and they sum to zero
        assertEquals(
            listOf(StockMovementType.SALE to -5, StockMovementType.CUSTOMER_RETURN to 2, StockMovementType.CUSTOMER_RETURN to 3),
            movements(product).map { it.movementType to it.quantityChange }
        )
        // a fully returned sale makes the product un-deletable (history is kept)
        assertTrue(products.delete(product) is DeleteResult.InUse)
    }

    // ------------------------------------------------------------ scenario 3

    @Test fun supplierReturnReducesStockRecordsCreditAndStaysSeparate() = runBlocking {
        val product = addProduct(cost = 6_000, stock = 20)
        val reports = ReportsViewModel(sales, expenses, products, returns)
        val dashboard = DashboardViewModel(settings, sales, expenses, products, returns)

        val tooMany = returns.createSupplierReturn(
            SupplierReturnDraft("Metro", SupplierReturnReason.WRONG_PRODUCT, null, listOf(SupplierReturnLine(product, 21, 6_000)))
        )
        assertTrue(tooMany is SaveResult.Invalid)
        assertEquals(20, stock(product))

        val ok = returns.createSupplierReturn(
            SupplierReturnDraft("Metro", SupplierReturnReason.DAMAGED, null, listOf(SupplierReturnLine(product, 4, 6_000)))
        ) as SaveResult.Success
        assertEquals(16, stock(product))
        assertEquals(24_000, returns.observeSupplierReturn(ok.value).first()!!.supplierReturn.totalSupplierCreditMinor)
        // the next return cannot exceed what is left (16)
        assertTrue(
            returns.createSupplierReturn(
                SupplierReturnDraft(null, SupplierReturnReason.EXPIRED, null, listOf(SupplierReturnLine(product, 17, 6_000)))
            ) is SaveResult.Invalid
        )
        assertEquals(16, stock(product))

        withTimeout(10_000) {
            val d = dashboard.state.first { it.monthSupplierCreditMinor == 24_000L }
            assertEquals(0, d.monthRefundsMinor)                 // not a customer refund
            assertEquals(0, d.monthSalesMinor)                   // and not net sales
            assertEquals(0, d.estimatedProfitMinor)              // and not profit
            val r = reports.state.first { it.supplierCreditMinor == 24_000L }
            assertEquals(0, r.customerRefundsMinor)
            assertEquals(1, r.supplierReturnCount)
            assertEquals(16 * 6_000L, r.inventoryValueMinor)     // valuation follows stock
        }
        assertEquals(listOf(StockMovementType.SUPPLIER_RETURN to -4), movements(product).map { it.movementType to it.quantityChange })
    }

    // ------------------------------------------------------------ scenario 4: audit + atomicity

    @Test fun everyStockChangeLeavesExactlyOneAccurateMovement() = runBlocking {
        val product = addProduct(stock = 20)
        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(product, 5)))) as SaveResult.Success).value
        val itemId = sales.observe(saleId).first()!!.items.single().id
        returns.createCustomerReturn(returnDraft(saleId, itemId, 2))
        returns.createSupplierReturn(SupplierReturnDraft(null, SupplierReturnReason.OTHER, null, listOf(SupplierReturnLine(product, 4, 6_000))))
        products.adjustStock(product, AdjustmentType.INCREASE, 10, StockAdjustmentReason.RESTOCK, "delivery")
        products.adjustStock(product, AdjustmentType.REDUCE, 1, StockAdjustmentReason.LOST, null)

        val m = movements(product)
        assertEquals(
            listOf(
                StockMovementType.SALE to -5,
                StockMovementType.CUSTOMER_RETURN to 2,
                StockMovementType.SUPPLIER_RETURN to -4,
                StockMovementType.STOCK_INCREASE to 10,
                StockMovementType.STOCK_DECREASE to -1
            ),
            m.map { it.movementType to it.quantityChange }
        )
        // starting stock 20 plus all movements equals the real stock: the ledger never drifts
        assertEquals(20 + m.sumOf { it.quantityChange }, stock(product))
        assertEquals(saleId, m[0].referenceId)
    }

    @Test fun aFailureAfterTheReturnRollsBackEveryWrite() = runBlocking {
        val product = addProduct(stock = 20)
        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(product, 5)))) as SaveResult.Success).value
        val itemId = sales.observe(saleId).first()!!.items.single().id
        val movementsBefore = movements(product).size

        // Room joins nested transactions, so failing the outer one proves the return's rows,
        // stock change and ledger entries all live in a single atomic unit.
        runCatching {
            db.withTransaction {
                returns.createCustomerReturn(returnDraft(saleId, itemId, 2))
                returns.createSupplierReturn(SupplierReturnDraft(null, SupplierReturnReason.DAMAGED, null, listOf(SupplierReturnLine(product, 3, 6_000))))
                error("simulated crash")
            }
        }

        assertEquals(15, stock(product))
        assertEquals(movementsBefore, movements(product).size)
        assertTrue(returns.observeCustomerReturns("", any.first, any.second, null).first().isEmpty())
        assertTrue(returns.observeSupplierReturns("", any.first, any.second, null).first().isEmpty())
        assertEquals(SaleReturnStatus.NONE, returns.observeSaleSummary(saleId).first()!!.status)
    }

    @Test fun rapidRepeatedTapsRecordOneCustomerReturn() = runBlocking {
        val product = addProduct(stock = 20)
        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(product, 5)))) as SaveResult.Success).value
        val itemId = sales.observe(saleId).first()!!.items.single().id

        val vm = NewCustomerReturnViewModel(returns, sales)
        val collector = launch(Dispatchers.Unconfined) { vm.state.collect { } }
        vm.start(saleId)
        withTimeout(10_000) { vm.state.first { it.loaded && it.returnNumber.isNotEmpty() } }
        vm.setQuantity(itemId, 2)
        repeat(5) { vm.confirm() }
        withTimeout(10_000) { vm.state.first { it.createdReturnId != null } }
        repeat(3) { vm.confirm() }                                    // after success, e.g. back-and-forth or rotation
        collector.cancel()

        assertEquals(1, returns.observeCustomerReturns("", any.first, any.second, null).first().size)
        assertEquals(17, stock(product))
        assertEquals(1, movements(product).count { it.movementType == StockMovementType.CUSTOMER_RETURN })
    }

    @Test fun rapidRepeatedTapsRecordOneSupplierReturn() = runBlocking {
        val product = addProduct(stock = 20)
        val vm = NewSupplierReturnViewModel(returns, products)
        val collector = launch(Dispatchers.Unconfined) { vm.state.collect { } }
        vm.addProduct(products.getById(product)!!)
        vm.setQuantity(product, "4")
        repeat(5) { vm.confirm() }
        withTimeout(10_000) { vm.state.first { it.createdReturnId != null } }
        repeat(3) { vm.confirm() }
        collector.cancel()

        assertEquals(1, returns.observeSupplierReturns("", any.first, any.second, null).first().size)
        assertEquals(16, stock(product))
        assertEquals(1, movements(product).count { it.movementType == StockMovementType.SUPPLIER_RETURN })
    }

    // ------------------------------------------------------------ scenario 5: currency, filters

    @Test fun refundsAndCreditsFormatInEachCurrency() {
        fun fmt(code: String, minor: Long) = CurrencyFormatter(Currency.fromCode(code)).format(minor)
        assertEquals("₹236.00", fmt("INR", 23_600))
        assertEquals("$236.00", fmt("USD", 23_600))
        assertEquals("C$236.00", fmt("CAD", 23_600))
        assertEquals("₹240000.00", fmt("INR", 24_000_000).replace(",", ""))   // JVM locale data lacks lakh grouping; devices have it
        assertEquals("$240,000.00", fmt("USD", 24_000_000))
        assertEquals("-₹236.00", fmt("INR", -23_600))              // refunds shown as negatives
    }

    @Test fun dateAndReasonFiltersWork() = runBlocking {
        val day = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        fun cr(n: Int, date: Long, reason: CustomerReturnReason) = CustomerReturnEntity(
            returnNumber = "CR-X-$n", originalSaleId = 1, originalInvoiceNumber = "INV-1", returnDate = date,
            refundMethod = PaymentMethod.CASH, reason = reason, subtotalRefundMinor = 100, taxRefundMinor = 0, totalRefundMinor = 100
        )
        db.returnDao().insertCustomerReturn(cr(1, now, CustomerReturnReason.DAMAGED))
        db.returnDao().insertCustomerReturn(cr(2, now - 40 * day, CustomerReturnReason.DAMAGED))
        db.returnDao().insertCustomerReturn(cr(3, now - 40 * day, CustomerReturnReason.EXPIRED))
        db.returnDao().insertSupplierReturn(SupplierReturnEntity(returnNumber = "SR-X-1", returnDate = now, reason = SupplierReturnReason.EXPIRED, totalSupplierCreditMinor = 5))
        db.returnDao().insertSupplierReturn(SupplierReturnEntity(returnNumber = "SR-X-2", returnDate = now - 40 * day, reason = SupplierReturnReason.DAMAGED, totalSupplierCreditMinor = 7))

        val month = DateFilter(DatePreset.MONTH).toRange(LocalDate.now())
        val today = DateFilter(DatePreset.TODAY).toRange()
        suspend fun customer(range: com.tarun.managemyshop.util.MillisRange, reason: CustomerReturnReason?) =
            returns.observeCustomerReturns("", range.start, range.end, reason).first().map { it.returnNumber }
        suspend fun supplier(range: com.tarun.managemyshop.util.MillisRange, reason: SupplierReturnReason?) =
            returns.observeSupplierReturns("", range.start, range.end, reason).first().map { it.returnNumber }
        val all = com.tarun.managemyshop.util.MillisRange(any.first, any.second)

        assertEquals(listOf("CR-X-1"), customer(today, null))
        assertEquals(listOf("CR-X-1"), customer(month, CustomerReturnReason.DAMAGED))
        assertEquals(listOf("CR-X-3"), customer(all, CustomerReturnReason.EXPIRED))
        assertEquals(setOf("CR-X-1", "CR-X-2"), customer(all, CustomerReturnReason.DAMAGED).toSet())
        assertTrue(customer(today, CustomerReturnReason.EXPIRED).isEmpty())
        assertEquals(listOf("SR-X-1"), supplier(month, null))
        assertEquals(listOf("SR-X-2"), supplier(all, SupplierReturnReason.DAMAGED))
        // old returns do not leak into this month's credit / refund totals
        assertEquals(5, returns.observeSupplierCreditSummary(month.start, month.end).first().totalMinor)
        assertEquals(100, returns.observeRefundSummary(month.start, month.end).first().totalMinor)
    }

    // ------------------------------------------------------------ scenario 6: regression

    @Test fun invoiceTextIsIdenticalBeforeAndAfterReturns() = runBlocking {
        val product = addProduct()
        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(product, 5)))) as SaveResult.Success).value
        val shop = ShopSettings(shopName = "Corner Shop", taxBasisPoints = 1800)
        val money = CurrencyFormatter()
        val before = sales.observe(saleId).first()!!
        val textBefore = ReceiptFormatter.build(shop, before, money)

        val itemId = before.items.single().id
        returns.createCustomerReturn(returnDraft(saleId, itemId, 5))   // full return

        val after = sales.observe(saleId).first()!!
        assertEquals(before, after)
        assertEquals(textBefore, ReceiptFormatter.build(shop, after, money))
    }

    @Test fun deleteRestrictionsKeepHistory() = runBlocking {
        val sold = addProduct("Sold")
        sales.recordSale(SaleDraft(listOf(SaleLine(sold, 1))))
        assertTrue(products.delete(sold) is DeleteResult.InUse)

        // A product that only appears in a supplier return can go, but the return keeps its snapshot.
        val gone = addProduct("Gone")
        val sr = (returns.createSupplierReturn(
            SupplierReturnDraft(null, SupplierReturnReason.DAMAGED, null, listOf(SupplierReturnLine(gone, 2, 6_000)))
        ) as SaveResult.Success).value
        assertTrue(products.delete(gone) is DeleteResult.Deleted)
        val kept = returns.observeSupplierReturn(sr).first()!!
        assertEquals("Gone", kept.items.single().productNameSnapshot)
        assertEquals(12_000, kept.supplierReturn.totalSupplierCreditMinor)
    }

    @Test fun validationRejectsEmptyAndNegativeInput() = runBlocking {
        val product = addProduct()
        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(product, 2)))) as SaveResult.Success).value
        val itemId = sales.observe(saleId).first()!!.items.single().id
        assertTrue(returns.createCustomerReturn(returnDraft(saleId, itemId, 0)) is SaveResult.Invalid)
        assertTrue(returns.createCustomerReturn(returnDraft(saleId, itemId, -1)) is SaveResult.Invalid)
        assertTrue(returns.createCustomerReturn(returnDraft(999, itemId, 1)) is SaveResult.Invalid)
        assertTrue(returns.createSupplierReturn(SupplierReturnDraft(null, SupplierReturnReason.OTHER, null, emptyList())) is SaveResult.Invalid)
        assertTrue(
            returns.createSupplierReturn(
                SupplierReturnDraft(null, SupplierReturnReason.OTHER, null, listOf(SupplierReturnLine(product, 1, -5)))
            ) is SaveResult.Invalid
        )
        assertEquals(18, stock(product))
        assertTrue(returns.observeCustomerReturns("", any.first, any.second, null).first().isEmpty())
    }
}
