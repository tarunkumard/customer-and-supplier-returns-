package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.ui.components.DateFilterChips
import com.tarun.managemyshop.ui.components.EmptyState
import com.tarun.managemyshop.ui.components.FilterChipRow
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.viewmodel.CustomerReturnsViewModel

@Composable
fun CustomerReturnsScreen(
    viewModel: CustomerReturnsViewModel,
    onBack: () -> Unit,
    onOpenReturn: (Long) -> Unit,
    onCreateReturnForSale: (Long) -> Unit
) {
    val loaded by viewModel.customerReturns.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val reason by viewModel.reasonFilter.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val pickable by viewModel.pickableSales.collectAsStateWithLifecycle()
    val pickerQuery by viewModel.pickerQuery.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    val returns = loaded.orEmpty()
    val isFiltering = query.isNotBlank() || reason != null || dateFilter.preset != DatePreset.ALL

    Scaffold(
        topBar = { SubScreenTopBar("Customer Returns", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New Return") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text("Search return, invoice or customer") },
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
            DateFilterChips(dateFilter, viewModel::onDatePreset, viewModel::onCustomRange)
            FilterChipRow {
                FilterChip(selected = reason == null, onClick = { viewModel.onReasonChange(null) }, label = { Text("Any reason") })
                CustomerReturnReason.entries.forEach {
                    FilterChip(selected = reason == it, onClick = { viewModel.onReasonChange(it) }, label = { Text(it.label) })
                }
            }

            when {
                loaded == null -> LoadingIndicator()
                returns.isNotEmpty() -> {
                    val money = LocalCurrencyFormatter.current
                    Text(
                        "${returns.size} return${if (returns.size == 1) "" else "s"} • " +
                            "Refunded ${money.format(returns.sumOf { it.totalRefundMinor })}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(returns, key = { it.id }) { CustomerReturnRow(it) { onOpenReturn(it.id) } }
                    }
                }
                isFiltering -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No matching returns",
                    message = "Try a different search, date range or reason."
                )
                else -> EmptyState(
                    icon = Icons.AutoMirrored.Filled.AssignmentReturn,
                    title = "No customer returns yet",
                    message = "Open a sale and tap Create Return, or tap New Return to pick a sale."
                )
            }
        }
    }

    if (showPicker) {
        SalePickerDialog(
            sales = pickable,
            query = pickerQuery,
            onQueryChange = viewModel::onPickerQueryChange,
            onPick = {
                showPicker = false
                onCreateReturnForSale(it)
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Composable
private fun CustomerReturnRow(item: CustomerReturnEntity, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.returnNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Invoice ${item.originalInvoiceNumber}", style = MaterialTheme.typography.bodyMedium)
                item.customerName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                Text(
                    "${DateRanges.formatDateTime(item.returnDate)} • ${item.reason.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                Text("Refund", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(-item.totalRefundMinor, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.extraColors.negative)
            }
        }
    }
}

@Composable
private fun SalePickerDialog(
    sales: List<SaleEntity>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select the original sale") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search invoice or customer") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true
                )
                if (sales.isEmpty()) {
                    Text(
                        "No sales found.",
                        Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val money = LocalCurrencyFormatter.current
                    LazyColumn(Modifier.height(320.dp)) {
                        items(sales, key = { it.id }) { sale ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(sale.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(sale.invoiceNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        listOfNotNull(sale.customerName, DateRanges.formatDate(sale.saleDate)).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(money.format(sale.totalMinor), style = MaterialTheme.typography.bodyMedium)
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
