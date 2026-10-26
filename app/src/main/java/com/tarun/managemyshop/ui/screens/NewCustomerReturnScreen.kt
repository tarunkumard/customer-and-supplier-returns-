package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.domain.model.CustomerReturnReason
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.ui.components.ConfirmReturnDialog
import com.tarun.managemyshop.ui.components.FilterChipRow
import com.tarun.managemyshop.ui.components.InfoCard
import com.tarun.managemyshop.ui.components.LabelValueRow
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.NewCustomerReturnViewModel
import com.tarun.managemyshop.viewmodel.ReturnLineState

@Composable
fun NewCustomerReturnScreen(
    saleId: Long,
    viewModel: NewCustomerReturnViewModel,
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    LaunchedEffect(saleId) { viewModel.start(saleId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val money = LocalCurrencyFormatter.current
    var confirming by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdReturnId) { state.createdReturnId?.let(onCreated) }

    Scaffold(topBar = { SubScreenTopBar("Customer Return", onBack) }) { padding ->
        val sale = state.sale
        if (sale == null) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard {
                LabelValueRow("Original invoice", sale.sale.invoiceNumber, bold = true)
                LabelValueRow("Sale date", DateRanges.formatDateTime(sale.sale.saleDate))
                LabelValueRow("Return number", state.returnNumber.ifEmpty { "…" }, bold = true)
            }

            if (!state.anythingReturnable) {
                InfoCard {
                    Text(
                        "Every item on this sale has already been returned. There is nothing left to return.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Column
            }

            InfoCard(title = "Items to return") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::returnEverything) { Text("Return all") }
                    OutlinedButton(onClick = viewModel::clearSelection, enabled = state.unitsSelected > 0) { Text("Clear") }
                }
                state.lines.forEachIndexed { index, line ->
                    if (index > 0) HorizontalDivider()
                    ReturnLineRow(line, onChange = { viewModel.setQuantity(line.saleItemId, it) })
                }
            }

            InfoCard(title = "Reason") {
                FilterChipRow(Modifier.padding(horizontal = 0.dp)) {
                    CustomerReturnReason.entries.forEach {
                        FilterChip(selected = state.reason == it, onClick = { viewModel.setReason(it) }, label = { Text(it.label) })
                    }
                }
                Text("Refund method", style = MaterialTheme.typography.titleMedium)
                FilterChipRow(Modifier.padding(horizontal = 0.dp)) {
                    PaymentMethod.entries.forEach {
                        FilterChip(selected = state.refundMethod == it, onClick = { viewModel.setRefundMethod(it) }, label = { Text(it.displayName()) })
                    }
                }
                OutlinedTextField(
                    value = state.customerName,
                    onValueChange = viewModel::setCustomerName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Customer name (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Notes (optional)") },
                    minLines = 2
                )
            }

            InfoCard(title = "Refund summary") {
                LabelValueRow("Items (after discount)", money.format(state.refund.subtotalMinor))
                LabelValueRow("Tax refunded", money.format(state.refund.taxMinor))
                HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    Text("Refund Amount", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        money.format(state.refund.totalMinor), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.extraColors.negative
                    )
                }
                LabelValueRow("Stock Restored", "${state.unitsSelected} unit${if (state.unitsSelected == 1) "" else "s"}")
            }

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Button(
                onClick = { confirming = true },
                enabled = state.unitsSelected > 0 && !state.saving,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Review & Confirm Return", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (confirming) {
        ConfirmReturnDialog(
            title = "Confirm customer return?",
            message = "Refund Amount: ${money.format(state.refund.totalMinor)} (${state.refundMethod.displayName()}).\n" +
                "Stock Restored: ${state.unitsSelected} unit${if (state.unitsSelected == 1) "" else "s"} will be added back to inventory.\n\n" +
                "The original sale is kept as it is. This return cannot be undone.",
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
private fun ReturnLineRow(line: ReturnLineState, onChange: (Int) -> Unit) {
    val money = LocalCurrencyFormatter.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.productName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                "${money.format(line.unitPriceMinor)} each • sold ${line.soldQuantity}" +
                    if (line.alreadyReturned > 0) " • returned ${line.alreadyReturned}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (line.returnable == 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = MaterialTheme.extraColors.warning, modifier = Modifier.height(14.dp))
                    Text("Fully returned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extraColors.warning)
                }
            } else {
                Text("Can return up to ${line.returnable}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (line.returnable > 0) {
            FilledTonalIconButton(onClick = { onChange(line.quantity - 1) }, enabled = line.quantity > 0) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease ${line.productName}")
            }
            Text(
                line.quantity.toString(), Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
            )
            FilledTonalIconButton(onClick = { onChange(line.quantity + 1) }, enabled = line.quantity < line.returnable) {
                Icon(Icons.Filled.Add, contentDescription = "Increase ${line.productName}")
            }
        }
    }
}
