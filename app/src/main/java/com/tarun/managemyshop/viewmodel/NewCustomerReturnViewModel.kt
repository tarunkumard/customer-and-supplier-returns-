package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.calc.ReturnCalculator
import com.tarun.managemyshop.domain.model.CustomerReturnDraft
import com.tarun.managemyshop.domain.model.CustomerReturnLine
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.repository.ReturnRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One returnable line of the original sale. */
data class ReturnLineState(
    val saleItemId: Long,
    val productName: String,
    val unitPriceMinor: Long,
    val soldQuantity: Int,
    val alreadyReturned: Int,
    val quantity: Int
) {
    /** Units that can still come back: sold minus what earlier returns already took. */
    val returnable: Int get() = (soldQuantity - alreadyReturned).coerceAtLeast(0)
}

data class NewCustomerReturnState(
    val sale: SaleWithItems? = null,
    val lines: List<ReturnLineState> = emptyList(),
    val returnNumber: String = "",
    val reason: CustomerReturnReason = CustomerReturnReason.CUSTOMER_CHANGED_MIND,
    val refundMethod: PaymentMethod = PaymentMethod.CASH,
    val customerName: String = "",
    val notes: String = "",
    val refund: ReturnCalculator.Refund = ReturnCalculator.Refund(0, 0),
    val error: String? = null,
    val saving: Boolean = false,
    /** Set once the return is saved; the screen then opens its receipt. */
    val createdReturnId: Long? = null
) {
    val loaded: Boolean get() = sale != null
    val unitsSelected: Int get() = lines.sumOf { it.quantity }
    val anythingReturnable: Boolean get() = lines.any { it.returnable > 0 }
}

private data class Form(
    val quantities: Map<Long, Int> = emptyMap(),
    val reason: CustomerReturnReason = CustomerReturnReason.CUSTOMER_CHANGED_MIND,
    val refundMethod: PaymentMethod = PaymentMethod.CASH,
    val customerName: String? = null,
    val notes: String = "",
    val error: String? = null,
    val saving: Boolean = false,
    val createdReturnId: Long? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class NewCustomerReturnViewModel(
    private val returns: ReturnRepository,
    sales: SaleRepository
) : ViewModel() {

    private val saleId = MutableStateFlow<Long?>(null)
    private val form = MutableStateFlow(Form())
    private val returnNumber = MutableStateFlow("")

    val state: StateFlow<NewCustomerReturnState> = saleId.filterNotNull().flatMapLatest { id ->
        combine(sales.observe(id), returns.observeReturnedQuantities(id), form, returnNumber) { sale, returned, f, number ->
            if (sale == null) return@combine NewCustomerReturnState()
            val previouslyReturned = returned.associate { it.originalSaleItemId to it.quantity }
            val lines = sale.items.map { item ->
                val already = previouslyReturned[item.id] ?: 0
                val max = (item.quantity - already).coerceAtLeast(0)
                ReturnLineState(
                    saleItemId = item.id,
                    productName = item.productNameSnapshot,
                    unitPriceMinor = item.unitPriceMinor,
                    soldQuantity = item.quantity,
                    alreadyReturned = already,
                    quantity = (f.quantities[item.id] ?: 0).coerceIn(0, max)
                )
            }
            val s = sale.sale
            val refund = ReturnCalculator.incrementalRefund(
                subtotalMinor = s.subtotalMinor,
                discountMinor = s.discountMinor,
                taxMinor = s.taxMinor,
                previousGrossMinor = lines.sumOf { it.unitPriceMinor * it.alreadyReturned },
                newGrossMinor = lines.sumOf { it.unitPriceMinor * it.quantity }
            )
            NewCustomerReturnState(
                sale = sale,
                lines = lines,
                returnNumber = number,
                reason = f.reason,
                refundMethod = f.refundMethod,
                customerName = f.customerName ?: s.customerName.orEmpty(),
                notes = f.notes,
                refund = refund,
                error = f.error,
                saving = f.saving,
                createdReturnId = f.createdReturnId
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewCustomerReturnState())

    /** Call when the screen opens for [id]; resets the form when the sale changes. */
    fun start(id: Long) {
        if (saleId.value == id) return
        form.value = Form()
        saleId.value = id
        viewModelScope.launch { returnNumber.value = returns.nextCustomerReturnNumber() }
    }

    fun setQuantity(saleItemId: Long, quantity: Int) {
        val line = state.value.lines.firstOrNull { it.saleItemId == saleItemId } ?: return
        form.update {
            it.copy(quantities = it.quantities + (saleItemId to quantity.coerceIn(0, line.returnable)), error = null)
        }
    }

    /** Full return: every unit that is still returnable. */
    fun returnEverything() {
        val all = state.value.lines.associate { it.saleItemId to it.returnable }
        form.update { it.copy(quantities = all, error = null) }
    }

    fun clearSelection() = form.update { it.copy(quantities = emptyMap(), error = null) }
    fun setReason(value: CustomerReturnReason) = form.update { it.copy(reason = value) }
    fun setRefundMethod(value: PaymentMethod) = form.update { it.copy(refundMethod = value) }
    fun setCustomerName(value: String) = form.update { it.copy(customerName = value) }
    fun setNotes(value: String) = form.update { it.copy(notes = value) }

    fun confirm() {
        val current = state.value
        val sale = current.sale ?: return
        if (current.saving || current.unitsSelected == 0) return
        form.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = returns.createCustomerReturn(
                CustomerReturnDraft(
                    originalSaleId = sale.sale.id,
                    customerName = current.customerName,
                    refundMethod = current.refundMethod,
                    reason = current.reason,
                    notes = current.notes,
                    lines = current.lines.filter { it.quantity > 0 }.map { CustomerReturnLine(it.saleItemId, it.quantity) }
                )
            )
            form.update {
                when (result) {
                    is SaveResult.Success -> it.copy(saving = false, createdReturnId = result.value)
                    is SaveResult.Invalid -> it.copy(saving = false, error = result.errors.values.first())
                }
            }
        }
    }
}
