package com.komgareader.plugin.kavita

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.model.DownloadState
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.source.PageRef
import com.komgareader.plugin.kavita.api.KavitaChapterDto
import com.komgareader.plugin.kavita.api.KavitaChapterInfoDto
import com.komgareader.plugin.kavita.api.KavitaProgressDto
import com.komgareader.plugin.kavita.api.KavitaSearchResultDto
import com.komgareader.plugin.kavita.api.KavitaSeriesDto
import com.komgareader.plugin.kavita.api.KavitaSeriesMetadataDto

/**
 * Reine Mapping-Funktionen: Kavita-DTOs → Domain-Modelle.
 *
 * Dieses Objekt hat keine Abhängigkeiten zu Netzwerk oder Android — alle Methoden
 * sind pure Funktionen und einfach unit-testbar.
 *
 * Kavita MangaFormat-Enum (Int):
 *   0 = Image, 1 = Archive, 2 = Unknown, 3 = Epub, 4 = Pdf
 *
 * Kavita PublicationStatus-Enum (Int):
 *   0 = OnGoing, 1 = Hiatus, 2 = Completed, 3 = Cancelled, 4 = Ended
 */
object KavitaMapper {

    // ------------------------------------------------------------------ //
    // Series                                                               //
    // ------------------------------------------------------------------ //

    /**
     * Bildet eine [KavitaSeriesDto] auf eine Domain-[Series] ab.
     *
     * [sourceId] wird vom Aufrufer beigesteuert (Host-seitig verwaltet).
     * [remoteId] ist die Kavita-Series-ID als String.
     * Metadaten (summary, genres, status) werden erst in [enrichWithMetadata] gefüllt,
     * da sie einen separaten API-Aufruf erfordern.
     */
    fun toSeries(dto: KavitaSeriesDto, sourceId: Long): Series = Series(
        id = 0L,                               // lokal von Room vergeben
        sourceId = sourceId,
        remoteId = dto.id.toString(),
        title = dto.name.ifBlank { dto.originalName }.ifBlank { "(unbekannt)" },
        coverUrl = null,                       // Cover wird über coverBytes geladen
        contentTypeOverride = null,            // kein Typ-Mapping — Nutzer setzt per Shelf
        summary = null,
        status = null,
        genres = emptyList(),
        readingDirection = null,
        libraryId = dto.libraryId.toString().takeIf { dto.libraryId > 0 },
    )

    /**
     * Reichert eine [Series] mit Daten aus [KavitaSeriesMetadataDto] an.
     * Gibt eine neue Kopie zurück (immutable).
     */
    fun enrichWithMetadata(series: Series, meta: KavitaSeriesMetadataDto): Series = series.copy(
        summary = meta.summary?.ifBlank { null },
        genres = meta.genres.map { it.title }.filter { it.isNotBlank() },
        status = publicationStatusToString(meta.publicationStatus),
    )

    /**
     * Bildet ein [KavitaSearchResultDto] (aus GET /api/Search/search) auf eine [Series] ab.
     *
     * Search-Ergebnisse haben weniger Felder als SeriesDto — fehlende Felder bleiben null.
     */
    fun searchResultToSeries(dto: KavitaSearchResultDto, sourceId: Long): Series = Series(
        id = 0L,
        sourceId = sourceId,
        remoteId = dto.seriesId.toString(),
        title = dto.name.ifBlank { dto.originalName }.ifBlank { "(unbekannt)" },
        coverUrl = null,
        contentTypeOverride = null,
        summary = null,
        status = null,
        genres = emptyList(),
        readingDirection = null,
        libraryId = dto.libraryId.toString().takeIf { dto.libraryId > 0 },
    )

    // ------------------------------------------------------------------ //
    // Book (= Kavita Chapter)                                              //
    // ------------------------------------------------------------------ //

    /**
     * Bildet ein [KavitaChapterDto] auf ein Domain-[Book] ab.
     *
     * Ein Kavita-Kapitel entspricht einem Book in der Domain.
     * - [remoteId] = Kapitel-ID als String
     * - [seriesId] und [sourceId] werden vom Aufrufer beigesteuert
     * - [pageCount] direkt aus dto.pages
     * - [number] = dto.range (menschenlesbare Kapitelnummer, z.B. "1" oder "1-3")
     * - Format wird aus dem MangaFormat-Int abgeleitet
     */
    fun toBook(
        dto: KavitaChapterDto,
        sourceId: Long,
        seriesId: Long,
        seriesTitle: String,
    ): Book = Book(
        id = 0L,
        sourceId = sourceId,
        seriesId = seriesId,
        remoteId = dto.id.toString(),
        title = buildChapterTitle(dto),
        format = mangaFormatToBookFormat(dto.format),
        pageCount = dto.pages.coerceAtLeast(0),
        downloadState = DownloadState.REMOTE,
        seriesTitle = seriesTitle,
        sizeBytes = 0L,
        fileUrl = null,
        createdDate = dto.createdUtc.ifBlank { dto.created }.ifBlank { null },
        modifiedDate = dto.lastModifiedUtc.ifBlank { null },
        summary = dto.summary?.ifBlank { null },
        number = dto.range.ifBlank { dto.minNumber.toBigDecimal().stripTrailingZeros().toPlainString() },
        lastReadPage = dto.pagesRead.takeIf { it > 0 },
        readCompleted = dto.pagesRead > 0 && dto.pages > 0 && dto.pagesRead >= dto.pages,
    )

