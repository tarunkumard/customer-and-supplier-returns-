package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleSummary
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import com.tarun.managemyshop.util.DateRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class DashboardState(
    val shopName: String = "",
    val todaySalesMinor: Long = 0,
    val monthSalesMinor: Long = 0,
    val monthExpensesMinor: Long = 0,
    val estimatedProfitMinor: Long = 0,
    val lowStock: List<ProductEntity> = emptyList(),
    val recentSales: List<SaleEntity> = emptyList(),
    val loaded: Boolean = false
)

private data class Totals(val today: Long, val month: SaleSummary, val expenses: Long)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    settings: ShopSettingsRepository,
    sales: SaleRepository,
    expenses: ExpenseRepository,
    products: ProductRepository
) : ViewModel() {

    private val totals = DateRanges.todayFlow().flatMapLatest { today ->
        val day = DateRanges.day(today)
        val month = DateRanges.monthOf(today)
        combine(
            sales.observeTotalBetween(day.start, day.end),
            sales.observeSummary(month.start, month.end),
            expenses.observeTotalBetween(month.start, month.end),
            ::Totals
        )
    }

    val state: StateFlow<DashboardState> = combine(
        settings.observe(),
        totals,
        products.observeLowStock(),
        sales.observeRecent(RECENT_SALES_LIMIT)
    ) { shop, t, lowStock, recent ->
        DashboardState(
            shopName = shop.shopName,
            todaySalesMinor = t.today,
            monthSalesMinor = t.month.totalMinor,
            monthExpensesMinor = t.expenses,
            // Profit = revenue (ex-tax) - purchase cost of goods sold - expenses, for this month.
            estimatedProfitMinor = t.month.netMinor - t.month.costMinor - t.expenses,
            lowStock = lowStock,
            recentSales = recent,
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private companion object {
        const val RECENT_SALES_LIMIT = 5
    }
}
