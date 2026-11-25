package com.cyron.manager.download

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpRangeProtocolTest {
    @Test fun rangeRequestsReturnExactAdjacentBytesAndReassembleOriginalFile() {
        val server = MockWebServer()
        val payload = ByteArray(10_013) { ((it * 73) % 256).toByte() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method == "HEAD") return MockResponse()
                    .setHeader("Content-Length", payload.size)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("ETag", "\"fixed-1\"")
                val header = request.getHeader("Range") ?: return MockResponse()
                    .setResponseCode(200).setBody(okio.Buffer().write(payload))
                val match = Regex("bytes=(\\d+)-(\\d+)").matchEntire(header)
                    ?: return MockResponse().setResponseCode(416)
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toInt()
                if (start !in payload.indices || end !in start until payload.size) return MockResponse().setResponseCode(416)
                return MockResponse().setResponseCode(206)
                    .setHeader("Content-Range", "bytes $start-$end/${payload.size}")
                    .setHeader("Content-Length", end - start + 1)
                    .setHeader("ETag", "\"fixed-1\"")
                    .setBody(okio.Buffer().write(payload, start, end - start + 1))
            }
        }
        server.start()
        try {
            val client = OkHttpClient()
            val ranges = OkHttpDownloadEngine.splitRange(payload.size.toLong(), 7)
            val reassembled = ByteArrayOutputStream()
            ranges.forEach { range ->
                client.newCall(Request.Builder().url(server.url("asset.bin"))
                    .header("Range", "bytes=${range.start}-${range.endInclusive}")
                    .header("If-Range", "\"fixed-1\"").build()).execute().use { response ->
                    assertEquals(206, response.code)
                    assertEquals("bytes ${range.start}-${range.endInclusive}/${payload.size}", response.header("Content-Range"))
                    val segment = response.body!!.bytes()
                    assertEquals(range.length, segment.size.toLong())
                    reassembled.write(segment)
                }
            }
            assertTrue(payload.contentEquals(reassembled.toByteArray()))
            assertEquals(7, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun serverThatIgnoresRangeMustNotBeMistakenForPartialContent() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(200).setBody("full entity"))
        server.start()
        try {
            OkHttpClient().newCall(Request.Builder().url(server.url("file.bin"))
                .header("Range", "bytes=0-3").build()).execute().use { response ->
                assertEquals(200, response.code)
                assertEquals("full entity", response.body!!.string())
                assertTrue(response.header("Content-Range") == null)
            }
        } finally { server.shutdown() }
    }

    @Test fun redirectedRangePreservesRangeHeaderAndUsesFinalOriginPath() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/files/final.bin"))
        server.enqueue(MockResponse().setResponseCode(206).setHeader("Content-Range", "bytes 4-7/8").setBody("part"))
        server.start()
        try {
            OkHttpClient().newCall(Request.Builder().url(server.url("redirect"))
                .header("Range", "bytes=4-7").build()).execute().use { response ->
                assertEquals(206, response.code)
                assertEquals("part", response.body!!.string())
                assertTrue(response.request.url.encodedPath.endsWith("/files/final.bin"))
            }
            assertEquals("bytes=4-7", server.takeRequest().getHeader("Range"))
            assertEquals("bytes=4-7", server.takeRequest().getHeader("Range"))
        } finally { server.shutdown() }
    }

    @Test fun sha256OfPayloadMatchesExpectedDigest() {
        val payload = "complete verified download".encodeToByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        assertEquals("648220b48175cb9fdf3e07378e7fbad1ae5cc89c1caf9ea6e7ad732064898a27", digest)
    }
}
