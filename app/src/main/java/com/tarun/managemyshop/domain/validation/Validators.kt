package com.tarun.managemyshop.domain.validation

import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.ShopSettings

/** Each validator returns a map of field name -> message; empty means valid. */
typealias ValidationErrors = Map<String, String>

object ProductValidator {
    fun validate(product: ProductEntity): ValidationErrors = buildMap {
        if (product.name.isBlank()) put("name", "Name is required")
        if (product.sellingPriceMinor < 0) put("sellingPrice", "Selling price cannot be negative")
        if (product.purchasePriceMinor < 0) put("purchasePrice", "Purchase price cannot be negative")
        if (product.stockQuantity < 0) put("stockQuantity", "Stock cannot be negative")
        if (product.lowStockThreshold < 0) put("lowStockThreshold", "Low stock threshold cannot be negative")
    }
}

object ExpenseValidator {
    fun validate(expense: ExpenseEntity): ValidationErrors = buildMap {
        if (expense.title.isBlank()) put("title", "Title is required")
        if (expense.amountMinor <= 0) put("amount", "Amount must be greater than zero")
    }
}

object SaleValidator {
    fun validate(draft: SaleDraft): ValidationErrors = buildMap {
        if (draft.lines.isEmpty()) put("lines", "Add at least one item")
        if (draft.lines.any { it.quantity <= 0 }) put("lines", "Quantity must be at least 1")
        if (draft.discountMinor < 0) put("discount", "Discount cannot be negative")
    }
}

object SettingsValidator {
    private val prefixPattern = Regex("^[A-Za-z0-9-]{1,10}$")
    private val phonePattern = Regex("^[0-9+()\\-\\s]{5,20}$")

    fun validate(settings: ShopSettings): ValidationErrors = buildMap {
        if (settings.shopName.isBlank()) put("shopName", "Shop name is required")
        if (settings.taxBasisPoints !in 0..10_000) put("tax", "Tax must be between 0% and 100%")
        if (!settings.shopPhone.isNullOrBlank() && !phonePattern.matches(settings.shopPhone)) {
            put("shopPhone", "Enter a valid phone number")
        }
        if (!prefixPattern.matches(settings.invoicePrefix)) {
            put("invoicePrefix", "Prefix must be 1-10 letters, digits or dashes")
        }
    }
}
