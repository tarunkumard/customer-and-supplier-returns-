package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun MoreScreen(
    onOpenCustomerReturns: () -> Unit,
    onOpenSupplierReturns: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        MoreItem(
            Icons.AutoMirrored.Filled.AssignmentReturn, "Customer Returns",
            "Refund customers and restore stock", onOpenCustomerReturns
        )
        HorizontalDivider()
        MoreItem(
            Icons.Filled.LocalShipping, "Supplier Returns",
            "Send damaged, expired or wrong goods back", onOpenSupplierReturns
        )
        HorizontalDivider()
        MoreItem(Icons.Filled.BarChart, "Reports", "Sales, expenses, profit and top products", onOpenReports)
        HorizontalDivider()
        MoreItem(Icons.Filled.Settings, "Settings", "Shop details, tax, currency and data", onOpenSettings)
        HorizontalDivider()
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
    )
}
