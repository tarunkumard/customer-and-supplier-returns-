package com.tarun.managemyshop.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.ui.components.EmptyState
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.CartLine
import com.tarun.managemyshop.viewmodel.CartState
import com.tarun.managemyshop.viewmodel.NewSaleViewModel
import java.math.BigDecimal

@Composable
fun NewSaleScreen(
    viewModel: NewSaleViewModel,
    onBack: () -> Unit,
    onViewInvoice: (Long) -> Unit
) {
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val completed by viewModel.completedSale.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val money = LocalCurrencyFormatter.current

    var showCart by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    fun goBack() {
        when {
            completed != null -> onBack()
            showCart -> showCart = false
            cart.lines.isNotEmpty() -> confirmDiscard = true
            else -> onBack()
        }
    }
    BackHandler(onBack = ::goBack)

    Scaffold(
        topBar = {
            SubScreenTopBar(
                title = when {
                    completed != null -> "Sale Complete"
                    showCart -> "Cart & Checkout"
                    else -> "New Sale"
                },
                onBack = ::goBack
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val sale = completed
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                sale != null -> SuccessView(
                    invoiceNumber = sale.invoiceNumber,
                    totalMinor = sale.totalMinor,
                    onViewInvoice = { onViewInvoice(sale.id) },
                    onNewSale = {
                        showCart = false
                        viewModel.startNewSale()
                    }
                )

                showCart -> CartStep(viewModel, cart, saving, money, onAddMore = { showCart = false })
                else -> PickStep(viewModel, cart, onReview = { showCart = true })
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this sale?") },
            text = { Text("The items in your cart will be cleared.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } }
        )
    }
}

// ---------------------------------------------------------------- Step 1: pick products

@Composable
private fun PickStep(viewModel: NewSaleViewModel, cart: CartState, onReview: () -> Unit) {
    val loadedProducts by viewModel.pickerProducts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val quantities by viewModel.cartQuantities.collectAsStateWithLifecycle()
    val money = LocalCurrencyFormatter.current

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            placeholder = { Text("Search products") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        val products = loadedProducts.orEmpty()
        if (loadedProducts == null) {
            Column(Modifier.weight(1f)) { LoadingIndicator() }
        } else if (products.isEmpty()) {
            Column(Modifier.weight(1f)) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = if (query.isBlank()) "No products yet" else "No matching products",
                    message = if (query.isBlank()) "Add products in Inventory before making a sale." else "Try a different name, SKU or category."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductPickRow(
                        product = product,
                        quantity = quantities[product.id] ?: 0,
                        onAdd = { viewModel.increment(product) },
                        onMinus = { viewModel.decrement(product) },
                        onSetQuantity = { viewModel.setQuantity(product, it) }
                    )
                }
            }
        }

        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Button(
                onClick = onReview,
                enabled = cart.lines.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.ShoppingCart, null)
                Text(
                    if (cart.lines.isEmpty()) "  Add items to start"
                    else "  View Cart (${cart.itemCount}) • ${money.format(cart.subtotalMinor)}",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun ProductPickRow(
    product: ProductEntity,
    quantity: Int,
    onAdd: () -> Unit,
    onMinus: () -> Unit,
    onSetQuantity: (Int) -> Unit
) {
    val outOfStock = product.stockQuantity <= 0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MoneyText(product.sellingPriceMinor, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (outOfStock) "Out of stock" else "${product.stockQuantity} left",
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            outOfStock -> MaterialTheme.extraColors.negative
                            product.isLowStock -> MaterialTheme.extraColors.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
            when {
                outOfStock -> Unit
                quantity == 0 -> FilledTonalButton(onClick = onAdd) { Text("Add") }
                else -> QuantityStepper(quantity, onMinus, onAdd, onSetQuantity, product.stockQuantity)
            }
        }
    }
}

@Composable
private fun QuantityStepper(
    quantity: Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSetQuantity: (Int) -> Unit,
    maxQuantity: Int
) {
    var editing by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity") }
        Text(
            quantity.toString(),
            modifier = Modifier
                .clickable(onClickLabel = "Set quantity") { editing = true }
                .padding(horizontal = 6.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onPlus, enabled = quantity < maxQuantity) {
            Icon(Icons.Filled.Add, contentDescription = "Increase quantity")
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(quantity.toString()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Set quantity") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit) },
                    singleLine = true,
                    supportingText = { Text("$maxQuantity in stock") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toIntOrNull()?.let(onSetQuantity)
                    editing = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- Step 2: cart & checkout

@Composable
private fun CartStep(
    viewModel: NewSaleViewModel,
    cart: CartState,
    saving: Boolean,
    money: CurrencyFormatter,
    onAddMore: () -> Unit
) {
    if (cart.lines.isEmpty()) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f)) {
                EmptyState(Icons.Filled.ShoppingCart, "Your cart is empty", "Go back and add some products.")
            }
            Button(
                onClick = onAddMore,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp)
            ) { Text("Add Products") }
        }
        return
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(cart.lines, key = { it.product.id }) { line ->
                CartLineRow(
                    line = line,
                    onMinus = { viewModel.decrement(line.product) },
                    onPlus = { viewModel.increment(line.product) },
                    onSetQuantity = { viewModel.setQuantity(line.product, it) },
                    onRemove = { viewModel.remove(line.product) }
                )
            }
            item {
                TextButton(onClick = onAddMore) {
                    Icon(Icons.Filled.Add, null)
                    Text("  Add more items")
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cart.form.discountText,
                        onValueChange = viewModel::onDiscountChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Discount (amount)") },
                        prefix = { Text(money.currency.symbol) },
                        isError = cart.discountError != null,
                        supportingText = cart.discountError?.let { { Text(it) } },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = cart.form.customer,
                        onValueChange = viewModel::onCustomerChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Customer name (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = cart.form.notes,
                        onValueChange = viewModel::onNotesChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Notes (optional)") },
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                    )
                    Text("Payment method", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PaymentMethod.entries.forEach { method ->
                            FilterChip(
                                selected = cart.form.payment == method,
                                onClick = { viewModel.onPaymentChange(method) },
                                label = { Text(method.displayName()) }
                            )
                        }
                    }
                }
            }
            item { Totals(cart, money) }
        }

        Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cart.stockProblem?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Button(
                    onClick = viewModel::checkout,
                    enabled = cart.canCheckout && !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Complete Sale • ${money.format(cart.totalMinor)}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(
    line: CartLine,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onSetQuantity: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val money = LocalCurrencyFormatter.current
    val overStock = line.quantity > line.product.stockQuantity
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(line.product.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    Text(
                        "${money.format(line.product.sellingPriceMinor)} each",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove ${line.product.name}", tint = MaterialTheme.colorScheme.error)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                QuantityStepper(line.quantity, onMinus, onPlus, onSetQuantity, line.product.stockQuantity)
                Spacer(Modifier.weight(1f))
                MoneyText(line.lineTotalMinor, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.size(12.dp))
            }
            if (overStock) {
                Text(
                    "Only ${line.product.stockQuantity} in stock",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun Totals(cart: CartState, money: CurrencyFormatter) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TotalRow("Subtotal", money.format(cart.subtotalMinor))
            TotalRow("Discount", if (cart.discountMinor > 0) "-${money.format(cart.discountMinor)}" else money.format(0))
            val rate = BigDecimal.valueOf(cart.taxBasisPoints.toLong(), 2).stripTrailingZeros().toPlainString()
            TotalRow("Tax ($rate%)", money.format(cart.taxMinor))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            TotalRow("Total", money.format(cart.totalMinor), large = true)
        }
    }
}

@Composable
private fun TotalRow(label: String, value: String, large: Boolean = false) {
    val style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge
    val weight = if (large) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = style, fontWeight = weight)
        Text(value, style = style, fontWeight = weight)
    }
}

// ---------------------------------------------------------------- Success

@Composable
private fun SuccessView(
    invoiceNumber: String,
    totalMinor: Long,
    onViewInvoice: () -> Unit,
    onNewSale: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.CheckCircle, null, Modifier.size(88.dp), tint = MaterialTheme.extraColors.positive)
        Spacer(Modifier.height(16.dp))
        Text("Sale completed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(invoiceNumber, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        MoneyText(totalMinor, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onViewInvoice, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("View Invoice") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onNewSale, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("New Sale") }
    }
}
