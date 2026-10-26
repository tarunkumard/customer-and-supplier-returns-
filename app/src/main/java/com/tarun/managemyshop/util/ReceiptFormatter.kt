package com.tarun.managemyshop.util

import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.ShopSettings

fun PaymentMethod.displayName(): String =
    if (this == PaymentMethod.UPI) "UPI" else name.lowercase().replaceFirstChar { it.uppercase() }

/** Plain-text receipt used by the Share action. */
object ReceiptFormatter {
    private const val RULE = "--------------------------------"

    fun build(shop: ShopSettings, saleWithItems: SaleWithItems, money: CurrencyFormatter): String {
        val sale = saleWithItems.sale
        return buildString {
            appendLine(shop.shopName)
            shop.shopAddress?.let { appendLine(it) }
            shop.shopPhone?.let { appendLine("Tel: $it") }
            appendLine(RULE)
            appendLine("Invoice: ${sale.invoiceNumber}")
            appendLine("Date: ${DateRanges.formatDateTime(sale.saleDate)}")
            sale.customerName?.let { appendLine("Customer: $it") }
            appendLine("Payment: ${sale.paymentMethod.displayName()}")
            appendLine(RULE)
            saleWithItems.items.forEach { item ->
                appendLine(item.productNameSnapshot)
                appendLine("  ${item.quantity} x ${money.format(item.unitPriceMinor)} = ${money.format(item.lineTotalMinor)}")
            }
            appendLine(RULE)
            appendLine("Subtotal: ${money.format(sale.subtotalMinor)}")
            if (sale.discountMinor > 0) appendLine("Discount: -${money.format(sale.discountMinor)}")
            if (sale.taxMinor > 0) appendLine("Tax: ${money.format(sale.taxMinor)}")
            appendLine("TOTAL: ${money.format(sale.totalMinor)}")
            sale.notes?.let {
                appendLine(RULE)
                appendLine("Notes: $it")
            }
            appendLine(RULE)
            append("Thank you for shopping with us!")
        }
    }
}
