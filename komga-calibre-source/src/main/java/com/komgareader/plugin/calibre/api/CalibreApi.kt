package com.komgareader.plugin.calibre.api

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

/**
 * Retrofit interface for the Calibre Content Server /ajax/ API.
 * `library` is the resolved library id (e.g. "Calibre_Library"). `raw` fetches /get/ byte routes.
 */
interface CalibreApi {

    @GET("ajax/library-info")
    suspend fun libraryInfo(): LibraryInfoDto

    @GET("ajax/categories/{library}")
    suspend fun categories(@Path("library") library: String): List<CategoryDto>

    @GET("ajax/category/{encoded}/{library}")
    suspend fun category(
        @Path("encoded", encoded = true) encoded: String,
        @Path("library") library: String,
        @Query("num") num: Int,
        @Query("offset") offset: Int,
        @Query("sort") sort: String = "name",
    ): CategoryItemsDto

    @GET("ajax/search/{library}")
    suspend fun search(
        @Path("library") library: String,
        @Query("query") query: String,
        @Query("num") num: Int,
        @Query("offset") offset: Int,
    ): SearchDto

    @GET("ajax/books/{library}")
    suspend fun books(
        @Path("library") library: String,
        @Query("ids") ids: String,
    ): Map<String, CalibreBookDto?>

    @GET("ajax/book/{bookId}/{library}")
    suspend fun book(
        @Path("bookId") bookId: String,
        @Path("library") library: String,
    ): CalibreBookDto

    /** Raw bytes for /get/ routes (cover, format download). [path] is relative to baseUrl. */
    @Streaming
    @GET
    suspend fun raw(@Url path: String): ResponseBody
}
