package com.tarun.managemyshop.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.tarun.managemyshop.data.local.entity.SaleWithItems
import com.tarun.managemyshop.domain.model.ShopSettings
import java.io.OutputStream

/** Draws an A4 invoice with the platform [PdfDocument] API (no third-party PDF library). */
object InvoicePdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val RIGHT = PAGE_WIDTH - MARGIN
    private const val BOTTOM = PAGE_HEIGHT - 60f
    private const val LINE = 14f
    private const val ROW_PADDING = 8f

    // Table columns: item name on the left, the rest right-aligned at these x positions.
    private const val NAME_X = MARGIN + 6f
    private const val NAME_WIDTH = 235f
    private const val QTY_X = 345f
    private const val UNIT_X = 450f
    private const val AMOUNT_X = RIGHT - 6f
    private const val TOTALS_LABEL_X = 330f

    private val brand = Color.rgb(0x2D, 0x3A, 0x8C)
    private val muted = Color.rgb(0x5F, 0x64, 0x73)

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val body = paint(11f)
    private val bold = paint(11f, bold = true)
    private val small = paint(9f, color = muted)
    private val shopTitle = paint(20f, bold = true, color = brand)
    private val invoiceTitle = paint(22f, bold = true, color = brand)
    private val total = paint(14f, bold = true, color = brand)
    private val rulePaint = Paint().apply { color = Color.rgb(0xD0, 0xD3, 0xDC); strokeWidth = 1f }
    private val headerFill = Paint().apply { color = Color.rgb(0xEE, 0xF0, 0xF8) }

    /** @throws java.io.IOException if [out] cannot be written. */
    fun write(shop: ShopSettings, data: SaleWithItems, money: CurrencyFormatter, out: OutputStream) {
        val sale = data.sale
        val addressLines = shop.shopAddress?.let { wrap(it, body, 280f) }.orEmpty()
        val phoneLines = shop.shopPhone?.let { listOf("Tel: $it") }.orEmpty()
        val detailLines = addressLines + phoneLines
        val shopNameLines = wrap(shop.shopName, shopTitle, 280f)
        val leftHeight = shopNameLines.size * 24f + detailLines.size * LINE
        val rightHeight = 26f + 3 * LINE
        val headerBottom = 50f + maxOf(leftHeight, rightHeight)
        val customerY = headerBottom + 28f
        val tableHeaderY = customerY + 22f
        val firstStart = tableHeaderY + 24f
        val continuationHeaderY = 90f
        val nextStart = continuationHeaderY + 24f

        val rowLines = data.items.map { wrap(it.productNameSnapshot, body, NAME_WIDTH) }
        val rowHeights = rowLines.map { it.size * LINE + ROW_PADDING }

        val noteLines = sale.notes?.let { wrap(it, body, RIGHT - MARGIN) }.orEmpty()
        val totalRows = 1 + (if (sale.discountMinor > 0) 1 else 0) + (if (sale.taxMinor > 0) 1 else 0)
        val totalsHeight = totalRows * 20f + 40f +
            (if (noteLines.isEmpty()) 0f else 30f + noteLines.size * LINE) + 50f

        val pages = InvoiceLayout.paginate(rowHeights, firstStart, nextStart, BOTTOM, totalsHeight)

        val document = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, layout ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
                val canvas = page.canvas
                val tableTop: Float
                if (pageIndex == 0) {
                    drawHeader(canvas, shopNameLines, detailLines, sale.invoiceNumber, sale.saleDate, sale.paymentMethod.displayName())
                    canvas.drawLine(MARGIN, headerBottom + 8f, RIGHT, headerBottom + 8f, rulePaint)
                    text(canvas, "Billed to", MARGIN, customerY - 4f, small)
                    text(canvas, sale.customerName ?: "Walk-in customer", MARGIN, customerY + 10f, bold)
                    tableTop = tableHeaderY
                } else {
                    text(canvas, "Invoice ${sale.invoiceNumber} (continued)", MARGIN, 60f, bold)
                    tableTop = continuationHeaderY
                }
                drawTableHeader(canvas, tableTop)

                var y = if (pageIndex == 0) firstStart else nextStart
                for (i in layout.rows) {
                    val item = data.items[i]
                    rowLines[i].forEachIndexed { n, line -> text(canvas, line, NAME_X, y + 11f + n * LINE, body) }
                    text(canvas, item.quantity.toString(), QTY_X, y + 11f, body, Paint.Align.RIGHT)
                    text(canvas, money.format(item.unitPriceMinor), UNIT_X, y + 11f, body, Paint.Align.RIGHT)
                    text(canvas, money.format(item.lineTotalMinor), AMOUNT_X, y + 11f, body, Paint.Align.RIGHT)
                    y += rowHeights[i]
                    canvas.drawLine(MARGIN, y - 3f, RIGHT, y - 3f, rulePaint)
                }
                if (layout.hasTotals) drawTotals(canvas, y + 14f, data, money, noteLines)

                text(canvas, "Page ${pageIndex + 1} of ${pages.size}", PAGE_WIDTH / 2f, PAGE_HEIGHT - 28f, small, Paint.Align.CENTER)
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun drawHeader(
        canvas: Canvas,
        shopNameLines: List<String>,
        detailLines: List<String>,
        invoiceNumber: String,
        saleDate: Long,
        payment: String
    ) {
        var y = 60f
        shopNameLines.forEach { text(canvas, it, MARGIN, y, shopTitle); y += 24f }
        y -= 6f
        detailLines.forEach { text(canvas, it, MARGIN, y, body); y += LINE }

        text(canvas, "INVOICE", RIGHT, 60f, invoiceTitle, Paint.Align.RIGHT)
        text(canvas, invoiceNumber, RIGHT, 80f, bold, Paint.Align.RIGHT)
        text(canvas, DateRanges.formatDateTime(saleDate), RIGHT, 80f + LINE, body, Paint.Align.RIGHT)
        text(canvas, "Payment: $payment", RIGHT, 80f + 2 * LINE, body, Paint.Align.RIGHT)
    }

    private fun drawTableHeader(canvas: Canvas, top: Float) {
        canvas.drawRect(MARGIN, top, RIGHT, top + 22f, headerFill)
        val baseline = top + 15f
        text(canvas, "Item", NAME_X, baseline, bold)
        text(canvas, "Qty", QTY_X, baseline, bold, Paint.Align.RIGHT)
        text(canvas, "Unit price", UNIT_X, baseline, bold, Paint.Align.RIGHT)
        text(canvas, "Amount", AMOUNT_X, baseline, bold, Paint.Align.RIGHT)
    }

    private fun drawTotals(canvas: Canvas, top: Float, data: SaleWithItems, money: CurrencyFormatter, noteLines: List<String>) {
        val sale = data.sale
        var y = top + 12f
        fun row(label: String, value: String) {
            text(canvas, label, TOTALS_LABEL_X, y, body)
            text(canvas, value, AMOUNT_X, y, body, Paint.Align.RIGHT)
            y += 20f
        }
        row("Subtotal", money.format(sale.subtotalMinor))
        if (sale.discountMinor > 0) row("Discount", "-${money.format(sale.discountMinor)}")
        if (sale.taxMinor > 0) row("Tax", money.format(sale.taxMinor))
        canvas.drawLine(TOTALS_LABEL_X, y - 12f, RIGHT, y - 12f, rulePaint)
        y += 6f
        text(canvas, "Total", TOTALS_LABEL_X, y, total)
        text(canvas, money.format(sale.totalMinor), AMOUNT_X, y, total, Paint.Align.RIGHT)
        y += 36f

        if (noteLines.isNotEmpty()) {
            text(canvas, "Notes", MARGIN, y, small)
            y += LINE + 2f
            noteLines.forEach { text(canvas, it, MARGIN, y, body); y += LINE }
            y += 16f
        }
        text(canvas, "Thank you for your business!", PAGE_WIDTH / 2f, y + 14f, bold, Paint.Align.CENTER)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, paint: Paint, align: Paint.Align = Paint.Align.LEFT) {
        paint.textAlign = align
        canvas.drawText(value, x, y, paint)
    }

    /** Word-wraps [value] to [maxWidth], hard-breaking words that are wider than the column. */
    internal fun wrap(value: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        value.split('\n').forEach { paragraph ->
            var current = ""
            for (word in paragraph.split(' ').filter { it.isNotEmpty() }) {
                var remaining = word
                while (paint.measureText(remaining) > maxWidth) {
                    if (current.isNotEmpty()) { lines += current; current = "" }
                    val fit = paint.breakText(remaining, true, maxWidth, null).coerceAtLeast(1)
                    lines += remaining.substring(0, fit)
                    remaining = remaining.substring(fit)
                }
                if (remaining.isEmpty()) continue
                val candidate = if (current.isEmpty()) remaining else "$current $remaining"
                if (paint.measureText(candidate) <= maxWidth) current = candidate
                else { lines += current; current = remaining }
            }
            lines += current
        }
        return lines.ifEmpty { listOf("") }
    }
}
