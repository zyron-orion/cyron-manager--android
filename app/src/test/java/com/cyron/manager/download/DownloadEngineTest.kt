package com.cyron.manager.download

import com.cyron.manager.domain.model.DownloadName
import com.cyron.manager.domain.model.ProbeResult
import com.cyron.manager.domain.model.ByteRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadEngineTest {
    @Test fun splittingCoversTheCompleteFileExactlyOnce() {
        for (size in 1L..2_000L) for (connections in 1..16) {
            val segments = OkHttpDownloadEngine.splitRange(size, connections)
            assertEquals(minOf(size, connections.toLong()).toInt(), segments.size)
            assertEquals(0L, segments.first().start)
            assertEquals(size - 1, segments.last().endInclusive)
            assertEquals(size, segments.sumOf(ByteRange::length))
            assertTrue(segments.zipWithNext().all { (a, b) -> a.endInclusive + 1 == b.start })
            assertTrue(segments.maxOf(ByteRange::length) - segments.minOf(ByteRange::length) <= 1)
        }
    }

    @Test fun oneConnectionUsesOneOrdinaryRequestSizedRange() {
        assertEquals(listOf(ByteRange(0, 0, 999)), OkHttpDownloadEngine.splitRange(1000, 1))
    }

    @Test fun moreConnectionsThanBytesNeverCreatesEmptyRanges() {
        assertEquals(listOf(ByteRange(0, 0, 0), ByteRange(1, 1, 1)), OkHttpDownloadEngine.splitRange(2, 16))
    }

    @Test fun unknownLengthIsNeverDividedIntoBogusRanges() {
        assertTrue(runCatching { OkHttpDownloadEngine.splitRange(-1, 4) }.isFailure)
        assertTrue(runCatching { OkHttpDownloadEngine.splitRange(0, 4) }.isFailure)
    }

    @Test fun validatorsAreCarriedFromProbeToTransfer() {
        val probe = ProbeResult("https://example.test/a", 100, true, "\"v1\"", "Wed, 01 Jan 2025 00:00:00 GMT", "application/octet-stream")
        assertEquals("\"v1\"", probe.etag)
        assertEquals(100, probe.totalBytes)
        assertTrue(probe.rangeSupported)
    }
}
