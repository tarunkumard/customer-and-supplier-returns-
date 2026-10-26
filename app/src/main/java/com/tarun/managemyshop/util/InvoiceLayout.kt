package com.tarun.managemyshop.util

/** Which rows (and whether the totals block) go on one PDF page. */
data class PageLayout(val rows: IntRange, val hasTotals: Boolean)

object InvoiceLayout {
    /**
     * Splits table rows over pages. [firstStart]/[nextStart] are the y positions where rows begin on
     * the first and following pages, [bottom] is the lowest usable y, and [totalsHeight] is the room
     * the totals/notes block needs on the final page (it gets its own page if it does not fit).
     */
    fun paginate(
        rowHeights: List<Float>,
        firstStart: Float,
        nextStart: Float,
        bottom: Float,
        totalsHeight: Float
    ): List<PageLayout> {
        val pages = mutableListOf<PageLayout>()
        var index = 0
        var pageStart = firstStart
        while (true) {
            var y = pageStart
            val first = index
            while (index < rowHeights.size && y + rowHeights[index] <= bottom) {
                y += rowHeights[index]
                index++
            }
            if (index < rowHeights.size) {
                // A row taller than an empty page is placed anyway so the loop always advances.
                if (index == first && y == pageStart && pageStart == nextStart) index++
                pages += PageLayout(first until index, hasTotals = false)
                pageStart = nextStart
                continue
            }
            if (y + totalsHeight <= bottom) {
                pages += PageLayout(first until index, hasTotals = true)
            } else {
                pages += PageLayout(first until index, hasTotals = false)
                pages += PageLayout(index until index, hasTotals = true)
            }
            return pages
        }
    }
}
