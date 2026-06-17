package com.komgareader.plugin.calibre

import com.komgareader.plugin.calibre.client.CalibreAuthInterceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalibreAuthInterceptorTest {

    @Test fun `adds Basic header when username present`() {
        val server = MockWebServer().apply { start(); enqueue(MockResponse().setResponseCode(200)) }
        val client = OkHttpClient.Builder()
            .addInterceptor(CalibreAuthInterceptor(username = "alice", password = "pw"))
            .build()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        val recorded = server.takeRequest()
        // base64("alice:pw") == "YWxpY2U6cHc="
        assertEquals("Basic YWxpY2U6cHc=", recorded.getHeader("Authorization"))
        server.shutdown()
    }

    @Test fun `no header when username blank`() {
        val server = MockWebServer().apply { start(); enqueue(MockResponse().setResponseCode(200)) }
        val client = OkHttpClient.Builder()
            .addInterceptor(CalibreAuthInterceptor(username = "", password = ""))
            .build()
        client.newCall(Request.Builder().url(server.url("/x")).build()).execute().close()
        assertNull(server.takeRequest().getHeader("Authorization"))
        server.shutdown()
    }
}
