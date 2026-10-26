package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.ui.components.InfoCard
import com.tarun.managemyshop.ui.components.LabelValueRow
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.viewmodel.SupplierReturnsViewModel

/** Confirmation receipt of a return to a supplier. */
@Composable
fun SupplierReturnDetailScreen(
    returnId: Long,
    shop: ShopSettings,
    viewModel: SupplierReturnsViewModel,
    onBack: () -> Unit
) {
    val data by remember(returnId) { viewModel.detail(returnId) }.collectAsStateWithLifecycle(initialValue = null)
    val money = LocalCurrencyFormatter.current

    Scaffold(topBar = { SubScreenTopBar("Supplier Return", onBack) }) { padding ->
        val current = data
        if (current == null) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        val ret = current.supplierReturn
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.extraColors.positive, modifier = Modifier.size(28.dp))
                    Column {
                        Text("Return to Supplier", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(shop.shopName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                HorizontalDivider()
                LabelValueRow("Return number", ret.returnNumber, bold = true)
                LabelValueRow("Date", DateRanges.formatDateTime(ret.returnDate))
                ret.supplierName?.let { LabelValueRow("Supplier", it) }
                LabelValueRow("Reason", ret.reason.label)
            }

            InfoCard(title = "Returned products") {
                current.items.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(item.productNameSnapshot, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${item.quantityReturned} × ${money.format(item.unitCostMinor)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(money.format(item.lineSupplierCreditMinor), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    Text("Supplier Credit", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        money.format(ret.totalSupplierCreditMinor), style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.extraColors.positive
                    )
                }
            }

            InfoCard(title = "Stock removed") {
                current.items.forEach {
                    LabelValueRow(it.productNameSnapshot, "-${it.quantityReturned}", valueColor = MaterialTheme.extraColors.negative)
                }
            }

            ret.notes?.let {
                InfoCard(title = "Notes") { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
