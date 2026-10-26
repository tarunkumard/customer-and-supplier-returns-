package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.data.local.entity.SupplierReturnWithItems
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.domain.repository.ReturnRepository
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

/** The Supplier Returns list (filter by search, date and reason) and a return's receipt. */
@OptIn(ExperimentalCoroutinesApi::class)
class SupplierReturnsViewModel(private val returns: ReturnRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val reasonFilter = MutableStateFlow<SupplierReturnReason?>(null)
    val dateFilter = MutableStateFlow(DateFilter())

    /** `null` until the first database read finishes. */
    val supplierReturns: StateFlow<List<SupplierReturnEntity>?> =
        combine(query, reasonFilter, dateFilter) { q, reason, date -> Triple(q.trim(), reason, date.toRange()) }
            .flatMapLatest { (q, reason, range) -> returns.observeSupplierReturns(q, range.start, range.end, reason) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onQueryChange(value: String) { query.value = value }
    fun onReasonChange(value: SupplierReturnReason?) { reasonFilter.value = value }
    fun onDatePreset(preset: DatePreset) { dateFilter.value = DateFilter(preset) }
    fun onCustomRange(from: LocalDate, to: LocalDate) { dateFilter.value = DateFilter.custom(from, to) }

    fun detail(id: Long): Flow<SupplierReturnWithItems?> = returns.observeSupplierReturn(id)
}
