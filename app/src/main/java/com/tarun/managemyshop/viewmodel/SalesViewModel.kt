package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.model.PaymentMethod
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

@OptIn(ExperimentalCoroutinesApi::class)
class SalesViewModel(private val repository: SaleRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val paymentFilter = MutableStateFlow<PaymentMethod?>(null)
    val dateFilter = MutableStateFlow(DateFilter())

    /** `null` until the first database read finishes. */
    val sales: StateFlow<List<SaleEntity>?> = combine(query, paymentFilter, dateFilter) { q, method, date ->
        Triple(q.trim(), method, date.toRange())
    }.flatMapLatest { (q, method, range) ->
        repository.observeFiltered(q, range.start, range.end, method)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onPaymentFilterChange(value: PaymentMethod?) {
        paymentFilter.value = value
    }

    fun onDatePreset(preset: DatePreset) {
        dateFilter.value = DateFilter(preset)
    }

    fun onCustomRange(from: LocalDate, to: LocalDate) {
        dateFilter.value = DateFilter.custom(from, to)
    }

    fun saleDetail(id: Long): Flow<SaleWithItems?> = repository.observe(id)
}
