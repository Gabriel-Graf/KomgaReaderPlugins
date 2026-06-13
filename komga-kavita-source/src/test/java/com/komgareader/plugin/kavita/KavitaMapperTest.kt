package com.komgareader.plugin.kavita

import com.komgareader.domain.model.BookFormat
import com.komgareader.plugin.kavita.api.KavitaChapterDto
import com.komgareader.plugin.kavita.api.KavitaProgressDto
import com.komgareader.plugin.kavita.api.KavitaSeriesDto
import com.komgareader.plugin.kavita.api.KavitaSeriesMetadataDto
import com.komgareader.plugin.kavita.api.KavitaGenreTagDto
import com.komgareader.domain.model.ReadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit-Tests für [KavitaMapper] — pure Mapping-Funktionen, kein Netzwerk.
 *
 * Jeder Test prüft sowohl einen „gesetzten" als auch einen „leeren/null"-Fall,
 * gemäß TDD-Vorgabe (Red-Green-Refactor).
 */
class KavitaMapperTest {

    // ------------------------------------------------------------------ //
    // toSeries                                                             //
    // ------------------------------------------------------------------ //

    @Test
    fun `toSeries bildet id, name und libraryId korrekt ab`() {
        val dto = KavitaSeriesDto(
            id = 42,
            name = "Berserk",
            libraryId = 7,
        )
        val series = KavitaMapper.toSeries(dto, sourceId = 100L)

        assertEquals("42", series.remoteId)
        assertEquals("Berserk", series.title)
        assertEquals(100L, series.sourceId)
        assertEquals("7", series.libraryId)
        assertNull("Summary bleibt null — Metadaten werden separat geladen", series.summary)
        assertTrue(series.genres.isEmpty())
    }

    @Test
    fun `toSeries fällt auf originalName zurück wenn name leer`() {
        val dto = KavitaSeriesDto(id = 1, name = "", originalName = "Vagabond")
        val series = KavitaMapper.toSeries(dto, sourceId = 1L)

        assertEquals("Vagabond", series.title)
    }

    @Test
    fun `toSeries liefert Fallback-Titel wenn name und originalName leer`() {
        val dto = KavitaSeriesDto(id = 1, name = "", originalName = "")
        val series = KavitaMapper.toSeries(dto, sourceId = 1L)

        assertEquals("(unbekannt)", series.title)
    }

    @Test
    fun `toSeries setzt libraryId null wenn libraryId 0`() {
        val dto = KavitaSeriesDto(id = 1, name = "Test", libraryId = 0)
        val series = KavitaMapper.toSeries(dto, sourceId = 1L)

        assertNull(series.libraryId)
    }

    // ------------------------------------------------------------------ //
    // enrichWithMetadata                                                   //
    // ------------------------------------------------------------------ //

    @Test
    fun `enrichWithMetadata füllt summary, genres und status`() {
        val base = KavitaMapper.toSeries(KavitaSeriesDto(id = 1, name = "Test"), 1L)
        val meta = KavitaSeriesMetadataDto(
            summary = "Eine tolle Serie",
            genres = listOf(KavitaGenreTagDto(id = 1, title = "Action"), KavitaGenreTagDto(id = 2, title = "Drama")),
            publicationStatus = 0, // ONGOING
        )

        val enriched = KavitaMapper.enrichWithMetadata(base, meta)

        assertEquals("Eine tolle Serie", enriched.summary)
        assertEquals(listOf("Action", "Drama"), enriched.genres)
        assertEquals("ONGOING", enriched.status)
    }

    @Test
    fun `enrichWithMetadata ignoriert leere summary`() {
        val base = KavitaMapper.toSeries(KavitaSeriesDto(id = 1, name = "Test"), 1L)
        val meta = KavitaSeriesMetadataDto(summary = "   ", genres = emptyList(), publicationStatus = 2)

        val enriched = KavitaMapper.enrichWithMetadata(base, meta)

        assertNull("Leere summary wird zu null", enriched.summary)
        assertEquals("COMPLETED", enriched.status)
    }

    @Test
    fun `enrichWithMetadata filtert leere genre-Titel`() {
        val base = KavitaMapper.toSeries(KavitaSeriesDto(id = 1, name = "Test"), 1L)
        val meta = KavitaSeriesMetadataDto(
            genres = listOf(KavitaGenreTagDto(id = 1, title = ""), KavitaGenreTagDto(id = 2, title = "Sci-Fi")),
        )

        val enriched = KavitaMapper.enrichWithMetadata(base, meta)

        assertEquals(listOf("Sci-Fi"), enriched.genres)
    }

