package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.CustomerReturnWithItems
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.repository.ReturnRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.util.DateFilter
import com.tarun.managemyshop.util.DatePreset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** The Customer Returns list (filter by search, date and reason), a return's receipt, and the sale picker. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerReturnsViewModel(
    private val returns: ReturnRepository,
    private val sales: SaleRepository
) : ViewModel() {

    val query = MutableStateFlow("")
    val reasonFilter = MutableStateFlow<CustomerReturnReason?>(null)
    val dateFilter = MutableStateFlow(DateFilter())
    val pickerQuery = MutableStateFlow("")

    /** `null` until the first database read finishes. */
    val customerReturns: StateFlow<List<CustomerReturnEntity>?> =
        combine(query, reasonFilter, dateFilter) { q, reason, date -> Triple(q.trim(), reason, date.toRange()) }
            .flatMapLatest { (q, reason, range) -> returns.observeCustomerReturns(q, range.start, range.end, reason) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Sales to start a return from, newest first. */
    val pickableSales: StateFlow<List<SaleEntity>> = pickerQuery
        .flatMapLatest { q -> sales.observeFiltered(q.trim(), Long.MIN_VALUE, Long.MAX_VALUE, null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) { query.value = value }
    fun onReasonChange(value: CustomerReturnReason?) { reasonFilter.value = value }
    fun onDatePreset(preset: DatePreset) { dateFilter.value = DateFilter(preset) }
    fun onCustomRange(from: LocalDate, to: LocalDate) { dateFilter.value = DateFilter.custom(from, to) }
    fun onPickerQueryChange(value: String) { pickerQuery.value = value }

    fun detail(id: Long): Flow<CustomerReturnWithItems?> = returns.observeCustomerReturn(id)
}
