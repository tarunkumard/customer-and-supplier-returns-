package com.tarun.managemyshop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp

/** Label + value with a proportional bar underneath; a simple chart that needs no library. */
@Composable
fun BarRow(
    label: String,
    value: String,
    fraction: Float,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(barColor.copy(alpha = 0.12f))
        ) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val width = (constraints.maxWidth * fraction.coerceIn(0f, 1f)).toInt()
                        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(barColor)
            )
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
