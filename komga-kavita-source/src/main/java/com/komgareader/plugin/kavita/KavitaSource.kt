package com.komgareader.plugin.kavita

import com.komgareader.domain.model.Book
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.model.Series
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.source.PagedResult
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SourceId
import com.komgareader.domain.source.SyncingSource
import com.komgareader.plugin.kavita.api.KavitaApi
import com.komgareader.plugin.kavita.api.KavitaMarkReadDto
import com.komgareader.plugin.kavita.api.KavitaPaginationDto
import com.komgareader.plugin.kavita.api.KavitaSeriesFilterV2Dto
import kotlinx.serialization.json.Json

/**
 * Implementierung von [BrowsableSource] + [SyncingSource] gegen die Kavita-REST-API.
 *
 * Jede Instanz ist an eine Kavita-Server-URL und einen API-Schlüssel gebunden.
 * Der [api]-Client enthält bereits den Auth-Interceptor ([KavitaAuthInterceptor]),
 * der JWT-Tokens transparent beschafft und bei 401 erneuert.
 *
 * Thread-Sicherheit: alle suspend-Funktionen sind reentrant — der Auth-Interceptor
 * verwendet intern einen [ReentrantLock].
 */
class KavitaSource(
    private val api: KavitaApi,
    private val baseUrl: String,
    private val apiKey: String,
    override val name: String,
) : BrowsableSource, SyncingSource {

    // ------------------------------------------------------------------ //
    // MediaSource-Identität                                                //
    // ------------------------------------------------------------------ //

    override val id: Long = SourceId.of(name, SourceKind.PLUGIN, baseUrl.trimEnd('/'))
    override val kind: SourceKind = SourceKind.PLUGIN

    companion object {
        /** Seitengröße für /api/Series/v2 — groß genug für typische Bibliotheken. */
        private const val PAGE_SIZE = 50

        /** Name des Antwort-Headers mit Paginierungs-Metadaten. */
        private const val PAGINATION_HEADER = "X-Pagination"

        /**
         * Lenient Json-Instanz nur zum Parsen des X-Pagination-Headers.
         * Unbekannte Felder werden ignoriert, damit zukünftige Kavita-Versionen
         * den Header erweitern können, ohne den Parser zu brechen.
         */
        private val paginationJson = Json { ignoreUnknownKeys = true }
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: Browsen                                            //
    // ------------------------------------------------------------------ //

    /**
     * Listet Serien — paginiert über POST /api/Series/v2.
     *
     * [SourceFilter.containerIds] kann Library-IDs enthalten; falls vorhanden, wird der
     * erste Eintrag als Filter benutzt (Kavita hat kein Multi-Library-Filter in v2).
     * Leerer Filter → alle Serien.
     *
     * Paginierung: liest den X-Pagination-Antwort-Header (JSON mit currentPage/totalPages).
     * Falls der Header fehlt oder nicht parsebar ist, fällt die Logik auf die alte
     * Seitengrößen-Heuristik zurück (hasNextPage = items.size >= PAGE_SIZE).
     */
    override suspend fun browse(page: Int, filter: SourceFilter): PagedResult<Series> {
        val filterBody = KavitaSeriesFilterV2Dto()
        // page ist 1-basiert in der Domain, Kavita erwartet ebenfalls 1-basiert
        val kavitaPage = (page - 1).coerceAtLeast(0) + 1
        val response = api.seriesV2(
            pageNumber = kavitaPage,
            pageSize = PAGE_SIZE,
            filter = filterBody,
        )
        val items = response.body().orEmpty()
        val series = items.map { KavitaMapper.toSeries(it, id) }

        // X-Pagination-Header auslesen; bei Fehler Heuristik als Fallback
        val hasNextPage = runCatching {
            val headerValue = response.headers()[PAGINATION_HEADER]
                ?: return@runCatching null
            val pagination = paginationJson.decodeFromString<KavitaPaginationDto>(headerValue)
            pagination.currentPage < pagination.totalPages
        }.getOrNull() ?: (items.size >= PAGE_SIZE)

        return PagedResult(
            items = series,
            hasNextPage = hasNextPage,
        )
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: Suche                                              //
    // ------------------------------------------------------------------ //

    /**
     * Sucht Serien über GET /api/Search/search.
     *
     * Kavita unterstützt keine paginierte Suche; [page] > 1 liefert immer eine leere Liste.
     * Die Ergebnisse kommen einmalig aus dem ersten Aufruf.
     */
    override suspend fun search(query: String, page: Int): PagedResult<Series> {
        if (page > 1) return PagedResult(emptyList(), hasNextPage = false)
        val result = api.search(query = query, includeChapterAndFiles = false)
        val series = (result.series).map { KavitaMapper.searchResultToSeries(it, id) }
        return PagedResult(items = series, hasNextPage = false)
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: Serie-Detail                                       //
    // ------------------------------------------------------------------ //

    /**
     * Lädt eine einzelne Serie mit ihren Metadaten.
     *
     * Kombination aus POST /api/Series/v2 (um die SeriesDto zu erhalten) und
     * GET /api/Series/metadata (für summary, genres, status).
     *
     * Hinweis: GET /api/Series/{id} existiert im OpenAPI-Spec nicht (NOT FOUND) —
     * daher wird /api/Series/v2 mit einem einzigen Eintrag als Fallback genutzt,
     * falls die vollständige Liste keine Effizienz-Bedenken hat. Für die typische
     * Einzelabfrage holen wir via Metadata-Endpoint und konstruieren die Series
     * aus den Metadaten + einer leichtgewichtigen Browse-Suche.
     */
    override suspend fun seriesDetail(seriesRemoteId: String): Series? {
        val seriesId = seriesRemoteId.toIntOrNull() ?: return null
        // Alle Serien suchen ist teuer — wir nutzen search als Näherung
        // (keine direkte GET /api/Series/{id} verfügbar, laut OpenAPI nicht vorhanden)
        val searchResult = runCatching {
            api.search(query = seriesRemoteId, includeChapterAndFiles = false).series
                .firstOrNull { it.seriesId == seriesId }
        }.getOrNull()

        val baseSeries = if (searchResult != null) {
            KavitaMapper.searchResultToSeries(searchResult, id)
        } else {
            // Fallback: leere Basis-Series mit bekannter ID
            Series(
                id = 0L,
                sourceId = id,
                remoteId = seriesRemoteId,
                title = seriesRemoteId,
                libraryId = null,
            )
        }

        // Metadaten anreichern
        val meta = runCatching { api.seriesMetadata(seriesId) }.getOrNull()
        return if (meta != null) KavitaMapper.enrichWithMetadata(baseSeries, meta) else baseSeries
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: Books (= Chapters)                                 //
    // ------------------------------------------------------------------ //

    /**
     * Lädt alle Bücher (Chapters) einer Serie.
     *
     * Nutzt GET /api/Series/series-detail, das Volumes+Chapters+Specials in einem
     * Aufruf liefert. Sortierung: Volume-Reihenfolge, dann Chapter-sortOrder.
     */
    override suspend fun books(seriesRemoteId: String): List<Book> {
        val seriesId = seriesRemoteId.toIntOrNull() ?: return emptyList()

        val detail = runCatching { api.seriesDetail(seriesId) }.getOrNull() ?: return emptyList()

        // Serien-Titel für Book.seriesTitle
        val seriesTitle = runCatching {
            api.search(query = seriesRemoteId, includeChapterAndFiles = false)
                .series.firstOrNull { it.seriesId == seriesId }?.name
        }.getOrNull() ?: seriesRemoteId

        val books = mutableListOf<Book>()

        // Chapters aus Volumes (geordnet nach Volume-Nummer, dann sortOrder)
        val volumeChapters = detail.volumes
            .sortedBy { it.minNumber }
            .flatMap { vol -> vol.chapters.sortedBy { it.sortOrder } }

        // Standalone storyline chapters (nicht in Volumes)
        val storyChapters = detail.storylineChapters.sortedBy { it.sortOrder }

        // Specials
        val specialChapters = detail.specials.sortedBy { it.sortOrder }

        // Alle Chapters deduplicieren (series-detail kann Überschneidungen haben)
        val seen = mutableSetOf<Int>()
        for (ch in volumeChapters + storyChapters + specialChapters) {
            if (seen.add(ch.id)) {
                books.add(KavitaMapper.toBook(ch, id, 0L, seriesTitle))
            }
        }

        return books
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: Pages                                              //
    // ------------------------------------------------------------------ //

    /**
     * Erzeugt PageRef-Liste für ein Kapitel.
     *
     * Ruft zunächst GET /api/Reader/chapter-info auf (liefert pageCount + cacht Bilder),
     * dann wird die Liste deterministisch aus der Seitenanzahl generiert.
     */
    override suspend fun pages(bookRemoteId: String): List<PageRef> {
        val chapterId = bookRemoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Kapitel-ID: $bookRemoteId")
        val info = api.chapterInfo(chapterId)
        return KavitaMapper.toPageRefs(bookRemoteId, info.pages, baseUrl, apiKey)
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: openPage                                           //
    // ------------------------------------------------------------------ //

    /**
     * Lädt eine einzelne Seite als Bytes.
     *
     * GET /api/Reader/image?chapterId=&page=&apiKey= (0-basierter Index).
     */
    override suspend fun openPage(ref: PageRef): ByteArray {
        val chapterId = ref.bookRemoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Kapitel-ID in PageRef: ${ref.bookRemoteId}")
        val body = api.readerImage(
            chapterId = chapterId,
            page = ref.index,     // 0-basiert
            apiKey = apiKey,
        )
        return body.bytes()
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: downloadFile                                       //
    // ------------------------------------------------------------------ //

    /**
     * Lädt die Rohdatei eines Kapitels herunter.
     *
     * GET /api/Download/chapter?chapterId= — gibt bei mehreren Dateien eine ZIP zurück.
     * onProgress wird best-effort mit Byte-Fortschritt aufgerufen (nur wenn Content-Length bekannt).
     */
    override suspend fun downloadFile(
        bookRemoteId: String,
        onProgress: (read: Long, total: Long) -> Unit,
    ): ByteArray {
        val chapterId = bookRemoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Kapitel-ID: $bookRemoteId")
        val body = api.downloadChapter(chapterId)
        val total = body.contentLength()
        return if (total > 0) {
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream(total.toInt().coerceAtLeast(8192))
            var read = 0L
            body.byteStream().use { stream ->
                var n: Int
                while (stream.read(buffer).also { n = it } != -1) {
                    out.write(buffer, 0, n)
                    read += n
                    onProgress(read, total)
                }
            }
            out.toByteArray()
        } else {
            // Content-Length unbekannt → alles auf einmal laden, kein Fortschritt
            body.bytes()
        }
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: seriesIdOf                                         //
    // ------------------------------------------------------------------ //

    /**
     * Gibt die Serien-ID für ein gegebenes Kapitel zurück.
     *
     * Nutzt GET /api/Reader/chapter-info, das [KavitaChapterInfoDto.seriesId] enthält.
     */
    override suspend fun seriesIdOf(bookRemoteId: String): String {
        val chapterId = bookRemoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Kapitel-ID: $bookRemoteId")
        val info = api.chapterInfo(chapterId)
        return info.seriesId.toString()
    }

    // ------------------------------------------------------------------ //
    // BrowsableSource: coverBytes                                         //
    // ------------------------------------------------------------------ //

    /**
     * Lädt ein Cover-Bild.
     *
     * [isSeriesCover] = true → GET /api/Image/series-cover
     * [isSeriesCover] = false → GET /api/Image/chapter-cover
     */
    override suspend fun coverBytes(remoteId: String, isSeriesCover: Boolean): ByteArray {
        val id = remoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Remote-ID für Cover: $remoteId")
        return if (isSeriesCover) {
            api.seriesCover(seriesId = id, apiKey = apiKey).bytes()
        } else {
            api.chapterCover(chapterId = id, apiKey = apiKey).bytes()
        }
    }

    // ------------------------------------------------------------------ //
    // SyncingSource                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Sendet Lesefortschritt an Kavita.
     *
     * Benötigt volumeId, seriesId, libraryId — diese werden über
     * GET /api/Reader/chapter-info nachgeladen, da ReadProgress sie nicht trägt.
     */
    override suspend fun pushProgress(bookRemoteId: String, progress: ReadProgress) {
        val chapterId = bookRemoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Kapitel-ID: $bookRemoteId")
        val info = api.chapterInfo(chapterId)
        val dto = KavitaMapper.toProgressDto(
            progress = progress,
            chapterId = chapterId,
            volumeId = info.volumeId,
            seriesId = info.seriesId,
            libraryId = info.libraryId,
        )
        api.saveProgress(dto)
    }

    /**
     * Liest Lesefortschritt von Kavita.
     *
     * GET /api/Reader/get-progress?chapterId=
     * Gibt null zurück, wenn noch kein Fortschritt gespeichert wurde (404 oder pageNum=0).
     *
     * Die Gesamtseitenzahl wird über chapter-info nachgeladen.
     */
    override suspend fun pullProgress(bookRemoteId: String): ReadProgress? {
        val chapterId = bookRemoteId.toIntOrNull() ?: return null
        val progressDto = runCatching { api.getProgress(chapterId) }.getOrNull() ?: return null
        if (progressDto.pageNum == 0 && progressDto.lastModifiedUtc.isBlank()) return null

        val info = runCatching { api.chapterInfo(chapterId) }.getOrNull()
        val totalPages = info?.pages ?: progressDto.pageNum.coerceAtLeast(1)

        return KavitaMapper.toReadProgress(progressDto, totalPages)
    }

    /**
     * Markiert eine Serie als gelesen oder ungelesen.
     *
     * [read] = true  → POST /api/Reader/mark-read  (setzt alle Volumes/Chapters auf gelesen)
     * [read] = false → POST /api/Reader/mark-unread
     *
     * Kavita mark-read/mark-unread arbeitet auf Series-Ebene, nicht auf Chapter-Ebene.
     * Daher wird über chapter-info die seriesId ermittelt.
     *
     * [pageCount] wird nicht benötigt, da Kavita den Status intern verwaltet.
     */
    override suspend fun setRead(bookRemoteId: String, read: Boolean, pageCount: Int) {
        val chapterId = bookRemoteId.toIntOrNull()
            ?: throw IllegalArgumentException("Ungültige Kapitel-ID: $bookRemoteId")
        val info = api.chapterInfo(chapterId)
        val body = KavitaMarkReadDto(seriesId = info.seriesId)
        if (read) {
            api.markRead(body)
        } else {
            api.markUnread(body)
        }
    }
}
