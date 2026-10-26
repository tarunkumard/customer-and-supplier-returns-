package com.tarun.managemyshop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tarun.managemyshop.data.local.entity.ProductEntity
import com.tarun.managemyshop.ui.theme.extraColors

/** Pill showing "Out of stock", "Low stock" or "In stock" using the alert palette. */
@Composable
fun StockBadge(product: ProductEntity, modifier: Modifier = Modifier) {
    val extra = MaterialTheme.extraColors
    val (label, color) = when {
        product.stockQuantity <= 0 -> "Out of stock" to extra.negative
        product.isLowStock -> "Low stock" to extra.warning
        else -> "In stock" to extra.positive
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
