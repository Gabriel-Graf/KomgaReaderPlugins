package com.komgareader.plugin.calibre

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import com.komgareader.plugin.calibre.api.CalibreApi
import com.komgareader.plugin.calibre.api.CalibreBookDto

/**
 * [BrowsableSource] over the Calibre Content Server /ajax/ API. Read-only (no progress sync).
 * Groups books into Series→volumes by Calibre's `series` metadata (series-name join key).
 *
 * Browse presents Calibre series first (one page per [PAGE_SIZE] series names), then standalone
 * books (those without a series tag). Both phases use the same [BrowsePaging] math.
 */
class CalibreSource(
    private val api: CalibreApi,
    private val baseUrl: String,
    private val library: String,
    override val name: String,
) : BrowsableSource {

    override val id: Long = SourceId.of(name, SourceKind.PLUGIN, baseUrl)
    override val kind: SourceKind = SourceKind.PLUGIN

    /** Encoded segment of the Calibre series category URL; cached after first resolution. */
    @Volatile private var seriesCategoryEncoded: String? = null

    private companion object {
        const val PAGE_SIZE = 50
        const val STANDALONE_QUERY = "series:false"
    }

    // ----------------------------------------------------------------- browse

    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val encoded = resolveSeriesCategory()

        // Probe totals for paging math (num=0 returns only the count, no items).
        val seriesTotal = api.category(encoded, library, num = 0, offset = 0).total_num
        val standaloneTotal = api.search(library, STANDALONE_QUERY, num = 0, offset = 0).total_num

        val slice = BrowsePaging.slice(page, PAGE_SIZE, seriesTotal)
        val items: List<Series> = when (slice.phase) {
            BrowsePaging.Phase.SERIES -> {
                api.category(encoded, library, num = PAGE_SIZE, offset = slice.offset)
                    .items.map { CalibreMapper.seriesTile(it.name, id) }
            }
            BrowsePaging.Phase.STANDALONE -> {
                val ids = api.search(
                    library, STANDALONE_QUERY, num = PAGE_SIZE, offset = slice.offset,
                ).book_ids
                fetchBooks(ids).map { (bid, dto) -> CalibreMapper.standaloneSeries(dto, bid, id) }
            }
        }
        return PagedResult(items, hasNextPage = BrowsePaging.hasNext(page, PAGE_SIZE, seriesTotal, standaloneTotal))
    }

    // ----------------------------------------------------------------- search

    override suspend fun search(query: String, page: Int): PagedResult<Series> {
        val offset = (page - 1).coerceAtLeast(0) * PAGE_SIZE
        val result = api.search(library, query, num = PAGE_SIZE, offset = offset)
        val books = fetchBooks(result.book_ids)
        val series = CalibreMapper.groupSearch(books, id)
        val hasNext = offset + result.book_ids.size < result.total_num
        return PagedResult(series, hasNextPage = hasNext)
    }

    // ----------------------------------------------------------------- books

    override suspend fun books(seriesRemoteId: String): List<Book> {
        return when (val parsed = CalibreRemoteId.decode(seriesRemoteId)) {
            is CalibreRemoteId.Parsed.Book -> {
                val dto = runCatching { api.book(parsed.id, library) }.getOrNull()
                    ?: return emptyList()
                listOfNotNull(CalibreMapper.toBook(dto, parsed.id, id, dto.title))
            }
            is CalibreRemoteId.Parsed.Series -> {
                val ids = api.search(
                    library, seriesQuery(parsed.name), num = PAGE_SIZE, offset = 0,
                ).book_ids
                fetchBooks(ids)
                    .toList()
                    .sortedBy { (_, dto) -> dto.series_index ?: 0.0 }
                    .mapNotNull { (bid, dto) -> CalibreMapper.toBook(dto, bid, id, parsed.name) }
            }
        }
    }

    // ----------------------------------------------------------------- detail

    override suspend fun seriesDetail(seriesRemoteId: String): Series? {
        return when (val parsed = CalibreRemoteId.decode(seriesRemoteId)) {
            is CalibreRemoteId.Parsed.Series -> CalibreMapper.seriesTile(parsed.name, id)
            is CalibreRemoteId.Parsed.Book -> {
                val dto = runCatching { api.book(parsed.id, library) }.getOrNull() ?: return null
                CalibreMapper.standaloneSeries(dto, parsed.id, id)
            }
        }
    }

    // ----------------------------------------------------------------- reading

    /**
     * Calibre serves whole files; there is no page streaming. Returning an empty list
     * signals the reader to use the whole-file path via [downloadFile].
     */
    override suspend fun pages(bookRemoteId: String): List<PageRef> = emptyList()

    override suspend fun openPage(ref: PageRef): ByteArray =
        throw UnsupportedOperationException("Calibre has no page streaming; use downloadFile")

    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray {
        val dto = api.book(bookRemoteId, library)
        val fmt = CalibreMapper.pickFormat(dto.formats)?.name
            ?: throw IllegalStateException("No readable format for book $bookRemoteId")
        val body = api.raw("get/$fmt/$bookRemoteId/$library")
        val total = body.contentLength()
        if (total <= 0) return body.bytes()
        val out = java.io.ByteArrayOutputStream(total.toInt().coerceAtLeast(8192))
        val buffer = ByteArray(8192)
        var read = 0L
        body.byteStream().use { stream ->
            var n: Int
            while (stream.read(buffer).also { n = it } != -1) {
                out.write(buffer, 0, n)
                read += n
                onProgress(read, total)
            }
        }
        return out.toByteArray()
    }

    override suspend fun seriesIdOf(bookRemoteId: String): String {
        val dto = api.book(bookRemoteId, library)
        val series = dto.series?.ifBlank { null }
        return if (series != null) CalibreRemoteId.forSeries(series)
        else CalibreRemoteId.forBook(bookRemoteId)
    }

    // ----------------------------------------------------------------- cover

    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        val bookId: String = if (isSeriesCover) {
            firstVolumeId(remoteId) ?: return ByteArray(0)
        } else {
            remoteId
        }
        return api.raw("get/cover/$bookId/$library").bytes()
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Resolves and caches the Base64-encoded segment of the Calibre "Series" category URL.
     * The URL from /ajax/categories looks like "/ajax/category/<encoded>" (optionally "/<lib>").
     */
    private suspend fun resolveSeriesCategory(): String {
        seriesCategoryEncoded?.let { return it }
        val cats = api.categories(library)
        val seriesCat = cats.firstOrNull { it.name.equals("Series", ignoreCase = true) }
            ?: throw IllegalStateException("Calibre: no Series category found in library '$library'")
        // Strip the /ajax/category/ prefix and any trailing library segment.
        val encoded = seriesCat.url
            .substringAfter("/ajax/category/")
            .substringBefore("/")
        seriesCategoryEncoded = encoded
        return encoded
    }

    /** Fetches book metadata for the given numeric IDs, dropping nulls, preserving order. */
    private suspend fun fetchBooks(ids: List<Int>): Map<String, CalibreBookDto> {
        if (ids.isEmpty()) return emptyMap()
        val raw = api.books(library, ids.joinToString(","))
        val out = LinkedHashMap<String, CalibreBookDto>()
        for (idInt in ids) {
            val key = idInt.toString()
            raw[key]?.let { out[key] = it }
        }
        return out
    }

    /**
     * Returns the book ID of the lowest-[series_index] volume in a series,
     * or null when the series is empty or remoteId is not a series.
     */
    private suspend fun firstVolumeId(seriesRemoteId: String): String? {
        val parsed = CalibreRemoteId.decode(seriesRemoteId)
        if (parsed is CalibreRemoteId.Parsed.Book) return parsed.id
        val name = (parsed as CalibreRemoteId.Parsed.Series).name
        val ids = api.search(library, seriesQuery(name), num = PAGE_SIZE, offset = 0).book_ids
        return fetchBooks(ids)
            .toList()
            .minByOrNull { (_, dto) -> dto.series_index ?: 0.0 }
            ?.first
    }

    private fun seriesQuery(name: String): String = "series:\"${name.replace("\"", "\\\"")}\""
}
