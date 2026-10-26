package com.tarun.managemyshop.domain.model

enum class AdjustmentType { INCREASE, REDUCE }

enum class StockAdjustmentReason(val label: String) {
    RESTOCK("Restock"),
    RETURNED("Customer return"),
    DAMAGED("Damaged / expired"),
    LOST("Lost / theft"),
    CORRECTION("Count correction"),
    OTHER("Other")
}

sealed interface DeleteResult {
    data object Deleted : DeleteResult

    /** The product appears on [saleCount] past sale lines, so it is kept to preserve history. */
    data class InUse(val saleCount: Int) : DeleteResult
}
