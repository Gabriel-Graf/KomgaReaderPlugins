package com.komgareader.plugin.calibre.api

import kotlinx.serialization.Serializable

/** Library metadata: list of available libraries and default. */
@Serializable
data class LibraryInfoDto(
    val library_map: Map<String, String> = emptyMap(),
    val default_library: String = "",
)

/** A searchable category (e.g., authors, tags). */
@Serializable
data class CategoryDto(
    val name: String = "",
    val url: String = "",
)

/** Paginated list of category items. */
@Serializable
data class CategoryItemsDto(
    val total_num: Int = 0,
    val items: List<CategoryItemDto> = emptyList(),
)

/** Single category item with count. */
@Serializable
data class CategoryItemDto(
    val name: String = "",
    val count: Int = 0,
)

/** Search result: total count and book IDs. */
@Serializable
data class SearchDto(
    val total_num: Int = 0,
    val book_ids: List<Int> = emptyList(),
)

/** A Calibre book with metadata and format info. */
@Serializable
data class CalibreBookDto(
    val title: String = "",
    val authors: List<String> = emptyList(),
    val series: String? = null,
    val series_index: Double? = null,
    val formats: List<String> = emptyList(),
    val main_format: Map<String, String> = emptyMap(),
    val comments: String? = null,
)
