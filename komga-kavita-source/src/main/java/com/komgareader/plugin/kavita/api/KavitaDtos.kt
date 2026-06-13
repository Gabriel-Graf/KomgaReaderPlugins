package com.komgareader.plugin.kavita.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Antwort von POST /api/Plugin/authenticate.
 *
 * Das Feld [token] enthält den JWT für alle weiteren Anfragen.
 */
@Serializable
data class KavitaAuthResponse(
    val token: String,
    @SerialName("refreshToken")
    val refreshToken: String = "",
    val username: String = "",
)

// ---------------------------------------------------------------------------
// Library
// ---------------------------------------------------------------------------

/** Antwort-Element von GET /api/Library/libraries */
@Serializable
data class KavitaLibraryDto(
    val id: Int = 0,
    val name: String = "",
    /** Typ als Integer (0=Manga, 1=Comic, 2=Book, 3=Image, 4=LightNovel) */
    val type: Int = 0,
)

// ---------------------------------------------------------------------------
// Series
// ---------------------------------------------------------------------------

/** Ein Series-Eintrag aus POST /api/Series/v2 */
@Serializable
data class KavitaSeriesDto(
    val id: Int = 0,
    val name: String = "",
    val originalName: String = "",
    val sortName: String = "",
    /** MangaFormat-Enum als Int: 0=Image, 1=Archive, 2=Unknown, 3=Epub, 4=Pdf */
    val format: Int = 2,
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val libraryId: Int = 0,
    val libraryName: String = "",
    val coverImage: String? = null,
    val created: String = "",
)

/** Antwort von GET /api/Series/metadata?seriesId= */
@Serializable
data class KavitaSeriesMetadataDto(
    val id: Int = 0,
    val summary: String? = null,
    val genres: List<KavitaGenreTagDto> = emptyList(),
    /** PublicationStatus: 0=OnGoing, 1=Hiatus, 2=Completed, 3=Cancelled, 4=Ended */
    val publicationStatus: Int = 0,
    val releaseYear: Int = 0,
    val language: String? = null,
    val seriesId: Int = 0,
)

@Serializable
data class KavitaGenreTagDto(
    val id: Int = 0,
    val title: String = "",
)

/** Antwort von GET /api/Series/series-detail?seriesId= */
@Serializable
data class KavitaSeriesDetailDto(
    val specials: List<KavitaChapterDto> = emptyList(),
    val chapters: List<KavitaChapterDto> = emptyList(),
    val volumes: List<KavitaVolumeDto> = emptyList(),
    val storylineChapters: List<KavitaChapterDto> = emptyList(),
    val unreadCount: Int = 0,
    val totalCount: Int = 0,
)

// ---------------------------------------------------------------------------
// Volume / Chapter
// ---------------------------------------------------------------------------

/** Antwort-Element von GET /api/Series/volumes?seriesId= */
@Serializable
data class KavitaVolumeDto(
    val id: Int = 0,
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val name: String = "",
    val pages: Int = 0,
    val pagesRead: Int = 0,
    val seriesId: Int = 0,
    val chapters: List<KavitaChapterDto> = emptyList(),
    val coverImage: String? = null,
    val lastModifiedUtc: String = "",
    val createdUtc: String = "",
)

/**
 * Ein Kapitel (Chapter) oder Special in Kavita.
 *
 * Felder, die im Mapping wichtig sind:
 *  - [id]: Kapitel-ID (bookRemoteId)
 *  - [pages]: Seitenanzahl
 *  - [title] / [titleName]: menschenlesbarer Titel
 *  - [sortOrder]: Sortierreihenfolge innerhalb des Volumes
 *  - [volumeId]: übergeordnetes Volume
 *  - [isSpecial]: ob es ein Special ist
 *  - [format]: MangaFormat als Int
 */
