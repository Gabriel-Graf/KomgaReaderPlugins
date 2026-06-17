package com.komgareader.plugin.calibre

import com.komgareader.domain.model.BookFormat
import com.komgareader.plugin.calibre.api.CalibreBookDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibreMapperTest {

    @Test
    fun `pickFormat prefers EPUB then PDF then CBZ then CBR`() {
        assertEquals(BookFormat.EPUB, CalibreMapper.pickFormat(listOf("MOBI", "PDF", "EPUB")))
        assertEquals(BookFormat.PDF, CalibreMapper.pickFormat(listOf("MOBI", "PDF")))
        assertEquals(BookFormat.CBZ, CalibreMapper.pickFormat(listOf("CBZ", "CBR")))
        assertNull(CalibreMapper.pickFormat(listOf("MOBI", "AZW3")))
        assertNull(CalibreMapper.pickFormat(emptyList()))
    }

    @Test
    fun `toBook maps title, format, number and skips unreadable`() {
        val dto = CalibreBookDto(title = "Vol 1", series = "X", series_index = 1.0, formats = listOf("EPUB"))
        val book = CalibreMapper.toBook(dto, bookId = "7", sourceId = 99L, seriesTitle = "X")!!
        assertEquals("Vol 1", book.title)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals("7", book.remoteId)
        assertEquals("1", book.number)
        assertEquals(0, book.pageCount)
        assertNull(CalibreMapper.toBook(dto.copy(formats = listOf("MOBI")), "7", 99L, "X"))
    }

    @Test
    fun `groupSearch collapses series and keeps standalone separate`() {
        val books = mapOf(
            "1" to CalibreBookDto(title = "A1", series = "Saga", series_index = 1.0, formats = listOf("EPUB")),
            "2" to CalibreBookDto(title = "A2", series = "Saga", series_index = 2.0, formats = listOf("EPUB")),
            "3" to CalibreBookDto(title = "Solo", series = null, formats = listOf("PDF")),
        )
        val series = CalibreMapper.groupSearch(books, sourceId = 99L)
        assertEquals(2, series.size)
        assertTrue(series.any { it.title == "Saga" && it.remoteId == CalibreRemoteId.forSeries("Saga") })
        assertTrue(series.any { it.title == "Solo" && it.remoteId == CalibreRemoteId.forBook("3") })
    }
}
