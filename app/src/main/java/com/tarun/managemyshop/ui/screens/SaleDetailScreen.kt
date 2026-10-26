package com.tarun.managemyshop.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.model.ShopSettings
import com.tarun.managemyshop.ui.components.LoadingIndicator
import com.tarun.managemyshop.ui.components.SubScreenTopBar
import com.tarun.managemyshop.util.CurrencyFormatter
import com.tarun.managemyshop.util.DateRanges
import com.tarun.managemyshop.util.InvoiceExporter
import com.tarun.managemyshop.util.LocalCurrencyFormatter
import com.tarun.managemyshop.data.local.entity.CustomerReturnEntity
import com.tarun.managemyshop.domain.model.SaleReturnStatus
import com.tarun.managemyshop.ui.components.InfoCard
import com.tarun.managemyshop.ui.components.LabelValueRow
import com.tarun.managemyshop.ui.components.ReturnStatusPill
import com.tarun.managemyshop.ui.theme.extraColors
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import com.tarun.managemyshop.util.ReceiptFormatter
import com.tarun.managemyshop.util.displayName
import com.tarun.managemyshop.viewmodel.SalesViewModel
import kotlinx.coroutines.launch

@Composable
fun SaleDetailScreen(
    saleId: Long,
    shop: ShopSettings,
    viewModel: SalesViewModel,
    onBack: () -> Unit,
    onCreateReturn: (Long) -> Unit,
    onOpenReturn: (Long) -> Unit
) {
    val sale by remember(saleId) { viewModel.saleDetail(saleId) }.collectAsStateWithLifecycle(initialValue = null)
    val returnSummary by remember(saleId) { viewModel.returnSummary(saleId) }.collectAsStateWithLifecycle(initialValue = null)
    val saleReturns by remember(saleId) { viewModel.returnsForSale(saleId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val money = LocalCurrencyFormatter.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var busy by remember { mutableStateOf(false) }
    val current = sale

    fun message(text: String) = scope.launch { snackbar.showSnackbar(text) }

    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val data = sale
        if (uri == null || data == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            InvoiceExporter.saveTo(context, uri, shop, data, money)
                .onSuccess { message("Invoice saved") }
                .onFailure { message("Couldn't save the PDF. Please try again.") }
            busy = false
        }
    }

    fun sharePdf(data: SaleWithItems) {
        scope.launch {
            busy = true
            InvoiceExporter.createShareIntent(context, shop, data, money)
                .onSuccess { intent ->
                    try {
                        context.startActivity(Intent.createChooser(intent, "Share invoice"))
                    } catch (e: ActivityNotFoundException) {
                        message("No app available to share the PDF")
                    }
                }
                .onFailure { message("Couldn't create the PDF. Please try again.") }
            busy = false
        }
    }

    Scaffold(
        topBar = { SubScreenTopBar("Receipt", onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (current == null) {
            LoadingIndicator(Modifier.padding(padding))
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val status = returnSummary?.status ?: SaleReturnStatus.NONE
            if (status != SaleReturnStatus.NONE) {
                ReturnsCard(status, returnSummary?.refundMinor ?: 0, saleReturns, money, onOpenReturn)
            }
            Receipt(shop, current, money)
            Button(
                onClick = { onCreateReturn(current.sale.id) },
                enabled = status != SaleReturnStatus.FULL,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.AssignmentReturn, null)
                Text(
                    if (status == SaleReturnStatus.FULL) "  Fully returned" else "  Create Return",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Button(
                onClick = { sharePdf(current) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Icon(Icons.Filled.PictureAsPdf, null)
                Text("  Share PDF Invoice", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(
                onClick = { savePdf.launch(InvoiceExporter.fileName(current)) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Icon(Icons.Filled.Download, null)
                Text("  Download PDF Invoice", style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = { shareReceipt(context, ReceiptFormatter.build(shop, current, money), current.sale.invoiceNumber) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Share, null)
                Text("  Share as text")
            }
        }
    }
}

private fun shareReceipt(context: Context, text: String, invoiceNumber: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Receipt $invoiceNumber")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Share receipt"))
}

/** The original sale stays as it was; this card shows what has come back and been refunded since. */
@Composable
private fun ReturnsCard(
    status: SaleReturnStatus,
    refundMinor: Long,
    returns: List<CustomerReturnEntity>,
    money: CurrencyFormatter,
    onOpenReturn: (Long) -> Unit
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Customer Returns", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            ReturnStatusPill(status)
        }
        LabelValueRow("Total refunded", money.format(refundMinor), bold = true, valueColor = MaterialTheme.extraColors.negative)
        returns.forEach { ret ->
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { onOpenReturn(ret.id) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ret.returnNumber, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        "${DateRanges.formatDateTime(ret.returnDate)} • ${ret.reason.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(money.format(ret.totalRefundMinor), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun Receipt(shop: ShopSettings, data: SaleWithItems, money: CurrencyFormatter) {
    val sale = data.sale
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    shop.shopName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center
                )
                shop.shopAddress?.let { Text(it, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center) }
                shop.shopPhone?.let { Text("Tel: $it", style = MaterialTheme.typography.bodySmall) }
            }
            HorizontalDivider()
            InfoRow("Invoice", sale.invoiceNumber, bold = true)
            InfoRow("Date", DateRanges.formatDateTime(sale.saleDate))
            sale.customerName?.let { InfoRow("Customer", it) }
            InfoRow("Payment", sale.paymentMethod.displayName())
            HorizontalDivider()

            data.items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(item.productNameSnapshot, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${item.quantity} × ${money.format(item.unitPriceMinor)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(money.format(item.lineTotalMinor), style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider()

            InfoRow("Subtotal", money.format(sale.subtotalMinor))
            if (sale.discountMinor > 0) InfoRow("Discount", "-${money.format(sale.discountMinor)}")
            if (sale.taxMinor > 0) InfoRow("Tax", money.format(sale.taxMinor))
            Row(Modifier.fillMaxWidth()) {
                Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    money.format(sale.totalMinor), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
                )
            }
            sale.notes?.let {
                HorizontalDivider()
                Text("Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider()
            Text(
                "Thank you for shopping with us!",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
