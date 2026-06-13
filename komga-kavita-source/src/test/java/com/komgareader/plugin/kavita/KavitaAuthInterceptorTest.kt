package com.komgareader.plugin.kavita

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.komgareader.plugin.kavita.api.KavitaApi
import com.komgareader.plugin.kavita.client.KavitaAuthInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Gezielte Unit-Tests für [KavitaAuthInterceptor].
 *
 * Zwei MockWebServer-Instanzen:
 *   - [authServer]: simuliert den Auth-Endpunkt (genutzter [KavitaAuthInterceptor.authClient])
 *   - [apiServer]:  simuliert echte API-Aufrufe (Hauptclient mit Interceptor)
 *
 * Dadurch wird der Zirkel aufgebrochen: der Auth-Call läuft über [authServer],
 * die eigentlichen API-Calls über [apiServer]. Kein runTest/runBlocking-Konflikt —
 * Interceptoren sind synchron, Tests sind einfache JUnit-4-Tests ohne Coroutine-Dispatcher.
 *
 *  A. Auth-Request sendet apiKey + pluginName als Query-Parameter.
 *  B. Folge-Request trägt "Authorization: Bearer <token>".
 *  C. 401-Refresh: Re-Auth + Retry — exakt 4 Requests insgesamt (2 Auth, 2 API).
 *  D. Token-Caching: zweiter Request braucht keinen neuen Auth-Call.
 */
class KavitaAuthInterceptorTest {

    private lateinit var authServer: MockWebServer
    private lateinit var apiServer: MockWebServer

    @Before
    fun setUp() {
        authServer = MockWebServer()
        authServer.start()
        apiServer = MockWebServer()
        apiServer.start()
    }

    @After
    fun tearDown() {
        authServer.shutdown()
        apiServer.shutdown()
    }

    // ------------------------------------------------------------------ //
    // Hilfsmethoden                                                        //
    // ------------------------------------------------------------------ //

    private fun authJson(token: String) =
        """{"token":"$token","refreshToken":"","username":"test"}"""

    private fun authResponse(token: String) =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(authJson(token))