    // ------------------------------------------------------------------ //
    // PageRef                                                              //
    // ------------------------------------------------------------------ //

    /**
     * Erzeugt eine Liste von [PageRef]s für ein Kapitel.
     *
     * [pageCount] aus [KavitaChapterInfoDto.pages] (0-basierter Index, 1-basierte pageNumber).
     * [baseUrl] = Kavita-Basis-URL ohne Trailing-Slash (wird für url verwendet).
     */
    fun toPageRefs(chapterId: String, pageCount: Int, baseUrl: String, apiKey: String): List<PageRef> {
        if (pageCount <= 0) return emptyList()
        val chapterIdInt = chapterId.toIntOrNull() ?: return emptyList()
        return (0 until pageCount).map { index ->
            PageRef(
                index = index,
                bookRemoteId = chapterId,
                pageNumber = index + 1,         // 1-basiert
                url = "${baseUrl.trimEnd('/')}/api/Reader/image?chapterId=$chapterIdInt&page=$index&apiKey=$apiKey",
            )
        }
    }

    // ------------------------------------------------------------------ //
    // ReadProgress                                                         //
    // ------------------------------------------------------------------ //

    /**
     * Bildet ein [KavitaProgressDto] auf einen Domain-[ReadProgress] ab.
     *
     * [bookId] ist lokal (Room-ID) — beim Pull-Vorgang noch unbekannt, daher 0.
     * Der Aufrufer muss [bookId] nach dem lokalen DB-Lookup befüllen.
     */
    fun toReadProgress(dto: KavitaProgressDto, totalPages: Int): ReadProgress = ReadProgress(
        bookId = 0L,
        page = dto.pageNum.coerceAtLeast(0),
        totalPages = totalPages.coerceAtLeast(1),
        completed = totalPages > 0 && dto.pageNum >= totalPages,
        locator = dto.bookScrollId,
        dirty = false,
        updatedAt = parseIsoMillis(dto.lastModifiedUtc),
    )

    /**
     * Bildet einen Domain-[ReadProgress] auf ein [KavitaProgressDto] ab (für Push).
     *
     * [chapterId], [volumeId], [seriesId], [libraryId] müssen vom Aufrufer beigesteuert werden,
     * da ReadProgress sie nicht trägt.
     */
    fun toProgressDto(
        progress: ReadProgress,
        chapterId: Int,
        volumeId: Int,
        seriesId: Int,
        libraryId: Int,
    ): KavitaProgressDto = KavitaProgressDto(
        chapterId = chapterId,
        volumeId = volumeId,
        seriesId = seriesId,
        libraryId = libraryId,
        pageNum = progress.page.coerceAtLeast(0),
        bookScrollId = progress.locator,
    )

    // ------------------------------------------------------------------ //
    // Hilfsfunktionen                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Lesbarer Titel eines Kapitels: Titel-Feld bevorzugt, dann titleName, dann Nummern-Fallback.
     */
    internal fun buildChapterTitle(dto: KavitaChapterDto): String {
        val explicitTitle = dto.title.ifBlank { dto.titleName?.ifBlank { null } }
        if (explicitTitle != null) return explicitTitle
        return if (dto.isSpecial) {
            dto.range.ifBlank { "Special" }
        } else {
            val num = dto.range.ifBlank { dto.minNumber.toBigDecimal().stripTrailingZeros().toPlainString() }
            "Kapitel $num"
        }
    }

    /**
     * Kavita MangaFormat (Int) → Domain [BookFormat].
     *
     * 0=Image, 1=Archive → CBZ (Bild-basiert)
     * 3=Epub            → EPUB
     * 4=Pdf             → PDF
     * 2=Unknown         → CBZ als sicherer Fallback
     */
    internal fun mangaFormatToBookFormat(format: Int): BookFormat = when (format) {
        3 -> BookFormat.EPUB
        4 -> BookFormat.PDF
        else -> BookFormat.CBZ      // Image, Archive, Unknown → CBZ
    }

    /**
     * Kavita PublicationStatus (Int) → lesbarer String für Domain [Series.status].
     *
     * 0=OnGoing, 1=Hiatus, 2=Completed, 3=Cancelled, 4=Ended
     */
    internal fun publicationStatusToString(status: Int): String? = when (status) {
        0 -> "ONGOING"
        1 -> "HIATUS"
        2 -> "COMPLETED"
        3 -> "CANCELLED"
        4 -> "ENDED"
        else -> null
    }

    /**
     * Parst ISO-8601-Zeitstempel (z.B. "2024-01-15T10:30:00Z") in Epoch-Millis (UTC).
     * Gibt 0L zurück wenn leer oder nicht parsebar.
     */
    internal fun parseIsoMillis(isoString: String): Long {
        if (isoString.isBlank()) return 0L
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            0L
        }
    }
}
