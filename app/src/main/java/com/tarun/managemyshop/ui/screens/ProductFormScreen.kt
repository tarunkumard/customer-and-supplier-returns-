package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.validation.ProductValidator
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.Money
import com.tarun.managemyshop.viewmodel.ProductViewModel

/** Add (productId == null) or edit a product. Stock of an existing product changes via "Adjust stock". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormScreen(
    productId: Long?,
    viewModel: ProductViewModel,
    onBack: () -> Unit
) {
    val isEdit = productId != null
    val symbol = LocalCurrencyFormatter.current.currency.symbol
    val categories by viewModel.categories.collectAsStateWithLifecycle()

    var original by remember { mutableStateOf<ProductEntity?>(null) }
    var loaded by rememberSaveable { mutableStateOf(!isEdit) }
    var name by rememberSaveable { mutableStateOf("") }
    var sku by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var selling by rememberSaveable { mutableStateOf("") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var stock by rememberSaveable { mutableStateOf("0") }
    var threshold by rememberSaveable { mutableStateOf("5") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        if (productId == null) return@LaunchedEffect
        val product = viewModel.load(productId)
        original = product
        if (product != null && !loaded) {
            name = product.name
            sku = product.sku
            category = product.category
            selling = Money.toPlainString(product.sellingPriceMinor)
            purchase = Money.toPlainString(product.purchasePriceMinor)
            threshold = product.lowStockThreshold.toString()
        }
        loaded = true
    }

    fun submit() {
        val clientErrors = mutableMapOf<String, String>()
        fun price(text: String, key: String, label: String): Long {
            val minor = Money.parseToMinor(text)
            if (minor == null) {
                clientErrors[key] = when {
                    text.isBlank() -> "Enter the $label"
                    text.trim().startsWith("-") -> "Price cannot be negative"
                    else -> "Enter a valid amount"
                }
            }
            return minor ?: 0
        }
        val sellingMinor = price(selling, "sellingPrice", "selling price")
        val purchaseMinor = price(purchase, "purchasePrice", "purchase price")
        val stockQty = if (isEdit) original?.stockQuantity ?: 0 else stock.trim().toIntOrNull().also {
            if (it == null) clientErrors["stockQuantity"] = "Enter a whole number"
        } ?: 0
        val thresholdQty = threshold.trim().toIntOrNull().also {
            if (it == null) clientErrors["lowStockThreshold"] = "Enter a whole number"
        } ?: 0
        val product = ProductEntity(
            id = productId ?: 0,
            name = name,
            sku = sku,
            category = category,
            sellingPriceMinor = sellingMinor,
            purchasePriceMinor = purchaseMinor,
            stockQuantity = stockQty,
            lowStockThreshold = thresholdQty,
            createdAt = original?.createdAt ?: 0
        )
        // Show every problem at once: unparseable fields first, then the shared business rules.
        val allErrors = ProductValidator.validate(product.copy(name = name.trim())) + clientErrors
        if (allErrors.isNotEmpty()) {
            errors = allErrors
            return
        }
        saving = true
        viewModel.save(product) { result ->
            saving = false
            when (result) {
                is SaveResult.Success -> onBack()
                is SaveResult.Invalid -> errors = result.errors
            }
        }
    }

    Scaffold(
        topBar = { SubScreenTopBar(if (isEdit) "Edit Product" else "Add Product", onBack) }
    ) { padding ->
        if (!loaded) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormField(
                name, { name = it; errors = errors - "name" }, "Product name *", errors["name"],
                capitalization = KeyboardCapitalization.Words
            )
            FormField(category, { category = it }, "Category", errors["category"], capitalization = KeyboardCapitalization.Words)
            val suggestions = categories.filter { !it.equals(category.trim(), ignoreCase = true) }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { SuggestionChip(onClick = { category = it }, label = { Text(it) }) }
                }
            }
            FormField(sku, { sku = it }, "SKU / Barcode", errors["sku"])
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField(
                    selling, { selling = it; errors = errors - "sellingPrice" }, "Selling price *",
                    errors["sellingPrice"], Modifier.weight(1f), KeyboardType.Decimal, prefix = symbol
                )
                FormField(
                    purchase, { purchase = it; errors = errors - "purchasePrice" }, "Purchase price *",
                    errors["purchasePrice"], Modifier.weight(1f), KeyboardType.Decimal, prefix = symbol
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isEdit) {
                    OutlinedTextField(
                        value = (original?.stockQuantity ?: 0).toString(),
                        onValueChange = {},
                        modifier = Modifier.weight(1f),
                        label = { Text("Current stock") },
                        supportingText = { Text("Use Adjust Stock to change") },
                        readOnly = true,
                        enabled = false
                    )
                } else {
                    FormField(
                        stock, { stock = it; errors = errors - "stockQuantity" }, "Opening stock",
                        errors["stockQuantity"], Modifier.weight(1f), KeyboardType.Number
                    )
                }
                FormField(
                    threshold, { threshold = it; errors = errors - "lowStockThreshold" }, "Low stock alert at",
                    errors["lowStockThreshold"], Modifier.weight(1f), KeyboardType.Number, last = true
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = ::submit,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text(if (isEdit) "Save Changes" else "Save Product", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    prefix: String? = null,
    last: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(if (modifier == Modifier) Modifier.fillMaxWidth() else Modifier),
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = if (last) ImeAction.Done else ImeAction.Next
        )
    )
}
