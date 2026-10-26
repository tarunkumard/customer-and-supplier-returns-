package com.tarun.managemyshop.util

import androidx.compose.runtime.compositionLocalOf
import com.tarun.managemyshop.domain.model.Currency
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

/** Formats money held as integer minor units (paise/cents) using the selected currency. */
class CurrencyFormatter(val currency: Currency = Currency.Default) {

    private val numberFormat: NumberFormat =
        NumberFormat.getNumberInstance(Locale.forLanguageTag(currency.localeTag)).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

    fun format(amountMinor: Long): String {
        val sign = if (amountMinor < 0) "-" else ""
        val major = BigDecimal.valueOf(kotlin.math.abs(amountMinor), 2)
        return "$sign${currency.symbol}${numberFormat.format(major)}"
    }
}

val LocalCurrencyFormatter = compositionLocalOf { CurrencyFormatter() }
