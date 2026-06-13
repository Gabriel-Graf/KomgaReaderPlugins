package com.komgareader.plugin.kavita

import com.komgareader.plugin.kavita.client.buildKavitaClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integrationstests für den Kavita-Auth-Stack über [buildKavitaClient].
 *
 * Zwei MockWebServer-Instanzen:
 *   - [authServer]: bedient den Auth-Endpunkt (/api/Plugin/authenticate)
 *   - [apiServer]:  bedient echte API-Calls (simulate Kavita-Endpunkte)
 *
 * Verifiziert:
 *   1. Auth-Call erhält apiKey + pluginName als Query-Parameter und liefert Token zurück.
 *   2. Folge-Request nach Auth trägt "Authorization: Bearer <token>"-Header.
 *   3. 401-Refresh: Re-Auth + Retry mit neuem Token.
 */
class KavitaAuthTest {

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

    private fun authResponse(token: String = "jwt-test-token-abc123"): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"token":"$token","refreshToken":"","username":"testuser"}""")

    private fun apiOkResponse(token: String = "api-ok-token"): MockResponse =
        MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("""{"token":"$token","refreshToken":"","username":"testuser"}""")

    // ------------------------------------------------------------------ //
    // Test 1: Auth-Call enthält korrekte Query-Parameter                  //
    // ------------------------------------------------------------------ //

    @Test
    fun `auth-call sendet apiKey und pluginName als Query-Parameter`() = runBlocking {
        authServer.enqueue(authResponse("mein-jwt-token"))
        apiServer.enqueue(apiOkResponse())

        val api = buildKavitaClient(
            baseUrl = apiServer.url("/").toString(),
            apiKey = "test-api-key-123",
            pluginName = "KomgaReaderTest",
            authBaseUrl = authServer.url("/").toString(),
        )

        // Trigger: erster Aufruf löst Lazy-Auth aus
        api.authenticate(apiKey = "test-api-key-123", pluginName = "KomgaReaderTest")

        val authRequest = authServer.takeRequest()
        assertEquals("POST", authRequest.method)
        assertTrue(
            "URL muss /api/Plugin/authenticate enthalten (Ist: ${authRequest.path})",
            authRequest.path!!.contains("/api/Plugin/authenticate"),
        )
        assertTrue(
            "apiKey-Query-Parameter muss übermittelt werden (Ist: ${authRequest.path})",
            authRequest.path!!.contains("apiKey=test-api-key-123"),
        )
        assertTrue(
            "pluginName-Query-Parameter muss übermittelt werden (Ist: ${authRequest.path})",
            authRequest.path!!.contains("pluginName=KomgaReaderTest"),
        )
    }

    // ------------------------------------------------------------------ //
    // Test 2: Folge-Request trägt Bearer-Token                            //
    // ------------------------------------------------------------------ //

    @Test
    fun `folge-request traegt Authorization-Bearer-Header`() = runBlocking {
        authServer.enqueue(authResponse("bearer-token-xyz"))
        apiServer.enqueue(apiOkResponse())
        apiServer.enqueue(apiOkResponse())

        val api = buildKavitaClient(
            baseUrl = apiServer.url("/").toString(),
            apiKey = "key-abc",
            pluginName = "KomgaReaderTest",
            authBaseUrl = authServer.url("/").toString(),
        )

        // Erster Aufruf: Interceptor holt Token
        api.authenticate(apiKey = "key-abc", pluginName = "KomgaReaderTest")
        // Zweiter Aufruf: Token gecacht, trägt Bearer-Header
        api.authenticate(apiKey = "key-abc", pluginName = "KomgaReaderTest")

        apiServer.takeRequest()  // Erster Request
        val secondReq = apiServer.takeRequest()

        val authHeader = secondReq.getHeader("Authorization")
        assertTrue(
            "Zweiter Request muss Authorization: Bearer tragen (Ist: $authHeader)",
            authHeader != null && authHeader.startsWith("Bearer "),
        )
        assertEquals("Bearer bearer-token-xyz", authHeader)
    }

    // ------------------------------------------------------------------ //
    // Test 3: 401-Refresh — Re-Auth + Retry                              //
    // ------------------------------------------------------------------ //

    @Test
    fun `bei-401-wird-neu-authentifiziert-und-request-wiederholt`() = runBlocking {
        // Auth#1 → API-Versuch(401) → Auth#2 → API-Retry(200)
        authServer.enqueue(authResponse("token-first"))         // Auth#1
        apiServer.enqueue(MockResponse().setResponseCode(401))  // Versuch → 401
        authServer.enqueue(authResponse("token-second"))        // Auth#2 (Refresh)
        apiServer.enqueue(authResponse("token-second"))         // Retry → 200

        val api = buildKavitaClient(
            baseUrl = apiServer.url("/").toString(),
            apiKey = "key-refresh",
            pluginName = "KomgaReaderTest",
            authBaseUrl = authServer.url("/").toString(),
        )

        val result = api.authenticate(apiKey = "key-refresh", pluginName = "KomgaReaderTest")

        assertEquals("Genau 2 Auth-Requests erwartet", 2, authServer.requestCount)
        assertEquals("Genau 2 API-Requests erwartet (Versuch + Retry)", 2, apiServer.requestCount)

        // Ergebnis ist die letzte Antwort (token-second)
        assertEquals("token-second", result.token)
    }
}
