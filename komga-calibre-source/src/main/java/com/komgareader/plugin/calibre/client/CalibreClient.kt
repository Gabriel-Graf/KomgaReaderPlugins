package com.komgareader.plugin.calibre.client

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.komgareader.plugin.calibre.api.CalibreApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Builds the Calibre Retrofit client.
 * @param baseUrl must end with "/".
 * @param username optional Basic-Auth (blank username = no auth header).
 * @param password optional Basic-Auth password.
 */
fun buildCalibreClient(
    baseUrl: String,
    username: String?,
    password: String?,
    debug: Boolean = false,
): CalibreApi {
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val okHttpClient = OkHttpClient.Builder()
        .apply {
            if (debug) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .addInterceptor(CalibreAuthInterceptor(username = username.orEmpty(), password = password.orEmpty()))
        .build()

    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CalibreApi::class.java)
}
