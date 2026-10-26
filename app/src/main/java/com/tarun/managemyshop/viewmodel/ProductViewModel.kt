package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.DeleteResult
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.StockAdjustmentReason
import com.tarun.managemyshop.domain.repository.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StockFilter(val label: String) {
    ALL("All"),
    IN_STOCK("In Stock"),
    LOW_STOCK("Low Stock")
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModel(private val repository: ProductRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val filter = MutableStateFlow(StockFilter.ALL)

    /** `null` until the first database read finishes (drives the loading spinner). */
    val products: StateFlow<List<ProductEntity>?> = combine(
        query.flatMapLatest { if (it.isBlank()) repository.observeAll() else repository.search(it) },
        filter
    ) { list, selected ->
        when (selected) {
            StockFilter.ALL -> list
            StockFilter.IN_STOCK -> list.filter { it.stockQuantity > 0 }
            StockFilter.LOW_STOCK -> list.filter { it.isLowStock }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Existing categories, offered as quick suggestions in the product form. */
    val categories: StateFlow<List<String>> = repository.observeAll()
        .map { list -> list.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: StockFilter) {
        filter.value = value
    }

    fun product(id: Long): Flow<ProductEntity?> = repository.observe(id)

    fun adjustments(productId: Long): Flow<List<StockAdjustmentEntity>> = repository.observeAdjustments(productId)

    suspend fun load(id: Long): ProductEntity? = repository.getById(id)

    fun save(product: ProductEntity, onResult: (SaveResult<Long>) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.save(product)) }
    }

    fun delete(id: Long, onResult: (DeleteResult) -> Unit = {}) {
        viewModelScope.launch { onResult(repository.delete(id)) }
    }

    fun adjustStock(
        productId: Long,
        type: AdjustmentType,
        quantity: Int,
        reason: StockAdjustmentReason,
        note: String?,
        onResult: (SaveResult<Unit>) -> Unit
    ) {
        viewModelScope.launch { onResult(repository.adjustStock(productId, type, quantity, reason, note)) }
    }
}
