package com.tarun.managemyshop.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.model.ShopSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * PDF export without storage permissions:
 *  - download: the user picks a location through the system file picker (Storage Access Framework);
 *  - share: the file is written to the app cache and exposed with a FileProvider URI.
 */
object InvoiceExporter {

    fun fileName(data: SaleWithItems) = "Invoice_${data.sale.invoiceNumber}.pdf"

    suspend fun saveTo(
        context: Context,
        uri: Uri,
        shop: ShopSettings,
        data: SaleWithItems,
        money: CurrencyFormatter
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Could not open the selected file")
            stream.use { InvoicePdf.write(shop, data, money, it) }
        }
    }

    suspend fun createShareIntent(
        context: Context,
        shop: ShopSettings,
        data: SaleWithItems,
        money: CurrencyFormatter
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "invoices").apply { mkdirs() }
            val file = File(dir, fileName(data))
            file.outputStream().use { InvoicePdf.write(shop, data, money, it) }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Invoice ${data.sale.invoiceNumber}")
                clipData = ClipData.newRawUri(null, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
