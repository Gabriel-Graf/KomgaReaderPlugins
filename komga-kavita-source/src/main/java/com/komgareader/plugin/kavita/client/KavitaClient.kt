package com.komgareader.plugin.kavita.client

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.komgareader.plugin.kavita.api.KavitaApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * Fabrik-Funktion für den Kavita-Retrofit-Client.
 *
 * Baut den vollständigen OkHttp+Retrofit-Stack:
 *   - JSON-Deserialisierung über kotlinx.serialization (ignoreUnknownKeys)
 *   - [KavitaAuthInterceptor]: apiKey → JWT-Austausch + Bearer-Header + 401-Retry
 *   - Optionaler HTTP-Logging-Interceptor (nur für Entwicklung)
 *
 * Der Auth-Interceptor nutzt einen eigenen OkHttpClient ohne Interceptor, der
 * an [authBaseUrl] sendet. Das verhindert zirkuläre Aufrufe (Hauptclient →
 * Interceptor → Auth-Call → Hauptclient → Deadlock).
 *
 * @param baseUrl     Vollständige Basis-URL der Kavita-Instanz für API-Aufrufe
 *                    (z. B. "https://mein-kavita.example.com/"). Muss mit "/" enden.
 * @param apiKey      Kavita-API-Schlüssel des Nutzers.
 * @param pluginName  Bezeichner, der beim Authenticate an Kavita gemeldet wird.
 * @param authBaseUrl Basis-URL für Auth-Anfragen — normalerweise identisch mit [baseUrl].
 *                    Kann für Tests auf einen separaten MockWebServer zeigen.
 * @param debug       true = HTTP-Body-Logging aktiviert (nur für Entwicklung).
 */
fun buildKavitaClient(
    baseUrl: String,
    apiKey: String,
    pluginName: String = "Komga-Reader",
    authBaseUrl: String = baseUrl,
    debug: Boolean = false,
): KavitaApi {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Eigener Client nur für Auth-Anfragen — kein Zirkel, kein Deadlock
    val authOnlyClient = OkHttpClient()

    val authInterceptor = KavitaAuthInterceptor(
        baseUrl = authBaseUrl,
        apiKey = apiKey,
        pluginName = pluginName,
        authClient = authOnlyClient,
    )

    val okHttpClient = OkHttpClient.Builder()
        .apply {
            if (debug) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
            }
        }
        // Auth-Interceptor kommt nach dem Logging, damit der Token im Log sichtbar ist
        .addInterceptor(authInterceptor)
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    return retrofit.create(KavitaApi::class.java)
}
