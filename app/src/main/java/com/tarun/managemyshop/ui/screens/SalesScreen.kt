package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.data.local.entity.SaleReturnSummary
import com.tarun.managemyshop.ui.components.ReturnStatusPill
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.ui.components.DateFilterChips
import com.tarun.managemyshop.ui.components.EmptyState
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.SalesViewModel

@Composable
fun SalesScreen(
    viewModel: SalesViewModel,
    onNewSale: () -> Unit,
    onOpenSale: (Long) -> Unit
) {
    val loadedSales by viewModel.sales.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val payment by viewModel.paymentFilter.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val returnSummaries by viewModel.returnSummaries.collectAsStateWithLifecycle()
    val sales = loadedSales.orEmpty()
    val isFiltering = query.isNotBlank() || payment != null || dateFilter.preset != DatePreset.ALL

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text("Search invoice or customer") },
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
            DateFilterChips(
                filter = dateFilter,
                onPreset = viewModel::onDatePreset,
                onCustomRange = viewModel::onCustomRange
            )
            ChipRow {
                FilterChip(selected = payment == null, onClick = { viewModel.onPaymentFilterChange(null) }, label = { Text("Any payment") })
                PaymentMethod.entries.forEach { method ->
                    FilterChip(
                        selected = payment == method,
                        onClick = { viewModel.onPaymentFilterChange(method) },
                        label = { Text(method.displayName()) }
                    )
                }
            }

            when {
                loadedSales == null -> LoadingIndicator()

                sales.isNotEmpty() -> {
                    val money = LocalCurrencyFormatter.current
                    Text(
                        "${sales.size} sale${if (sales.size == 1) "" else "s"} • ${money.format(sales.sumOf { it.totalMinor })}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sales, key = { it.id }) { sale -> SaleRow(sale, returnSummaries[sale.id]) { onOpenSale(sale.id) } }
                    }
                }

                isFiltering -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No matching sales",
                    message = "Try a different search, date range or payment method."
                )

                else -> EmptyState(
                    icon = Icons.Filled.PointOfSale,
                    title = "No sales yet",
                    message = "Tap New Sale to record your first sale."
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onNewSale,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New Sale") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun SaleRow(sale: SaleEntity, returnSummary: SaleReturnSummary?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(sale.invoiceNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                sale.customerName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                Text(
                    "${DateRanges.formatDateTime(sale.saleDate)} • ${sale.paymentMethod.displayName()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MoneyText(sale.totalMinor, style = MaterialTheme.typography.titleMedium)
                returnSummary?.let { ReturnStatusPill(it.status) }
            }
        }
    }
}
