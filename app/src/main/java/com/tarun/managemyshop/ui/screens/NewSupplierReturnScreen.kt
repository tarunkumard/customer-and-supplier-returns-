package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.ui.components.ConfirmReturnDialog
import com.tarun.managemyshop.ui.components.FilterChipRow
import com.tarun.managemyshop.ui.components.InfoCard
import com.tarun.managemyshop.ui.components.LabelValueRow
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.viewmodel.NewSupplierReturnViewModel
import com.tarun.managemyshop.viewmodel.SupplierLineState

@Composable
fun NewSupplierReturnScreen(
    viewModel: NewSupplierReturnViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickable by viewModel.pickableProducts.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
    val money = LocalCurrencyFormatter.current
    var picking by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdReturnId) { state.createdReturnId?.let(onCreated) }

    Scaffold(topBar = { SubScreenTopBar("Return to Supplier", onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard {
                LabelValueRow("Return number", state.returnNumber.ifEmpty { "…" }, bold = true)
                OutlinedTextField(
                    value = state.supplierName,
                    onValueChange = viewModel::setSupplierName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Supplier / wholesaler (optional)") },
                    singleLine = true
                )
                Text("Reason", style = MaterialTheme.typography.titleMedium)
                FilterChipRow(Modifier.padding(horizontal = 0.dp)) {
                    SupplierReturnReason.entries.forEach {
                        FilterChip(selected = state.reason == it, onClick = { viewModel.setReason(it) }, label = { Text(it.label) })
                    }
                }
            }

            InfoCard(title = "Products to return") {
                if (state.lines.isEmpty()) {
                    Text(
                        "No products added yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.showErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider()
                    SupplierLineEditor(
                        line = line,
                        showErrors = state.showErrors,
                        onQuantity = { viewModel.setQuantity(line.productId, it) },
                        onUnitCost = { viewModel.setUnitCost(line.productId, it) },
                        onRemove = { viewModel.removeProduct(line.productId) }
                    )
                }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("  Add product")
                }
            }

            InfoCard(title = "Summary") {
                LabelValueRow("Stock to remove", "${state.unitsSelected} unit${if (state.unitsSelected == 1) "" else "s"}")
                Row(Modifier.fillMaxWidth()) {
                    Text("Supplier Credit", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        money.format(state.totalCreditMinor), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.extraColors.positive
                    )
                }
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") },
                    minLines = 2
                )
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = { if (viewModel.validate()) confirming = true },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Review & Confirm Return", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (picking) {
        ProductPickerDialog(
            products = pickable,
            alreadyAdded = state.lines.map { it.productId }.toSet(),
            query = pickerQuery,
            onQueryChange = viewModel::onPickerQueryChange,
            onPick = {
                viewModel.addProduct(it)
                picking = false
            },
            onDismiss = { picking = false }
        )
    }

    if (confirming) {
        ConfirmReturnDialog(
            title = "Return goods to supplier?",
            message = "${state.unitsSelected} unit${if (state.unitsSelected == 1) "" else "s"} will be removed from inventory.\n" +
                "Supplier Credit recorded: ${money.format(state.totalCreditMinor)}.\n\n" +
                "This is not a customer refund, and it cannot be undone.",
            confirmLabel = "Confirm return",
            onConfirm = {
                confirming = false
                viewModel.confirm()
            },
            onDismiss = { confirming = false }
        )
    }
}

@Composable
private fun SupplierLineEditor(
    line: SupplierLineState,
    showErrors: Boolean,
    onQuantity: (String) -> Unit,
    onUnitCost: (String) -> Unit,
    onRemove: () -> Unit
) {
    val money = LocalCurrencyFormatter.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "In stock: ${line.stockQuantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${line.productName}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = line.quantityText,
                onValueChange = onQuantity,
                modifier = Modifier.weight(1f),
                label = { Text("Quantity") },
                isError = showErrors && line.quantityError != null,
                supportingText = if (showErrors) line.quantityError?.let { { Text(it) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = line.unitCostText,
                onValueChange = onUnitCost,
                modifier = Modifier.weight(1f),
                label = { Text("Unit cost") },
                prefix = { Text(money.currency.symbol) },
                isError = showErrors && line.costError != null,
                supportingText = if (showErrors) line.costError?.let { { Text(it) } } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        }
        LabelValueRow("Line credit", money.format(line.creditMinor))
    }
}

@Composable
private fun ProductPickerDialog(
    products: List<ProductEntity>,
    alreadyAdded: Set<Long>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (ProductEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add product") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search products") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true
                )
                if (products.isEmpty()) {
                    Text(
                        "No products found.",
                        Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(Modifier.height(320.dp)) {
                        items(products, key = { it.id }) { product ->
                            val selectable = product.id !in alreadyAdded && product.stockQuantity > 0
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectable) { onPick(product) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        product.name, style = MaterialTheme.typography.bodyLarge,
                                        color = if (selectable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                                    )
                                    Text(
                                        when {
                                            product.id in alreadyAdded -> "Already added"
                                            product.stockQuantity <= 0 -> "Out of stock"
                                            else -> "In stock: ${product.stockQuantity}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
