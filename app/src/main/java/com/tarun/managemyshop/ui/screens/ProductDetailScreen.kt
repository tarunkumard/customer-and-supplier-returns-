package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.StockAdjustmentEntity
import com.tarun.managemyshop.domain.model.AdjustmentType
import com.tarun.managemyshop.domain.model.DeleteResult
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.StockAdjustmentReason
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.ui.components.StockBadge
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.viewmodel.ProductViewModel
import kotlinx.coroutines.launch

private sealed interface DetailDialog {
    data object None : DetailDialog
    data object ConfirmDelete : DetailDialog
    data class DeleteBlocked(val saleCount: Int) : DetailDialog
    data object AdjustStock : DetailDialog
}

@Composable
fun ProductDetailScreen(
    productId: Long,
    viewModel: ProductViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val product by remember(productId) { viewModel.product(productId) }.collectAsStateWithLifecycle(initialValue = null)
    val history by remember(productId) { viewModel.adjustments(productId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var dialog by remember { mutableStateOf<DetailDialog>(DetailDialog.None) }
    var adjustError by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            SubScreenTopBar("Product", onBack) {
                IconButton(onClick = onEdit, enabled = product != null) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit product")
                }
                IconButton(onClick = { dialog = DetailDialog.ConfirmDelete }, enabled = product != null) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete product")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val current = product
        if (current == null) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(current)
            PriceAndStock(current)
            Button(
                onClick = { adjustError = null; dialog = DetailDialog.AdjustStock },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.SwapVert, null)
                Text("  Adjust Stock", style = MaterialTheme.typography.titleMedium)
            }
            History(history)
            Text(
                "Added ${DateRanges.formatDate(current.createdAt)} • Updated ${DateRanges.formatDate(current.updatedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (val d = dialog) {
            DetailDialog.None -> Unit
            DetailDialog.ConfirmDelete -> AlertDialog(
                onDismissRequest = { dialog = DetailDialog.None },
                title = { Text("Delete ${current.name}?") },
                text = { Text("This permanently removes the product and its stock history.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(productId) { result ->
                            when (result) {
                                DeleteResult.Deleted -> onBack()
                                is DeleteResult.InUse -> dialog = DetailDialog.DeleteBlocked(result.saleCount)
                            }
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { dialog = DetailDialog.None }) { Text("Cancel") } }
            )

            is DetailDialog.DeleteBlocked -> AlertDialog(
                onDismissRequest = { dialog = DetailDialog.None },
                title = { Text("Can't delete this product") },
                text = {
                    Text(
                        "${current.name} appears in ${d.saleCount} past sale item(s). It is kept so your sales " +
                            "history stays accurate. Set its stock to 0 with Adjust Stock if you no longer sell it."
                    )
                },
                confirmButton = { TextButton(onClick = { dialog = DetailDialog.None }) { Text("OK") } }
            )

            DetailDialog.AdjustStock -> StockAdjustmentDialog(
                product = current,
                error = adjustError,
                onDismiss = { dialog = DetailDialog.None },
                onConfirm = { type, qtyText, reason, note ->
                    val qty = qtyText.trim().toIntOrNull()
                    if (qty == null || qty <= 0) {
                        adjustError = "Enter a quantity of at least 1"
                    } else {
                        viewModel.adjustStock(productId, type, qty, reason, note) { result ->
                            when (result) {
                                is SaveResult.Success -> {
                                    dialog = DetailDialog.None
                                    scope.launch { snackbar.showSnackbar("Stock updated") }
                                }
                                is SaveResult.Invalid -> adjustError = result.errors.values.firstOrNull()
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun Header(product: ProductEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        val subtitle = listOf(product.category, product.sku).filter { it.isNotBlank() }.joinToString(" • ")
        if (subtitle.isNotEmpty()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StockBadge(product)
    }
}

@Composable
private fun PriceAndStock(product: ProductEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("In stock", product.stockQuantity.toString(), Modifier.weight(1f))
                Stat("Low stock alert at", product.lowStockThreshold.toString(), Modifier.weight(1f))
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Label("Selling price")
                    MoneyText(product.sellingPriceMinor, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f)) {
                    Label("Purchase price")
                    MoneyText(
                        product.purchasePriceMinor, style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Column {
                Label("Profit per unit")
                val margin = product.sellingPriceMinor - product.purchasePriceMinor
                MoneyText(margin, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Label(label)
        Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun History(history: List<StockAdjustmentEntity>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Stock history", style = MaterialTheme.typography.titleMedium)
        if (history.isEmpty()) {
            Text(
                "No stock adjustments yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            history.forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(entry.reason.label, style = MaterialTheme.typography.bodyLarge)
                        val detail = listOfNotNull(entry.note, DateRanges.formatDateTime(entry.createdAt)).joinToString(" • ")
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (entry.delta > 0) "+${entry.delta}" else entry.delta.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (entry.delta > 0) MaterialTheme.extraColors.positive else MaterialTheme.extraColors.warning
                        )
                        Text("now ${entry.quantityAfter}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun StockAdjustmentDialog(
    product: ProductEntity,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (AdjustmentType, String, StockAdjustmentReason, String) -> Unit
) {
    var type by remember { mutableStateOf(AdjustmentType.INCREASE) }
    var quantity by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf(StockAdjustmentReason.RESTOCK) }
    var note by remember { mutableStateOf("") }

    val qty = quantity.trim().toIntOrNull()?.takeIf { it > 0 }
    val preview = qty?.let { if (type == AdjustmentType.INCREASE) product.stockQuantity + it else product.stockQuantity - it }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust stock") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(AdjustmentType.INCREASE to "Increase Stock", AdjustmentType.REDUCE to "Reduce Stock")
                    options.forEachIndexed { index, (option, label) ->
                        SegmentedButton(
                            selected = type == option,
                            onClick = {
                                type = option
                                reason = if (option == AdjustmentType.INCREASE) StockAdjustmentReason.RESTOCK
                                else StockAdjustmentReason.DAMAGED
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size)
                        ) { Text(label, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Quantity") },
                    isError = error != null,
                    supportingText = {
                        Text(
                            error ?: preview?.let { "Stock will be $it (now ${product.stockQuantity})" }
                                ?: "Currently ${product.stockQuantity} in stock"
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Reason", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StockAdjustmentReason.entries.forEach { option ->
                        FilterChip(selected = reason == option, onClick = { reason = option }, label = { Text(option.label) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(type, quantity, reason, note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
