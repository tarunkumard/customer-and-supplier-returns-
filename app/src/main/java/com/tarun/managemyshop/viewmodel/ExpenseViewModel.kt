package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.repository.ExpenseRepository
import com.tarun.managemyshop.util.DateFilter
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.DateRanges
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val dateFilter = MutableStateFlow(DateFilter())

    /** `null` until the first database read finishes. */
    val expenses: StateFlow<List<ExpenseEntity>?> = combine(query, dateFilter) { q, date ->
        q.trim() to date.toRange()
    }.flatMapLatest { (q, range) ->
        repository.observeFiltered(q, range.start, range.end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Current calendar month, independent of the list filters. */
    val monthTotalMinor: StateFlow<Long> = DateRanges.todayFlow().flatMapLatest { today ->
        val month = DateRanges.monthOf(today)
        repository.observeTotalBetween(month.start, month.end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onDatePreset(preset: DatePreset) {
        dateFilter.value = DateFilter(preset)
    }

    fun onCustomRange(from: LocalDate, to: LocalDate) {
        dateFilter.value = DateFilter.custom(from, to)
    }

    suspend fun load(id: Long): ExpenseEntity? = repository.getById(id)

    fun save(expense: ExpenseEntity, onResult: (SaveResult<Long>) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.save(expense)) }
    }

    fun delete(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }
}
