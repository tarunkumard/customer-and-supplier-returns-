package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.data.local.entity.SaleEntity
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.MoneyText
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.DashboardViewModel
import java.time.LocalTime

private const val LOW_STOCK_PREVIEW = 5

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNewSale: () -> Unit,
    onAddProduct: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenSale: (Long) -> Unit,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (!state.loaded) {
        LoadingIndicator()
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Greeting(state.shopName) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Today's Sales", state.todaySalesMinor, Icons.Filled.Today, Modifier.weight(1f))
                    SummaryCard("This Month's Sales", state.monthSalesMinor, Icons.Filled.CalendarMonth, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard(
                        "Expenses This Month", state.monthExpensesMinor, Icons.Filled.Payments, Modifier.weight(1f),
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )
                    SummaryCard(
                        "Estimated Profit", state.estimatedProfitMinor, Icons.AutoMirrored.Filled.TrendingUp,
                        Modifier.weight(1f),
                        valueColor = if (state.estimatedProfitMinor < 0) MaterialTheme.extraColors.negative
                        else MaterialTheme.extraColors.positive
                    )
                }
                Text(
                    "Profit = this month's sales (excl. tax) − cost of items sold − expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { QuickActions(onNewSale, onAddProduct, onAddExpense) }
        item { LowStockCard(state.lowStock, onOpenProduct, onOpenInventory) }
        item { RecentSalesCard(state.recentSales, onOpenSale) }
    }
}

@Composable
private fun Greeting(shopName: String) {
    val hour = LocalTime.now().hour
    val hello = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    Column {
        Text(hello, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            shopName.ifBlank { "Your shop" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    amountMinor: Long,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.extraColors.positive
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            MoneyText(amountMinor, style = MaterialTheme.typography.titleLarge, color = valueColor)
        }
    }
}

@Composable
private fun QuickActions(onNewSale: () -> Unit, onAddProduct: () -> Unit, onAddExpense: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile("New Sale", Icons.Filled.AddShoppingCart, onNewSale, Modifier.weight(1f), filled = true)
        ActionTile("Add Product", Icons.Filled.Inventory2, onAddProduct, Modifier.weight(1f))
        ActionTile("Add Expense", Icons.Filled.Payments, onAddExpense, Modifier.weight(1f))
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    val container = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val content = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, Modifier.size(26.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun LowStockCard(
    lowStock: List<ProductEntity>,
    onOpenProduct: (Long) -> Unit,
    onOpenInventory: () -> Unit
) {
    val warning = MaterialTheme.extraColors.warning
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (lowStock.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.extraColors.positive)
                    Text("All products are well stocked", style = MaterialTheme.typography.titleMedium)
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Warning, null, tint = warning)
                Text(
                    "Low stock alert (${lowStock.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = warning,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            lowStock.take(LOW_STOCK_PREVIEW).forEachIndexed { index, product ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProduct(product.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(product.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        if (product.stockQuantity <= 0) "Out of stock" else "${product.stockQuantity} left",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (product.stockQuantity <= 0) MaterialTheme.extraColors.negative else warning
                    )
                }
            }
            if (lowStock.size > LOW_STOCK_PREVIEW) {
                TextButton(onClick = onOpenInventory) { Text("View all in Inventory") }
            }
        }
    }
}

@Composable
private fun RecentSalesCard(sales: List<SaleEntity>, onOpenSale: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Recent sales", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (sales.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.PointOfSale, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "No sales yet. Tap New Sale to record your first one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                return@Column
            }
            sales.forEachIndexed { index, sale ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSale(sale.id) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sale.invoiceNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            "${DateRanges.formatDateTime(sale.saleDate)} • ${sale.paymentMethod.displayName()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MoneyText(sale.totalMinor)
                }
            }
        }
    }
}
