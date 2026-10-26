package com.tarun.managemyshop

import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.InvoiceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyAndLayoutTest {
    private fun format(code: String, minor: Long) = CurrencyFormatter(Currency.fromCode(code)).format(minor)

    @Test fun inrUsesRupeeSymbol() {
        // Lakh-style grouping (1,23,456.78) comes from the device's ICU data, which the host JVM lacks,
        // so only the symbol and digits are asserted here.
        assertEquals("₹123456.78", format("INR", 12_345_678).replace(",", ""))
        assertEquals("₹0.05", format("INR", 5))
    }

    @Test fun usdUsesDollarSymbol() {
        assertEquals("$1,234.56", format("USD", 123_456))
    }

    @Test fun cadUsesCDollarSymbol() {
        assertEquals("C$1,234.56", format("CAD", 123_456))
    }

    @Test fun negativeAmountsKeepTheSignBeforeTheSymbol() {
        assertEquals("-₹10.00", format("INR", -1_000))
    }

    @Test fun supportedCurrenciesAreExactlyInrUsdCad() {
        assertEquals(listOf("INR", "USD", "CAD"), Currency.Supported.map { it.code })
    }

    @Test fun shortInvoiceFitsOnOnePageWithTotals() {
        val pages = InvoiceLayout.paginate(List(5) { 22f }, firstStart = 300f, nextStart = 114f, bottom = 782f, totalsHeight = 200f)
        assertEquals(1, pages.size)
        assertTrue(pages[0].hasTotals)
        assertEquals(0..4, pages[0].rows)
    }

    @Test fun longInvoiceSpansPagesAndEveryRowAppearsOnce() {
        val pages = InvoiceLayout.paginate(List(80) { 22f }, firstStart = 300f, nextStart = 114f, bottom = 782f, totalsHeight = 200f)
        assertTrue(pages.size > 1)
        assertEquals((0 until 80).toList(), pages.flatMap { it.rows.toList() })
        assertEquals(1, pages.count { it.hasTotals })
        assertTrue(pages.last().hasTotals)
    }

    @Test fun totalsMoveToTheirOwnPageWhenTheyDoNotFit() {
        // 21 rows of 22 end at y=762, leaving less than the 200 needed, leaving no room for the totals block.
        val pages = InvoiceLayout.paginate(List(21) { 22f }, firstStart = 300f, nextStart = 114f, bottom = 782f, totalsHeight = 200f)
        assertEquals(2, pages.size)
        assertTrue(pages[1].rows.isEmpty())
        assertTrue(pages[1].hasTotals)
    }

    @Test fun rowTallerThanAPageStillTerminates() {
        val pages = InvoiceLayout.paginate(listOf(2_000f, 22f), firstStart = 300f, nextStart = 114f, bottom = 782f, totalsHeight = 200f)
        assertEquals(listOf(0, 1), pages.flatMap { it.rows.toList() })
    }
}
