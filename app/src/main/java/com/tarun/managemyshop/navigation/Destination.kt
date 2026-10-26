package com.tarun.managemyshop.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.ui.graphics.vector.ImageVector

enum class Destination(val route: String, val title: String, val icon: ImageVector) {
    Dashboard("dashboard", "Dashboard", Icons.Filled.Dashboard),
    Inventory("inventory", "Inventory", Icons.Filled.Inventory2),
    Sales("sales", "Sales", Icons.Filled.PointOfSale),
    Expenses("expenses", "Expenses", Icons.Filled.Payments),
    More("more", "More", Icons.Filled.MoreHoriz);

    companion object {
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}

/** Screens pushed on top of the tabs (no bottom bar). */
object Routes {
    const val PRODUCT_ID = "productId"
    const val PRODUCT_NEW = "product/new"
    const val PRODUCT_DETAIL = "product/{$PRODUCT_ID}"
    const val PRODUCT_EDIT = "product/{$PRODUCT_ID}/edit"

    const val SALE_ID = "saleId"
    const val SALE_NEW = "sale/new"
    const val SALE_DETAIL = "sale/{$SALE_ID}"

    const val EXPENSE_ID = "expenseId"
    const val EXPENSE_NEW = "expense/new"
    const val EXPENSE_EDIT = "expense/{$EXPENSE_ID}/edit"
    const val RETURN_ID = "returnId"
    const val CUSTOMER_RETURNS = "returns/customer"
    const val CUSTOMER_RETURN_NEW = "returns/customer/new/{$SALE_ID}"
    const val CUSTOMER_RETURN_DETAIL = "returns/customer/{$RETURN_ID}"
    const val SUPPLIER_RETURNS = "returns/supplier"
    const val SUPPLIER_RETURN_NEW = "returns/supplier/new"
    const val SUPPLIER_RETURN_DETAIL = "returns/supplier/{$RETURN_ID}"

    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    fun customerReturnNew(saleId: Long) = "returns/customer/new/$saleId"
    fun customerReturnDetail(id: Long) = "returns/customer/$id"
    fun supplierReturnDetail(id: Long) = "returns/supplier/$id"
    fun expenseEdit(id: Long) = "expense/$id/edit"
    fun saleDetail(id: Long) = "sale/$id"
    fun productDetail(id: Long) = "product/$id"
    fun productEdit(id: Long) = "product/$id/edit"
}
