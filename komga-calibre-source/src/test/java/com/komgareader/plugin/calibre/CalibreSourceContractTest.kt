package com.komgareader.plugin.calibre

import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.calibre.client.buildCalibreClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CalibreSourceContractTest {

    private lateinit var server: MockWebServer
    private lateinit var source: CalibreSource

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(req: RecordedRequest): MockResponse {
                val path = req.path ?: ""
                return when {
                    path.startsWith("/ajax/categories/") -> json(
                        """[{"name":"Series","url":"/ajax/category/c2VyaWVz"},{"name":"Authors","url":"/ajax/category/YXV0aA=="}]"""
                    )
                    path.startsWith("/ajax/category/") -> json(
                        """{"total_num":1,"items":[{"name":"Saga","count":2}]}"""
                    )
                    // standalone count probe + page (query=series:false)
                    path.startsWith("/ajax/search/") && path.contains("series%3Afalse") -> json(
                        """{"total_num":1,"book_ids":[9]}"""
                    )
                    // empty series case: query contains URL-encoded "Empty"
                    path.contains("Empty") -> json(
                        """{"total_num":0,"book_ids":[]}"""
                    )
                    // books(series) resolution: query=series:"Saga"
                    path.startsWith("/ajax/search/") -> json(
                        """{"total_num":2,"book_ids":[1,2]}"""
                    )
                    path.startsWith("/ajax/books/") -> json(
                        """{"1":{"title":"Saga 1","series":"Saga","series_index":1.0,"formats":["EPUB"]},
                            "2":{"title":"Saga 2","series":"Saga","series_index":2.0,"formats":["EPUB"]},
                            "9":{"title":"Solo","formats":["PDF"]}}"""
                    )
                    path.startsWith("/ajax/book/") -> json(
                        """{"title":"Solo","formats":["PDF"]}"""
                    )
                    path.startsWith("/get/") -> MockResponse().setResponseCode(200).setBody("BYTES")
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        source = CalibreSource(
            api = buildCalibreClient(server.url("/").toString(), username = null, password = null),
            baseUrl = server.url("/").toString().trimEnd('/'),
            library = "Calibre_Library",
            name = "Test-Calibre",
        )
    }

    @After fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test fun `browse returns series tiles then standalone`() = runBlocking {
        val page1 = source.browse(1, SourceFilter())
        assertTrue(page1.items.any { it.title == "Saga" })
        // seriesTotal=1, pageSize=50 -> 1 series page; standaloneTotal=1 -> page 2 has the standalone
        assertTrue(page1.hasNextPage)
        val page2 = source.browse(2, SourceFilter())
        assertTrue(page2.items.any { it.title == "Solo" })
    }

    @Test fun `books resolves a series sorted by index`() = runBlocking {
        val rid = CalibreRemoteId.forSeries("Saga")
        val books = source.books(rid)
        assertEquals(listOf("Saga 1", "Saga 2"), books.map { it.title })
    }

    @Test fun `pages is empty (whole-file read)`() = runBlocking {
        assertTrue(source.pages("1").isEmpty())
    }

    @Test fun `coverBytes returns empty bytes for empty series`() = runBlocking {
        val emptySeriesId = CalibreRemoteId.forSeries("Empty")
        val bytes = source.coverBytes(emptySeriesId, isSeriesCover = true)
        assertTrue("coverBytes should return empty ByteArray for empty series", bytes.isEmpty())
        // Verify that no /get/cover/null/... request was made (would have matched /get/ pattern)
        // The absence of a null URL in requests means no malformed request was sent
    }
}
