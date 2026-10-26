package com.tarun.managemyshop.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.ui.components.DateFilterChips
import com.tarun.managemyshop.ui.components.EmptyState
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.ExpenseViewModel

@Composable
fun ExpensesScreen(
    viewModel: ExpenseViewModel,
    onAddExpense: () -> Unit,
    onOpenExpense: (Long) -> Unit
) {
    val loadedExpenses by viewModel.expenses.collectAsStateWithLifecycle()
    val monthTotal by viewModel.monthTotalMinor.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val expenses = loadedExpenses.orEmpty()
    val isFiltering = query.isNotBlank() || dateFilter.preset != DatePreset.ALL
    val money = LocalCurrencyFormatter.current

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Expenses this month",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    MoneyText(
                        monthTotal, style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                placeholder = { Text("Search title, category or notes") },
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

            when {
                loadedExpenses == null -> LoadingIndicator()

                expenses.isNotEmpty() -> {
                    if (isFiltering) {
                        Text(
                            "${expenses.size} expense${if (expenses.size == 1) "" else "s"} • ${money.format(expenses.sumOf { it.amountMinor })}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(expenses, key = { it.id }) { ExpenseRow(it) { onOpenExpense(it.id) } }
                    }
                }

                isFiltering -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No matching expenses",
                    message = "Try a different search or date range."
                )

                else -> EmptyState(
                    icon = Icons.Filled.Payments,
                    title = "No expenses yet",
                    message = "Track rent, supplies and other costs. Tap Add Expense to start."
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = onAddExpense,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Add Expense") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity, onClick: () -> Unit) {
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
                Text(expense.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Text(
                    listOf(expense.category, DateRanges.formatDate(expense.expenseDate), expense.paymentMethod.displayName())
                        .filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(expense.amountMinor, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
