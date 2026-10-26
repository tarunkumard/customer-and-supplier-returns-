package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.RefundSummary
import com.tarun.managemyshop.data.local.entity.SaleSummary
import com.tarun.managemyshop.data.local.entity.SupplierCreditSummary
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.ReturnRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import com.tarun.managemyshop.util.DateRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** One row of the dashboard's recent activity: a sale or a return, newest first. */
sealed interface ActivityItem {
    val date: Long

    data class Sale(val sale: SaleEntity) : ActivityItem {
        override val date get() = sale.saleDate
    }

    data class CustomerReturn(val customerReturn: CustomerReturnEntity) : ActivityItem {
        override val date get() = customerReturn.returnDate
    }

    data class SupplierReturn(val supplierReturn: SupplierReturnEntity) : ActivityItem {
        override val date get() = supplierReturn.returnDate
    }
}

data class DashboardState(
    val shopName: String = "",
    /** Net sales: completed sales minus customer refunds. */
    val todaySalesMinor: Long = 0,
    val monthSalesMinor: Long = 0,
    val monthRefundsMinor: Long = 0,
    val monthSupplierCreditMinor: Long = 0,
    val monthExpensesMinor: Long = 0,
    val estimatedProfitMinor: Long = 0,
    val lowStock: List<ProductEntity> = emptyList(),
    val recentActivity: List<ActivityItem> = emptyList(),
    val loaded: Boolean = false
)

private data class Totals(
    val today: Long,
    val todayRefunds: Long,
    val month: SaleSummary,
    val monthRefunds: RefundSummary,
    val supplierCredit: SupplierCreditSummary,
    val expenses: Long
)

private data class Activity(
    val sales: List<SaleEntity>,
    val customerReturns: List<CustomerReturnEntity>,
    val supplierReturns: List<SupplierReturnEntity>
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    settings: ShopSettingsRepository,
    sales: SaleRepository,
    expenses: ExpenseRepository,
    products: ProductRepository,
    returns: ReturnRepository
) : ViewModel() {

    private val totals = DateRanges.todayFlow().flatMapLatest { today ->
        val day = DateRanges.day(today)
        val month = DateRanges.monthOf(today)
        combine(
            sales.observeTotalBetween(day.start, day.end),
            returns.observeRefundSummary(day.start, day.end),
            sales.observeSummary(month.start, month.end),
            returns.observeRefundSummary(month.start, month.end),
            returns.observeSupplierCreditSummary(month.start, month.end),
            expenses.observeTotalBetween(month.start, month.end)
        ) { values ->
            Totals(
                today = values[0] as Long,
                todayRefunds = (values[1] as RefundSummary).totalMinor,
                month = values[2] as SaleSummary,
                monthRefunds = values[3] as RefundSummary,
                supplierCredit = values[4] as SupplierCreditSummary,
                expenses = values[5] as Long
            )
        }
    }

    private val activity = combine(
        sales.observeRecent(RECENT_LIMIT),
        returns.observeRecentCustomerReturns(RECENT_LIMIT),
        returns.observeRecentSupplierReturns(RECENT_LIMIT),
        ::Activity
    )

    val state: StateFlow<DashboardState> = combine(
        settings.observe(),
        totals,
        products.observeLowStock(),
        activity
    ) { shop, t, lowStock, recent ->
        DashboardState(
            shopName = shop.shopName,
            // Net sales = completed sales minus customer refunds (refunds count on the day they are given).
            todaySalesMinor = t.today - t.todayRefunds,
            monthSalesMinor = t.month.totalMinor - t.monthRefunds.totalMinor,
            monthRefundsMinor = t.monthRefunds.totalMinor,
            monthSupplierCreditMinor = t.supplierCredit.totalMinor,
            monthExpensesMinor = t.expenses,
            // Profit = net revenue (ex-tax) - cost of goods actually sold - expenses. Returned items
            // reverse their cost; supplier credits are reported separately and are not part of profit.
            estimatedProfitMinor = (t.month.netMinor - t.monthRefunds.subtotalMinor) -
                (t.month.costMinor - t.monthRefunds.costMinor) - t.expenses,
            lowStock = lowStock,
            recentActivity = (recent.sales.map(ActivityItem::Sale) +
                recent.customerReturns.map(ActivityItem::CustomerReturn) +
                recent.supplierReturns.map(ActivityItem::SupplierReturn))
                .sortedByDescending { it.date }
                .take(RECENT_LIMIT),
            loaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    private companion object {
        const val RECENT_LIMIT = 6
    }
}
