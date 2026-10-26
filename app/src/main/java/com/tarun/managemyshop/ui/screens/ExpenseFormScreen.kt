package com.tarun.managemyshop.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tarun.managemyshop.data.local.entity.ExpenseEntity
import com.tarun.managemyshop.domain.model.ExpenseCategories
import com.tarun.managemyshop.domain.model.PaymentMethod
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.validation.ExpenseValidator
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.Money
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.ExpenseViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Add (expenseId == null) or edit an expense; editing also offers delete with confirmation. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpenseFormScreen(
    expenseId: Long?,
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val isEdit = expenseId != null
    val symbol = LocalCurrencyFormatter.current.currency.symbol

    var loaded by rememberSaveable { mutableStateOf(!isEdit) }
    var title by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(ExpenseCategories.DEFAULT) }
    var payment by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var notes by rememberSaveable { mutableStateOf("") }
    var dateMillis by rememberSaveable { mutableLongStateOf(DateRanges.day(LocalDate.now()).start) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(expenseId) {
        if (expenseId == null || loaded) return@LaunchedEffect
        viewModel.load(expenseId)?.let {
            title = it.title
            amount = Money.toPlainString(it.amountMinor)
            category = it.category.ifBlank { ExpenseCategories.DEFAULT }
            payment = it.paymentMethod
            notes = it.notes.orEmpty()
            dateMillis = it.expenseDate
        }
        loaded = true
    }

    fun submit() {
        val minor = Money.parseToMinor(amount)
        val amountError = when {
            minor != null -> null
            amount.isBlank() -> "Enter the amount"
            amount.trim().startsWith("-") -> "Amount cannot be negative"
            else -> "Enter a valid amount"
        }
        val expense = ExpenseEntity(
            id = expenseId ?: 0,
            title = title,
            category = category,
            amountMinor = minor ?: 0,
            expenseDate = dateMillis,
            paymentMethod = payment,
            notes = notes.trim().ifEmpty { null }
        )
        // Show every problem at once: the unparseable amount plus the shared business rules.
        val allErrors = ExpenseValidator.validate(expense.copy(title = title.trim())).let { rules ->
            if (amountError != null) rules + ("amount" to amountError) else rules
        }
        if (allErrors.isNotEmpty()) {
            errors = allErrors
            return
        }
        saving = true
        viewModel.save(expense) { result ->
            saving = false
            when (result) {
                is SaveResult.Success -> onBack()
                is SaveResult.Invalid -> errors = result.errors
            }
        }
    }

    Scaffold(
        topBar = {
            SubScreenTopBar(if (isEdit) "Edit Expense" else "Add Expense", onBack) {
                if (isEdit) {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                    }
                }
            }
        }
    ) { padding ->
        if (!loaded) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it; errors = errors - "title" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title *") },
                isError = "title" in errors,
                supportingText = errors["title"]?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; errors = errors - "amount" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Amount *") },
                prefix = { Text(symbol) },
                isError = "amount" in errors,
                supportingText = errors["amount"]?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
            )

            Text("Category", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseCategories.all.forEach { option ->
                    FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                }
            }

            // A read-only field only reports presses via its interaction source, so watch that to open the picker.
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            LaunchedEffect(pressed) { if (pressed) showDatePicker = true }
            OutlinedTextField(
                value = DateRanges.formatDate(dateMillis),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date") },
                trailingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                readOnly = true,
                singleLine = true,
                interactionSource = interaction
            )

            Text("Payment method", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentMethod.entries.forEach { method ->
                    FilterChip(selected = payment == method, onClick = { payment = method }, label = { Text(method.displayName()) })
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )
            Button(
                onClick = ::submit,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) { Text(if (isEdit) "Save Changes" else "Save Expense", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (showDatePicker) {
        // The picker works in UTC midnight; convert to/from the local calendar day.
        val initial = Instant.ofEpochMilli(dateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        val day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        dateMillis = DateRanges.day(day).start
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this expense?") },
            text = { Text("\"$title\" will be removed and your totals will change. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    expenseId?.let { viewModel.delete(it, onDone = onBack) }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}
