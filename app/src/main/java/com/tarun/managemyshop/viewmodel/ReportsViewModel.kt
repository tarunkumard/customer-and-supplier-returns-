package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.PaymentBreakdownRow
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.TopProductRow
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.util.DateFilter
import com.tarun.managemyshop.util.DatePreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ReportState(
    val totalSalesMinor: Long = 0,
    val totalExpensesMinor: Long = 0,
    val estimatedProfitMinor: Long = 0,
    val saleCount: Int = 0,
    val averageOrderMinor: Long = 0,
    val paymentBreakdown: List<PaymentBreakdownRow> = emptyList(),
    val topProducts: List<TopProductRow> = emptyList(),
    val lowStock: List<ProductEntity> = emptyList(),
    val loaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(
    sales: SaleRepository,
    expenses: ExpenseRepository,
    products: ProductRepository
) : ViewModel() {

    val filter = MutableStateFlow(DateFilter(DatePreset.MONTH))

    val state: StateFlow<ReportState> = filter.flatMapLatest { selected ->
        val range = selected.toRange()
        val totalExpenses = expenses.observeTotalBetween(range.start, range.end)
        combine(
            sales.observeSummary(range.start, range.end),
            totalExpenses,
            sales.observePaymentBreakdown(range.start, range.end),
            sales.observeTopProducts(range.start, range.end, TOP_PRODUCT_LIMIT),
            products.observeLowStock()
        ) { summary, expensesTotal, byPayment, top, lowStock ->
            ReportState(
                totalSalesMinor = summary.totalMinor,
                totalExpensesMinor = expensesTotal,
                // Revenue excludes tax collected; cost is the purchase price snapshot of items sold.
                estimatedProfitMinor = summary.netMinor - summary.costMinor - expensesTotal,
                saleCount = summary.saleCount,
                averageOrderMinor = if (summary.saleCount == 0) 0 else (summary.totalMinor + summary.saleCount / 2) / summary.saleCount,
                paymentBreakdown = byPayment,
                topProducts = top,
                lowStock = lowStock,
                loaded = true
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    fun onPreset(preset: DatePreset) {
        filter.value = DateFilter(preset)
    }

    fun onCustomRange(from: LocalDate, to: LocalDate) {
        filter.value = DateFilter.custom(from, to)
    }

    private companion object {
        const val TOP_PRODUCT_LIMIT = 5
    }
}