    // ------------------------------------------------------------------ //
    // toBook                                                               //
    // ------------------------------------------------------------------ //

    @Test
    fun `toBook bildet Kapitel-Felder korrekt ab`() {
        val dto = KavitaChapterDto(
            id = 10,
            range = "3",
            pages = 32,
            isSpecial = false,
            title = "Der Kampf",
            format = 1, // Archive → CBZ
            pagesRead = 0,
            createdUtc = "2024-01-15T10:00:00Z",
            lastModifiedUtc = "2024-06-01T12:00:00Z",
        )

        val book = KavitaMapper.toBook(dto, sourceId = 100L, seriesId = 50L, seriesTitle = "Berserk")

        assertEquals("10", book.remoteId)
        assertEquals("Der Kampf", book.title)
        assertEquals(32, book.pageCount)
        assertEquals(BookFormat.CBZ, book.format)
        assertEquals(100L, book.sourceId)
        assertEquals(50L, book.seriesId)
        assertEquals("Berserk", book.seriesTitle)
        assertEquals("3", book.number)
        assertFalse(book.readCompleted)
        assertEquals("2024-01-15T10:00:00Z", book.createdDate)
        assertEquals("2024-06-01T12:00:00Z", book.modifiedDate)
    }

    @Test
    fun `toBook setzt readCompleted wenn pagesRead groesser gleich pages`() {
        val dto = KavitaChapterDto(id = 5, pages = 20, pagesRead = 20)
        val book = KavitaMapper.toBook(dto, 1L, 1L, "Serie")

        assertTrue(book.readCompleted)
        assertEquals(20, book.lastReadPage)
    }

    @Test
    fun `toBook setzt pageCount 0 wenn pages negativ`() {
        val dto = KavitaChapterDto(id = 1, pages = -5)
        val book = KavitaMapper.toBook(dto, 1L, 1L, "Serie")

        assertEquals(0, book.pageCount)
    }

    @Test
    fun `toBook Special hat Fallback-Titel`() {
        val dto = KavitaChapterDto(id = 99, isSpecial = true, title = "", range = "Omake")
        val book = KavitaMapper.toBook(dto, 1L, 1L, "Serie")

        assertEquals("Omake", book.title)
    }

    @Test
    fun `toBook Special ohne range hat Fallback Special`() {
        val dto = KavitaChapterDto(id = 99, isSpecial = true, title = "", range = "")
        val book = KavitaMapper.toBook(dto, 1L, 1L, "Serie")

        assertEquals("Special", book.title)
    }

    // ------------------------------------------------------------------ //
    // mangaFormatToBookFormat                                              //
    // ------------------------------------------------------------------ //

    @Test
    fun `mangaFormatToBookFormat bildet alle Werte korrekt ab`() {
        assertEquals(BookFormat.CBZ, KavitaMapper.mangaFormatToBookFormat(0)) // Image
        assertEquals(BookFormat.CBZ, KavitaMapper.mangaFormatToBookFormat(1)) // Archive
        assertEquals(BookFormat.CBZ, KavitaMapper.mangaFormatToBookFormat(2)) // Unknown
        assertEquals(BookFormat.EPUB, KavitaMapper.mangaFormatToBookFormat(3))
        assertEquals(BookFormat.PDF, KavitaMapper.mangaFormatToBookFormat(4))
        assertEquals(BookFormat.CBZ, KavitaMapper.mangaFormatToBookFormat(99)) // unbekannt → Fallback
    }

    // ------------------------------------------------------------------ //
    // publicationStatusToString                                            //
    // ------------------------------------------------------------------ //

    @Test
    fun `publicationStatusToString bildet alle bekannten Werte ab`() {
        assertEquals("ONGOING", KavitaMapper.publicationStatusToString(0))
        assertEquals("HIATUS", KavitaMapper.publicationStatusToString(1))
        assertEquals("COMPLETED", KavitaMapper.publicationStatusToString(2))
        assertEquals("CANCELLED", KavitaMapper.publicationStatusToString(3))
        assertEquals("ENDED", KavitaMapper.publicationStatusToString(4))
        assertNull(KavitaMapper.publicationStatusToString(99))
    }

    // ------------------------------------------------------------------ //
    // toPageRefs                                                           //
    // ------------------------------------------------------------------ //

