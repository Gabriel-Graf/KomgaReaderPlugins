package com.komgareader.plugin.calibre

/**
 * Browse paginates Calibre series first (the built-in "series" category), then standalone
 * books (search query "series:false"). All offset/boundary math lives here, pure & testable.
 * [page] is 1-based.
 */
object BrowsePaging {

    enum class Phase { SERIES, STANDALONE }
    data class Slice(val phase: Phase, val offset: Int)

    fun seriesPages(seriesTotal: Int, pageSize: Int): Int =
        if (seriesTotal <= 0) 0 else (seriesTotal + pageSize - 1) / pageSize

    fun slice(page: Int, pageSize: Int, seriesTotal: Int): Slice {
        val sPages = seriesPages(seriesTotal, pageSize)
        return if (page <= sPages) {
            Slice(Phase.SERIES, (page - 1) * pageSize)
        } else {
            Slice(Phase.STANDALONE, (page - 1 - sPages) * pageSize)
        }
    }

    fun hasNext(page: Int, pageSize: Int, seriesTotal: Int, standaloneTotal: Int): Boolean {
        val sPages = seriesPages(seriesTotal, pageSize)
        val stdPages = if (standaloneTotal <= 0) 0 else (standaloneTotal + pageSize - 1) / pageSize
        return page < sPages + stdPages
    }
}
