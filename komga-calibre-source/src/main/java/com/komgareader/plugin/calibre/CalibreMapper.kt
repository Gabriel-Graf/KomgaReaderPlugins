package com.komgareader.plugin.calibre

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.Series
import com.komgareader.plugin.calibre.api.CalibreBookDto

/** Pure DTO → domain mapping. */
object CalibreMapper {

    /** Readable formats in descending preference. */
    private val FORMAT_PRIORITY = listOf(
        "EPUB" to BookFormat.EPUB,
        "PDF" to BookFormat.PDF,
        "CBZ" to BookFormat.CBZ,
        "CBR" to BookFormat.CBR,
    )

    /**
     * Pick the best readable format from a list.
     * Prefers EPUB > PDF > CBZ > CBR.
     * Returns null if no readable format exists.
     */
    fun pickFormat(formats: List<String>?): BookFormat? {
        val upper = formats?.map { it.uppercase() }?.toSet() ?: return null
        return FORMAT_PRIORITY.firstOrNull { it.first in upper }?.second
    }

    /**
     * A standalone book (no Calibre series) → its own one-volume Series.
     */
    fun standaloneSeries(dto: CalibreBookDto, bookId: String, sourceId: Long): Series = Series(
        id = 0L,
        sourceId = sourceId,
        remoteId = CalibreRemoteId.forBook(bookId),
        title = dto.title.ifBlank { bookId },
        summary = dto.comments?.ifBlank { null },
    )

    /**
     * A Calibre series → a Series tile (volumes resolved later via [books]).
     */
    fun seriesTile(name: String, sourceId: Long): Series = Series(
        id = 0L,
        sourceId = sourceId,
        remoteId = CalibreRemoteId.forSeries(name),
        title = name,
    )

    /**
     * Maps a book DTO to a domain Book; returns null when no readable format exists.
     */
    fun toBook(dto: CalibreBookDto, bookId: String, sourceId: Long, seriesTitle: String): Book? {
        val format = pickFormat(dto.formats) ?: return null
        return Book(
            id = 0L,
            sourceId = sourceId,
            seriesId = 0L,
            remoteId = bookId,
            title = dto.title.ifBlank { bookId },
            format = format,
            pageCount = 0,
            seriesTitle = seriesTitle,
            summary = dto.comments?.ifBlank { null },
            number = dto.series_index?.let { formatIndex(it) },
        )
    }

    /**
     * Groups a fetched book map into Series tiles (series collapsed, standalone separate).
     */
    fun groupSearch(books: Map<String, CalibreBookDto>, sourceId: Long): List<Series> {
        val out = mutableListOf<Series>()
        val seenSeries = mutableSetOf<String>()
        // Preserve insertion order for determinism.
        for ((bookId, dto) in books) {
            val series = dto.series?.ifBlank { null }
            if (series != null) {
                if (seenSeries.add(series)) out.add(seriesTile(series, sourceId))
            } else {
                out.add(standaloneSeries(dto, bookId, sourceId))
            }
        }
        return out
    }

    /**
     * Format a series index for display.
     * "1.0" → "1", "1.5" → "1.5".
     */
    private fun formatIndex(index: Double): String =
        if (index % 1.0 == 0.0) index.toLong().toString() else index.toString()
}
