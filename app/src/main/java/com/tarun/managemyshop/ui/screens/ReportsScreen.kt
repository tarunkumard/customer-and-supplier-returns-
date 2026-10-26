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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.ui.components.BarRow
import com.tarun.managemyshop.ui.components.DateFilterChips
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.DatePreset
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.ReportsViewModel

@Composable
fun ReportsScreen(viewModel: ReportsViewModel, onBack: () -> Unit) {
    val report by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val money = LocalCurrencyFormatter.current

    Scaffold(topBar = { SubScreenTopBar("Reports", onBack) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            DateFilterChips(
                filter = filter,
                onPreset = viewModel::onPreset,
                onCustomRange = viewModel::onCustomRange,
                presets = listOf(DatePreset.TODAY, DatePreset.WEEK, DatePreset.MONTH, DatePreset.CUSTOM)
            )

            if (!report.loaded) {
                LoadingIndicator(Modifier.height(240.dp))
                return@Column
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProfitCard(report.estimatedProfitMinor)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Net Sales", Modifier.weight(1f)) { MoneyText(report.totalSalesMinor, style = MaterialTheme.typography.titleLarge) }
                    StatCard("Total Expenses", Modifier.weight(1f)) {
                        MoneyText(report.totalExpensesMinor, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Gross Sales", Modifier.weight(1f)) {
                        MoneyText(report.grossSalesMinor, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    StatCard("Customer Refunds", Modifier.weight(1f)) {
                        MoneyText(
                            report.customerRefundsMinor, style = MaterialTheme.typography.titleLarge,
                            color = if (report.customerRefundsMinor > 0) MaterialTheme.extraColors.negative else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${report.customerReturnCount} return${if (report.customerReturnCount == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Supplier Credits", Modifier.weight(1f)) {
                        MoneyText(report.supplierCreditMinor, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${report.supplierReturnCount} return${if (report.supplierReturnCount == 1) "" else "s"} to suppliers",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatCard("Inventory Value", Modifier.weight(1f)) {
                        MoneyText(report.inventoryValueMinor, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text("At purchase price, now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Number of Sales", Modifier.weight(1f)) {
                        Text(report.saleCount.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    StatCard("Average Order", Modifier.weight(1f)) {
                        MoneyText(report.averageOrderMinor, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                SectionCard("Sales by payment method (before refunds)") {
                    if (report.paymentBreakdown.isEmpty()) {
                        EmptyNote("No sales in this period.")
                    } else {
                        val max = report.paymentBreakdown.maxOf { it.totalMinor }.coerceAtLeast(1)
                        report.paymentBreakdown.forEach { row ->
                            BarRow(
                                label = row.method.displayName(),
                                value = money.format(row.totalMinor),
                                fraction = row.totalMinor.toFloat() / max,
                                caption = "${row.saleCount} sale${if (row.saleCount == 1) "" else "s"}"
                            )
                        }
                    }
                }

                SectionCard("Top 5 products") {
                    if (report.topProducts.isEmpty()) {
                        EmptyNote("No products sold in this period.")
                    } else {
                        val max = report.topProducts.maxOf { it.quantity }.coerceAtLeast(1)
                        report.topProducts.forEachIndexed { index, row ->
                            BarRow(
                                label = "${index + 1}. ${row.name}",
                                value = "${row.quantity} sold",
                                fraction = row.quantity.toFloat() / max,
                                barColor = MaterialTheme.extraColors.positive,
                                caption = "Revenue ${money.format(row.revenueMinor)}"
                            )
                        }
                    }
                }

                SectionCard("Low-stock products") {
                    if (report.lowStock.isEmpty()) {
                        EmptyNote("All products are well stocked.")
                    } else {
                        report.lowStock.forEachIndexed { index, product ->
                            if (index > 0) HorizontalDivider()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(product.name, Modifier.weight(1f), maxLines = 1)
                                Text(
                                    if (product.stockQuantity <= 0) "Out of stock" else "${product.stockQuantity} left",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (product.stockQuantity <= 0) MaterialTheme.extraColors.negative
                                    else MaterialTheme.extraColors.warning
                                )
                            }
                        }
                    }
                }
                Text(
                    "Net sales = sales − customer refunds. Profit = net sales (excluding tax) − purchase cost of " +
                        "items kept by customers (returned items reverse their cost) − expenses. " +
                        "Supplier credits are money owed to you by wholesalers, shown separately and not included in profit. " +
                        "Inventory value already reflects stock returned to suppliers. " +
                        "Product revenue is before bill-level discounts, tax and refunds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfitCard(profitMinor: Long) {
    val color: Color = if (profitMinor < 0) MaterialTheme.extraColors.negative else MaterialTheme.extraColors.positive
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Estimated Profit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MoneyText(profitMinor, style = MaterialTheme.typography.headlineMedium, color = color)
        }
    }
}

@Composable
private fun StatCard(label: String, modifier: Modifier = Modifier, value: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            value()
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun EmptyNote(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
