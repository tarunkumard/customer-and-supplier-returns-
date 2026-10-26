package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.SupplierReturnDraft
import com.tarun.managemyshop.domain.model.SupplierReturnLine
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.ReturnRepository
import com.tarun.managemyshop.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A product being sent back; quantity and cost are kept as typed so editing feels natural. */
data class SupplierLineState(
    val productId: Long,
    val productName: String,
    val stockQuantity: Int,
    val quantityText: String = "1",
    val unitCostText: String
) {
    val quantity: Int? get() = quantityText.trim().toIntOrNull()?.takeIf { it > 0 }
    val unitCostMinor: Long? get() = Money.parseToMinor(unitCostText)
    val creditMinor: Long get() = (unitCostMinor ?: 0L) * (quantity ?: 0)
    val quantityError: String?
        get() = when {
            quantity == null -> "Enter a quantity of at least 1"
            quantity!! > stockQuantity -> "Only $stockQuantity in stock"
            else -> null
        }
    val costError: String? get() = if (unitCostMinor == null) "Enter a valid cost" else null
}

data class NewSupplierReturnState(
    val supplierName: String = "",
    val reason: SupplierReturnReason = SupplierReturnReason.DAMAGED,
    val notes: String = "",
    val lines: List<SupplierLineState> = emptyList(),
    val returnNumber: String = "",
    val error: String? = null,
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val createdReturnId: Long? = null
) {
    val totalCreditMinor: Long get() = lines.sumOf { it.creditMinor }
    val unitsSelected: Int get() = lines.sumOf { it.quantity ?: 0 }
    val isValid: Boolean get() = lines.isNotEmpty() && lines.all { it.quantityError == null && it.costError == null }
}

@OptIn(ExperimentalCoroutinesApi::class)
class NewSupplierReturnViewModel(
    private val returns: ReturnRepository,
    private val products: ProductRepository
) : ViewModel() {

    private val _state = MutableStateFlow(NewSupplierReturnState())
    val state: StateFlow<NewSupplierReturnState> = _state

    val pickerQuery = MutableStateFlow("")

    /** Products that can be picked: only those with stock to send back. */
    val pickableProducts: StateFlow<List<ProductEntity>> = pickerQuery
        .flatMapLatest { products.search(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { _state.update { it.copy(returnNumber = returns.nextSupplierReturnNumber()) } }
    }

    fun onPickerQueryChange(value: String) { pickerQuery.value = value }
    fun setSupplierName(value: String) = _state.update { it.copy(supplierName = value) }
    fun setReason(value: SupplierReturnReason) = _state.update { it.copy(reason = value) }
    fun setNotes(value: String) = _state.update { it.copy(notes = value) }

    fun addProduct(product: ProductEntity) = _state.update { s ->
        if (s.lines.any { it.productId == product.id }) s
        else s.copy(
            lines = s.lines + SupplierLineState(
                productId = product.id,
                productName = product.name,
                stockQuantity = product.stockQuantity,
                unitCostText = Money.toPlainString(product.purchasePriceMinor)
            ),
            error = null
        )
    }

    fun removeProduct(productId: Long) = _state.update { s -> s.copy(lines = s.lines.filter { it.productId != productId }) }

    fun setQuantity(productId: Long, text: String) = updateLine(productId) { it.copy(quantityText = text.filter(Char::isDigit)) }
    fun setUnitCost(productId: Long, text: String) = updateLine(productId) { it.copy(unitCostText = text) }

    private fun updateLine(productId: Long, change: (SupplierLineState) -> SupplierLineState) =
        _state.update { s -> s.copy(lines = s.lines.map { if (it.productId == productId) change(it) else it }, error = null) }

    /** Returns true when the form is valid and the confirmation dialog may be shown. */
    fun validate(): Boolean {
        _state.update { it.copy(showErrors = true) }
        return _state.value.isValid
    }

    fun confirm() {
        val s = _state.value
        if (s.saving || !s.isValid) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = returns.createSupplierReturn(
                SupplierReturnDraft(
                    supplierName = s.supplierName,
                    reason = s.reason,
                    notes = s.notes,
                    lines = s.lines.map { SupplierReturnLine(it.productId, it.quantity!!, it.unitCostMinor!!) }
                )
            )
            _state.update {
                when (result) {
                    is SaveResult.Success -> it.copy(saving = false, createdReturnId = result.value)
                    is SaveResult.Invalid -> it.copy(saving = false, error = result.errors.values.first())
                }
            }
        }
    }
}
