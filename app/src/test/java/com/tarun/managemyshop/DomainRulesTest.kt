package com.tarun.managemyshop

import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.calc.SaleCalculator
import com.tarun.managemyshop.domain.validation.ProductValidator
import com.tarun.managemyshop.util.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {
    private val valid = ProductEntity(name = "Pen", sellingPriceMinor = 1000, purchasePriceMinor = 500, stockQuantity = 3)

    @Test fun validProductHasNoErrors() = assertTrue(ProductValidator.validate(valid).isEmpty())

    @Test fun invalidProductReportsEachField() {
        val errors = ProductValidator.validate(
            valid.copy(name = " ", sellingPriceMinor = -1, purchasePriceMinor = -1, stockQuantity = -1)
        )
        assertEquals(setOf("name", "sellingPrice", "purchasePrice", "stockQuantity"), errors.keys)
    }

    @Test fun taxIsRoundedHalfUp() {
        // 18% of 999 = 179.82 -> 180
        val t = SaleCalculator.calculate(subtotalMinor = 1_000, discountMinor = 1, taxBasisPoints = 1800)
        assertEquals(180, t.taxMinor)
        assertEquals(1_179, t.totalMinor)
    }

    @Test fun moneyParsing() {
        assertEquals(1250L, Money.parseToMinor("12.5"))
        assertEquals(1L, Money.parseToMinor("0.005"))
        assertNull(Money.parseToMinor("-3"))
        assertNull(Money.parseToMinor("abc"))
        assertEquals("12.50", Money.toPlainString(1250))
    }
}
