package com.tarun.managemyshop.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.tarun.managemyshop.ui.theme.extraColors
import com.tarun.managemyshop.util.LocalCurrencyFormatter

/** Money amount (minor units) in the selected currency: green when positive, red when negative. */
@Composable
fun MoneyText(
    amountMinor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = if (amountMinor < 0) MaterialTheme.extraColors.negative else MaterialTheme.extraColors.positive
) {
    Text(
        text = LocalCurrencyFormatter.current.format(amountMinor),
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = color
    )
}