    @Test
    fun `toPageRefs erzeugt korrekte PageRef-Liste`() {
        val refs = KavitaMapper.toPageRefs("7", 3, "http://server:5000", "my-api-key")

        assertEquals(3, refs.size)
        assertEquals(0, refs[0].index)
        assertEquals(1, refs[0].pageNumber)
        assertEquals("7", refs[0].bookRemoteId)
        assertTrue("URL muss chapterId und page enthalten",
            refs[0].url.contains("chapterId=7") && refs[0].url.contains("page=0"))
        assertEquals(2, refs[2].index)
        assertEquals(3, refs[2].pageNumber)
    }

    @Test
    fun `toPageRefs liefert leere Liste bei pageCount 0`() {
        val refs = KavitaMapper.toPageRefs("1", 0, "http://server", "key")
        assertTrue(refs.isEmpty())
    }

    @Test
    fun `toPageRefs liefert leere Liste bei ungültiger chapterId`() {
        val refs = KavitaMapper.toPageRefs("abc", 5, "http://server", "key")
        assertTrue(refs.isEmpty())
    }

    // ------------------------------------------------------------------ //
    // toReadProgress                                                       //
    // ------------------------------------------------------------------ //

    @Test
    fun `toReadProgress bildet pageNum und completed korrekt ab`() {
        val dto = KavitaProgressDto(pageNum = 15, chapterId = 1, volumeId = 1, seriesId = 1, libraryId = 1)
        val progress = KavitaMapper.toReadProgress(dto, totalPages = 30)

        assertEquals(15, progress.page)
        assertEquals(30, progress.totalPages)
        assertFalse(progress.completed)
        assertEquals(0L, progress.bookId) // bookId unbekannt beim Pull
    }

    @Test
    fun `toReadProgress setzt completed wenn pageNum gleich totalPages`() {
        val dto = KavitaProgressDto(pageNum = 20, chapterId = 1, volumeId = 1, seriesId = 1, libraryId = 1)
        val progress = KavitaMapper.toReadProgress(dto, totalPages = 20)

        assertTrue(progress.completed)
    }

    @Test
    fun `toReadProgress verarbeitet leere lastModifiedUtc`() {
        val dto = KavitaProgressDto(pageNum = 5, chapterId = 1, volumeId = 1, seriesId = 1, libraryId = 1, lastModifiedUtc = "")
        val progress = KavitaMapper.toReadProgress(dto, totalPages = 10)

        assertEquals(0L, progress.updatedAt)
    }

    @Test
    fun `toReadProgress parst ISO-Zeitstempel korrekt`() {
        val dto = KavitaProgressDto(
            pageNum = 1, chapterId = 1, volumeId = 1, seriesId = 1, libraryId = 1,
            lastModifiedUtc = "2024-01-15T10:30:00Z",
        )
        val progress = KavitaMapper.toReadProgress(dto, totalPages = 10)

        assertTrue("updatedAt muss > 0 sein für gültigen Zeitstempel", progress.updatedAt > 0L)
    }

    // ------------------------------------------------------------------ //
    // toProgressDto                                                        //
    // ------------------------------------------------------------------ //

    @Test
    fun `toProgressDto befüllt alle Pflichtfelder`() {
        val progress = ReadProgress(bookId = 99L, page = 7, totalPages = 50, updatedAt = 0L)
        val dto = KavitaMapper.toProgressDto(progress, chapterId = 10, volumeId = 2, seriesId = 3, libraryId = 1)

        assertEquals(10, dto.chapterId)
        assertEquals(2, dto.volumeId)
        assertEquals(3, dto.seriesId)
        assertEquals(1, dto.libraryId)
        assertEquals(7, dto.pageNum)
    }

    // ------------------------------------------------------------------ //
    // buildChapterTitle                                                    //
    // ------------------------------------------------------------------ //

    @Test
    fun `buildChapterTitle verwendet expliziten Titel`() {
        val dto = KavitaChapterDto(title = "Prolog", range = "0")
        assertEquals("Prolog", KavitaMapper.buildChapterTitle(dto))
    }

    @Test
    fun `buildChapterTitle fällt auf titleName zurück wenn title leer`() {
        val dto = KavitaChapterDto(title = "", titleName = "Epilog", range = "99")
        assertEquals("Epilog", KavitaMapper.buildChapterTitle(dto))
    }

    @Test
    fun `buildChapterTitle baut Kapitel-Nummer als Fallback`() {
        val dto = KavitaChapterDto(title = "", titleName = "", range = "5")
        assertEquals("Kapitel 5", KavitaMapper.buildChapterTitle(dto))
    }
}