    private fun apiOkResponse(token: String = "api-response-token") =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"token":"$token","refreshToken":"","username":"test"}""")

    /**
     * Baut eine [KavitaApi] über den [apiServer], mit einem [KavitaAuthInterceptor],
     * der den [authServer] für Token-Abrufe nutzt.
     */
    private fun buildApi(
        apiKey: String = "my-api-key",
        pluginName: String = "TestPlugin",
    ): KavitaApi {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }

        // Auth-Client zeigt auf authServer (kein Interceptor, kein Zirkel)
        val authOnlyClient = OkHttpClient.Builder()
            .build()

        val interceptor = KavitaAuthInterceptor(
            baseUrl = authServer.url("/").toString(),
            apiKey = apiKey,
            pluginName = pluginName,
            authClient = authOnlyClient,
        )

        val mainClient = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(apiServer.url("/").toString())
            .client(mainClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(KavitaApi::class.java)
    }

    // ------------------------------------------------------------------ //
    // A. Auth-Request enthält korrekte Query-Parameter                    //
    // ------------------------------------------------------------------ //

    @Test
    fun `erster request holt token mit korrektem apiKey und pluginName`() {
        // Auth-Server liefert Token, API-Server liefert OK
        authServer.enqueue(authResponse("erster-token"))
        apiServer.enqueue(apiOkResponse())

        val api = buildApi(apiKey = "test-key", pluginName = "TestPlugin")

        // Suspend-Call via runBlocking ist hier sicher — kein zirkulärer Aufruf mehr
        kotlinx.coroutines.runBlocking {
            api.authenticate("test-key", "TestPlugin")
        }

        // Auth-Request prüfen
        val authReq = authServer.takeRequest()
        assertEquals("POST", authReq.method)
        assertTrue(
            "Pfad muss api/Plugin/authenticate enthalten (Ist: ${authReq.path})",
            authReq.path!!.contains("api/Plugin/authenticate"),
        )
        assertTrue(
            "apiKey muss als Query-Parameter übergeben werden (Ist: ${authReq.path})",
            authReq.path!!.contains("apiKey=test-key"),
        )
        assertTrue(
            "pluginName muss als Query-Parameter übergeben werden (Ist: ${authReq.path})",
            authReq.path!!.contains("pluginName=TestPlugin"),
        )
    }

    // ------------------------------------------------------------------ //
    // B. Folge-Request trägt Authorization: Bearer-Header                 //
    // ------------------------------------------------------------------ //

    @Test
    fun `folge-request nach gecachtem token traegt Bearer-Header`() {
        // Auth einmalig, dann zwei API-Calls
        authServer.enqueue(authResponse("mein-bearer-token"))
        apiServer.enqueue(apiOkResponse())
        apiServer.enqueue(apiOkResponse())

        val api = buildApi(apiKey = "key-bearer", pluginName = "TestPlugin")

        kotlinx.coroutines.runBlocking {
            // Erster Aufruf: Interceptor holt Token
            api.authenticate("key-bearer", "TestPlugin")
            // Zweiter Aufruf: Token gecacht, kein neuer Auth-Call
            api.authenticate("key-bearer", "TestPlugin")
        }

        // Nur EIN Auth-Request erwartet
        assertEquals("Genau 1 Auth-Request erwartet", 1, authServer.requestCount)

        // Zweiter API-Request muss Bearer tragen
        apiServer.takeRequest()                   // Erster Request (kein Header nötig hier)
        val secondReq = apiServer.takeRequest()   // Zweiter Request
        val authHeader = secondReq.getHeader("Authorization")
        assertNotNull("Authorization-Header muss vorhanden sein", authHeader)
        assertTrue(
            "Header muss mit 'Bearer ' beginnen (Ist: $authHeader)",
            authHeader!!.startsWith("Bearer "),
        )
        assertEquals("Bearer mein-bearer-token", authHeader)
    }

    // ------------------------------------------------------------------ //
    // C. 401-Refresh: Re-Auth + Retry — exakt 4 Requests                 //
    // ------------------------------------------------------------------ //

    @Test
    fun `bei 401 wird re-authentifiziert und request wiederholt`() {
        // Auth#1 → API-Versuch(401) → Auth#2 → API-Retry(200)
        authServer.enqueue(authResponse("token-v1"))            // Auth#1
        apiServer.enqueue(MockResponse().setResponseCode(401))  // API-Versuch → 401
        authServer.enqueue(authResponse("token-v2"))            // Auth#2 (Refresh)
        apiServer.enqueue(apiOkResponse())                      // API-Retry → 200

        val api = buildApi(apiKey = "refresh-key", pluginName = "TestPlugin")

        kotlinx.coroutines.runBlocking {
            api.authenticate("refresh-key", "TestPlugin")
        }

        // Auth-Server: genau 2 Aufrufe
        assertEquals("Genau 2 Auth-Requests erwartet", 2, authServer.requestCount)
        // API-Server: genau 2 Aufrufe (Versuch + Retry)
        assertEquals("Genau 2 API-Requests erwartet", 2, apiServer.requestCount)

        // Retry muss frisches Token tragen
        apiServer.takeRequest() // Versuch#1 (401)
        val retryReq = apiServer.takeRequest()
        val authHeader = retryReq.getHeader("Authorization")
        assertEquals("Bearer token-v2", authHeader)
    }

    // ------------------------------------------------------------------ //
    // D. Token-Caching: zweiter Request kein extra Auth-Call              //
    // ------------------------------------------------------------------ //

    @Test
    fun `gecachter token vermeidet zweiten auth-call`() {
        // Nur EIN Auth-Call, danach zwei API-Calls
        authServer.enqueue(authResponse("cached-token"))
        apiServer.enqueue(apiOkResponse())
        apiServer.enqueue(apiOkResponse())

        val api = buildApi(apiKey = "cache-key", pluginName = "TestPlugin")

        kotlinx.coroutines.runBlocking {
            api.authenticate("cache-key", "TestPlugin")  // Aufruf #1 → löst Auth aus
            api.authenticate("cache-key", "TestPlugin")  // Aufruf #2 → Token gecacht
        }

        assertEquals(
            "Genau 1 Auth-Request erwartet (Token gecacht)",
            1,
            authServer.requestCount,
        )
        assertEquals(
            "Genau 2 API-Requests erwartet",
            2,
            apiServer.requestCount,
        )
    }
}
