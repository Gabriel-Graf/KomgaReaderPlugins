package com.komgareader.plugin.calibre

import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.calibre.client.buildCalibreClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live test against a real Calibre Content Server. Gated by env CALIBRE_LIVE=1 so the
 * normal unit run skips it. Set CALIBRE_LIVE_URL (default http://localhost:8081),
 * CALIBRE_LIVE_LIBRARY (default Calibre_Library), and optional CALIBRE_LIVE_USER/PASS.
 */
class CalibreLiveTest {

    private fun env(k: String, d: String) = System.getenv(k)?.ifBlank { null } ?: d

    @Test fun `browse, books and download work against a real server`() = runBlocking {
        assumeTrue("set CALIBRE_LIVE=1 to run", System.getenv("CALIBRE_LIVE") == "1")
        val url = env("CALIBRE_LIVE_URL", "http://localhost:8081")
        val source = CalibreSource(
            api = buildCalibreClient("$url/", System.getenv("CALIBRE_LIVE_USER"), System.getenv("CALIBRE_LIVE_PASS"), debug = true),
            baseUrl = url,
            library = env("CALIBRE_LIVE_LIBRARY", "Calibre_Library"),
            name = "Live-Calibre",
        )
        val first = source.browse(1, SourceFilter())
        assertTrue("expected at least one shelf entry", first.items.isNotEmpty())
        // Find a series tile, list its volumes, download the first volume's bytes.
        val tile = first.items.first()
        val books = source.books(tile.remoteId)
        assertTrue("expected volumes", books.isNotEmpty())
        val bytes = source.downloadFile(books.first().remoteId) { _, _ -> }
        assertTrue("expected file bytes", bytes.isNotEmpty())
        // Cover bytes
        assertTrue(source.coverBytes(tile.remoteId, isSeriesCover = true).isNotEmpty())
    }
}
