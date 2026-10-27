package com.tarun.managemyshop.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tarun.managemyshop.domain.model.Currency
import com.tarun.managemyshop.domain.model.SaveResult
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.domain.validation.SettingsValidator
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.util.Percent
import com.tarun.managemyshop.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

private const val CONFIRM_WORD = "DELETE"

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit, onOpenPrivacy: () -> Unit) {
    var loaded by rememberSaveable { mutableStateOf(false) }
    var shopName by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var tax by rememberSaveable { mutableStateOf("") }
    var prefix by rememberSaveable { mutableStateOf("") }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val currentCurrency = LocalCurrencyFormatter.current.currency

    // Keyed on `loaded` so clearing it (after "Delete all data") reloads the defaults into the form.
    LaunchedEffect(loaded) {
        if (!loaded) {
            val saved = viewModel.load()
            shopName = saved.shopName
            address = saved.shopAddress.orEmpty()
            phone = saved.shopPhone.orEmpty()
            tax = Percent.toPlainString(saved.taxBasisPoints)
            prefix = saved.invoicePrefix
            loaded = true
        }
    }

    fun save() {
        val basisPoints = Percent.parseToBasisPoints(tax)
        val draft = ShopSettings(
            shopName = shopName,
            shopAddress = address,
            shopPhone = phone,
            // Currency is saved separately (and instantly), so carry the live value through here.
            currency = currentCurrency,
            taxBasisPoints = basisPoints ?: 0,
            invoicePrefix = prefix
        )
        // Show every problem at once: an unparseable tax plus the shared business rules.
        val problems = SettingsValidator.validate(draft.copy(shopName = shopName.trim(), invoicePrefix = prefix.trim())).let { rules ->
            if (basisPoints == null) rules + ("tax" to "Enter a percentage between 0 and 100") else rules
        }
        if (problems.isNotEmpty()) {
            errors = problems
            return
        }
        viewModel.save(draft) { result ->
            when (result) {
                is SaveResult.Success -> {
                    errors = emptyMap()
                    scope.launch { snackbar.showSnackbar("Settings saved") }
                }
                is SaveResult.Invalid -> errors = result.errors
            }
        }
    }

    Scaffold(
        topBar = { SubScreenTopBar("Settings", onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
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
            SectionTitle("Shop details")
            OutlinedTextField(
                value = shopName, onValueChange = { shopName = it; errors = errors - "shopName" },
                modifier = Modifier.fillMaxWidth(), label = { Text("Shop name *") },
                isError = "shopName" in errors, supportingText = errors["shopName"]?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = address, onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(), label = { Text("Address") }, minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it; errors = errors - "shopPhone" },
                modifier = Modifier.fillMaxWidth(), label = { Text("Phone number") }, singleLine = true,
                isError = "shopPhone" in errors, supportingText = errors["shopPhone"]?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next)
            )

            SectionTitle("Billing")
            OutlinedTextField(
                value = tax, onValueChange = { tax = it; errors = errors - "tax" },
                modifier = Modifier.fillMaxWidth(), label = { Text("Tax percentage") }, suffix = { Text("%") },
                isError = "tax" in errors, supportingText = errors["tax"]?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = prefix, onValueChange = { prefix = it; errors = errors - "invoicePrefix" },
                modifier = Modifier.fillMaxWidth(), label = { Text("Invoice prefix") },
                isError = "invoicePrefix" in errors,
                supportingText = { Text(errors["invoicePrefix"] ?: "Invoices look like ${prefix.ifBlank { "INV" }}-20260101-0001") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done)
            )
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Save Changes", style = MaterialTheme.typography.titleMedium)
            }

            SectionTitle("Currency")
            CurrencyDropdown(current = currentCurrency, onSelected = viewModel::setCurrency)
            InfoCard(
                "Changing the currency only changes how amounts are displayed. " +
                    "Amounts already stored are not converted."
            )

            SectionTitle("About")
            AboutCard()
            OutlinedButton(onClick = onOpenPrivacy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PrivacyTip, null)
                Text("  Privacy Policy")
            }

            SectionTitle("Danger zone")
            OutlinedButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                Text("  Delete all app data", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showDeleteDialog) {
        DeleteAllDialog(
            deleting = deleting,
            onDismiss = { if (!deleting) showDeleteDialog = false },
            onConfirm = {
                deleting = true
                viewModel.deleteAllData {
                    deleting = false
                    showDeleteDialog = false
                    // Reload the now-default values into the form.
                    loaded = false
                    scope.launch { snackbar.showSnackbar("All app data deleted") }
                }
            }
        )
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyDropdown(current: Currency, onSelected: (Currency) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current.label(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Currency") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Currency.Supported.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${option.label()} — ${option.displayName}") },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                )
            }
        }
    }
}

private fun Currency.label() = "$code ($symbol)"

@Composable
private fun InfoCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Manage My Shop", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Offline-first local business manager", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Version ${appVersion(context)} • All data stays on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "1.0"

@Composable
private fun DeleteAllDialog(deleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("Delete all app data?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This permanently erases every product, sale, expense, stock record and all settings on this phone. " +
                        "There is no backup and it cannot be undone."
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type $CONFIRM_WORD to confirm") },
                    singleLine = true,
                    enabled = !deleting,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == CONFIRM_WORD && !deleting) {
                if (deleting) CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                else Text("Delete everything", color = if (typed.trim() == CONFIRM_WORD) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Cancel") } }
    )
}
