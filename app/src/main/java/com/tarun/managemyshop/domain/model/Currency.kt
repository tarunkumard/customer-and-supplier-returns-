package com.tarun.managemyshop.domain.model

/** All supported currencies use 2 minor-unit digits (amounts are stored as cents/paise). */
data class Currency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val localeTag: String
) {
    companion object {
        val Supported = listOf(
            Currency("INR", "₹", "Indian Rupee", "en-IN"),
            Currency("USD", "$", "US Dollar", "en-US"),
            Currency("CAD", "C$", "Canadian Dollar", "en-CA")
        )
        val Default = Supported.first()

        fun fromCode(code: String?): Currency =
            Supported.firstOrNull { it.code == code } ?: Default
    }
}
