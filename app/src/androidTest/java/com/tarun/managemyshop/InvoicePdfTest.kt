package com.tarun.managemyshop

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleItemEntity
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.InvoicePdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Needs a real device/emulator: PdfDocument is backed by native code. */
@RunWith(AndroidJUnit4::class)
class InvoicePdfTest {
    private val sale = SaleEntity(
        id = 1, invoiceNumber = "INV-20261026-0001", customerName = "Asha", saleDate = System.currentTimeMillis(),
        subtotalMinor = 100, discountMinor = 0, taxMinor = 0, totalMinor = 100,
        paymentMethod = PaymentMethod.CASH, notes = "Thanks"
    )

    private fun render(itemCount: Int): ByteArray {
        val items = List(itemCount) {
            SaleItemEntity(saleId = 1, productId = it.toLong(), productNameSnapshot = "Item number $it", unitPriceMinor = 100, quantity = 1, lineTotalMinor = 100)
        }
        return ByteArrayOutputStream().also {
            InvoicePdf.write(ShopSettings("Corner Shop", "1 Main St", "12345"), SaleWithItems(sale, items), CurrencyFormatter(), it)
        }.toByteArray()
    }

    @Test fun producesAValidPdf() {
        val bytes = render(3)
        assertEquals("%PDF-", String(bytes.copyOf(5)))
    }

    @Test fun longInvoiceStillProducesAPdf() {
        val short = render(3)
        val long = render(150)
        assertTrue(long.size > short.size)
        assertEquals("%PDF-", String(long.copyOf(5)))
    }
}
