package com.tarun.managemyshop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.preferences.ShopSettingsRepositoryImpl
import com.tarun.managemyshop.data.repository.ExpenseRepositoryImpl
import com.tarun.managemyshop.data.repository.ProductRepositoryImpl
import com.tarun.managemyshop.data.repository.ReturnRepositoryImpl
import com.tarun.managemyshop.data.repository.SaleRepositoryImpl
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.domain.model.DeleteResult
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaleLine
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.model.StockAdjustmentReason
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.InvoicePdf
import com.tarun.managemyshop.viewmodel.DashboardViewModel
import com.tarun.managemyshop.viewmodel.ReportsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** End-to-end checks of the data layer against a real (in-memory) Room database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var settings: ShopSettingsRepositoryImpl
    private lateinit var products: ProductRepositoryImpl
    private lateinit var sales: SaleRepositoryImpl
    private lateinit var expenses: ExpenseRepositoryImpl

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        settings = ShopSettingsRepositoryImpl(context)
        products = ProductRepositoryImpl(db)
        sales = SaleRepositoryImpl(db, settings)
        expenses = ExpenseRepositoryImpl(db.expenseDao())
        runBlocking { settings.resetAll() }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun addProduct(name: String = "Rice", price: Long = 1_000, cost: Long = 600, stock: Int = 10): Long =
        (products.save(
            ProductEntity(name = name, sellingPriceMinor = price, purchasePriceMinor = cost, stockQuantity = stock)
        ) as SaveResult.Success).value

    // ------------------------------------------------------------ stock

    @Test fun saleDeductsStockAndSnapshotsPriceAndCost() = runBlocking {
        val id = addProduct(price = 1_000, cost = 600, stock = 10)

        val result = sales.recordSale(SaleDraft(listOf(SaleLine(id, 3))))

        assertTrue(result is SaveResult.Success)
        assertEquals(7, products.getById(id)!!.stockQuantity)
        val sale = sales.observe((result as SaveResult.Success).value).first()!!
        assertEquals(3_000, sale.sale.totalMinor)
        assertEquals(600, sale.items.single().unitCostMinor)
        assertTrue(sale.sale.invoiceNumber.matches(Regex("INV-\\d{8}-0001")))
    }

    @Test fun cannotSellMoreThanInStockAndNothingIsWritten() = runBlocking {
        val id = addProduct(stock = 2)

        val result = sales.recordSale(SaleDraft(listOf(SaleLine(id, 3))))

        assertTrue(result is SaveResult.Invalid)
        assertEquals(2, products.getById(id)!!.stockQuantity)
        assertEquals(0, sales.observeSummary(Long.MIN_VALUE, Long.MAX_VALUE).first().saleCount)
    }

    @Test fun duplicateLinesAreCombinedBeforeTheStockCheck() = runBlocking {
        val id = addProduct(stock = 5)
        val result = sales.recordSale(SaleDraft(listOf(SaleLine(id, 3), SaleLine(id, 3))))
        assertTrue(result is SaveResult.Invalid)
        assertEquals(5, products.getById(id)!!.stockQuantity)
    }

    @Test fun stockAdjustmentCannotGoBelowZero() = runBlocking {
        val id = addProduct(stock = 4)

        val tooMuch = products.adjustStock(id, AdjustmentType.REDUCE, 5, StockAdjustmentReason.DAMAGED, null)
        assertTrue(tooMuch is SaveResult.Invalid)
        assertEquals(4, products.getById(id)!!.stockQuantity)

        assertTrue(products.adjustStock(id, AdjustmentType.REDUCE, 4, StockAdjustmentReason.DAMAGED, "Spoiled") is SaveResult.Success)
        assertEquals(0, products.getById(id)!!.stockQuantity)
        assertTrue(products.adjustStock(id, AdjustmentType.INCREASE, 6, StockAdjustmentReason.RESTOCK, null) is SaveResult.Success)
        assertEquals(6, products.getById(id)!!.stockQuantity)
    }

    @Test fun negativeStockIsRejectedByValidation() = runBlocking {
        val result = products.save(ProductEntity(name = "X", sellingPriceMinor = 1, purchasePriceMinor = 1, stockQuantity = -1))
        assertTrue(result is SaveResult.Invalid)
    }

    @Test fun productWithSalesHistoryCannotBeDeleted() = runBlocking {
        val sold = addProduct("Sold")
        val unsold = addProduct("Unsold")
        sales.recordSale(SaleDraft(listOf(SaleLine(sold, 1))))

        assertEquals(DeleteResult.InUse(1), products.delete(sold))
        assertEquals(DeleteResult.Deleted, products.delete(unsold))
        assertEquals("Sold", products.getById(sold)!!.name)
    }

    @Test fun discountAboveSubtotalIsRejected() = runBlocking {
        val id = addProduct(price = 1_000)
        val result = sales.recordSale(SaleDraft(listOf(SaleLine(id, 1)), discountMinor = 1_001))
        assertTrue(result is SaveResult.Invalid)
    }

    // ------------------------------------------------------------ low stock

    @Test fun lowStockIncludesProductsExactlyAtTheThresholdAndOutOfStock() = runBlocking {
        fun product(name: String, stock: Int, threshold: Int) =
            ProductEntity(name = name, sellingPriceMinor = 100, purchasePriceMinor = 50, stockQuantity = stock, lowStockThreshold = threshold)
        products.save(product("above", 6, 5))
        products.save(product("at", 5, 5))
        products.save(product("below", 2, 5))
        products.save(product("out", 0, 5))

        assertEquals(listOf("out", "below", "at"), products.observeLowStock().first().map { it.name })
        assertEquals(false, product("above", 6, 5).isLowStock)
        assertEquals(true, product("at", 5, 5).isLowStock)
    }

    @Test fun sellingDownToTheThresholdRaisesTheAlert() = runBlocking {
        val id = addProduct(stock = 8)   // default threshold 5
        assertTrue(products.observeLowStock().first().isEmpty())
        sales.recordSale(SaleDraft(listOf(SaleLine(id, 3))))
        assertEquals(listOf(id), products.observeLowStock().first().map { it.id })
    }

    // ------------------------------------------------------------ totals: discount + tax

    @Test fun checkoutAppliesDiscountThenTaxOnTheDiscountedAmount() = runBlocking {
        settings.save(ShopSettings(taxBasisPoints = 1_800))
        val rice = addProduct("Rice", price = 14_500, stock = 40)
        val book = addProduct("Notebook", price = 6_000, stock = 60)

        val id = (sales.recordSale(
            SaleDraft(listOf(SaleLine(rice, 2), SaleLine(book, 3)), discountMinor = 2_000)
        ) as SaveResult.Success).value
        val sale = sales.observe(id).first()!!

        assertEquals(47_000, sale.sale.subtotalMinor)
        assertEquals(2_000, sale.sale.discountMinor)
        assertEquals(8_100, sale.sale.taxMinor)      // 18% of 450.00
        assertEquals(53_100, sale.sale.totalMinor)
        assertEquals(38, products.getById(rice)!!.stockQuantity)
        assertEquals(57, products.getById(book)!!.stockQuantity)
    }

    // ------------------------------------------------------------ sales search & filters

    @Test fun salesCanBeFilteredBySearchPaymentMethodAndDate() = runBlocking {
        val id = addProduct(stock = 20)
        sales.recordSale(SaleDraft(listOf(SaleLine(id, 1)), customerName = "Asha", paymentMethod = PaymentMethod.UPI))
        sales.recordSale(SaleDraft(listOf(SaleLine(id, 1)), customerName = "Ben", paymentMethod = PaymentMethod.CASH))
        val all = Long.MIN_VALUE to Long.MAX_VALUE

        assertEquals(2, sales.observeFiltered("", all.first, all.second, null).first().size)
        assertEquals(listOf("Asha"), sales.observeFiltered("ash", all.first, all.second, null).first().map { it.customerName })
        assertEquals(1, sales.observeFiltered("0002", all.first, all.second, null).first().size)
        assertEquals(listOf("Ben"), sales.observeFiltered("", all.first, all.second, PaymentMethod.CASH).first().map { it.customerName })
        assertEquals(0, sales.observeFiltered("nobody", all.first, all.second, null).first().size)

        val tomorrow = DateRanges.day(LocalDate.now().plusDays(1))
        assertEquals(0, sales.observeFiltered("", tomorrow.start, tomorrow.end, null).first().size)
        val today = DateRanges.day(LocalDate.now())
        assertEquals(2, sales.observeFiltered("", today.start, today.end, null).first().size)
    }

    @Test fun reportTotalsOnlyCoverTheSelectedRange() = runBlocking {
        val id = addProduct(price = 1_000, cost = 400, stock = 20)
        sales.recordSale(SaleDraft(listOf(SaleLine(id, 2))))
        val today = DateRanges.day(LocalDate.now())
        val tomorrow = DateRanges.day(LocalDate.now().plusDays(1))

        val inRange = sales.observeSummary(today.start, today.end).first()
        assertEquals(1, inRange.saleCount)
        assertEquals(2_000, inRange.totalMinor)
        assertEquals(800, inRange.costMinor)

        val outOfRange = sales.observeSummary(tomorrow.start, tomorrow.end).first()
        assertEquals(0, outOfRange.saleCount)
        assertEquals(0, outOfRange.totalMinor)
        assertEquals(0, outOfRange.costMinor)
    }

    // ------------------------------------------------------------ expenses

    @Test fun expenseCanBeAddedEditedFilteredAndDeleted() = runBlocking {
        val today = DateRanges.day(LocalDate.now())
        val id = (expenses.save(ExpenseEntity(title = "Electricity", category = "Utilities", amountMinor = 4_000, expenseDate = today.start)) as SaveResult.Success).value
        assertEquals(4_000, expenses.observeTotalBetween(today.start, today.end).first())

        expenses.save(expenses.getById(id)!!.copy(amountMinor = 4_500))
        assertEquals(4_500, expenses.observeTotalBetween(today.start, today.end).first())
        assertEquals(1, expenses.observeFiltered("util", today.start, today.end).first().size)
        assertEquals(0, expenses.observeFiltered("rent", today.start, today.end).first().size)

        expenses.delete(id)
        assertEquals(0, expenses.observeTotalBetween(today.start, today.end).first())
    }

    @Test fun expenseValidationRejectsBlankTitleAndNonPositiveAmount() = runBlocking {
        val result = expenses.save(ExpenseEntity(title = " ", amountMinor = 0, expenseDate = 0))
        assertEquals(setOf("title", "amount"), (result as SaveResult.Invalid).errors.keys)
    }

    // ------------------------------------------------------------ dashboard & reports update live

    @Test fun reportsUpdateAfterSaleAndExpense() = runBlocking {
        val reports = ReportsViewModel(sales, expenses, products, ReturnRepositoryImpl(db))
        withTimeout(10_000) {
            assertEquals(0, reports.state.first { it.loaded }.saleCount)

            val id = addProduct(price = 1_000, cost = 600, stock = 10)
            sales.recordSale(SaleDraft(listOf(SaleLine(id, 3))))
            val afterSale = reports.state.first {
                it.saleCount == 1 && it.totalSalesMinor == 3_000L && it.paymentBreakdown.isNotEmpty() && it.topProducts.isNotEmpty()
            }
            assertEquals(3_000, afterSale.totalSalesMinor)
            assertEquals(3_000, afterSale.averageOrderMinor)
            // revenue 3000 - cost 1800 - no expenses
            assertEquals(1_200, afterSale.estimatedProfitMinor)
            assertEquals(3_000, afterSale.paymentBreakdown.single().totalMinor)
            assertEquals(3, afterSale.topProducts.single().quantity)

            expenses.save(ExpenseEntity(title = "Rent", category = "Rent", amountMinor = 500, expenseDate = System.currentTimeMillis()))
            val afterExpense = reports.state.first { it.totalExpensesMinor == 500L && it.estimatedProfitMinor == 700L }
            assertEquals(700, afterExpense.estimatedProfitMinor)
        }
    }

    @Test fun dashboardUpdatesAfterSaleAndExpense() = runBlocking {
        val dashboard = DashboardViewModel(settings, sales, expenses, products, ReturnRepositoryImpl(db))
        withTimeout(10_000) {
            val id = addProduct(price = 2_000, cost = 1_500, stock = 6)
            dashboard.state.first { it.loaded && it.lowStock.isEmpty() }

            sales.recordSale(SaleDraft(listOf(SaleLine(id, 3))))   // leaves 3, below the default threshold of 5
            // The dashboard combines several live queries, so wait until they have all caught up.
            val afterSale = dashboard.state.first {
                it.recentActivity.size == 1 && it.todaySalesMinor == 6_000L && it.monthSalesMinor == 6_000L && it.lowStock.isNotEmpty()
            }
            assertEquals(6_000, afterSale.todaySalesMinor)
            assertEquals(6_000, afterSale.monthSalesMinor)

            expenses.save(ExpenseEntity(title = "Bags", amountMinor = 1_000, expenseDate = System.currentTimeMillis()))
            val afterExpense = dashboard.state.first { it.monthExpensesMinor == 1_000L && it.estimatedProfitMinor == 500L }
            // revenue 6000 - cost 4500 - expenses 1000
            assertEquals(500, afterExpense.estimatedProfitMinor)
            assertEquals(listOf(id), afterSale.lowStock.map { it.id })
        }
    }

    // ------------------------------------------------------------ settings & currency

    @Test fun currencyChangeIsObservedImmediately() = runBlocking {
        for (currency in Currency.Supported) {
            settings.setCurrency(currency)
            assertEquals(currency, settings.observe().first().currency)
        }
    }

    @Test fun settingsValidationAndRoundTrip() = runBlocking {
        val bad = settings.save(ShopSettings(shopName = " ", invoicePrefix = "bad prefix!", shopPhone = "abc", taxBasisPoints = 20_000))
        assertTrue(bad is SaveResult.Invalid)
        assertEquals(setOf("shopName", "invoicePrefix", "shopPhone", "tax"), (bad as SaveResult.Invalid).errors.keys)

        val good = ShopSettings("Corner Shop", "1 Main St", "+91 98765 43210", Currency.Supported[1], 1_800, "SHOP")
        assertTrue(settings.save(good) is SaveResult.Success)
        assertEquals(good, settings.get())
    }

    @Test fun taxAndPrefixFromSettingsAreUsedAtCheckout() = runBlocking {
        settings.save(ShopSettings(taxBasisPoints = 1_800, invoicePrefix = "SHOP"))
        val id = addProduct(price = 1_000)

        val saleId = (sales.recordSale(SaleDraft(listOf(SaleLine(id, 1)))) as SaveResult.Success).value
        val sale = sales.observe(saleId).first()!!.sale

        assertEquals(180, sale.taxMinor)
        assertEquals(1_180, sale.totalMinor)
        assertTrue(sale.invoiceNumber.startsWith("SHOP-"))
    }

    // ------------------------------------------------------------ PDF text wrapping

    @Test fun wrapKeepsWordsAndBreaksOverlongOnes() {
        val paint = android.graphics.Paint()
        // Robolectric measures one unit per character, so a width of 10 means 10 characters.
        // (Hard-breaking a single overlong word relies on Paint.breakText, which Robolectric does not emulate.)
        assertEquals(listOf("hello", "world"), InvoicePdf.wrap("hello world", paint, 10f))
        assertEquals(listOf("one two", "three"), InvoicePdf.wrap("one two three", paint, 8f))
        assertEquals(listOf("one", "", "two"), InvoicePdf.wrap("one\n\ntwo", paint, 10f))
        assertEquals(listOf(""), InvoicePdf.wrap("", paint, 10f))
    }
}
