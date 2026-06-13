package com.komgareader.plugin.kavita

import com.komgareader.domain.model.BookFormat
import com.komgareader.domain.source.PageRef
import com.komgareader.domain.model.ReadProgress
import com.komgareader.plugin.kavita.client.buildKavitaClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MockWebServer-Vertragstest für [KavitaSource].
 *
 * Jede Testmethode verifiziert:
 *  1. Den korrekten HTTP-Endpunkt (Methode + Pfad)
 *  2. Das korrekte Mapping der Antwort auf Domain-Modelle
 *  3. Den leeren / null-Fall, wo er relevant ist
 *
 * Zwei MockWebServer-Instanzen:
 *  - [authServer]: bedient POST /api/Plugin/authenticate
 *  - [apiServer]:  bedient alle anderen API-Calls
 */
class KavitaSourceContractTest {

    private lateinit var authServer: MockWebServer
    private lateinit var apiServer: MockWebServer
    private lateinit var source: KavitaSource

    companion object {
        private const val API_KEY = "test-api-key"
        private const val TOKEN = "test-jwt-token"
        private const val PAGE_SIZE_RESPONSE = 2 // Für hasNextPage-Tests
    }

    @Before
    fun setUp() {
        authServer = MockWebServer()
        authServer.start()
        apiServer = MockWebServer()
        apiServer.start()

        // Auth-Token vorab bereitstellen
        authServer.enqueue(authResponse())

        val api = buildKavitaClient(
            baseUrl = apiServer.url("/").toString(),
            apiKey = API_KEY,
            pluginName = "KomgaReaderTest",
            authBaseUrl = authServer.url("/").toString(),
        )

        source = KavitaSource(
            api = api,
            baseUrl = apiServer.url("/").toString().trimEnd('/'),
            apiKey = API_KEY,
            name = "Test-Kavita",
        )
    }

    @After
    fun tearDown() {
        authServer.shutdown()
        apiServer.shutdown()
    }

    // ------------------------------------------------------------------ //
    // Hilfsmethoden                                                        //
    // ------------------------------------------------------------------ //

    private fun authResponse(): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody("""{"token":"$TOKEN","refreshToken":"","username":"testuser"}""")

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    /**
     * Wie [jsonResponse], aber mit X-Pagination-Header — für /api/Series/v2-Tests.
     *
     * [currentPage] und [totalPages] werden direkt in den JSON-Header kodiert,
     * damit der Grenzfall (letzte Seite, volle Seitengröße) testbar ist.
     */
    private fun seriesV2Response(
        body: String,
        currentPage: Int,
        totalPages: Int,
        itemsPerPage: Int = PAGE_SIZE_RESPONSE,
        totalItems: Int = 0,
    ): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setHeader(
            "X-Pagination",
            """{"currentPage":$currentPage,"totalPages":$totalPages,"itemsPerPage":$itemsPerPage,"totalItems":$totalItems}""",
        )
        .setBody(body)

    private fun emptyJsonArrayResponse(): MockResponse = jsonResponse("[]")

