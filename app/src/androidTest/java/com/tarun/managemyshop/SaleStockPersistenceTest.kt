package com.tarun.managemyshop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tarun.managemyshop.data.local.AppDatabase
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.preferences.ShopSettingsRepositoryImpl
import com.tarun.managemyshop.data.repository.SaleRepositoryImpl
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaleLine
import com.tarun.managemyshop.domain.model.SaveResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a real on-disk database so closing and reopening it mimics an app restart. */
@RunWith(AndroidJUnit4::class)
class SaleStockPersistenceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "sale-test.db"
    private lateinit var db: AppDatabase

    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()

    @Before fun setUp() {
        context.deleteDatabase(dbName)
        db = open()
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test fun saleDeductsStockAndSurvivesReopen() = runBlocking {
        val id = db.productDao().insert(
            ProductEntity(name = "Rice", sellingPriceMinor = 1000, purchasePriceMinor = 700, stockQuantity = 10)
        )
        val repo = SaleRepositoryImpl(db, ShopSettingsRepositoryImpl(context))

        val ok = repo.recordSale(SaleDraft(listOf(SaleLine(id, 3))))
        assertTrue(ok is SaveResult.Success)
        assertEquals(7, db.productDao().getById(id)!!.stockQuantity)

        db.close()
        db = open()

        assertEquals(7, db.productDao().getById(id)!!.stockQuantity)
        val sales = db.saleDao().observeAll().first()
        assertEquals(1, sales.size)
        assertEquals(1, sales[0].items.size)
        assertTrue(sales[0].sale.invoiceNumber.startsWith("INV-"))
    }

    @Test fun oversellIsRejectedAndWritesNothing() = runBlocking {
        val id = db.productDao().insert(
            ProductEntity(name = "Pen", sellingPriceMinor = 100, purchasePriceMinor = 50, stockQuantity = 2)
        )
        val result = SaleRepositoryImpl(db, ShopSettingsRepositoryImpl(context)).recordSale(SaleDraft(listOf(SaleLine(id, 3))))

        assertTrue(result is SaveResult.Invalid)
        assertEquals(2, db.productDao().getById(id)!!.stockQuantity)
        assertTrue(db.saleDao().observeAll().first().isEmpty())
    }
}
