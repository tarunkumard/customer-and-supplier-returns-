package com.tarun.managemyshop.util

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** Parses user input such as "12.5" into minor units (1250); null if not a valid non-negative number. */
    fun parseToMinor(input: String): Long? {
        val value = input.trim().toBigDecimalOrNull() ?: return null
        if (value.signum() < 0) return null
        return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
    }

    /** Minor units to an editable plain string, e.g. 1250 -> "12.50". */
    fun toPlainString(minor: Long): String = BigDecimal.valueOf(minor, 2).toPlainString()
}

object Percent {
    /** Parses "18" or "5.5" into basis points (1800 / 550); null if not a valid non-negative number. */
    fun parseToBasisPoints(input: String): Int? {
        val value = input.trim().toBigDecimalOrNull() ?: return null
        if (value.signum() < 0 || value > BigDecimal(100)) return null
        return value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toInt()
    }

    fun toPlainString(basisPoints: Int): String =
        BigDecimal.valueOf(basisPoints.toLong(), 2).stripTrailingZeros().toPlainString()
}
