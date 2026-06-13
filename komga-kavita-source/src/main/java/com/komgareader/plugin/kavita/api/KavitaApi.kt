package com.komgareader.plugin.kavita.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit-Interface für alle genutzten Kavita-Endpunkte.
 *
 * Alle Pfade wurden gegen den offiziellen OpenAPI-Spec (Kareadita/Kavita develop) verifiziert.
 */
interface KavitaApi {

    // ------------------------------------------------------------------ //
    // Auth                                                                 //
    // ------------------------------------------------------------------ //

    /** POST /api/Plugin/authenticate — liefert JWT für alle Folge-Requests. */
    @POST("api/Plugin/authenticate")
    suspend fun authenticate(
        @Query("apiKey") apiKey: String,
        @Query("pluginName") pluginName: String,
    ): KavitaAuthResponse

    // ------------------------------------------------------------------ //
    // Library                                                              //
    // ------------------------------------------------------------------ //

    /** GET /api/Library/libraries — alle Bibliotheken des Servers. */
    @GET("api/Library/libraries")
    suspend fun libraries(): List<KavitaLibraryDto>

    // ------------------------------------------------------------------ //
    // Series                                                               //
    // ------------------------------------------------------------------ //

    /**
     * POST /api/Series/v2 — alle Serien mit optionalem Filter (paginiert).
     *
     * [pageNumber] und [pageSize] als Query-Parameter; Body = SeriesFilterV2Dto.
     * Leerer Statements-Filter liefert alle Serien.
     *
     * Gibt [Response] zurück, damit der X-Pagination-Header ausgelesen werden kann.
     * Der Header enthält ein JSON-Objekt mit currentPage, totalPages, itemsPerPage, totalItems.
     */
    @POST("api/Series/v2")
    suspend fun seriesV2(
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
        @Body filter: KavitaSeriesFilterV2Dto,
    ): Response<List<KavitaSeriesDto>>

    /**
     * GET /api/Series/metadata?seriesId= — Metadaten (Summary, Genres, Status) einer Serie.
     */
    @GET("api/Series/metadata")
    suspend fun seriesMetadata(
        @Query("seriesId") seriesId: Int,
    ): KavitaSeriesMetadataDto

    /**
     * GET /api/Series/series-detail?seriesId= — Volumes, Chapters und Specials einer Serie.
     *
     * Hinweis: laut OpenAPI-Kommentar „Do not rely on this API externally. May change without hesitation."
     * Alternative wäre GET /api/Series/volumes, aber series-detail liefert Volumes+Chapters+Specials
     * in einem Aufruf.
     */
    @GET("api/Series/series-detail")
    suspend fun seriesDetail(
        @Query("seriesId") seriesId: Int,
    ): KavitaSeriesDetailDto

    /**
     * GET /api/Series/volumes?seriesId= — alle Volumes einer Serie inkl. ihrer Chapters.
     */
    @GET("api/Series/volumes")
    suspend fun seriesVolumes(
        @Query("seriesId") seriesId: Int,
    ): List<KavitaVolumeDto>

    // ------------------------------------------------------------------ //
    // Reader                                                               //
    // ------------------------------------------------------------------ //

    /**
     * GET /api/Reader/chapter-info?chapterId= — Kapitelinfos (Seitenanzahl, SeriesId, LibraryId).
     *
     * Wichtiger Seiteneffekt laut Kavita: cacht Kapitelbilder für den Reader.
     * Daher vor dem ersten openPage-Aufruf aufrufen.
     */
    @GET("api/Reader/chapter-info")
    suspend fun chapterInfo(
        @Query("chapterId") chapterId: Int,
    ): KavitaChapterInfoDto

    /**
     * GET /api/Reader/image?chapterId=&page=&apiKey= — ein Seitenbild als Byte-Stream.
     *
     * [page] ist 0-basiert. [apiKey] für direkte Authentifizierung ohne Bearer-Token.
     */
    @Streaming
    @GET("api/Reader/image")
    suspend fun readerImage(
        @Query("chapterId") chapterId: Int,
        @Query("page") page: Int,
        @Query("apiKey") apiKey: String,
    ): ResponseBody

    /**
     * POST /api/Reader/progress — Lesefortschritt speichern.
     *
     * Pflichtfelder: volumeId, chapterId, pageNum, seriesId, libraryId.
     */
    @POST("api/Reader/progress")
    suspend fun saveProgress(
        @Body progress: KavitaProgressDto,
    )

    /**
     * GET /api/Reader/get-progress?chapterId= — aktuellen Lesefortschritt abrufen.
     */
    @GET("api/Reader/get-progress")
    suspend fun getProgress(
        @Query("chapterId") chapterId: Int,
    ): KavitaProgressDto?

    /**
     * POST /api/Reader/mark-read — gesamte Serie als gelesen markieren.
     */
    @POST("api/Reader/mark-read")
    suspend fun markRead(
        @Body body: KavitaMarkReadDto,
    )

    /**
     * POST /api/Reader/mark-unread — gesamte Serie als ungelesen markieren.
     */
    @POST("api/Reader/mark-unread")
    suspend fun markUnread(
        @Body body: KavitaMarkReadDto,
    )

    // ------------------------------------------------------------------ //
    // Image                                                                //
    // ------------------------------------------------------------------ //

    /**
     * GET /api/Image/series-cover?seriesId=&apiKey= — Cover-Bild einer Serie.
     */
    @Streaming
    @GET("api/Image/series-cover")
    suspend fun seriesCover(
        @Query("seriesId") seriesId: Int,
        @Query("apiKey") apiKey: String,
    ): ResponseBody

    /**
     * GET /api/Image/chapter-cover?chapterId=&apiKey= — Cover-Bild eines Kapitels.
     */
    @Streaming
    @GET("api/Image/chapter-cover")
    suspend fun chapterCover(
        @Query("chapterId") chapterId: Int,
        @Query("apiKey") apiKey: String,
    ): ResponseBody

    // ------------------------------------------------------------------ //
    // Download                                                             //
    // ------------------------------------------------------------------ //

    /**
     * GET /api/Download/chapter?chapterId= — Kapitel-Datei herunterladen (ggf. als ZIP).
     */
    @Streaming
    @GET("api/Download/chapter")
    suspend fun downloadChapter(
        @Query("chapterId") chapterId: Int,
    ): ResponseBody

    // ------------------------------------------------------------------ //
    // Search                                                               //
    // ------------------------------------------------------------------ //

    /**
     * GET /api/Search/search?queryString= — Volltextsuche.
     *
     * Gibt [KavitaSearchResultGroupDto] mit einer Liste gefundener Serien zurück.
     */
    @GET("api/Search/search")
    suspend fun search(
        @Query("queryString") query: String,
        @Query("includeChapterAndFiles") includeChapterAndFiles: Boolean = false,
    ): KavitaSearchResultGroupDto
}
