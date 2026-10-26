package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.SupplierReturnEntity
import com.tarun.managemyshop.domain.model.SupplierReturnReason
import com.tarun.managemyshop.ui.components.DateFilterChips
import com.tarun.managemyshop.ui.components.EmptyState
import com.tarun.managemyshop.ui.components.FilterChipRow
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.viewmodel.SupplierReturnsViewModel

@Composable
fun SupplierReturnsScreen(
    viewModel: SupplierReturnsViewModel,
    onBack: () -> Unit,
    onOpenReturn: (Long) -> Unit,
    onNewReturn: () -> Unit
) {
    val loaded by viewModel.supplierReturns.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val reason by viewModel.reasonFilter.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val returns = loaded.orEmpty()
    val isFiltering = query.isNotBlank() || reason != null || dateFilter.preset != DatePreset.ALL

    Scaffold(
        topBar = { SubScreenTopBar("Supplier Returns", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewReturn,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Return to Supplier") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text("Search return or supplier") },
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
                SupplierReturnReason.entries.forEach {
                    FilterChip(selected = reason == it, onClick = { viewModel.onReasonChange(it) }, label = { Text(it.label) })
                }
            }

            when {
                loaded == null -> LoadingIndicator()
                returns.isNotEmpty() -> {
                    val money = LocalCurrencyFormatter.current
                    Text(
                        "${returns.size} return${if (returns.size == 1) "" else "s"} • " +
                            "Supplier Credit ${money.format(returns.sumOf { it.totalSupplierCreditMinor })}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(returns, key = { it.id }) { SupplierReturnRow(it) { onOpenReturn(it.id) } }
                    }
                }
                isFiltering -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No matching returns",
                    message = "Try a different search, date range or reason."
                )
                else -> EmptyState(
                    icon = Icons.Filled.LocalShipping,
                    title = "No supplier returns yet",
                    message = "Tap Return to Supplier to send damaged, expired or wrong products back and record the credit."
                )
            }
        }
    }
}

@Composable
private fun SupplierReturnRow(item: SupplierReturnEntity, onClick: () -> Unit) {
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
                Text(item.returnNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                item.supplierName?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
                Text(
                    "${DateRanges.formatDateTime(item.returnDate)} • ${item.reason.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Supplier Credit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                MoneyText(item.totalSupplierCreditMinor, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
