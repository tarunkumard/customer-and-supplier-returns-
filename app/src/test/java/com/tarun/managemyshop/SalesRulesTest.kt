package com.tarun.managemyshop

import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleItemEntity
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.calc.InvoiceNumber
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.ReceiptFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SalesRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun invoiceNumberUsesPrefixDateAndSequence() {
        val millis = LocalDate.of(2026, 10, 26).atTime(14, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals("INV-20261026-0007", InvoiceNumber.format("INV", millis, 7, zone))
        assertEquals("SHOP-20261026-12345", InvoiceNumber.format("SHOP", millis, 12_345, zone))
    }

    @Test fun receiptTextContainsKeyLines() {
        val sale = SaleEntity(
            id = 1, invoiceNumber = "INV-20261026-0001", customerName = "Asha", saleDate = 0,
            subtotalMinor = 29_000, discountMinor = 1_000, taxMinor = 0, totalMinor = 28_000,
            paymentMethod = PaymentMethod.UPI, notes = null
        )
        val item = SaleItemEntity(
            saleId = 1, productId = 1, productNameSnapshot = "Rice 1kg",
            unitPriceMinor = 14_500, quantity = 2, lineTotalMinor = 29_000
        )
        val text = ReceiptFormatter.build(ShopSettings(shopName = "Corner Shop"), SaleWithItems(sale, listOf(item)), CurrencyFormatter())

        assertTrue(text.startsWith("Corner Shop"))
        assertTrue(text.contains("Invoice: INV-20261026-0001"))
        assertTrue(text.contains("Customer: Asha"))
        assertTrue(text.contains("Payment: UPI"))
        assertTrue(text.contains("2 x ₹145.00 = ₹290.00"))
        assertTrue(text.contains("Discount: -₹10.00"))
        assertTrue(text.contains("TOTAL: ₹280.00"))
        assertFalse(text.contains("Tax:"))
        assertFalse(text.contains("Notes:"))
    }
}
