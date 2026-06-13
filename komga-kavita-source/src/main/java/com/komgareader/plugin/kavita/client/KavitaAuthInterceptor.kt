package com.komgareader.plugin.kavita.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * OkHttp-Interceptor für die Kavita-Authentifizierung.
 *
 * Ablauf:
 *   1. Erster Aufruf: apiKey → POST /api/Plugin/authenticate → JWT cachen.
 *   2. JWT als "Authorization: Bearer <token>" an jeden Request anhängen.
 *   3. Bei 401: einmalig erneut authentifizieren und Request wiederholen.
 *
 * Thread-sicher: [lock] schützt [cachedToken] vor Race Conditions bei
 * gleichzeitigen 401-Retries aus parallelen Coroutines.
 *
 * Auth-Aufruf über [authClient] — ein eigener, interceptor-freier OkHttpClient,
 * der nur für den Token-Hol-Request genutzt wird. Das verhindert den Zirkel:
 * Hauptclient → Interceptor → Auth-Call → Hauptclient → Deadlock.
 *
 * Verifizierter Kavita-Endpunkt (OpenAPI, Stand 2026-06-11):
 *   POST /api/Plugin/authenticate?apiKey={key}&pluginName={name}
 *   → { "token": "...", "refreshToken": "...", ... }
 *
 * @param baseUrl    Basis-URL der Kavita-Instanz für Auth-Anfragen.
 * @param apiKey     Vom Nutzer hinterlegter API-Schlüssel.
 * @param pluginName Bezeichner des Plugins (wird an Kavita übergeben).
 * @param authClient Eigener OkHttpClient für Auth-Anfragen (ohne diesen Interceptor).
 */
internal class KavitaAuthInterceptor(
    private val baseUrl: String,
    private val apiKey: String,
    private val pluginName: String,
    private val authClient: OkHttpClient = OkHttpClient(),
) : Interceptor {

    /** Gespeicherter JWT-Token; null = noch nicht authentifiziert. */
    @Volatile
    private var cachedToken: String? = null

    private val lock = ReentrantLock()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun intercept(chain: Interceptor.Chain): Response {
        // Sicherstellen, dass ein Token vorhanden ist (Lazy-Auth beim ersten Aufruf)
        val token = getOrFetchToken()

        val response = chain.proceed(chain.request().withBearer(token))

        // 401 → einmalig re-authentifizieren und wiederholen
        if (response.code == 401) {
            response.close()
            val freshToken = lock.withLock {
                // Doppelter Check: ein anderer Thread könnte bereits neu geholt haben
                val currentToken = cachedToken
                if (currentToken != null && currentToken != token) {
                    currentToken
                } else {
                    fetchToken().also { cachedToken = it }
                }
            }
            return chain.proceed(chain.request().withBearer(freshToken))
        }

        return response
    }

    private fun getOrFetchToken(): String =
        cachedToken ?: lock.withLock {
            cachedToken ?: fetchToken().also { cachedToken = it }
        }

    /**
     * Führt den Auth-Call synchron über [authClient] durch.
     * Kein Retrofit, kein Coroutine-Kontext — reines OkHttp, damit kein Deadlock entsteht.
     */
    private fun fetchToken(): String {
        val authUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/Plugin/authenticate")
            .addQueryParameter("apiKey", apiKey)
            .addQueryParameter("pluginName", pluginName)
            .build()

        val request = Request.Builder()
            .url(authUrl)
            .post(ByteArray(0).toRequestBody(null))
            .build()

        val response = authClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("Kavita-Auth fehlgeschlagen: HTTP ${response.code}")
        }
        val body = response.body?.string()
            ?: throw IOException("Kavita-Auth lieferte leeren Body")

        return try {
            json.parseToJsonElement(body).jsonObject["token"]?.jsonPrimitive?.content
                ?: throw IOException("Kein 'token'-Feld in Kavita-Auth-Antwort")
        } catch (e: SerializationException) {
            throw IOException("Kavita-Auth-Antwort nicht parsebar: ${e.message}", e)
        }
    }

    /** Setzt den gecachten Token zurück (z. B. bei Passwort-Änderung oder in Tests). */
    internal fun clearToken() {
        lock.withLock { cachedToken = null }
    }
}

private fun Request.withBearer(token: String): Request =
    newBuilder()
        .header("Authorization", "Bearer $token")
        .build()
