package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.PaymentBreakdownRow
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.TopProductRow
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.ReturnRepository
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
    /** Net sales: completed sales minus customer refunds. */
    val totalSalesMinor: Long = 0,
    val grossSalesMinor: Long = 0,
    val customerRefundsMinor: Long = 0,
    val customerReturnCount: Int = 0,
    val supplierCreditMinor: Long = 0,
    val supplierReturnCount: Int = 0,
    /** Cost of the returned customer items, taken back out of cost of goods sold. */
    val returnedCostMinor: Long = 0,
    /** Stock on hand at purchase price (right now, not for the period). */
    val inventoryValueMinor: Long = 0,
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
    products: ProductRepository,
    returns: ReturnRepository
) : ViewModel() {

    val filter = MutableStateFlow(DateFilter(DatePreset.MONTH))

    val state: StateFlow<ReportState> = filter.flatMapLatest { selected ->
        val range = selected.toRange()
        val totalExpenses = expenses.observeTotalBetween(range.start, range.end)
        combine(
            combine(
                sales.observeSummary(range.start, range.end),
                returns.observeRefundSummary(range.start, range.end),
                returns.observeSupplierCreditSummary(range.start, range.end),
                totalExpenses
            ) { summary, refunds, credit, expensesTotal -> Quad(summary, refunds, credit, expensesTotal) },
            sales.observePaymentBreakdown(range.start, range.end),
            sales.observeTopProducts(range.start, range.end, TOP_PRODUCT_LIMIT),
            products.observeLowStock(),
            products.observeInventoryValue()
        ) { (summary, refunds, credit, expensesTotal), byPayment, top, lowStock, inventoryValue ->
            ReportState(
                totalSalesMinor = summary.totalMinor - refunds.totalMinor,
                grossSalesMinor = summary.totalMinor,
                customerRefundsMinor = refunds.totalMinor,
                customerReturnCount = refunds.returnCount,
                supplierCreditMinor = credit.totalMinor,
                supplierReturnCount = credit.returnCount,
                returnedCostMinor = refunds.costMinor,
                inventoryValueMinor = inventoryValue,
                totalExpensesMinor = expensesTotal,
                // Net revenue (ex-tax) - cost of goods actually sold (returned items reverse their cost)
                // - expenses. Supplier credits are reported separately, not folded into profit.
                estimatedProfitMinor = (summary.netMinor - refunds.subtotalMinor) -
                    (summary.costMinor - refunds.costMinor) - expensesTotal,
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

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private companion object {
        const val TOP_PRODUCT_LIMIT = 5
    }
}