    private fun bytesResponse(content: ByteArray): MockResponse = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "image/jpeg")
        .setBody(okio.Buffer().write(content))

    /** Liest den nächsten Request, der kein Auth-Request ist. */
    private fun takeApiRequest(): RecordedRequest = apiServer.takeRequest()

    // ------------------------------------------------------------------ //
    // browse                                                               //
    // ------------------------------------------------------------------ //

    @Test
    fun `browse sendet POST an api-Series-v2 und mappt Serien`() = runBlocking {
        apiServer.enqueue(seriesV2Response(
            body = """
                [
                  {"id":1,"name":"Berserk","libraryId":2,"format":1},
                  {"id":2,"name":"Vagabond","libraryId":2,"format":1}
                ]
            """.trimIndent(),
            currentPage = 1,
            totalPages = 2,
        ))

        val result = source.browse(page = 1, filter = com.komgareader.domain.source.SourceFilter())

        val req = takeApiRequest()
        assertEquals("POST", req.method)
        assertTrue("Pfad muss /api/Series/v2 enthalten", req.path!!.contains("/api/Series/v2"))
        assertTrue("Query muss PageNumber enthalten", req.path!!.contains("PageNumber="))
        assertTrue("Query muss PageSize enthalten", req.path!!.contains("PageSize="))

        assertEquals(2, result.items.size)
        assertEquals("Berserk", result.items[0].title)
        assertEquals("1", result.items[0].remoteId)
        assertEquals("Vagabond", result.items[1].title)
    }

    @Test
    fun `browse liefert leere Liste bei leerem Server-Response`() = runBlocking {
        apiServer.enqueue(seriesV2Response(body = "[]", currentPage = 1, totalPages = 1))

        val result = source.browse(page = 1, filter = com.komgareader.domain.source.SourceFilter())

        assertTrue(result.items.isEmpty())
        assertFalse(result.hasNextPage)
    }

    @Test
    fun `browse hasNextPage true wenn currentPage kleiner totalPages`() = runBlocking {
        // Seite 1 von 4 — weiteres Laden erwartet
        apiServer.enqueue(seriesV2Response(
            body = """[{"id":1,"name":"Berserk","libraryId":2,"format":1}]""",
            currentPage = 1,
            totalPages = 4,
        ))

        val result = source.browse(page = 1, filter = com.komgareader.domain.source.SourceFilter())

        assertTrue("hasNextPage muss true sein wenn currentPage < totalPages", result.hasNextPage)
    }

    @Test
    fun `browse hasNextPage false auf letzter Seite auch wenn Body voll ist`() = runBlocking {
        // Grenzfall: letzte Seite hat genau PAGE_SIZE Einträge — die alte Heuristik
        // würde hier fälschlicherweise hasNextPage=true liefern; der Header korrigiert das.
        val fullPageBody = (1..PAGE_SIZE_RESPONSE)
            .joinToString(prefix = "[", postfix = "]") { i ->
                """{"id":$i,"name":"Serie $i","libraryId":1,"format":1}"""
            }
        apiServer.enqueue(seriesV2Response(
            body = fullPageBody,
            currentPage = 3,
            totalPages = 3, // das IST die letzte Seite
            itemsPerPage = PAGE_SIZE_RESPONSE,
        ))

        val result = source.browse(page = 3, filter = com.komgareader.domain.source.SourceFilter())

        assertEquals(PAGE_SIZE_RESPONSE, result.items.size)
        assertFalse(
            "hasNextPage muss false sein auf der letzten Seite, auch wenn Body voll ist",
            result.hasNextPage,
        )
    }

    @Test
    fun `browse fällt auf Heuristik zurück wenn X-Pagination-Header fehlt`() = runBlocking {
        // Antwort ohne X-Pagination-Header → Fallback: items.size >= PAGE_SIZE (50).
        // Mit 2 Einträgen greift die Heuristik korrekt auf false (unter der Schwelle).
        val partialBody = (1..PAGE_SIZE_RESPONSE)
            .joinToString(prefix = "[", postfix = "]") { i ->
                """{"id":$i,"name":"Serie $i","libraryId":1,"format":1}"""
            }
        apiServer.enqueue(jsonResponse(partialBody))

        val result = source.browse(page = 1, filter = com.komgareader.domain.source.SourceFilter())

        assertEquals(PAGE_SIZE_RESPONSE, result.items.size)
        assertFalse(
            "Fallback-Heuristik: hasNextPage=false wenn items.size < PAGE_SIZE und Header fehlt",
            result.hasNextPage,
        )
    }

    // ------------------------------------------------------------------ //
    // search                                                               //
    // ------------------------------------------------------------------ //

    @Test
    fun `search sendet GET an api-Search-search mit queryString`() = runBlocking {
        apiServer.enqueue(jsonResponse("""
            {
              "series": [
                {"seriesId":5,"name":"Berserk","libraryId":1,"format":1}
              ]
            }
        """.trimIndent()))

        val result = source.search("Berserk", page = 1)

        val req = takeApiRequest()
        assertEquals("GET", req.method)
        assertTrue("Pfad muss /api/Search/search enthalten", req.path!!.contains("/api/Search/search"))
        assertTrue("Query muss queryString enthalten", req.path!!.contains("queryString="))

        assertEquals(1, result.items.size)
        assertEquals("Berserk", result.items[0].title)
        assertEquals("5", result.items[0].remoteId)
        assertFalse("Suche ist nie paginiert", result.hasNextPage)
    }

    @Test
    fun `search liefert leere Liste bei page groeßer 1`() = runBlocking {
        val result = source.search("Test", page = 2)

        assertEquals(0, result.items.size)
        assertFalse(result.hasNextPage)
        // kein API-Call ausgelöst
        assertEquals(0, apiServer.requestCount)
    }

    @Test
    fun `search liefert leere Liste wenn series-Array fehlt oder leer`() = runBlocking {
        apiServer.enqueue(jsonResponse("""{"series":[]}"""))

        val result = source.search("Nichts", page = 1)

        assertTrue(result.items.isEmpty())
    }

    // ------------------------------------------------------------------ //
    // books                                                                //
    // ------------------------------------------------------------------ //

    @Test
    fun `books sendet GET an api-Series-series-detail und mappt Chapters`() = runBlocking {
        // series-detail-Antwort
        apiServer.enqueue(jsonResponse("""
            {
              "specials": [],
              "chapters": [],
              "volumes": [
                {
                  "id":1,"minNumber":1.0,"maxNumber":1.0,"seriesId":10,
                  "chapters": [
                    {"id":100,"range":"1","pages":32,"format":1,"title":"Kapitel 1","sortOrder":1.0}
                  ]
                }
              ],
              "storylineChapters": [],
              "unreadCount":1,"totalCount":1
            }
        """.trimIndent()))
        // search-Aufruf für seriesTitle
        apiServer.enqueue(jsonResponse("""{"series":[{"seriesId":10,"name":"Berserk","libraryId":1}]}"""))

        val books = source.books("10")

        // Erster Request: series-detail
        val req1 = takeApiRequest()
        assertTrue("Pfad muss series-detail enthalten", req1.path!!.contains("series-detail"))

        assertEquals(1, books.size)
        assertEquals("100", books[0].remoteId)
        assertEquals("Kapitel 1", books[0].title)
        assertEquals(32, books[0].pageCount)
        assertEquals(BookFormat.CBZ, books[0].format)
    }

    @Test
    fun `books liefert leere Liste bei ungültiger seriesId`() = runBlocking {
        val books = source.books("abc")
        assertTrue(books.isEmpty())
    }

    @Test
    fun `books liefert leere Liste wenn series-detail 500 zurückgibt`() = runBlocking {
        apiServer.enqueue(MockResponse().setResponseCode(500))

        val books = source.books("99")
        assertTrue(books.isEmpty())
    }

    // ------------------------------------------------------------------ //
    // seriesDetail                                                         //
    // ------------------------------------------------------------------ //

    @Test
    fun `seriesDetail enrichiert Series mit Metadaten`() = runBlocking {
        // search für Basis-Daten
        apiServer.enqueue(jsonResponse("""
            {"series":[{"seriesId":1,"name":"Berserk","libraryId":2}]}
        """.trimIndent()))
        // metadata
        apiServer.enqueue(jsonResponse("""
            {
              "summary":"Dark fantasy manga",
              "genres":[{"id":1,"title":"Action"},{"id":2,"title":"Drama"}],
              "publicationStatus":0
            }
        """.trimIndent()))

        val series = source.seriesDetail("1")

        assertEquals("Berserk", series?.title)
        assertEquals("Dark fantasy manga", series?.summary)
        assertEquals(listOf("Action", "Drama"), series?.genres)
        assertEquals("ONGOING", series?.status)
    }

    @Test
    fun `seriesDetail gibt null zurück bei ungültiger remoteId`() = runBlocking {
        val series = source.seriesDetail("not-a-number")
        assertNull(series)
    }

    // ------------------------------------------------------------------ //
    // pages                                                                //
    // ------------------------------------------------------------------ //

    @Test
    fun `pages sendet GET an api-Reader-chapter-info und erzeugt PageRef-Liste`() = runBlocking {
        apiServer.enqueue(jsonResponse("""
            {
              "chapterNumber":"1","volumeNumber":"1","volumeId":1,
              "seriesName":"Berserk","seriesId":1,"libraryId":2,
              "pages":3,"isSpecial":false,"title":"Kapitel 1",
              "seriesFormat":1
            }
        """.trimIndent()))

        val refs = source.pages("42")

        val req = takeApiRequest()
        assertTrue("Pfad muss chapter-info enthalten", req.path!!.contains("chapter-info"))
        assertTrue("Query muss chapterId=42 enthalten", req.path!!.contains("chapterId=42"))

        assertEquals(3, refs.size)
        assertEquals(0, refs[0].index)
        assertEquals(1, refs[0].pageNumber)
        assertEquals("42", refs[0].bookRemoteId)
        assertTrue("URL muss chapterId enthalten", refs[0].url.contains("chapterId=42"))
        assertTrue("URL muss page=0 enthalten", refs[0].url.contains("page=0"))
        assertTrue("URL muss apiKey enthalten", refs[0].url.contains("apiKey="))
    }

    @Test
    fun `pages liefert leere Liste wenn pageCount 0`() = runBlocking {
        apiServer.enqueue(jsonResponse("""
            {"pages":0,"seriesId":1,"volumeId":1,"libraryId":1,"seriesFormat":1}
        """.trimIndent()))

        val refs = source.pages("1")
        assertTrue(refs.isEmpty())
    }

    // ------------------------------------------------------------------ //
    // openPage                                                             //
    // ------------------------------------------------------------------ //

    @Test
    fun `openPage sendet GET an api-Reader-image und gibt Bytes zurück`() = runBlocking {
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) // JPEG-Header
        apiServer.enqueue(bytesResponse(imageBytes))

        val ref = PageRef(index = 0, bookRemoteId = "42", pageNumber = 1, url = "")
        val result = source.openPage(ref)

        val req = takeApiRequest()
        assertEquals("GET", req.method)
        assertTrue("Pfad muss /api/Reader/image enthalten", req.path!!.contains("/api/Reader/image"))
        assertTrue("Query muss chapterId=42 enthalten", req.path!!.contains("chapterId=42"))
        assertTrue("Query muss page=0 enthalten", req.path!!.contains("page=0"))
        assertTrue("Query muss apiKey enthalten", req.path!!.contains("apiKey="))

        assertEquals(3, result.size)
        assertEquals(0xFF.toByte(), result[0])
    }

    // ------------------------------------------------------------------ //
    // coverBytes                                                           //
    // ------------------------------------------------------------------ //

    @Test
    fun `coverBytes series sendet GET an api-Image-series-cover`() = runBlocking {
        val coverData = byteArrayOf(1, 2, 3, 4)
        apiServer.enqueue(bytesResponse(coverData))

        val result = source.coverBytes("7", isSeriesCover = true)

        val req = takeApiRequest()
        assertTrue("Pfad muss series-cover enthalten", req.path!!.contains("series-cover"))
        assertTrue("Query muss seriesId=7 enthalten", req.path!!.contains("seriesId=7"))

        assertEquals(4, result.size)
    }

    @Test
    fun `coverBytes chapter sendet GET an api-Image-chapter-cover`() = runBlocking {
        val coverData = byteArrayOf(5, 6, 7)
        apiServer.enqueue(bytesResponse(coverData))

        val result = source.coverBytes("100", isSeriesCover = false)

        val req = takeApiRequest()
        assertTrue("Pfad muss chapter-cover enthalten", req.path!!.contains("chapter-cover"))
        assertTrue("Query muss chapterId=100 enthalten", req.path!!.contains("chapterId=100"))

        assertEquals(3, result.size)
    }

    // ------------------------------------------------------------------ //
    // downloadFile                                                         //
    // ------------------------------------------------------------------ //

    @Test
    fun `downloadFile sendet GET an api-Download-chapter`() = runBlocking {
        val fileBytes = ByteArray(10) { it.toByte() }
        apiServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/zip")
                .setBody(okio.Buffer().write(fileBytes)),
        )

        val result = source.downloadFile("55")

        val req = takeApiRequest()
        assertEquals("GET", req.method)
        assertTrue("Pfad muss /api/Download/chapter enthalten", req.path!!.contains("/api/Download/chapter"))
        assertTrue("Query muss chapterId=55 enthalten", req.path!!.contains("chapterId=55"))

        assertEquals(10, result.size)
    }

    // ------------------------------------------------------------------ //
    // seriesIdOf                                                           //
    // ------------------------------------------------------------------ //

    @Test
    fun `seriesIdOf gibt seriesId aus chapter-info zurück`() = runBlocking {
        apiServer.enqueue(jsonResponse("""
            {"seriesId":42,"volumeId":1,"libraryId":2,"pages":20,"seriesFormat":1}
        """.trimIndent()))

        val seriesId = source.seriesIdOf("99")

        val req = takeApiRequest()
        assertTrue("Pfad muss chapter-info enthalten", req.path!!.contains("chapter-info"))

        assertEquals("42", seriesId)
    }

    // ------------------------------------------------------------------ //
    // pushProgress                                                         //
    // ------------------------------------------------------------------ //

    @Test
    fun `pushProgress sendet POST an api-Reader-progress mit korrektem Body`() = runBlocking {
        // chapterInfo für volumeId, seriesId, libraryId
        apiServer.enqueue(jsonResponse("""
            {"seriesId":5,"volumeId":2,"libraryId":3,"pages":30,"seriesFormat":1}
        """.trimIndent()))
        // progress-Antwort (kein Body benötigt)
        apiServer.enqueue(MockResponse().setResponseCode(200))

        val progress = ReadProgress(bookId = 0L, page = 15, totalPages = 30, updatedAt = 0L)
        source.pushProgress("10", progress)

        takeApiRequest() // chapter-info
        val progressReq = takeApiRequest()
        assertEquals("POST", progressReq.method)
        assertTrue("Pfad muss /api/Reader/progress enthalten", progressReq.path!!.contains("/api/Reader/progress"))

        val body = progressReq.body.readUtf8()
        assertTrue("Body muss chapterId enthalten", body.contains("\"chapterId\""))
        assertTrue("Body muss pageNum:15 enthalten", body.contains("\"pageNum\":15"))
    }

    // ------------------------------------------------------------------ //
    // pullProgress                                                         //
    // ------------------------------------------------------------------ //

    @Test
    fun `pullProgress gibt ReadProgress zurück wenn Fortschritt vorhanden`() = runBlocking {
        // get-progress
        apiServer.enqueue(jsonResponse("""
            {"chapterId":10,"volumeId":1,"seriesId":2,"libraryId":3,"pageNum":8,"lastModifiedUtc":"2024-06-01T10:00:00Z"}
        """.trimIndent()))
        // chapter-info für totalPages
        apiServer.enqueue(jsonResponse("""
            {"seriesId":2,"volumeId":1,"libraryId":3,"pages":20,"seriesFormat":1}
        """.trimIndent()))

        val progress = source.pullProgress("10")

        assertEquals(8, progress?.page)
        assertEquals(20, progress?.totalPages)
        assertFalse(progress?.completed ?: true)
        assertTrue("updatedAt muss > 0", (progress?.updatedAt ?: 0L) > 0L)
    }

    @Test
    fun `pullProgress gibt null zurück wenn pageNum 0 und kein Zeitstempel`() = runBlocking {
        apiServer.enqueue(jsonResponse("""
            {"chapterId":1,"volumeId":0,"seriesId":0,"libraryId":0,"pageNum":0,"lastModifiedUtc":""}
        """.trimIndent()))

        val progress = source.pullProgress("1")

        assertNull("pageNum=0 ohne Zeitstempel gilt als kein Fortschritt", progress)
    }

    // ------------------------------------------------------------------ //
    // setRead                                                              //
    // ------------------------------------------------------------------ //

    @Test
    fun `setRead true sendet POST an api-Reader-mark-read mit seriesId`() = runBlocking {
        // chapter-info für seriesId
        apiServer.enqueue(jsonResponse("""
            {"seriesId":7,"volumeId":1,"libraryId":1,"pages":20,"seriesFormat":1}
        """.trimIndent()))
        // mark-read
        apiServer.enqueue(MockResponse().setResponseCode(200))

        source.setRead("10", read = true, pageCount = 20)

        takeApiRequest() // chapter-info
        val markReq = takeApiRequest()
        assertEquals("POST", markReq.method)
        assertTrue("Pfad muss mark-read enthalten", markReq.path!!.contains("mark-read"))
        assertTrue("Body muss seriesId:7 enthalten", markReq.body.readUtf8().contains("\"seriesId\":7"))
    }

    @Test
    fun `setRead false sendet POST an api-Reader-mark-unread`() = runBlocking {
        apiServer.enqueue(jsonResponse("""
            {"seriesId":7,"volumeId":1,"libraryId":1,"pages":20,"seriesFormat":1}
        """.trimIndent()))
        apiServer.enqueue(MockResponse().setResponseCode(200))

        source.setRead("10", read = false, pageCount = 20)

        takeApiRequest() // chapter-info
        val markReq = takeApiRequest()
        assertTrue("Pfad muss mark-unread enthalten", markReq.path!!.contains("mark-unread"))
    }
}
