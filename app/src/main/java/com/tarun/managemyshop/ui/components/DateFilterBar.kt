package com.tarun.managemyshop.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tarun.managemyshop.util.DateFilter
import com.tarun.managemyshop.util.DatePreset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Scrollable preset chips; "Custom" opens a calendar range picker. */
@Composable
fun DateFilterChips(
    filter: DateFilter,
    onPreset: (DatePreset) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    presets: List<DatePreset> = DatePreset.entries
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            FilterChip(
                selected = filter.preset == preset,
                onClick = { if (preset == DatePreset.CUSTOM) showPicker = true else onPreset(preset) },
                label = { Text(chipLabel(preset, filter)) }
            )
        }
    }

    if (showPicker) {
        DateRangePickerDialog(
            onDismiss = { showPicker = false },
            onApply = { from, to ->
                onCustomRange(from, to)
                showPicker = false
            }
        )
    }
}

private fun chipLabel(preset: DatePreset, filter: DateFilter): String {
    val from = filter.from
    if (preset != DatePreset.CUSTOM || filter.preset != preset || from == null) return preset.label
    val to = filter.to
    return if (to == null || to == from) from.toString() else "$from → $to"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(onDismiss: () -> Unit, onApply: (LocalDate, LocalDate) -> Unit) {
    val state = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    // The picker reports UTC midnight, so read the date in UTC to avoid off-by-one days.
                    val from = Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).toLocalDate()
                    val to = state.selectedEndDateMillis?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    } ?: from
                    onApply(from, to)
                }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        DateRangePicker(state = state, modifier = Modifier.height(520.dp))
    }
}