@Serializable
data class KavitaChapterDto(
    val id: Int = 0,
    val range: String = "",
    val minNumber: Float = 0f,
    val maxNumber: Float = 0f,
    val sortOrder: Float = 0f,
    val pages: Int = 0,
    val isSpecial: Boolean = false,
    val title: String = "",
    val titleName: String? = null,
    val summary: String? = null,
    val pagesRead: Int = 0,
    val volumeId: Int = 0,
    val createdUtc: String = "",
    val lastModifiedUtc: String = "",
    val created: String = "",
    val releaseDate: String = "",
    val coverImage: String? = null,
    /** MangaFormat: 0=Image, 1=Archive, 2=Unknown, 3=Epub, 4=Pdf */
    val format: Int = 2,
)

/** Antwort von GET /api/Reader/chapter-info?chapterId= */
@Serializable
data class KavitaChapterInfoDto(
    val chapterNumber: String = "",
    val volumeNumber: String = "",
    val volumeId: Int = 0,
    val seriesName: String = "",
    /** MangaFormat als Int */
    val seriesFormat: Int = 2,
    val seriesId: Int = 0,
    val libraryId: Int = 0,
    val chapterTitle: String = "",
    val pages: Int = 0,
    val fileName: String = "",
    val isSpecial: Boolean = false,
    val title: String = "",
)

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

/**
 * Lese-Fortschritt für POST /api/Reader/progress und GET /api/Reader/get-progress.
 *
 * Pflichtfelder (lt. OpenAPI required): volumeId, chapterId, pageNum, seriesId, libraryId.
 */
@Serializable
data class KavitaProgressDto(
    val volumeId: Int = 0,
    val chapterId: Int = 0,
    val pageNum: Int = 0,
    val seriesId: Int = 0,
    val libraryId: Int = 0,
    val bookScrollId: String? = null,
    val lastModifiedUtc: String = "",
)

/**
 * Request-Body für POST /api/Reader/mark-read und /api/Reader/mark-unread.
 * Beide Endpunkte erwarten MarkReadDto { seriesId, generateReadingSession }.
 */
@Serializable
data class KavitaMarkReadDto(
    val seriesId: Int,
    val generateReadingSession: Boolean = false,
)

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

/** Eintrag in SearchResultGroupDto.series */
@Serializable
data class KavitaSearchResultDto(
    val seriesId: Int = 0,
    val name: String = "",
    val originalName: String = "",
    val sortName: String = "",
    val localizedName: String = "",
    val format: Int = 2,
    val libraryName: String = "",
    val libraryId: Int = 0,
    val releaseYear: Int = 0,
    val volumeCount: Int = 0,
    val chapterCount: Int = 0,
)

/** Antwort von GET /api/Search/search?queryString= */
@Serializable
data class KavitaSearchResultGroupDto(
    val series: List<KavitaSearchResultDto> = emptyList(),
)

// ---------------------------------------------------------------------------
// Pagination
// ---------------------------------------------------------------------------

/**
 * Paginierungs-Metadaten aus dem X-Pagination-Antwort-Header von POST /api/Series/v2.
 *
 * Format: {"currentPage":1,"itemsPerPage":50,"totalItems":200,"totalPages":4}
 */
@Serializable
data class KavitaPaginationDto(
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val itemsPerPage: Int = 0,
    val totalItems: Int = 0,
)

// ---------------------------------------------------------------------------
// Series v2 Filter (minimaler Request-Body für POST /api/Series/v2)
// ---------------------------------------------------------------------------

/**
 * Minimal-Body für POST /api/Series/v2.
 *
 * [statements] leer = kein Filter → alle Serien.
 * [combination] 0 = Or, 1 = And.
 * [entityType] 0 = Series.
 */
@Serializable
data class KavitaSeriesFilterV2Dto(
    val id: Int = 0,
    val name: String = "",
    val statements: List<KavitaSeriesFilterStatementDto> = emptyList(),
    val combination: Int = 0,
    val sortOptions: KavitaSeriesSortOptionDto = KavitaSeriesSortOptionDto(),
    val entityType: Int = 0,
    val limitTo: Int = 0,
)

@Serializable
data class KavitaSeriesSortOptionDto(
    /** 1 = SortName */
    val sortField: Int = 1,
    val isAscending: Boolean = true,
)

@Serializable
data class KavitaSeriesFilterStatementDto(
    val comparison: Int = 0,
    val field: Int = 0,
    val value: String = "",
)
