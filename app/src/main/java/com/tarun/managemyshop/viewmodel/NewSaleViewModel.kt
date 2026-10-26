package com.tarun.managemyshop.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.domain.calc.SaleCalculator
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaleDraft
import com.tarun.managemyshop.domain.model.SaleLine
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.repository.ProductRepository
import com.tarun.managemyshop.domain.repository.SaleRepository
import com.tarun.managemyshop.domain.repository.ShopSettingsRepository
import com.tarun.managemyshop.util.Money
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartLine(val product: ProductEntity, val quantity: Int) {
    val lineTotalMinor: Long get() = product.sellingPriceMinor * quantity
}

data class SaleForm(
    val customer: String = "",
    val notes: String = "",
    val discountText: String = "",
    val payment: PaymentMethod = PaymentMethod.CASH
)

data class CartState(
    val lines: List<CartLine> = emptyList(),
    val form: SaleForm = SaleForm(),
    val subtotalMinor: Long = 0,
    val discountMinor: Long = 0,
    val discountError: String? = null,
    val taxBasisPoints: Int = 0,
    val taxMinor: Long = 0,
    val totalMinor: Long = 0,
    val stockProblem: String? = null
) {
    val itemCount: Int get() = lines.sumOf { it.quantity }
    val canCheckout: Boolean get() = lines.isNotEmpty() && discountError == null && stockProblem == null
}

@OptIn(ExperimentalCoroutinesApi::class)
class NewSaleViewModel(
    private val products: ProductRepository,
    private val sales: SaleRepository,
    settings: ShopSettingsRepository
) : ViewModel() {

    val query = MutableStateFlow("")
    private val quantities = MutableStateFlow<Map<Long, Int>>(emptyMap())
    private val form = MutableStateFlow(SaleForm())

    val saving = MutableStateFlow(false)

    /** Set once checkout succeeds; the screen then shows the success view. */
    val completedSale = MutableStateFlow<SaleEntity?>(null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val pickerProducts: StateFlow<List<ProductEntity>?> = query
        .flatMapLatest { if (it.isBlank()) products.observeAll() else products.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Cart quantity per product id, for the picker's inline steppers. */
    val cartQuantities: StateFlow<Map<Long, Int>> = quantities

    val cart: StateFlow<CartState> = combine(
        quantities, products.observeAll(), form, settings.observe()
    ) { qty, catalog, formState, shop ->
        val byId = catalog.associateBy { it.id }
        val lines = qty.mapNotNull { (id, q) -> byId[id]?.let { CartLine(it, q) } }
        val subtotal = lines.sumOf { it.lineTotalMinor }

        val parsedDiscount = if (formState.discountText.isBlank()) 0L else Money.parseToMinor(formState.discountText)
        val discountError = when {
            parsedDiscount == null -> "Enter a valid amount"
            parsedDiscount > subtotal -> "Discount can't exceed the subtotal"
            else -> null
        }
        val discount = if (discountError == null) parsedDiscount ?: 0 else 0
        val totals = SaleCalculator.calculate(subtotal, discount, shop.taxBasisPoints)
        val stockProblem = lines.firstOrNull { it.quantity > it.product.stockQuantity }?.let {
            "Only ${it.product.stockQuantity} of ${it.product.name} in stock"
        }
        CartState(
            lines = lines,
            form = formState,
            subtotalMinor = subtotal,
            discountMinor = discount,
            discountError = discountError,
            taxBasisPoints = shop.taxBasisPoints,
            taxMinor = totals.taxMinor,
            totalMinor = totals.totalMinor,
            stockProblem = stockProblem
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setQuantity(product: ProductEntity, quantity: Int) {
        val allowed = minOf(quantity, product.stockQuantity)
        if (quantity > product.stockQuantity) {
            _messages.tryEmit(
                if (product.stockQuantity <= 0) "${product.name} is out of stock"
                else "Only ${product.stockQuantity} of ${product.name} in stock"
            )
        }
        quantities.update { current ->
            if (allowed <= 0) current - product.id else current + (product.id to allowed)
        }
    }

    fun increment(product: ProductEntity) = setQuantity(product, (quantities.value[product.id] ?: 0) + 1)

    fun decrement(product: ProductEntity) = setQuantity(product, (quantities.value[product.id] ?: 0) - 1)

    fun remove(product: ProductEntity) {
        quantities.update { it - product.id }
    }

    fun onCustomerChange(value: String) = form.update { it.copy(customer = value) }
    fun onNotesChange(value: String) = form.update { it.copy(notes = value) }
    fun onDiscountChange(value: String) = form.update { it.copy(discountText = value) }
    fun onPaymentChange(value: PaymentMethod) = form.update { it.copy(payment = value) }

    fun checkout() {
        val current = cart.value
        if (!current.canCheckout || saving.value) return
        saving.value = true
        viewModelScope.launch {
            val result = sales.recordSale(
                SaleDraft(
                    lines = current.lines.map { SaleLine(it.product.id, it.quantity) },
                    customerName = current.form.customer,
                    discountMinor = current.discountMinor,
                    paymentMethod = current.form.payment,
                    notes = current.form.notes
                )
            )
            when (result) {
                is SaveResult.Success -> {
                    completedSale.value = sales.observe(result.value).first()?.sale
                    quantities.value = emptyMap()
                    form.value = SaleForm()
                }
                is SaveResult.Invalid -> _messages.emit(result.errors.values.joinToString("\n"))
            }
            saving.value = false
        }
    }

    fun startNewSale() {
        quantities.value = emptyMap()
        form.value = SaleForm()
        query.value = ""
        completedSale.value = null
    }
}
